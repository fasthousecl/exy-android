package cl.exy.app

import android.content.Context
import java.util.Calendar

/** Ajustes de la escucha, en preferencias privadas de la app. */
class ExySettings(context: Context) {

    private val prefs = context.getSharedPreferences("exy_settings", Context.MODE_PRIVATE)

    var sensitivity: Float
        get() = prefs.getFloat(KEY_SENSITIVITY, DEFAULT_SENSITIVITY)
        set(value) = prefs.edit().putFloat(KEY_SENSITIVITY, value.coerceIn(0f, 1f)).apply()

    var resumeDelaySeconds: Int
        get() = prefs.getInt(KEY_RESUME_DELAY, DEFAULT_RESUME_DELAY)
        set(value) = prefs.edit().putInt(KEY_RESUME_DELAY, value.coerceIn(10, 300)).apply()

    /** Lo que abre «Oye Exi» a secas (ver [Destino.favorito]). */
    var favoritoId: String
        get() = prefs.getString(KEY_FAVORITO, null) ?: Destino.GEMINI.id
        set(value) = prefs.edit().putString(KEY_FAVORITO, value).apply()

    /** Horario de descanso: el micrófono queda libre entre [descansoDesde] y [descansoHasta]. */
    var descansoActivo: Boolean
        get() = prefs.getBoolean(KEY_DESCANSO, false)
        set(value) = prefs.edit().putBoolean(KEY_DESCANSO, value).apply()

    /** Minutos desde medianoche. Por defecto 23:00. */
    var descansoDesde: Int
        get() = prefs.getInt(KEY_DESDE, 23 * 60)
        set(value) = prefs.edit().putInt(KEY_DESDE, value.mod(MIN_DIA)).apply()

    /** Minutos desde medianoche. Por defecto 07:00. */
    var descansoHasta: Int
        get() = prefs.getInt(KEY_HASTA, 7 * 60)
        set(value) = prefs.edit().putInt(KEY_HASTA, value.mod(MIN_DIA)).apply()

    /** Modo auriculares: escuchar solo con audífonos Bluetooth conectados. */
    var soloAuriculares: Boolean
        get() = prefs.getBoolean(KEY_AURICULARES, false)
        set(value) = prefs.edit().putBoolean(KEY_AURICULARES, value).apply()

    /** Si ahora cae dentro del horario de descanso. Soporta horarios que cruzan la medianoche. */
    fun enDescanso(ahora: Calendar = Calendar.getInstance()): Boolean {
        if (!descansoActivo) return false
        val m = ahora.get(Calendar.HOUR_OF_DAY) * 60 + ahora.get(Calendar.MINUTE)
        val desde = descansoDesde
        val hasta = descansoHasta
        return when {
            desde == hasta -> false
            desde < hasta -> m in desde until hasta
            else -> m >= desde || m < hasta
        }
    }

    /** El próximo cambio (inicio o fin del descanso), en milisegundos de reloj. */
    fun proximoCambio(ahora: Calendar = Calendar.getInstance()): Long {
        val objetivo = if (enDescanso(ahora)) descansoHasta else descansoDesde
        val c = (ahora.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, objetivo / 60)
            set(Calendar.MINUTE, objetivo % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= ahora.timeInMillis) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

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

    companion object {
        private const val KEY_SENSITIVITY = "sensitivity"
        private const val KEY_RESUME_DELAY = "resume_delay_s"
        private const val KEY_FAVORITO = "favorito"
        private const val KEY_DESCANSO = "descanso"
        private const val KEY_DESDE = "descanso_desde"
        private const val KEY_HASTA = "descanso_hasta"
        private const val KEY_AURICULARES = "solo_auriculares"
        private const val DEFAULT_SENSITIVITY = 0.5f
        private const val DEFAULT_RESUME_DELAY = 60
        private const val MIN_DIA = 24 * 60

        /** "07:00" */
        fun hora(minutos: Int): String = "%02d:%02d".format(minutos / 60, minutos % 60)
    }
}
