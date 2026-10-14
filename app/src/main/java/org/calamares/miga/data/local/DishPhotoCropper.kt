package org.calamares.miga.data.local

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import org.calamares.miga.data.vision.DishPhotoDto
import kotlin.math.abs
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
/** Size of the thumbnails used to find the photo and its borders; small enough to be fast. */
private const val ANALYSIS_DIMENSION = 320
/** Inward margin applied to the AI box when it is used as is (no photo found around it). */
private const val INSET_FRACTION = 0.015f
/** Margin added around the AI box before looking for the photo, as a fraction of the image. */
private const val EXPAND_FRACTION = 0.05f
/** Side, in analysis pixels, of the cells classified as photo or page. */
internal const val ANALYSIS_CELL = 4
/** Page pixels are neutral (low chroma) and light, within a tolerance of the page luminance. */
private const val PAPER_MAX_CHROMA = 32
private const val PAPER_MIN_LUMINANCE = 110
private const val PAPER_LUMINANCE_TOLERANCE = 34
/** Allowed width/height ratio; longer photos are center-cropped along their long side. */
private const val MIN_ASPECT = 0.6f
private const val MAX_ASPECT = 1.8f
private const val MIN_RESULT_SIDE = 160

/** Normalised (0..1) box on image number [image]. */
internal data class NormalizedBox(
    val image: Int,
    val top: Float,
    val left: Float,
    val bottom: Float,
    val right: Float,
    /** Clockwise degrees (0, 90, 180 or 270) that turn the cropped photo upright. */
    val rotation: Int = 0
) {
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
                    ?.let { cleaned -> PhotoStorage.rotateBitmap(cleaned, box.rotation.toFloat()) }
                    ?.let { upright -> runCatching { PhotoStorage.saveNormalized(context, upright) }.getOrNull() }
                    ?.let { uri -> saved[box] = uri }
            }
        }
        return boxes.mapNotNull { saved[it] }
    }

    private fun cleanCrop(source: Bitmap, box: NormalizedBox): Bitmap? {
        val w = source.width
        val h = source.height
        // The analysed region is the AI box plus a margin: its edges are often a little off, either
        // cutting the photo or taking in some text.
        val marginX = w * EXPAND_FRACTION
        val marginY = h * EXPAND_FRACTION
        val rx = (box.left * w - marginX).roundToInt().coerceIn(0, w - 1)
        val ry = (box.top * h - marginY).roundToInt().coerceIn(0, h - 1)
        val rw = ((box.right * w + marginX).roundToInt() - rx).coerceIn(1, w - rx)
        val rh = ((box.bottom * h + marginY).roundToInt() - ry).coerceIn(1, h - ry)
        val region = Bitmap.createBitmap(source, rx, ry, rw, rh)

        val scale = min(1f, ANALYSIS_DIMENSION.toFloat() / max(rw, rh))
        val aw = (rw * scale).roundToInt().coerceAtLeast(1)
        val ah = (rh * scale).roundToInt().coerceAtLeast(1)
        val small = if (scale < 1f) Bitmap.createScaledBitmap(region, aw, ah, true) else region
        val pixels = IntArray(aw * ah)
        small.getPixels(pixels, 0, aw, 0, 0, aw, ah)

        val hintLeft = ((box.left * w - rx) * scale).roundToInt().coerceIn(0, aw - 1)
        val hintTop = ((box.top * h - ry) * scale).roundToInt().coerceIn(0, ah - 1)
        val hint = PixelRect(
            hintLeft,
            hintTop,
            ((box.right * w - rx) * scale).roundToInt().coerceIn(hintLeft + 1, aw),
            ((box.bottom * h - ry) * scale).roundToInt().coerceIn(hintTop + 1, ah)
        )
        // The photo found on the page itself; the AI box (slightly shrunk) is the fallback.
        val photo = refinePhotoRect(pixels, aw, ah, hint)
            ?.takeIf { isPlausibleRefinement(it, hint) }
            ?.inset(ANALYSIS_CELL)
            ?: hint.insetFraction(INSET_FRACTION)

        val x = (rx + photo.left / scale).roundToInt().coerceIn(0, w - 1)
        val y = (ry + photo.top / scale).roundToInt().coerceIn(0, h - 1)
        val cw = ((rx + photo.right / scale).roundToInt() - x).coerceIn(1, w - x)
        val ch = ((ry + photo.bottom / scale).roundToInt() - y).coerceIn(1, h - y)
        val crop = Bitmap.createBitmap(source, x, y, cw, ch)
        return trimAndFrame(crop)
    }

    /** Removes uniform borders left on [crop] and center-crops it when it is too elongated. */
    private fun trimAndFrame(crop: Bitmap): Bitmap? {
        val cw = crop.width
        val ch = crop.height
        // Uniform borders (white margin, frame, flat shadow) are detected on a thumbnail.
        val scale = min(1f, ANALYSIS_DIMENSION.toFloat() / max(cw, ch))
        val aw = (cw * scale).roundToInt().coerceAtLeast(1)
        val ah = (ch * scale).roundToInt().coerceAtLeast(1)
        val small = if (scale < 1f) Bitmap.createScaledBitmap(crop, aw, ah, true) else crop
        val pixels = IntArray(aw * ah)
        small.getPixels(pixels, 0, aw, 0, 0, aw, ah)
        val luminance = IntArray(pixels.size) { i -> luminanceOf(pixels[i]) }
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

/** Rectangle in pixels; [right] and [bottom] are exclusive. */
internal data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    val area get() = width * height

    fun intersectionArea(other: PixelRect): Int {
        val iw = (min(right, other.right) - max(left, other.left)).coerceAtLeast(0)
        val ih = (min(bottom, other.bottom) - max(top, other.top)).coerceAtLeast(0)
        return iw * ih
    }

    /** Shrunk by [pixels] on every side, or unchanged when that would leave it too small. */
    fun inset(pixels: Int): PixelRect =
        if (width > pixels * 4 && height > pixels * 4) PixelRect(left + pixels, top + pixels, right - pixels, bottom - pixels) else this

    fun insetFraction(fraction: Float): PixelRect {
        val dx = (width * fraction).roundToInt()
        val dy = (height * fraction).roundToInt()
        return PixelRect(left + dx, top + dy, right - dx, bottom - dy)
    }
}

