package org.calamares.miga.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import org.calamares.miga.data.vision.DishPhotoDto
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Resolution of the original loaded for cropping: higher than what is sent to the AI so the photo
 * looks good in the recipe (saveNormalized later caps it at 1600 px).
 */
private const val MAX_SOURCE_DIMENSION = 2400
private const val MAX_DISH_PHOTOS = 3
/** Size of the thumbnail used to look for uniform borders; small enough to be fast. */
private const val ANALYSIS_DIMENSION = 400
/** Inward margin applied to the AI box so frame lines and the paper edge are not included. */
private const val INSET_FRACTION = 0.015f
/** Allowed width/height ratio; longer photos are center-cropped along their long side. */
private const val MIN_ASPECT = 0.6f
private const val MAX_ASPECT = 1.8f
private const val MIN_RESULT_SIDE = 160

/** Normalised (0..1) box on image number [image]. */
internal data class NormalizedBox(val image: Int, val top: Float, val left: Float, val bottom: Float, val right: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val area get() = width * height
}

/**
 * Crops the dish photos the AI located in a recipe's images, cleans them (no margins or uniform
 * borders, centered) and stores them as app photos.
 */
object DishPhotoCropper {

    /** Returns the uris ("file://...") of the saved photos, in the order given by the AI. */
    fun extract(context: Context, sources: List<Uri>, dishPhotos: List<DishPhotoDto>): List<String> {
        val boxes = validDishBoxes(dishPhotos, sources.size)
        if (boxes.isEmpty()) return emptyList()
        // One source image is decoded at a time and dropped before the next, since every decoded
        // image can take tens of megabytes.
        val saved = mutableMapOf<NormalizedBox, String>()
        boxes.groupBy { it.image }.forEach { (imageIndex, imageBoxes) ->
            val source = PhotoStorage.loadUprightSampled(context, sources[imageIndex], MAX_SOURCE_DIMENSION) ?: return@forEach
            imageBoxes.forEach { box ->
                runCatching { cleanCrop(source, box) }.getOrNull()
                    ?.let { cleaned -> runCatching { PhotoStorage.saveNormalized(context, cleaned) }.getOrNull() }
                    ?.let { uri -> saved[box] = uri }
            }
        }
        return boxes.mapNotNull { saved[it] }
    }

