package cl.exy.app

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/** Si hay audífonos Bluetooth conectados (no pide permisos: usa AudioManager). */
object Auriculares {

    fun conectados(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java)
        val tipos = buildSet {
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
        return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in tipos }
    }
}