/**
 * Finds the printed photo around [hint] (the AI box, in pixels of the [w]x[h] [argb] image) and
 * returns the largest rectangle that contains only photo: no page, text, frame or binding.
 *
 * The page colour is estimated from the light, neutral pixels around the hint. The image is split
 * into cells of [cell] pixels and a cell counts as photo when less than half of it looks like
 * page. The connected group of photo cells that overlaps the hint the most is the photo; holes in
 * it (a white plate, a pale background) are filled, and the largest rectangle inside it is
 * returned. Returns null when there is no page around the hint (the whole image is a photo) or no
 * group overlaps the hint enough, so the caller falls back to the AI box.
 */
internal fun refinePhotoRect(argb: IntArray, w: Int, h: Int, hint: PixelRect, cell: Int = ANALYSIS_CELL): PixelRect? {
    val paperLuminance = estimatePaperLuminance(argb, w, h, hint) ?: return null
    val cols = (w + cell - 1) / cell
    val rows = (h + cell - 1) / cell
    val photo = BooleanArray(cols * rows) { index ->
        val cx = index % cols
        val cy = index / cols
        var total = 0
        var paper = 0
        for (y in cy * cell until min(h, (cy + 1) * cell)) {
            for (x in cx * cell until min(w, (cx + 1) * cell)) {
                total++
                if (isPaperLike(argb[y * w + x], paperLuminance)) paper++
            }
        }
        paper * 2 < total
    }

    // Connected groups of photo cells (4-neighbourhood); keep the one overlapping the hint most.
    val hintCells = PixelRect(hint.left / cell, hint.top / cell, (hint.right + cell - 1) / cell, (hint.bottom + cell - 1) / cell)
    val group = IntArray(cols * rows) { -1 }
    var bestGroup = -1
    var bestOverlap = 0
    var groupCount = 0
    val queue = ArrayDeque<Int>()
    for (start in photo.indices) {
        if (!photo[start] || group[start] >= 0) continue
        val id = groupCount++
        var overlap = 0
        group[start] = id
        queue.addLast(start)
        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            val cx = index % cols
            val cy = index / cols
            if (cx >= hintCells.left && cx < hintCells.right && cy >= hintCells.top && cy < hintCells.bottom) overlap++
            for ((nx, ny) in listOf(cx - 1 to cy, cx + 1 to cy, cx to cy - 1, cx to cy + 1)) {
                if (nx < 0 || ny < 0 || nx >= cols || ny >= rows) continue
                val next = ny * cols + nx
                if (photo[next] && group[next] < 0) {
                    group[next] = id
                    queue.addLast(next)
                }
            }
        }
        if (overlap > bestOverlap) {
            bestOverlap = overlap
            bestGroup = id
        }
    }
    if (bestGroup < 0 || bestOverlap * 10 < hintCells.area * 3) return null

    val inPhoto = BooleanArray(cols * rows) { group[it] == bestGroup }
    fillHoles(inPhoto, cols, rows)
    val rect = largestRectangle(inPhoto, cols, rows) ?: return null
    return PixelRect(rect.left * cell, rect.top * cell, min(w, rect.right * cell), min(h, rect.bottom * cell))
}

/**
 * A refined rectangle is trusted when it is mostly inside the AI box and not a sliver of it;
 * otherwise the AI box is used as is.
 */
internal fun isPlausibleRefinement(refined: PixelRect, hint: PixelRect): Boolean =
    refined.width >= hint.width / 4 && refined.height >= hint.height / 4 &&
        refined.area * 100 >= hint.area * 15 &&
        refined.intersectionArea(hint) * 10 >= refined.area * 6

