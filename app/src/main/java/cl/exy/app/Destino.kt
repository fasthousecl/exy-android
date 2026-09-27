package cl.exy.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Una app que Exy sabe abrir: las cuatro integradas (sus ids coinciden con los
 * de assets/comandos.json) o una app que el usuario agregó ([ComandosUsuario]).
 *
 * Cada destino se intenta en orden: primero la app instalada y, si no está, su
 * versión web. Desde el servicio en segundo plano esto solo funciona con el
 * permiso "Mostrar sobre otras apps" concedido.
 */
data class Destino(
    val id: String,
    private val nombreRes: Int?,
    private val nombreTexto: String?,
    val paquete: String?,
    private val url: String?,
    private val tipo: Tipo,
) {

    enum class Tipo {
        /** Su app; si no está, el asistente predeterminado del sistema. */
        ASISTENTE,

        /** Un enlace que se ofrece primero a su app (Claude Code en claude.ai/code). */
        ENLACE,

        /** Su app; si no está, su versión web (si tiene). */
        APP,
    }

    fun nombre(context: Context): String = nombreTexto ?: context.getString(nombreRes ?: R.string.app_name)

    val esIntegrado: Boolean get() = INTEGRADOS.any { it.id == id }

    /** Si abre una página web cuando la app no está (Claude Code siempre es web). */
    val tieneWeb: Boolean get() = url != null

    fun instalada(context: Context): Boolean =
        paquete != null && context.packageManager.getLaunchIntentForPackage(paquete) != null

    /** Abre el destino. Devuelve false si no hubo forma de abrirlo. */
    fun abrir(context: Context): Boolean {
        for (intent in intentos(context)) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
                // probar el siguiente
            } catch (_: SecurityException) {
                // idem
            }
        }
        return false
    }

    private fun intentos(context: Context): List<Intent> = buildList {
        val lanzar = paquete?.let { context.packageManager.getLaunchIntentForPackage(it) }
        val web = url?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) }
        when (tipo) {
            Tipo.ASISTENTE -> {
                lanzar?.let(::add)
                add(Intent(Intent.ACTION_VOICE_COMMAND))
                add(Intent(Intent.ACTION_ASSIST))
            }
            Tipo.ENLACE -> {
                web?.let { add(Intent(it).setPackage(paquete)) }
                web?.let(::add)
            }
            Tipo.APP -> {
                lanzar?.let(::add)
                web?.let(::add)
            }
        }
    }

    companion object {
        val GEMINI = Destino(
            "gemini", R.string.dest_gemini, null, "com.google.android.apps.bard", null, Tipo.ASISTENTE,
        )
        val CLAUDE_CODE = Destino(
            "claude_code", R.string.dest_claude_code, null, "com.anthropic.claude", "https://claude.ai/code", Tipo.ENLACE,
        )
        val CLAUDE = Destino(
            "claude", R.string.dest_claude, null, "com.anthropic.claude", "https://claude.ai/new", Tipo.APP,
        )
        val CHATGPT = Destino(
            "chatgpt", R.string.dest_chatgpt, null, "com.openai.chatgpt", "https://chatgpt.com/", Tipo.APP,
        )

        val INTEGRADOS = listOf(GEMINI, CLAUDE_CODE, CLAUDE, CHATGPT)

        /** Id de un destino agregado por el usuario: "app:<paquete>". */
        fun idDeApp(paquete: String) = "app:$paquete"

        fun deApp(paquete: String, nombre: String) = Destino(idDeApp(paquete), null, nombre, paquete, null, Tipo.APP)

        /** Integrados y los que agregó el usuario. */
        fun todos(context: Context): List<Destino> =
            INTEGRADOS + ComandosUsuario(context).lista().map { deApp(it.paquete, it.nombre) }

        fun porId(context: Context, id: String): Destino? = todos(context).firstOrNull { it.id == id }

        /** El que abre «Oye Exi» a secas (Gemini si no se eligió otro o ya no existe). */
        fun favorito(context: Context): Destino =
            porId(context, ExySettings(context).favoritoId) ?: GEMINI
    }
}
