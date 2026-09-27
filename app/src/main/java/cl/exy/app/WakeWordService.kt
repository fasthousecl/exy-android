package cl.exy.app

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Servicio en primer plano que mantiene a Vosk escuchando «oye exi» y sus
 * comandos (ver [Comandos]), y abre la app que corresponda.
 *
 * Todo lo que toca a Vosk (cargar el modelo, abrir y cerrar el micrófono) corre
 * en un único hilo de trabajo, en orden. El estado y la notificación se manejan
 * en el hilo principal, que es también donde Vosk entrega sus resultados.
 * [generation] invalida resultados viejos: si el usuario pausa mientras la
 * escucha se está iniciando, ese inicio se descarta.
 */
class WakeWordService : Service() {

    enum class State { STOPPED, STARTING, LISTENING, PAUSED, COOLDOWN, ERROR }

    private val handler = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()

    /** Solo se leen y escriben desde [worker]. El modelo se carga una vez y se reutiliza. */
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speech: SpeechService? = null

    /** Solo se usan desde el hilo principal. */
    private var generation = 0
    private var inForeground = false
    private var resumeAtWall = 0L
    private var wakeLock: PowerManager.WakeLock? = null

    private val resumeRunnable = Runnable { startListening() }

    override fun onCreate() {
        super.onCreate()
        LibVosk.setLogLevel(LogLevel.WARNINGS)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }

        if (!inForeground && !promoteToForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> startListening()
            ACTION_RELOAD -> if (state == State.LISTENING || state == State.ERROR) startListening()
            // ACTION_START, o reinicio del sistema (intent nulo)
            else -> if (state == State.STOPPED || state == State.ERROR) startListening()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        generation++
        handler.removeCallbacksAndMessages(null)
        worker.execute {
            releaseMic()
            model?.close()
            model = null
        }
        worker.shutdown()
        releaseWakeLock()
        inForeground = false
        // Se conserva el último mensaje para que la pantalla muestre por qué se detuvo.
        updateState(State.STOPPED, lastMessage, notify = false)
        super.onDestroy()
    }

    // ---------------------------------------------------------------- estados

    private fun startListening() {
        handler.removeCallbacks(resumeRunnable)

        val missing = missingConfig(this)
        if (missing.isNotEmpty()) {
            fail(getString(R.string.missing_config, missing.joinToString(", ")))
            return
        }

        val token = ++generation
        // Sensibilidad alta = se exige menos confianza a cada palabra.
        val minConfidence = 1f - ExySettings(this).sensitivity
        val listener = Listener(token, minConfidence)
        updateState(State.STARTING, null)

        worker.execute {
            releaseMic()
            try {
                val m = model ?: Model(VoskModel.prepare(applicationContext).absolutePath)
                    .also { model = it }
                val gramatica = Comandos.cargar(applicationContext).gramatica
                val r = Recognizer(m, SAMPLE_RATE, gramatica).apply { setWords(true) }
                recognizer = r
                val s = SpeechService(r, SAMPLE_RATE)
                speech = s
                if (!s.startListening(listener)) throw IllegalStateException("la escucha ya estaba activa")
                handler.post {
                    if (token == generation) {
                        acquireWakeLock()
                        updateState(State.LISTENING, null)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo iniciar la escucha", e)
                releaseMic()
                handler.post { if (token == generation) fail(describe(e)) }
            }
        }
    }

    /** Recibe los resultados de Vosk en el hilo principal. */
    private inner class Listener(
        private val token: Int,
        private val minConfidence: Float,
    ) : RecognitionListener {

        override fun onResult(hypothesis: String) = handleResult(hypothesis)

        override fun onFinalResult(hypothesis: String) = handleResult(hypothesis)

        override fun onPartialResult(hypothesis: String) = Unit

        override fun onError(exception: Exception) = onRuntimeError(token, exception)

        override fun onTimeout() = Unit

        private fun handleResult(hypothesis: String) {
            if (token != generation || state != State.LISTENING) return
            val heard = try {
                Comandos.textoOido(hypothesis)
            } catch (e: Exception) {
                null
            } ?: return
            lastHeard = heard
            val deteccion = try {
                Comandos.cargar(this@WakeWordService).detectar(hypothesis, minConfidence)
            } catch (e: Exception) {
                Log.w(TAG, "Resultado de Vosk inválido: $hypothesis", e)
                null
            }
            if (deteccion != null) onWakeWord(deteccion.destino) else listeners.forEach { it() }
        }
    }

    private fun onWakeWord(destino: Destino) {
        val next = ++generation
        val delayMs = ExySettings(this).resumeDelaySeconds * 1000L

        vibrate()
        releaseWakeLock()
        resumeAtWall = System.currentTimeMillis() + delayMs
        lastDestino = destino
        updateState(State.COOLDOWN, null)

        // Primero soltar el micrófono, después abrir la app, para que el
        // asistente lo encuentre libre.
        worker.execute {
            releaseMic()
            handler.post {
                if (next != generation) return@post
                if (!destino.abrir(this)) {
                    updateState(State.COOLDOWN, getString(R.string.no_se_pudo_abrir, getString(destino.nombre)))
                }
                handler.postDelayed(resumeRunnable, delayMs)
            }
        }
    }

    /** Error en plena escucha (por ejemplo, otra app tomó el micrófono). Reintenta solo. */
    private fun onRuntimeError(token: Int, e: Exception) {
        if (token != generation) return
        Log.e(TAG, "Error durante la escucha", e)
        generation++
        worker.execute { releaseMic() }
        releaseWakeLock()
        updateState(State.ERROR, getString(R.string.error_retry, describe(e)))
        handler.postDelayed(resumeRunnable, RETRY_DELAY_MS)
    }

    private fun pause() {
        generation++
        handler.removeCallbacks(resumeRunnable)
        worker.execute { releaseMic() }
        releaseWakeLock()
        updateState(State.PAUSED, null)
    }

    private fun fail(message: String) {
        generation++
        worker.execute { releaseMic() }
        releaseWakeLock()
        updateState(State.ERROR, message)
    }

    private fun shutdown() {
        generation++
        handler.removeCallbacks(resumeRunnable)
        worker.execute { releaseMic() }
        releaseWakeLock()
        updateState(State.STOPPED, null, notify = false)
        if (inForeground) stopForeground(STOP_FOREGROUND_REMOVE)
        inForeground = false
        stopSelf()
    }

    /** Solo desde [worker]. Cierra el micrófono; el modelo queda cargado. */
    private fun releaseMic() {
        speech?.let {
            try {
                it.stop()
                it.shutdown()
            } catch (e: Exception) {
                Log.w(TAG, "Error al detener la escucha", e)
            }
        }
        speech = null
        recognizer?.close()
        recognizer = null
    }

    // ------------------------------------------------------- primer plano

    private fun promoteToForeground(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            updateState(State.ERROR, getString(R.string.missing_config, getString(R.string.missing_mic)), notify = false)
            return false
        }
        return try {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
            inForeground = true
            true
        } catch (e: Exception) {
            // Android 14+ no deja iniciar un servicio de micrófono desde segundo
            // plano (por ejemplo, cuando el sistema lo reinicia solo).
            Log.e(TAG, "No se pudo pasar a primer plano", e)
            updateState(State.ERROR, e.message ?: e.javaClass.simpleName, notify = false)
            false
        }
    }

    private fun updateState(newState: State, message: String?, notify: Boolean = true) {
        state = newState
        lastMessage = message
        if (notify && inForeground) {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification())
        }
        listeners.forEach { it() }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(this, ExyApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        val text = when (state) {
            State.STOPPED, State.STARTING -> getString(R.string.notif_starting)
            State.LISTENING -> getString(R.string.notif_listening)
            State.PAUSED -> getString(R.string.notif_paused)
            State.COOLDOWN -> lastMessage
                ?: getString(R.string.notif_cooldown, getString((lastDestino ?: Destino.FAVORITO).nombre))
            State.ERROR -> getString(R.string.notif_error, lastMessage.orEmpty())
        }
        builder.setContentText(text)
        builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))

