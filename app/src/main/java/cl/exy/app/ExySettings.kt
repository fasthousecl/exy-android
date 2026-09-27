package cl.exy.app

import android.content.Context

/** Ajustes no sensibles. La AccessKey va aparte, cifrada, en [SecureStore]. */
class ExySettings(context: Context) {

    private val prefs = context.getSharedPreferences("exy_settings", Context.MODE_PRIVATE)

    var sensitivity: Float
        get() = prefs.getFloat(KEY_SENSITIVITY, DEFAULT_SENSITIVITY)
        set(value) = prefs.edit().putFloat(KEY_SENSITIVITY, value.coerceIn(0f, 1f)).apply()

    var resumeDelaySeconds: Int
        get() = prefs.getInt(KEY_RESUME_DELAY, DEFAULT_RESUME_DELAY)
        set(value) = prefs.edit().putInt(KEY_RESUME_DELAY, value.coerceIn(10, 300)).apply()

    private companion object {
        const val KEY_SENSITIVITY = "sensitivity"
        const val KEY_RESUME_DELAY = "resume_delay_s"
        const val DEFAULT_SENSITIVITY = 0.5f
        const val DEFAULT_RESUME_DELAY = 60
    }
}