    private fun cleanCrop(source: Bitmap, box: NormalizedBox): Bitmap? {
        val w = source.width
        val h = source.height
        // AI box in pixels, shrunk slightly inwards.
        var left = box.left * w
        var top = box.top * h
        var right = box.right * w
        var bottom = box.bottom * h
        val insetX = (right - left) * INSET_FRACTION
        val insetY = (bottom - top) * INSET_FRACTION
        left += insetX; right -= insetX; top += insetY; bottom -= insetY
        val x = left.roundToInt().coerceIn(0, w - 1)
        val y = top.roundToInt().coerceIn(0, h - 1)
        val cw = (right.roundToInt() - x).coerceIn(1, w - x)
        val ch = (bottom.roundToInt() - y).coerceIn(1, h - y)
        val crop = Bitmap.createBitmap(source, x, y, cw, ch)

        // Uniform borders (white margin, frame, flat shadow) are detected on a thumbnail.
        val scale = min(1f, ANALYSIS_DIMENSION.toFloat() / max(cw, ch))
        val aw = (cw * scale).roundToInt().coerceAtLeast(1)
        val ah = (ch * scale).roundToInt().coerceAtLeast(1)
        val small = if (scale < 1f) Bitmap.createScaledBitmap(crop, aw, ah, true) else crop
        val pixels = IntArray(aw * ah)
        small.getPixels(pixels, 0, aw, 0, 0, aw, ah)
        val luminance = IntArray(pixels.size) { i ->
            val c = pixels[i]
            (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
        }
        val trim = uniformEdgeTrim(luminance, aw, ah)
        var tx = (trim[1] / scale).roundToInt()
        var ty = (trim[0] / scale).roundToInt()
        var tw = cw - tx - (trim[3] / scale).roundToInt()
        var th = ch - ty - (trim[2] / scale).roundToInt()
        if (tw < MIN_RESULT_SIDE || th < MIN_RESULT_SIDE) { tx = 0; ty = 0; tw = cw; th = ch }

        // Center-crop the long side when the photo is too elongated.
        val aspect = tw.toFloat() / th
        if (aspect > MAX_ASPECT) {
            val newW = (th * MAX_ASPECT).roundToInt()
            tx += (tw - newW) / 2; tw = newW
        } else if (aspect < MIN_ASPECT) {
            val newH = (tw / MIN_ASPECT).roundToInt()
            ty += (th - newH) / 2; th = newH
        }
        if (tw < MIN_RESULT_SIDE || th < MIN_RESULT_SIDE) return null
        return Bitmap.createBitmap(crop, tx.coerceIn(0, cw - 1), ty.coerceIn(0, ch - 1), tw.coerceAtMost(cw - tx), th.coerceAtMost(ch - ty))
    }
}

/**
 * Validates and normalises the boxes returned by the AI: drops invalid indexes or coordinates, tiny
 * boxes and duplicates (heavy overlap on the same image), and keeps at most [MAX_DISH_PHOTOS].
 */
internal fun validDishBoxes(dishPhotos: List<DishPhotoDto>, imageCount: Int): List<NormalizedBox> {
    val candidates = dishPhotos.mapNotNull { dto ->
        if (dto.image !in 0 until imageCount || dto.box.size != 4) return@mapNotNull null
        val (ymin, xmin, ymax, xmax) = dto.box.map { it.coerceIn(0, 1000) / 1000f }
        val box = NormalizedBox(dto.image, min(ymin, ymax), min(xmin, xmax), max(ymin, ymax), max(xmin, xmax))
        box.takeIf { it.width >= 0.08f && it.height >= 0.08f && it.area >= 0.02f }
    }
    val kept = mutableListOf<NormalizedBox>()
    for (box in candidates) {
        val duplicate = kept.any { it.image == box.image && intersectionOverUnion(it, box) > 0.5f }
        if (!duplicate) kept += box
        if (kept.size == MAX_DISH_PHOTOS) break
    }
    return kept
}

internal fun intersectionOverUnion(a: NormalizedBox, b: NormalizedBox): Float {
    val iw = (min(a.right, b.right) - max(a.left, b.left)).coerceAtLeast(0f)
    val ih = (min(a.bottom, b.bottom) - max(a.top, b.top)).coerceAtLeast(0f)
    val intersection = iw * ih
    val union = a.area + b.area - intersection
    return if (union <= 0f) 0f else intersection / union
}

/**
 * Counts the uniform rows and columns (almost no luminance variation: paper margin, frame, plain
 * background) on each edge of a [w]x[h] image given as 0..255 luminances, up to [maxFraction] per
 * side. Returns `[top, left, bottom, right]`.
 */
internal fun uniformEdgeTrim(luminance: IntArray, w: Int, h: Int, maxFraction: Float = 0.15f, maxStdDev: Double = 12.0): IntArray {
    fun stdDev(values: IntArray): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }
    fun row(y: Int) = IntArray(w) { x -> luminance[y * w + x] }
    fun column(x: Int) = IntArray(h) { y -> luminance[y * w + x] }
    val maxRows = (h * maxFraction).toInt()
    val maxCols = (w * maxFraction).toInt()
    var top = 0
    while (top < maxRows && stdDev(row(top)) < maxStdDev) top++
    var bottom = 0
    while (bottom < maxRows && stdDev(row(h - 1 - bottom)) < maxStdDev) bottom++
    var left = 0
    while (left < maxCols && stdDev(column(left)) < maxStdDev) left++
    var right = 0
    while (right < maxCols && stdDev(column(w - 1 - right)) < maxStdDev) right++
    return intArrayOf(top, left, bottom, right)
}
