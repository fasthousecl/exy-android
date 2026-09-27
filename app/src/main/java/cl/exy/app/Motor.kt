package cl.exy.app

import android.content.Context
import org.vosk.Model

/**
 * El modelo de Vosk, cargado una sola vez por proceso y compartido entre el
 * servicio y las pantallas de prueba (cargarlo dos veces gastaría ~50 MB más).
 */
object Motor {

    const val SAMPLE_RATE = 16_000f

    @Volatile
    private var modelo: Model? = null

    /** Solo desde un hilo de trabajo: la primera vez copia y carga el modelo. */
    fun modelo(context: Context): Model = modelo ?: synchronized(this) {
        modelo ?: Model(VoskModel.prepare(context.applicationContext).absolutePath).also { modelo = it }
    }
}