/**
 * Luminance of the page around [hint]: the median of the light, neutral pixels outside it. Null
 * when there are too few of them, i.e. there is no page to tell the photo apart from.
 */
private fun estimatePaperLuminance(argb: IntArray, w: Int, h: Int, hint: PixelRect): Int? {
    val histogram = IntArray(256)
    var outside = 0
    var count = 0
    for (y in 0 until h) {
        for (x in 0 until w) {
            if (x >= hint.left && x < hint.right && y >= hint.top && y < hint.bottom) continue
            outside++
            val c = argb[y * w + x]
            val luminance = luminanceOf(c)
            if (chromaOf(c) < PAPER_MAX_CHROMA && luminance >= PAPER_MIN_LUMINANCE) {
                histogram[luminance]++
                count++
            }
        }
    }
    if (count < 50 || count * 10 < outside) return null
    var seen = 0
    for (luminance in 0..255) {
        seen += histogram[luminance]
        if (seen * 2 >= count) return luminance
    }
    return null
}

private fun isPaperLike(c: Int, paperLuminance: Int): Boolean =
    chromaOf(c) < PAPER_MAX_CHROMA && abs(luminanceOf(c) - paperLuminance) <= PAPER_LUMINANCE_TOLERANCE

/** Marks as true every false cell that cannot reach the grid border through false cells. */
private fun fillHoles(cells: BooleanArray, cols: Int, rows: Int) {
    val outside = BooleanArray(cells.size)
    val queue = ArrayDeque<Int>()
    for (index in cells.indices) {
        val cx = index % cols
        val cy = index / cols
        val onBorder = cx == 0 || cy == 0 || cx == cols - 1 || cy == rows - 1
        if (onBorder && !cells[index]) {
            outside[index] = true
            queue.addLast(index)
        }
    }
    while (queue.isNotEmpty()) {
        val index = queue.removeFirst()
        val cx = index % cols
        val cy = index / cols
        for ((nx, ny) in listOf(cx - 1 to cy, cx + 1 to cy, cx to cy - 1, cx to cy + 1)) {
            if (nx < 0 || ny < 0 || nx >= cols || ny >= rows) continue
            val next = ny * cols + nx
            if (!cells[next] && !outside[next]) {
                outside[next] = true
                queue.addLast(next)
            }
        }
    }
    for (index in cells.indices) if (!outside[index]) cells[index] = true
}

/** Largest axis-aligned rectangle made only of true cells, in cell units; null when none. */
internal fun largestRectangle(cells: BooleanArray, cols: Int, rows: Int): PixelRect? {
    val heights = IntArray(cols)
    var best: PixelRect? = null
    var bestArea = 0
    for (row in 0 until rows) {
        for (col in 0 until cols) heights[col] = if (cells[row * cols + col]) heights[col] + 1 else 0
        // Largest rectangle in the histogram of column heights ending at this row.
        val stack = ArrayDeque<Int>()
        for (col in 0..cols) {
            val current = if (col < cols) heights[col] else 0
            while (stack.isNotEmpty() && heights[stack.last()] >= current) {
                val height = heights[stack.removeLast()]
                val left = if (stack.isEmpty()) 0 else stack.last() + 1
                val area = height * (col - left)
                if (area > bestArea) {
                    bestArea = area
                    best = PixelRect(left, row - height + 1, col, row + 1)
                }
            }
            stack.addLast(col)
        }
    }
    return best
}

private fun luminanceOf(c: Int): Int {
    val r = (c shr 16) and 0xFF
    val g = (c shr 8) and 0xFF
    val b = c and 0xFF
    return (r * 299 + g * 587 + b * 114) / 1000
}

/** Difference between the strongest and weakest channel: 0 for greys, high for vivid colours. */
private fun chromaOf(c: Int): Int {
    val r = (c shr 16) and 0xFF
    val g = (c shr 8) and 0xFF
    val b = c and 0xFF
    return max(r, max(g, b)) - min(r, min(g, b))
}

/**
 * Validates and normalises the boxes returned by the AI: drops invalid indexes or coordinates, tiny
 * boxes and duplicates (heavy overlap on the same image), and keeps at most [MAX_DISH_PHOTOS].
 */
internal fun validDishBoxes(dishPhotos: List<DishPhotoDto>, imageCount: Int): List<NormalizedBox> {
    val candidates = dishPhotos.mapNotNull { dto ->
        if (dto.image !in 0 until imageCount || dto.box.size != 4) return@mapNotNull null
        val (ymin, xmin, ymax, xmax) = dto.box.map { it.coerceIn(0, 1000) / 1000f }
        val box = NormalizedBox(dto.image, min(ymin, ymax), min(xmin, xmax), max(ymin, ymax), max(xmin, xmax), normalizeRotation(dto.rotation))
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

/** Rounds [degrees] to the nearest quarter turn in 0..270; anything else the AI sends becomes 0. */
internal fun normalizeRotation(degrees: Int): Int {
    if (degrees < -360 || degrees > 720) return 0
    return Math.floorMod(Math.round(degrees / 90f) * 90, 360)
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
