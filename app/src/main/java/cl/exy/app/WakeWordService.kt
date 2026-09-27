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
import ai.picovoice.porcupine.PorcupineActivationException
import ai.picovoice.porcupine.PorcupineActivationLimitException
import ai.picovoice.porcupine.PorcupineActivationRefusedException
import ai.picovoice.porcupine.PorcupineActivationThrottledException
import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineInvalidArgumentException
import ai.picovoice.porcupine.PorcupineKeyException
import ai.picovoice.porcupine.PorcupineManager
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Servicio en primer plano que mantiene a Porcupine escuchando la palabra clave.
 *
 * Todo lo que toca a Porcupine (crear, iniciar, detener, liberar) corre en un
 * único hilo de trabajo, en orden. El estado y la notificación se manejan en el
 * hilo principal. [generation] invalida resultados viejos: si el usuario pausa
 * mientras Porcupine se está iniciando, el inicio se descarta.
 */
class WakeWordService : Service() {

    enum class State { STOPPED, STARTING, LISTENING, PAUSED, COOLDOWN, ERROR }

    private val handler = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()

    /** Solo se lee y escribe desde [worker]. */
    private var manager: PorcupineManager? = null

    /** Solo se usa desde el hilo principal. */
    private var generation = 0
    private var inForeground = false
    private var resumeAtWall = 0L
    private var wakeLock: PowerManager.WakeLock? = null

    private val resumeRunnable = Runnable { startListening() }

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
        worker.execute { releaseManager() }
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
        val accessKey = SecureStore(this).loadAccessKey()
        if (accessKey.isNullOrBlank()) {
            fail(getString(R.string.missing_config, getString(R.string.missing_key)))
            return
        }

        val token = ++generation
        val keywordPath = ModelFile.KEYWORD.file(this).absolutePath
        val modelPath = ModelFile.MODEL.file(this).absolutePath
        val sensitivity = ExySettings(this).sensitivity
        updateState(State.STARTING, null)

        worker.execute {
            releaseManager()
            try {
                val m = PorcupineManager.Builder()
                    .setAccessKey(accessKey)
                    .setModelPath(modelPath)
                    .setKeywordPath(keywordPath)
                    .setSensitivity(sensitivity)
                    .setErrorCallback { e -> handler.post { onRuntimeError(token, e) } }
                    .build(applicationContext) { handler.post { onWakeWord(token) } }
                manager = m
                m.start()
                handler.post {
                    if (token == generation) {
                        acquireWakeLock()
                        updateState(State.LISTENING, null)
                    }
                }
            } catch (e: PorcupineException) {
                Log.e(TAG, "No se pudo iniciar Porcupine", e)
                releaseManager()
                handler.post { if (token == generation) fail(describe(e)) }
            }
        }
    }

    private fun onWakeWord(token: Int) {
        if (token != generation || state != State.LISTENING) return
        val next = ++generation
        val delayMs = ExySettings(this).resumeDelaySeconds * 1000L

        vibrate()
        releaseWakeLock()
        resumeAtWall = System.currentTimeMillis() + delayMs
        updateState(State.COOLDOWN, null)

        // Primero soltar el micrófono, después abrir el asistente, para que el
        // asistente lo encuentre libre.
        worker.execute {
            releaseManager()
            handler.post {
                if (next != generation) return@post
                if (!AssistantLauncher.launch(this)) {
                    updateState(State.COOLDOWN, getString(R.string.no_assistant))
                }
                handler.postDelayed(resumeRunnable, delayMs)
            }
        }
    }

    /** Error en plena escucha (por ejemplo, otra app tomó el micrófono). Reintenta solo. */
    private fun onRuntimeError(token: Int, e: PorcupineException) {
        if (token != generation) return
        Log.e(TAG, "Error durante la escucha", e)
        generation++
        worker.execute { releaseManager() }
        releaseWakeLock()
        updateState(State.ERROR, getString(R.string.error_retry, describe(e)))
        handler.postDelayed(resumeRunnable, RETRY_DELAY_MS)
    }

    private fun pause() {
        generation++
        handler.removeCallbacks(resumeRunnable)
        worker.execute { releaseManager() }
        releaseWakeLock()
        updateState(State.PAUSED, null)
    }

    private fun fail(message: String) {
        generation++
        worker.execute { releaseManager() }
        releaseWakeLock()
        updateState(State.ERROR, message)
    }

    private fun shutdown() {
        generation++
        handler.removeCallbacks(resumeRunnable)
        worker.execute { releaseManager() }
        releaseWakeLock()
        updateState(State.STOPPED, null, notify = false)
        if (inForeground) stopForeground(STOP_FOREGROUND_REMOVE)
        inForeground = false
        stopSelf()
    }

    /** Solo desde [worker]. Deja el micrófono libre. */
    private fun releaseManager() {
        val m = manager ?: return
        manager = null
        try {
            m.stop()
        } catch (e: PorcupineException) {
            Log.w(TAG, "Error al detener Porcupine", e)
        }
        m.delete()
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
            State.COOLDOWN -> lastMessage ?: getString(R.string.notif_cooldown)
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

    private fun describe(e: PorcupineException): String = when (e) {
        is PorcupineActivationLimitException -> "la AccessKey alcanzó su límite de dispositivos"
        is PorcupineActivationRefusedException -> "Picovoice rechazó la AccessKey"
        is PorcupineActivationThrottledException -> "demasiados intentos, espera un rato"
        is PorcupineActivationException -> "no se pudo validar la AccessKey (¿hay internet?)"
        is PorcupineKeyException -> "la AccessKey no es válida"
        is PorcupineInvalidArgumentException ->
            "el .ppn y el .pv no son compatibles (deben ser del mismo idioma y versión)"
        else -> e.message ?: e.javaClass.simpleName
    }

    companion object {
        private const val TAG = "Exy"
        private const val NOTIFICATION_ID = 1
        private const val RETRY_DELAY_MS = 15_000L

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
            if (!SecureStore(context).hasAccessKey()) add(context.getString(R.string.missing_key))
            if (!ModelFile.KEYWORD.exists(context)) add(context.getString(R.string.missing_ppn))
            if (!ModelFile.MODEL.exists(context)) add(context.getString(R.string.missing_pv))
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                add(context.getString(R.string.missing_mic))
            }
        }
    }
}
