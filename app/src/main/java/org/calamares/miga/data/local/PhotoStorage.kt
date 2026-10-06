package org.calamares.miga.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Longest side of every photo stored by the app (book covers, recipe photos). Phone photos of 10-50
 * MP weigh several MB and are only ever shown as thumbnails or full screen on a phone.
 */
private const val MAX_PHOTO_DIMENSION = 1600
private const val JPEG_QUALITY = 85

/**
 * Size and quality of the photos sent to an AI model (not the ones stored for display). Gemini
 * charges tokens per image tile, which depends on resolution, so images are not sent larger than
 * needed to read the text; 1280 px is plenty for printed or handwritten text in a cookbook photo.
 */
private const val MAX_VISION_DIMENSION = 1280
private const val VISION_JPEG_QUALITY = 80

/** Stores and reads the photos kept by the app in internal storage (`files/photos`). */
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

    /** Same as [copyToInternalStorage] for bytes already in memory. */
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

    /** Same as [copyBytesToInternalStorage] but streaming from [input], for large ZIP entries. */
    fun copyStreamToInternalStorage(context: Context, input: InputStream): String? {
        return try {
            val dir = File(context.filesDir, "photos").apply { mkdirs() }
            val destination = File(dir, "${UUID.randomUUID()}.jpg")
            destination.outputStream().use { output -> input.copyTo(output) }
            "file://${destination.absolutePath}"
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Creates an empty file in internal storage for the system camera app to write into. Returns
     * the `content://` uri (through FileProvider, needed by the camera) and the `file://` uri used
     * to reference the photo like any other.
     */
    fun createCaptureTarget(context: Context): Pair<Uri, String> {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val destination = File(dir, "${UUID.randomUUID()}.jpg")
        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destination)
        return contentUri to "file://${destination.absolutePath}"
    }

    /**
     * Loads an image for editing: EXIF orientation corrected and downscaled to a manageable size.
     */
    fun loadBitmap(context: Context, uri: Uri): Bitmap? {
        val upright = loadUprightSampled(context, uri, MAX_PHOTO_DIMENSION) ?: return null
        return downscaleIfNeeded(upright, MAX_PHOTO_DIMENSION)
    }

    /**
     * Decodes an image with its EXIF orientation applied and its longest side roughly
     * [maxDimension] or more (subsampled with inSampleSize so a 50 MP photo is never decoded at
     * full size). The aspect ratio is kept, so coordinates normalised on the copy sent to the AI
     * also apply here.
     */
    fun loadUprightSampled(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return null
            rotateBitmap(decoded, readExifRotationDegrees(bytes))
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
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

    /**
     * Reads, straightens and downscales a photo and returns JPEG bytes ready to send to an AI
     * model, without writing to disk. Optimised for upload size (see [MAX_VISION_DIMENSION]), not
     * for display.
     */
    fun readResizedJpegBytes(context: Context, uri: Uri): ByteArray? {
        val upright = loadUprightSampled(context, uri, MAX_VISION_DIMENSION) ?: return null
        val resized = downscaleIfNeeded(upright, MAX_VISION_DIMENSION)
        return ByteArrayOutputStream().use { out ->
            resized.compress(Bitmap.CompressFormat.JPEG, VISION_JPEG_QUALITY, out)
            out.toByteArray()
        }
    }

    /** Stores [bitmap] as a JPEG in internal storage, downscaled when needed. */
    fun saveNormalized(context: Context, bitmap: Bitmap): String {
        val normalized = downscaleIfNeeded(bitmap, MAX_PHOTO_DIMENSION)
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val destination = File(dir, "${UUID.randomUUID()}.jpg")
        FileOutputStream(destination).use { out -> normalized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
        return "file://${destination.absolutePath}"
    }

    /**
     * Deletes the file behind a "file://..." uri stored by the app. Does nothing if it no longer
     * exists.
     */
    fun deleteFile(uri: String) {
        runCatching { File(Uri.parse(uri).path ?: return@runCatching).delete() }
    }

    /**
     * Raw bytes of a photo stored by the app ("file://..."), used to upload it to the sync server.
     */
    fun readBytes(uri: String): ByteArray? =
        runCatching { File(Uri.parse(uri).path ?: return null).readBytes() }.getOrNull()
}
