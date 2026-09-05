package com.bmo00.miga.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt

// Lado mayor al que se reduce cualquier foto guardada por la app (portada de libro, foto de
// receta) para que no ocupen más espacio del necesario; una foto de móvil normal (10-50 MP) puede
// pesar varios MB, y en esta app solo se ven en miniaturas o a pantalla completa en un móvil.
private const val MAX_PHOTO_DIMENSION = 1600
private const val JPEG_QUALITY = 85

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

    /** Carga una imagen para editarla: corrige su orientación EXIF y la reduce a un tamaño manejable. */
    fun loadBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val rotationDegrees = readExifRotationDegrees(bytes)
            val upright = if (rotationDegrees != 0f) rotateBitmap(decoded, rotationDegrees) else decoded
            downscaleIfNeeded(upright)
        } catch (e: Exception) {
            null
        }
    }

    private fun readExifRotationDegrees(bytes: ByteArray): Float {
        return try {
            ByteArrayInputStream(bytes).use { stream ->
                when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        } catch (e: Exception) {
            0f
        }
    }

    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun downscaleIfNeeded(bitmap: Bitmap): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= MAX_PHOTO_DIMENSION) return bitmap
        val scale = MAX_PHOTO_DIMENSION.toFloat() / maxSide
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    /**
     * Recorta [bitmap] a la ventana que quedaba visible dentro de un marco de
     * [frameWidthPx] x [frameHeightPx] (relleno "cover", como ContentScale.Crop) desplazado
     * [panOffsetX]/[panOffsetY] píxeles desde el centro. Ver PhotoEditorOverlay para el arrastre.
     */
    fun cropToFrame(bitmap: Bitmap, frameWidthPx: Float, frameHeightPx: Float, panOffsetX: Float, panOffsetY: Float): Bitmap {
        if (frameWidthPx <= 0f || frameHeightPx <= 0f) return bitmap
        val scale = maxOf(frameWidthPx / bitmap.width, frameHeightPx / bitmap.height)
        val cropWidth = (frameWidthPx / scale).coerceIn(1f, bitmap.width.toFloat())
        val cropHeight = (frameHeightPx / scale).coerceIn(1f, bitmap.height.toFloat())
        val left = (bitmap.width / 2f - cropWidth / 2f - panOffsetX / scale).coerceIn(0f, bitmap.width - cropWidth)
        val top = (bitmap.height / 2f - cropHeight / 2f - panOffsetY / scale).coerceIn(0f, bitmap.height - cropHeight)
        val leftPx = left.roundToInt()
        val topPx = top.roundToInt()
        val widthPx = cropWidth.roundToInt().coerceIn(1, bitmap.width - leftPx)
        val heightPx = cropHeight.roundToInt().coerceIn(1, bitmap.height - topPx)
        return Bitmap.createBitmap(bitmap, leftPx, topPx, widthPx, heightPx)
    }

    /** Guarda [bitmap] como JPEG en el almacenamiento interno, reduciéndolo si hiciera falta. */
    fun saveNormalized(context: Context, bitmap: Bitmap): String {
        val normalized = downscaleIfNeeded(bitmap)
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val destination = File(dir, "${UUID.randomUUID()}.jpg")
        FileOutputStream(destination).use { out -> normalized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
        return "file://${destination.absolutePath}"
    }
}
