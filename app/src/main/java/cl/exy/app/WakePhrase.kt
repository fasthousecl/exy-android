package cl.exy.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * La frase de activación "oye exi" y sus variantes.
 *
 * Vosk reconoce solo lo que está en la gramática: cualquier otra cosa sale como
 * "[unk]". Como "exi" no es una palabra del español, se agregan las formas en que
 * el modelo puede escucharla. Las palabras que no existan en el vocabulario del
 * modelo las descarta Vosk solo (queda un aviso en el log), así que sobran
 * variantes sin riesgo.
 */
object WakePhrase {

    private val HEY = listOf("oye", "oie", "olle", "oy", "hoy", "hey", "ey")

    private val EXI = listOf(
        "exi", "exy", "eksi", "ecsi", "exis", "equis", "sexy", "sexi", "éxi",
        "ex si", "eks i", "ex i",
    )

    /** Frases que cuentan como activación. */
    val phrases: List<String> = HEY.flatMap { h -> EXI.map { e -> "$h $e" } }

    /**
     * Gramática para Vosk. Incluye "oye" suelto para que un "oye" sin "exi" no
     * se fuerce hacia una frase completa, y "[unk]" para todo lo demás.
     */
    val grammarJson: String = JSONArray(phrases + HEY + "[unk]").toString()

    /**
     * Revisa un resultado final de Vosk (con `setWords(true)`).
     * Devuelve el texto reconocido si contiene la frase y la confianza mínima de
     * sus palabras supera [minConfidence]; si no, null.
     */
    fun match(resultJson: String, minConfidence: Float): String? {
        val json = JSONObject(resultJson)
        val text = json.optString("text").trim()
        if (text.isEmpty()) return null

        val padded = " $text "
        val phrase = phrases.firstOrNull { padded.contains(" $it ") } ?: return null

        // Confianza: la más baja entre las palabras de la frase detectada.
        val wanted = phrase.split(' ').toMutableList()
        var minConf = 1.0
        val words = json.optJSONArray("result")
        if (words != null) {
            for (i in 0 until words.length()) {
                val w = words.getJSONObject(i)
                if (wanted.remove(w.optString("word"))) {
                    minConf = minOf(minConf, w.optDouble("conf", 1.0))
                }
            }
        }
        return if (minConf >= minConfidence) text else null
    }

    /** Texto reconocido (sin "[unk]"), para mostrarlo en pantalla. */
    fun heardText(resultJson: String): String? =
        JSONObject(resultJson).optString("text")
            .replace("[unk]", "")
            .trim()
            .ifEmpty { null }
}
