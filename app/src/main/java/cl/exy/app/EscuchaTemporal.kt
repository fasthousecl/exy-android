package cl.exy.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.util.concurrent.Executors

/**
 * Escucha de corta duración para las pantallas "Prueba tu voz" y "Enseñar un
 * comando". Mientras está activa, la escucha de fondo queda en pausa (dos
 * grabaciones a la vez se estorban) y se reanuda al terminar si estaba activa.
 *
 * Con [gramatica] reconoce solo esas frases (igual que el servicio); sin ella,
 * reconocimiento libre con todo el vocabulario del modelo.
 * Los avisos llegan en el hilo principal.
 */
class EscuchaTemporal(
    private val context: Context,
    private val gramatica: String?,
    private val alParcial: (String) -> Unit,
    private val alResultado: (String) -> Unit,
    private val alError: (String) -> Unit,
) {

    private val main = Handler(Looper.getMainLooper())

    /** Solo desde [worker]. */
    private var speech: SpeechService? = null
    private var recognizer: Recognizer? = null

    /** Solo desde el hilo principal. */
    private var activa = false
    private var reanudarAlTerminar = false
    private var sesion = 0

    val escuchando: Boolean get() = activa

    fun iniciar() {
        if (activa) return
        activa = true
        val miSesion = ++sesion
        val estado = WakeWordService.state
        if (estado == WakeWordService.State.LISTENING || estado == WakeWordService.State.STARTING ||
            estado == WakeWordService.State.COOLDOWN
        ) {
            reanudarAlTerminar = true
            WakeWordService.send(context, WakeWordService.ACTION_PAUSE)
        }
        worker.execute {
            try {
                // Deja que el servicio suelte el micrófono.
                Thread.sleep(PAUSA_MS)
                val m = Motor.modelo(context)
                val r = (if (gramatica != null) Recognizer(m, Motor.SAMPLE_RATE, gramatica) else Recognizer(m, Motor.SAMPLE_RATE))
                    .apply { setWords(true) }
                recognizer = r
                val s = SpeechService(r, Motor.SAMPLE_RATE)
                speech = s
                s.startListening(object : RecognitionListener {
                    override fun onPartialResult(hypothesis: String) {
                        if (miSesion == sesion) alParcial(hypothesis)
                    }

                    override fun onResult(hypothesis: String) {
                        if (miSesion == sesion) alResultado(hypothesis)
                    }

                    override fun onFinalResult(hypothesis: String) {
                        if (miSesion == sesion) alResultado(hypothesis)
                    }

                    override fun onError(exception: Exception) {
                        if (miSesion == sesion) alError(exception.message ?: exception.javaClass.simpleName)
                    }

                    override fun onTimeout() = Unit
                })
            } catch (e: Exception) {
                Log.e("Exy", "No se pudo iniciar la escucha temporal", e)
                cerrarMicrofono()
                main.post {
                    if (miSesion == sesion) {
                        activa = false
                        alError(e.message ?: e.javaClass.simpleName)
                    }
                }
            }
        }
    }

    /** Suelta el micrófono y, si Exy estaba escuchando antes, la reanuda. */
    fun detener() {
        if (!activa) return
        activa = false
        sesion++
        val reanudar = reanudarAlTerminar
        reanudarAlTerminar = false
        worker.execute {
            cerrarMicrofono()
            if (reanudar) main.post { WakeWordService.send(context, WakeWordService.ACTION_RESUME) }
        }
    }

    /** Solo desde [worker]. */
    private fun cerrarMicrofono() {
        try {
            speech?.cancel()
            speech?.shutdown()
        } catch (e: Exception) {
            Log.w("Exy", "Error al cerrar la escucha temporal", e)
        }
        speech = null
        recognizer?.close()
        recognizer = null
    }

    private companion object {
        const val PAUSA_MS = 400L
        val worker = Executors.newSingleThreadExecutor()
    }
}
