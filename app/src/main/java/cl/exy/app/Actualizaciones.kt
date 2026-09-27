package cl.exy.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Revisa si hay una versión nueva en los Releases de GitHub (el repositorio
 * debe ser público). Es la única conexión a internet de Exy: no envía nada,
 * solo lee la última versión publicada.
 */
object Actualizaciones {

    /** Una versión publicada más nueva que la instalada. */
    data class Nueva(val version: String, val codigo: Int, val descarga: String)

    private const val API = "https://api.github.com/repos/fasthousecl/exy-android/releases/latest"
    private const val CADA_MS = 6 * 60 * 60 * 1000L
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** La última versión nueva encontrada (sigue guardada entre aperturas). */
    fun pendiente(context: Context): Nueva? {
        val p = prefs(context)
        val codigo = p.getInt("codigo", 0)
        if (codigo <= BuildConfig.VERSION_CODE) return null
        return Nueva(p.getString("version", "") ?: "", codigo, p.getString("descarga", "") ?: "")
    }

    /**
     * Consulta GitHub (en segundo plano) y avisa en el hilo principal. Sin
     * [forzar], no consulta más de una vez cada 6 horas.
     * [listo] recibe la versión nueva, o null si no hay; [error] si no se pudo revisar.
     */
    fun revisar(context: Context, forzar: Boolean, listo: (Nueva?) -> Unit, error: (() -> Unit)? = null) {
        val app = context.applicationContext
        val p = prefs(app)
        if (!forzar && System.currentTimeMillis() - p.getLong("revisado", 0) < CADA_MS) {
            listo(pendiente(app))
            return
        }
        worker.execute {
            try {
                val con = (URL(API).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Accept", "application/vnd.github+json")
                }
                val json = con.inputStream.bufferedReader().use { it.readText() }
                con.disconnect()
                val o = JSONObject(json)
                val tag = o.getString("tag_name")                    // "v1.0.23"
                val codigo = tag.substringAfterLast('.').toIntOrNull() ?: 0
                val assets = o.optJSONArray("assets")
                var descarga = o.optString("html_url")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name").endsWith(".apk")) descarga = a.getString("browser_download_url")
                    }
                }
                p.edit()
                    .putLong("revisado", System.currentTimeMillis())
                    .putInt("codigo", codigo)
                    .putString("version", tag.removePrefix("v"))
                    .putString("descarga", descarga)
                    .apply()
                main.post { listo(pendiente(app)) }
            } catch (e: Exception) {
                Log.w("Exy", "No se pudo revisar actualizaciones", e)
                main.post { error?.invoke() ?: listo(pendiente(app)) }
            }
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences("exy_actualizaciones", Context.MODE_PRIVATE)
}
