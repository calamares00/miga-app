package com.bmo00.miga.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt

// Lado mayor al que se reduce cualquier foto guardada por la app (portada de libro, foto de
// receta) para que no ocupen más espacio del necesario; una foto de móvil normal (10-50 MP) puede
// pesar varios MB, y en esta app solo se ven en miniaturas o a pantalla completa en un móvil.
private const val MAX_PHOTO_DIMENSION = 1600
private const val JPEG_QUALITY = 85

// Límite de resolución y calidad específico para las fotos que se envían a un LLM de visión (no
// las que se guardan para verse en la app): el coste en tokens de la API de Gemini depende del
// número de "tiles" en los que se divide la imagen según su resolución, así que no interesa
// enviarla a más tamaño del necesario para leer el texto. 1280px de lado mayor es de sobra para
// reconocer letra impresa o manuscrita de una foto de libro de cocina tomada con un móvil normal,
// aunque el original sea 4K o más. Además se convierte a escala de grises: reduce bastante el peso
// del JPEG (menos bytes que subir) y quita ruido de color que no aporta nada para leer texto.
private const val MAX_VISION_DIMENSION = 1280
private const val VISION_JPEG_QUALITY = 80

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
        val upright = decodeUpright(context, uri) ?: return null
        return downscaleIfNeeded(upright, MAX_PHOTO_DIMENSION)
    }

    /** Decodifica una imagen y corrige su orientación EXIF, sin reducir aún su tamaño. */
    private fun decodeUpright(context: Context, uri: Uri): Bitmap? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val rotationDegrees = readExifRotationDegrees(bytes)
            if (rotationDegrees != 0f) rotateBitmap(decoded, rotationDegrees) else decoded
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

    private fun downscaleIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / maxSide
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val grayscale = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) }
        Canvas(grayscale).drawBitmap(bitmap, 0f, 0f, paint)
        return grayscale
    }

    /** Lee una foto, la endereza, la reduce y la convierte a escala de grises, y devuelve sus
     *  bytes JPEG listos para enviar a un LLM de visión, sin escribirla a disco. Optimizada para
     *  minimizar tokens/peso de subida (ver [MAX_VISION_DIMENSION]), no para verse bien en la app. */
    fun readResizedJpegBytes(context: Context, uri: Uri): ByteArray? {
        val upright = decodeUpright(context, uri) ?: return null
        val optimized = toGrayscale(downscaleIfNeeded(upright, MAX_VISION_DIMENSION))
        return ByteArrayOutputStream().use { out ->
            optimized.compress(Bitmap.CompressFormat.JPEG, VISION_JPEG_QUALITY, out)
            out.toByteArray()
        }
    }

    /** Guarda [bitmap] como JPEG en el almacenamiento interno, reduciéndolo si hiciera falta. */
    fun saveNormalized(context: Context, bitmap: Bitmap): String {
        val normalized = downscaleIfNeeded(bitmap, MAX_PHOTO_DIMENSION)
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val destination = File(dir, "${UUID.randomUUID()}.jpg")
        FileOutputStream(destination).use { out -> normalized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
        return "file://${destination.absolutePath}"
    }
}
