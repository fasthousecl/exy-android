package cl.exy.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Comandos que agrega el usuario: «Oye Exi, Spotify» → abre Spotify.
 *
 * Las frases no se escriben: se "enseñan" diciéndolas en voz alta, y se guarda
 * lo que Vosk entendió con reconocimiento libre. Así cada palabra existe por
 * fuerza en el vocabulario del modelo y coincide con cómo te oye a ti.
 */
class ComandosUsuario(context: Context) {

    data class Comando(val paquete: String, val nombre: String, val frases: List<String>)

    private val prefs = context.getSharedPreferences("exy_comandos", Context.MODE_PRIVATE)

    fun lista(): List<Comando> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val f = o.getJSONArray("frases")
                Comando(o.getString("paquete"), o.getString("nombre"), List(f.length()) { f.getString(it) })
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Agrega o reemplaza el comando de esa app. */
    fun guardar(comando: Comando) {
        guardarTodo(lista().filter { it.paquete != comando.paquete } + comando)
    }

    fun borrar(paquete: String) {
        guardarTodo(lista().filter { it.paquete != paquete })
    }

    private fun guardarTodo(lista: List<Comando>) {
        val arr = JSONArray()
        lista.forEach { c ->
            arr.put(
                JSONObject()
                    .put("paquete", c.paquete)
                    .put("nombre", c.nombre)
                    .put("frases", JSONArray(c.frases)),
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
        // La gramática de Vosk depende de estos comandos.
        Comandos.invalidar()
    }

    private companion object {
        const val KEY = "comandos"
    }
}
