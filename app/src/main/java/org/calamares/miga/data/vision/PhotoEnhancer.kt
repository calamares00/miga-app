package org.calamares.miga.data.vision

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.calamares.miga.data.ai.AiCandidate
import org.calamares.miga.data.ai.AiImage
import org.calamares.miga.data.ai.AiRequest
import org.calamares.miga.data.ai.AiText
import org.calamares.miga.data.ai.complete
import org.calamares.miga.data.ai.decodeAiJson
import org.calamares.miga.data.support.AiErrors
import java.io.ByteArrayOutputStream
import kotlin.math.pow
import kotlin.math.roundToInt

/** Small: the answer is a handful of numbers, but reasoning models spend part of it thinking. */
private const val ENHANCE_MAX_TOKENS = 1024

/** The model only needs to judge light and colour, so the copy it sees is small and cheap. */
private const val ENHANCE_PREVIEW_DIMENSION = 768

/**
 * Corrections a model suggests for a dish photo, each from 0 (leave as is) towards the end of its
 * range. They are applied on the device by [applyAdjustments]: the model never edits the photo
 * itself, it only judges it, so any provider that reads images can do it and the result is never
 * an invented picture.
 */
@Serializable
data class PhotoAdjustments(
    /** -1 darker … 1 brighter, in stops. */
    val exposure: Float = 0f,
    /** -0.5 flatter … 1 punchier. */
    val contrast: Float = 0f,
    /** -0.5 duller … 1 more vivid; already saturated colours are raised less. */
    val saturation: Float = 0f,
    /** -1 cooler (bluer) … 1 warmer (more yellow). */
    val warmth: Float = 0f,
    /** -1 greener … 1 more magenta. */
    val tint: Float = 0f,
    /** 0 … 1 brightens the dark areas. */
    val shadows: Float = 0f,
    /** -1 recovers bright areas … 0. */
    val highlights: Float = 0f,
    /** 0 … 1 crisper detail. */
    val sharpness: Float = 0f
) {
    /** The same values within their ranges, whatever the model answered. */
    fun clamped() = PhotoAdjustments(
        exposure = exposure.safe().coerceIn(-1f, 1f),
        contrast = contrast.safe().coerceIn(-0.5f, 1f),
        saturation = saturation.safe().coerceIn(-0.5f, 1f),
        warmth = warmth.safe().coerceIn(-1f, 1f),
        tint = tint.safe().coerceIn(-1f, 1f),
        shadows = shadows.safe().coerceIn(0f, 1f),
        highlights = highlights.safe().coerceIn(-1f, 0f),
        sharpness = sharpness.safe().coerceIn(0f, 1f)
    )

    private fun Float.safe() = if (isFinite()) this else 0f
}

sealed interface PhotoEnhanceResult {
    data class Success(val adjustments: PhotoAdjustments) : PhotoEnhanceResult
    data class Error(val reason: String) : PhotoEnhanceResult
}

/** Asks the model how [photo] should be corrected; see [PhotoAdjustments]. */
suspend fun AiCandidate.suggestPhotoAdjustments(photo: Bitmap): PhotoEnhanceResult {
    val jpeg = withContext(Dispatchers.Default) {
        ByteArrayOutputStream().use { out ->
            scaleToFit(photo, ENHANCE_PREVIEW_DIMENSION).compress(Bitmap.CompressFormat.JPEG, 80, out)
            out.toByteArray()
        }
    }
    val request = AiRequest(PHOTO_ENHANCE_PROMPT, ENHANCE_MAX_TOKENS, images = listOf(AiImage(jpeg, "image/jpeg")))
    return when (val answer = complete(request)) {
        is AiText.Error -> PhotoEnhanceResult.Error(answer.reason)
        is AiText.Success -> try {
            PhotoEnhanceResult.Success(decodeAiJson(PhotoAdjustments.serializer(), answer.text).clamped())
        } catch (e: Exception) {
            PhotoEnhanceResult.Error(AiErrors.badResponse(e, answer.text))
        }
    }
}

internal val PHOTO_ENHANCE_PROMPT = """
    You are a food photo editor. Look at this photo of a dish and decide the corrections that make
    it look natural, appetising and well lit, as a careful photographer would: fix a dim or
    overexposed shot, a yellow or blue cast from indoor light, flat colours and soft detail. Keep
    it realistic; never exaggerate. If the photo already looks good, use values close to 0. If it
    is not a photo of food (a page of text, for example), return every value as 0.

    Values and ranges:
    - exposure: -1 (darker) to 1 (brighter), in stops.
    - contrast: -0.5 (flatter) to 1 (more contrast).
    - saturation: -0.5 (duller) to 1 (more vivid colours).
    - warmth: -1 (cooler, bluer) to 1 (warmer, more yellow).
    - tint: -1 (greener) to 1 (more magenta).
    - shadows: 0 to 1, brightens the dark areas.
    - highlights: -1 to 0, recovers very bright areas.
    - sharpness: 0 to 1, crisper detail.
    Typical values are small: exposure between -0.3 and 0.3, contrast, saturation and shadows
    between 0.1 and 0.3, sharpness between 0.2 and 0.5.

    Answer only with a JSON object with these numbers, for example:
    {"exposure": 0.2, "contrast": 0.15, "saturation": 0.2, "warmth": -0.1, "tint": 0.0, "shadows": 0.2, "highlights": -0.1, "sharpness": 0.3}
""".trimIndent()

