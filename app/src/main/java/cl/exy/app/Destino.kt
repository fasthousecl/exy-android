package cl.exy.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes

/**
 * Las apps que Exy sabe abrir. El id coincide con el de assets/comandos.json.
 *
 * Cada destino se intenta en orden: primero la app instalada y, si no está, su
 * versión web. Desde el servicio en segundo plano esto solo funciona con el
 * permiso "Mostrar sobre otras apps" concedido.
 */
enum class Destino(
    val id: String,
    @StringRes val nombre: Int,
    private val paquete: String?,
    private val url: String?,
) {
    GEMINI("gemini", R.string.dest_gemini, "com.google.android.apps.bard", null),
    CLAUDE_CODE("claude_code", R.string.dest_claude_code, "com.anthropic.claude", "https://claude.ai/code"),
    CLAUDE("claude", R.string.dest_claude, "com.anthropic.claude", "https://claude.ai/new"),
    CHATGPT("chatgpt", R.string.dest_chatgpt, "com.openai.chatgpt", "https://chatgpt.com/");

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
        when (this@Destino) {
            // Gemini: su app; si no está, el asistente predeterminado del sistema.
            GEMINI -> {
                lanzar?.let(::add)
                add(Intent(Intent.ACTION_VOICE_COMMAND))
                add(Intent(Intent.ACTION_ASSIST))
            }
            // Claude Code vive en claude.ai/code: primero se ofrece el enlace a la
            // app de Claude y, si no lo acepta, se abre en el navegador.
            CLAUDE_CODE -> {
                web?.let { add(Intent(it).setPackage(paquete)) }
                web?.let(::add)
            }
            // Claude y ChatGPT: su app; si no está, la versión web.
            CLAUDE, CHATGPT -> {
                lanzar?.let(::add)
                web?.let(::add)
            }
        }
    }

    companion object {
        val FAVORITO = GEMINI

        fun porId(id: String): Destino? = entries.firstOrNull { it.id == id }
    }
}
