package cl.exy.app

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import java.io.IOException

/**
 * El modelo pequeño de Vosk en español viene dentro del APK (assets/model-es,
 * lo descarga el CI). Vosk necesita rutas de archivo reales, así que la primera
 * vez se copia a almacenamiento interno. El archivo `uuid` identifica la versión
 * del modelo: solo se vuelve a copiar si cambia.
 */
object VoskModel {

    private const val ASSET_DIR = "model-es"
    private const val UUID_FILE = "uuid"

    /** Solo desde un hilo de trabajo: la primera copia tarda unos segundos. */
    @Throws(IOException::class)
    fun prepare(context: Context): File {
        val assets = context.assets
        val bundledId = try {
            assets.open("$ASSET_DIR/$UUID_FILE").bufferedReader().use { it.readLine()?.trim() }
        } catch (e: IOException) {
            null
        }
        if (bundledId.isNullOrEmpty()) {
            throw IOException("el modelo de voz no viene incluido en este APK")
        }

        val target = File(context.noBackupFilesDir, ASSET_DIR)
        val marker = File(target, UUID_FILE)
        if (marker.isFile && marker.readText().trim() == bundledId) return target

        val tmp = File(context.noBackupFilesDir, "$ASSET_DIR.tmp")
        tmp.deleteRecursively()
        copyAssetDir(assets, ASSET_DIR, tmp)
        target.deleteRecursively()
        if (!tmp.renameTo(target)) throw IOException("no se pudo instalar el modelo de voz")
        return target
    }

    private fun copyAssetDir(assets: AssetManager, path: String, dest: File) {
        val children = assets.list(path).orEmpty()
        if (children.isEmpty()) {
            dest.parentFile?.mkdirs()
            assets.open(path).use { input -> dest.outputStream().use { input.copyTo(it) } }
            return
        }
        dest.mkdirs()
        for (child in children) copyAssetDir(assets, "$path/$child", File(dest, child))
    }
}
