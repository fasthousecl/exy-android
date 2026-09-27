package cl.exy.app

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Botón de Exy en los ajustes rápidos (cortina de notificaciones).
 *
 * - Escuchando o en espera → toca para pausar.
 * - En pausa → toca para reanudar.
 * - Detenida → abre Exy y empieza a escuchar. Android no deja encender el
 *   micrófono desde segundo plano, así que el primer inicio pasa por la app.
 */
class ExyTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        actualizar()
    }

    override fun onClick() {
        super.onClick()
        if (WakeWordService.isRunning) {
            WakeWordService.send(this, WakeWordService.ACTION_TOGGLE)
            return
        }
        val abrir = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_INICIAR, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, abrir, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(abrir)
        }
    }

    private fun actualizar() {
        val tile = qsTile ?: return
        val estado = WakeWordService.state
        tile.icon = Icon.createWithResource(this, R.drawable.ic_notification)
        tile.label = getString(R.string.app_name)
        tile.state = when (estado) {
            WakeWordService.State.LISTENING, WakeWordService.State.COOLDOWN,
            WakeWordService.State.STARTING -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.subtitle = getString(
            when (estado) {
                WakeWordService.State.LISTENING, WakeWordService.State.STARTING -> R.string.tile_escuchando
                WakeWordService.State.COOLDOWN -> R.string.tile_espera
                WakeWordService.State.PAUSED -> R.string.tile_pausa
                WakeWordService.State.RESTING -> R.string.tile_descanso
                WakeWordService.State.NO_HEADPHONES -> R.string.tile_auriculares
                WakeWordService.State.ERROR -> R.string.tile_error
                WakeWordService.State.STOPPED -> R.string.tile_detenida
            },
        )
        tile.updateTile()
    }
}
