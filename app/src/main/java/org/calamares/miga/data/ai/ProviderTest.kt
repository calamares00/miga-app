package org.calamares.miga.data.ai

import androidx.core.graphics.createBitmap
import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream

/**
 * Result of "Test connection" in Settings: whether the provider answered with this key and model,
 * how long it took and, when the model is set as reading photos, whether it really read one.
 * [error] and [imageError] are user-facing messages (see AiErrors).
 */
data class ProviderTestResult(
    val millis: Long,
    val error: String? = null,
    /** Null when photos were not tried (the model is set as not reading them, or text failed). */
    val readsImage: Boolean? = null,
    val imageError: String? = null
) {
    val ok: Boolean get() = error == null
}

/**
 * Sends the smallest useful request to one provider, outside the usual fallback to the next one,
 * so the answer (or error) is that provider's own.
 */
object ProviderTest {
    /** Room for reasoning models, which spend tokens thinking before the answer. */
    private const val MAX_TOKENS = 512

    suspend fun run(candidate: AiCandidate, checkImage: Boolean, now: () -> Long = System::currentTimeMillis): ProviderTestResult {
        val start = now()
        val text = candidate.complete(AiRequest("Reply with just the word OK.", MAX_TOKENS, expectJson = false))
        val millis = now() - start
        if (text is AiText.Error) return ProviderTestResult(millis, error = text.reason)
        if (!checkImage) return ProviderTestResult(millis)
        val image = candidate.complete(
            AiRequest("What colour is this image? Answer with one word.", MAX_TOKENS, images = listOf(AiImage(testImage(), "image/jpeg")), expectJson = false)
        )
        return when (image) {
            is AiText.Success -> ProviderTestResult(millis, readsImage = true)
            is AiText.Error -> ProviderTestResult(millis, readsImage = false, imageError = image.reason)
        }
    }

    /** A small plain red picture: big enough for every API, nothing personal in it. */
    private fun testImage(): ByteArray {
        val bitmap = createBitmap(64, 64).apply { eraseColor(Color.RED) }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }
}
