package cl.exy.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException

/**
 * Los archivos de Picovoice se copian a almacenamiento interno de la app
 * (files/), fuera del alcance de otras apps y del repositorio.
 */
enum class ModelFile(val fileName: String, val extension: String) {
    KEYWORD("palabra_clave.ppn", ".ppn"),
    MODEL("modelo_es.pv", ".pv");

    fun file(context: Context): File = File(context.filesDir, fileName)

    fun exists(context: Context): Boolean = file(context).let { it.isFile && it.length() > 0 }
}

object ModelFiles {

    /** Nombre original con que se importó cada archivo, solo para mostrarlo. */
    private const val PREFS = "exy_files"

    fun originalName(context: Context, type: ModelFile): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(type.name, null)

    /**
     * Copia el documento elegido a almacenamiento interno. Escribe primero a un
     * temporal para no dejar un archivo a medias si la copia falla.
     */
    @Throws(IOException::class, IllegalArgumentException::class)
    fun import(context: Context, uri: Uri, type: ModelFile) {
        val name = displayName(context, uri) ?: uri.lastPathSegment.orEmpty()
        require(name.lowercase().endsWith(type.extension)) {
            context.getString(R.string.import_wrong_ext, type.extension)
        }

        val target = type.file(context)
        val tmp = File(context.filesDir, "${type.fileName}.tmp")
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) throw IOException("No se pudo abrir el archivo")
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        if (tmp.length() == 0L) {
            tmp.delete()
            throw IOException("El archivo está vacío")
        }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(type.name, name).apply()
    }

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}