        if (state == State.COOLDOWN && lastMessage == null) {
            builder.setWhen(resumeAtWall)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }

        if (state == State.PAUSED || state == State.ERROR) {
            builder.addAction(0, getString(R.string.action_resume), serviceIntent(ACTION_RESUME, 1))
        } else {
            builder.addAction(0, getString(R.string.action_pause), serviceIntent(ACTION_PAUSE, 2))
        }
        builder.addAction(0, getString(R.string.action_stop), serviceIntent(ACTION_STOP, 3))
        return builder.build()
    }

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, WakeWordService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    // -------------------------------------------------------------- extras

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /** Mantiene la CPU despierta mientras se escucha con la pantalla apagada. */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Exy:escucha")
            .apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun describe(e: Exception): String = e.message ?: e.javaClass.simpleName

    companion object {
        private const val TAG = "Exy"
        private const val NOTIFICATION_ID = 1
        private const val RETRY_DELAY_MS = 15_000L
        private const val SAMPLE_RATE = 16_000f

        const val ACTION_START = "cl.exy.app.START"
        const val ACTION_PAUSE = "cl.exy.app.PAUSE"
        const val ACTION_RESUME = "cl.exy.app.RESUME"
        const val ACTION_RELOAD = "cl.exy.app.RELOAD"
        const val ACTION_STOP = "cl.exy.app.STOP"

        @Volatile
        var state: State = State.STOPPED
            private set

        @Volatile
        var lastMessage: String? = null
            private set

        /** La última app que Exy abrió. */
        @Volatile
        var lastDestino: Destino? = null
            private set

        /** Lo último que Vosk entendió (sin "[unk]"), para ajustar la sensibilidad. */
        @Volatile
        var lastHeard: String? = null
            private set

        private val listeners = CopyOnWriteArraySet<() -> Unit>()

        /** Los avisos llegan en el hilo principal. */
        fun addListener(listener: () -> Unit) = listeners.add(listener)
        fun removeListener(listener: () -> Unit) = listeners.remove(listener)

        val isRunning: Boolean get() = state != State.STOPPED

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WakeWordService::class.java).setAction(ACTION_START),
            )
        }

        /** Se usa desde la app en primer plano, así que no hace falta startForegroundService. */
        fun send(context: Context, action: String) {
            if (!isRunning) return
            context.startService(Intent(context, WakeWordService::class.java).setAction(action))
        }

        /** Lo que falta para poder escuchar, en palabras para el usuario. */
        fun missingConfig(context: Context): List<String> = buildList {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                add(context.getString(R.string.missing_mic))
            }
        }
    }
}
