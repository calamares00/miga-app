package com.bmo00.miga.data.local

import android.content.Context
import android.net.Uri
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
}
