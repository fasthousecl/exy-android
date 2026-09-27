package cl.exy.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

object AssistantLauncher {

    /**
     * Abre el asistente predeterminado. Primero ACTION_VOICE_COMMAND (entra
     * directo en modo escucha) y, si nadie lo atiende, ACTION_ASSIST.
     * Desde el servicio en segundo plano esto solo funciona con el permiso
     * "Mostrar sobre otras apps" concedido.
     */
    fun launch(context: Context): Boolean {
        for (action in listOf(Intent.ACTION_VOICE_COMMAND, Intent.ACTION_ASSIST)) {
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // probar la siguiente acción
            } catch (_: SecurityException) {
                // idem
            }
        }
        return false
    }
}
