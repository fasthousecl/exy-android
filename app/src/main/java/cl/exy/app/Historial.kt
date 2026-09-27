package cl.exy.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Las últimas activaciones, para ver si Exy se activa sola o cuándo no te
 * entiende. Se guardan solo en el teléfono.
 */
class Historial(context: Context) {

    enum class Resultado { ABIERTA, NO_ABRIO, IGNORADA }

    data class Entrada(
        val cuando: Long,
        val oido: String,
        val destino: String?,
        val confianza: Double,
        val resultado: Resultado,
    )

    private val prefs = context.getSharedPreferences("exy_historial", Context.MODE_PRIVATE)

    fun lista(): List<Entrada> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Entrada(
                    cuando = o.getLong("t"),
                    oido = o.getString("oido"),
                    destino = o.optString("destino").ifEmpty { null },
                    confianza = o.optDouble("conf", 1.0),
                    resultado = runCatching { Resultado.valueOf(o.getString("res")) }.getOrDefault(Resultado.ABIERTA),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun agregar(entrada: Entrada) {
        val nueva = (listOf(entrada) + lista()).take(MAX)
        val arr = JSONArray()
        nueva.forEach { e ->
            arr.put(
                JSONObject()
                    .put("t", e.cuando)
                    .put("oido", e.oido)
                    .put("destino", e.destino ?: "")
                    .put("conf", e.confianza)
                    .put("res", e.resultado.name),
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun borrar() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "entradas"
        const val MAX = 50
    }
}
