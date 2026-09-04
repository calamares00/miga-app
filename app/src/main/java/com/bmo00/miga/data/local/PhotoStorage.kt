package com.bmo00.miga.data.local

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** Copia una imagen elegida por el usuario al almacenamiento interno de la app para que persista. */
object PhotoStorage {

    fun copyToInternalStorage(context: Context, source: Uri): String? {
        return try {
            val dir = File(context.filesDir, "photos").apply { mkdirs() }
            val destination = File(dir, "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(source)?.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            "file://${destination.absolutePath}"
        } catch (e: Exception) {
            null
        }
    }

    /** Igual que [copyToInternalStorage] pero a partir de bytes ya en memoria (foto extraída de un ZIP importado). */
    fun copyBytesToInternalStorage(context: Context, bytes: ByteArray): String? {
        return try {
            val dir = File(context.filesDir, "photos").apply { mkdirs() }
            val destination = File(dir, "${UUID.randomUUID()}.jpg")
            destination.writeBytes(bytes)
            "file://${destination.absolutePath}"
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Crea un fichero vacío en el almacenamiento interno para que la app de cámara del sistema
     * escriba la foto ahí. Devuelve el `content://` (vía FileProvider, necesario para pasárselo a
     * la cámara) y el `file://` con el que luego se referencia la foto igual que cualquier otra.
     */
    fun createCaptureTarget(context: Context): Pair<Uri, String> {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val destination = File(dir, "${UUID.randomUUID()}.jpg")
        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destination)
        return contentUri to "file://${destination.absolutePath}"
    }
}
