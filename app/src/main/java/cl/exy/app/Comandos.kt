package cl.exy.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Las frases que Exy reconoce, leídas de assets/comandos.json.
 *
 * Una frase es «<oye> <exi> [<prefijo>] [<destino>]»: «oye exi» solo abre el
 * favorito (Gemini) y «oye exi vamos a claude code» abre Claude Code.
 *
 * Vosk reconoce solo lo que está en la gramática; lo demás sale como "[unk]".
 * Como "exi" y "ChatGPT" no son palabras del español, se incluyen las formas en
 * que el modelo las oye ("equis", "chat ge pe te"...). El CI revisa que todas
 * las palabras existan en el vocabulario del modelo.
 */
class Comandos private constructor(
    private val oye: List<String>,
    private val exi: List<String>,
    /** Formas de "exi" demasiado comunes ("oye, sí"): solo valen seguidas de una orden. */
    private val exiDebil: List<String>,
    private val prefijos: List<String>,
    /** Texto del destino (p. ej. "claude code") → destino. */
    private val destinos: Map<String, Destino>,
    /** Órdenes completas que se revisan antes que los destinos ("vamos a claude" → Claude Code). */
    private val atajos: Map<String, Destino>,
) {

    /** Resultado de reconocer una frase de activación. */
    data class Deteccion(val destino: Destino, val texto: String)

    private val activaciones: List<String> = oye.flatMap { o -> exi.map { e -> "$o $e" } }

    private val activacionesDebiles: List<String> = oye.flatMap { o -> exiDebil.map { e -> "$o $e" } }

    private val ordenes: List<String> =
        (destinos.keys + prefijos.flatMap { p -> destinos.keys.map { d -> "$p $d" } } + atajos.keys).distinct()

    /**
     * Gramática para Vosk: cada activación sola o seguida de una orden, más las
     * activaciones incompletas ("oye" suelto) para que no se fuercen hacia una
     * frase completa, y "[unk]" para todo lo demás.
     */
    val gramatica: String = JSONArray(
        activaciones +
            (activaciones + activacionesDebiles).flatMap { a -> ordenes.map { o -> "$a $o" } } +
            oye +
            "[unk]",
    ).toString()

    /**
     * Revisa un resultado final de Vosk (con `setWords(true)`). Devuelve la
     * detección si contiene la frase de activación y la confianza mínima de sus
     * palabras supera [confianzaMinima]; si no, null.
     */
    fun detectar(resultadoJson: String, confianzaMinima: Float): Deteccion? {
        val json = JSONObject(resultadoJson)
        val texto = json.optString("text").trim()
        if (texto.isEmpty()) return null
        val palabras = texto.split(' ').filter { it.isNotEmpty() }

        // Buscar la activación («oye exi») en cualquier punto de la frase.
        var inicio = -1
        var largo = 0
        var debil = false
        loop@ for (i in palabras.indices) {
            for (a in activaciones + activacionesDebiles) {
                val partes = a.split(' ')
                if (palabras.size - i >= partes.size && palabras.subList(i, i + partes.size) == partes) {
                    inicio = i
                    largo = partes.size
                    debil = a in activacionesDebiles
                    break@loop
                }
            }
        }
        if (inicio < 0) return null

        // Lo que sigue: vacío o "[unk]" → favorito; si no, un destino con o sin prefijo.
        val resto = palabras.drop(inicio + largo).filter { it != "[unk]" }.joinToString(" ")
        val destino: Destino
        val usadas: List<String>
        if (resto.isEmpty()) {
            if (debil) return null
            destino = Destino.FAVORITO
            usadas = palabras.subList(inicio, inicio + largo)
        } else {
            val orden = prefijos.sortedByDescending { it.length }
                .firstOrNull { resto.startsWith("$it ") }
                ?.let { resto.removePrefix("$it ") }
                ?: resto
            destino = atajos[resto] ?: destinos[orden] ?: return null
            usadas = palabras.subList(inicio, inicio + largo) + resto.split(' ')
        }

        return if (confianza(json, usadas) >= confianzaMinima) Deteccion(destino, texto) else null
    }

    /** La confianza más baja entre las palabras usadas de la frase. */
    private fun confianza(json: JSONObject, usadas: List<String>): Double {
        val pendientes = usadas.toMutableList()
        var minima = 1.0
        val lista = json.optJSONArray("result") ?: return minima
        for (i in 0 until lista.length()) {
            val w = lista.getJSONObject(i)
            if (pendientes.remove(w.optString("word"))) {
                minima = minOf(minima, w.optDouble("conf", 1.0))
            }
        }
        return minima
    }

    companion object {
        private const val ARCHIVO = "comandos.json"

        @Volatile
        private var cache: Comandos? = null

        fun cargar(context: Context): Comandos = cache ?: synchronized(this) {
            cache ?: leer(context).also { cache = it }
        }

        private fun leer(context: Context): Comandos {
            val json = JSONObject(
                context.assets.open(ARCHIVO).bufferedReader().use { it.readText() },
            )
            val destinos = mutableMapOf<String, Destino>()
            val porId = json.getJSONObject("destinos")
            for (id in porId.keys()) {
                val destino = Destino.porId(id) ?: continue
                porId.getJSONArray(id).strings().forEach { destinos[it] = destino }
            }
            val atajos = mutableMapOf<String, Destino>()
            json.optJSONObject("atajos")?.let { a ->
                for (frase in a.keys()) Destino.porId(a.getString(frase))?.let { atajos[frase] = it }
            }
            return Comandos(
                oye = json.getJSONArray("oye").strings(),
                exi = json.getJSONArray("exi").strings(),
                exiDebil = json.optJSONArray("exi_debil")?.strings().orEmpty(),
                prefijos = json.getJSONArray("prefijos").strings(),
                destinos = destinos,
                atajos = atajos,
            )
        }

        private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

        /** Texto reconocido (sin "[unk]"), para mostrarlo en pantalla. */
        fun textoOido(resultadoJson: String): String? =
            JSONObject(resultadoJson).optString("text")
                .replace("[unk]", "")
                .replace(Regex("\\s+"), " ")
                .trim()
                .ifEmpty { null }
    }
}
