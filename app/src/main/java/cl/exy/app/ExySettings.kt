package cl.exy.app

import android.content.Context

/** Ajustes de la escucha, en preferencias privadas de la app. */
class ExySettings(context: Context) {

    private val prefs = context.getSharedPreferences("exy_settings", Context.MODE_PRIVATE)

    var sensitivity: Float
        get() = prefs.getFloat(KEY_SENSITIVITY, DEFAULT_SENSITIVITY)
        set(value) = prefs.edit().putFloat(KEY_SENSITIVITY, value.coerceIn(0f, 1f)).apply()

    var resumeDelaySeconds: Int
        get() = prefs.getInt(KEY_RESUME_DELAY, DEFAULT_RESUME_DELAY)
        set(value) = prefs.edit().putInt(KEY_RESUME_DELAY, value.coerceIn(10, 300)).apply()

    /** Las tres opciones de la pantalla; cada una fija una sensibilidad. */
    enum class Precision(val sensibilidad: Float) {
        ESTRICTA(0.3f),
        NORMAL(0.5f),
        SENSIBLE(0.75f);

        companion object {
            /** La opción más cercana a una sensibilidad guardada (de versiones con deslizador). */
            fun desde(sensibilidad: Float): Precision =
                entries.minBy { kotlin.math.abs(it.sensibilidad - sensibilidad) }
        }
    }

    private companion object {
        const val KEY_SENSITIVITY = "sensitivity"
        const val KEY_RESUME_DELAY = "resume_delay_s"
        const val DEFAULT_SENSITIVITY = 0.5f
        const val DEFAULT_RESUME_DELAY = 60
    }
}