/**
 * Applies [adjustments] to a copy of [source]: white balance and a tone curve (exposure, shadows,
 * highlights, contrast) through per-channel lookup tables, then saturation that spares colours
 * already intense, then an unsharp mask for detail.
 */
fun applyAdjustments(source: Bitmap, adjustments: PhotoAdjustments): Bitmap {
    val a = adjustments.clamped()
    val width = source.width
    val height = source.height
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)

    // Warm: more red, less blue. Tint: magenta lowers green.
    val redGain = 1f + 0.12f * a.warmth
    val blueGain = 1f - 0.12f * a.warmth
    val greenGain = 1f - 0.08f * a.tint
    val lutR = toneLut(a, redGain)
    val lutG = toneLut(a, greenGain)
    val lutB = toneLut(a, blueGain)

    for (i in pixels.indices) {
        val p = pixels[i]
        var r = lutR[(p shr 16) and 0xFF]
        var g = lutG[(p shr 8) and 0xFF]
        var b = lutB[p and 0xFF]
        if (a.saturation != 0f) {
            val luma = 0.299f * r + 0.587f * g + 0.114f * b
            val chroma = (maxOf(r, g, b) - minOf(r, g, b)) / 255f
            // Like "vibrance": a pale ingredient gains colour, a red tomato does not turn neon.
            val factor = if (a.saturation > 0f) 1f + a.saturation * (1f - chroma) else 1f + a.saturation
            r = (luma + (r - luma) * factor).coerceIn(0f, 255f)
            g = (luma + (g - luma) * factor).coerceIn(0f, 255f)
            b = (luma + (b - luma) * factor).coerceIn(0f, 255f)
        }
        pixels[i] = (p and 0xFF000000.toInt()) or (r.roundToInt() shl 16) or (g.roundToInt() shl 8) or b.roundToInt()
    }
    if (a.sharpness > 0f) sharpen(pixels, width, height, a.sharpness * 0.9f)

    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, width, 0, 0, width, height)
    }
}

/** Value 0-255 → corrected value, for one channel with its white balance [gain]. */
private fun toneLut(a: PhotoAdjustments, gain: Float): FloatArray {
    val exposureGain = 2f.pow(a.exposure)
    return FloatArray(256) { index ->
        var x = (index / 255f * gain * exposureGain).coerceIn(0f, 1f)
        // Shadows lift the low tones and highlights pull down the high ones, leaving black and white in place.
        x += a.shadows * 1.2f * x * (1f - x) * (1f - x)
        x += a.highlights * 1.2f * x * x * (1f - x)
        x = x.coerceIn(0f, 1f)
        // S-curve: positive contrast moves towards smoothstep, negative away from it.
        val smooth = x * x * (3f - 2f * x)
        x += a.contrast * (smooth - x)
        x.coerceIn(0f, 1f) * 255f
    }
}

/** Unsharp mask with a 3×3 box blur, in place. */
private fun sharpen(pixels: IntArray, width: Int, height: Int, amount: Float) {
    if (width < 3 || height < 3) return
    val source = pixels.copyOf()
    for (y in 1 until height - 1) {
        val row = y * width
        for (x in 1 until width - 1) {
            val i = row + x
            var sumR = 0
            var sumG = 0
            var sumB = 0
            for (dy in -width..width step width) {
                for (dx in -1..1) {
                    val q = source[i + dy + dx]
                    sumR += (q shr 16) and 0xFF
                    sumG += (q shr 8) and 0xFF
                    sumB += q and 0xFF
                }
            }
            val p = source[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val nr = (r + amount * (r - sumR / 9f)).roundToInt().coerceIn(0, 255)
            val ng = (g + amount * (g - sumG / 9f)).roundToInt().coerceIn(0, 255)
            val nb = (b + amount * (b - sumB / 9f)).roundToInt().coerceIn(0, 255)
            pixels[i] = (p and 0xFF000000.toInt()) or (nr shl 16) or (ng shl 8) or nb
        }
    }
}

private fun scaleToFit(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val maxSide = maxOf(bitmap.width, bitmap.height)
    if (maxSide <= maxDimension) return bitmap
    val scale = maxDimension.toFloat() / maxSide
    return Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * scale).roundToInt().coerceAtLeast(1),
        (bitmap.height * scale).roundToInt().coerceAtLeast(1),
        true
    )
}
