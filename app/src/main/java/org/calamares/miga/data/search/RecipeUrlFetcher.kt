package org.calamares.miga.data.search

import org.calamares.miga.L10n
import org.calamares.miga.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

private const val TIMEOUT_MILLIS = 15000
private const val MAX_PAGE_TEXT_CHARS = 20000
private const val MAX_PAGE_BYTES = 3 * 1024 * 1024
private const val MAX_REDIRECTS = 5

sealed interface UrlFetchResult {
    data class Success(val text: String) : UrlFetchResult
    data class Error(val reason: String) : UrlFetchResult
}

/**
 * Downloads a web page and extracts its readable text (no tags, scripts or styles) for the AI to
 * extract the recipe from. Structured data such as schema.org Recipe or JSON-LD is not parsed here;
 * the model interprets the text, the same way it does with a photo.
 */
object RecipeUrlFetcher {
    suspend fun fetchReadableText(url: String): UrlFetchResult = withContext(Dispatchers.IO) {
        try {
            var target = URL(url.trim())
            for (attempt in 0..MAX_REDIRECTS) {
                if (target.protocol != "http" && target.protocol != "https") break
                val connection = target.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                // HttpURLConnection does not follow redirects between http and https, so they are
                // followed by hand.
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Miga-Recipe-App")
                try {
                    val responseCode = connection.responseCode
                    val location = connection.getHeaderField("Location")
                    if (responseCode in 300..399 && location != null) {
                        target = URL(target, location)
                        continue
                    }
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        return@withContext UrlFetchResult.Error(L10n.str(R.string.page_responded_code_x, responseCode))
                    }
                    val bytes = connection.inputStream.use { readAtMost(it, MAX_PAGE_BYTES) }
                    val text = extractReadableText(String(bytes, charsetOf(connection.contentType, bytes)))
                    return@withContext if (text.isBlank()) {
                        UrlFetchResult.Error(L10n.str(R.string.couldnt_extract_text_page))
                    } else {
                        UrlFetchResult.Success(text.take(MAX_PAGE_TEXT_CHARS))
                    }
                } finally {
                    connection.disconnect()
                }
            }
            UrlFetchResult.Error(L10n.str(R.string.couldnt_download_page))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UrlFetchResult.Error(e.message ?: e::class.simpleName ?: L10n.str(R.string.couldnt_download_page))
        }
    }

    /** Reads up to [limit] bytes; the recipe is near the top of a page, so truncating is fine. */
    private fun readAtMost(input: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (out.size() < limit) {
            val read = input.read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /** Charset from the Content-Type header or a <meta charset> tag; UTF-8 when neither is valid. */
    internal fun charsetOf(contentType: String?, bytes: ByteArray): Charset {
        val head = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1)
        val name = contentType?.let { CHARSET_PATTERN.find(it)?.groupValues?.get(1) }
            ?: CHARSET_PATTERN.find(head)?.groupValues?.get(1)
        return name?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
    }

    internal fun extractReadableText(html: String): String {
        val withoutNoise = html
            .replace(Regex("(?is)<script.*?</script>"), " ")
            .replace(Regex("(?is)<style.*?</style>"), " ")
            .replace(Regex("(?is)<!--.*?-->"), " ")
        val withoutTags = withoutNoise.replace(Regex("(?is)<[^>]+>"), " ")
        return decodeEntities(withoutTags).replace(Regex("\\s+"), " ").trim()
    }

    private fun decodeEntities(text: String): String = ENTITY_PATTERN.replace(text) { match ->
        val entity = match.groupValues[1]
        val codePoint = when {
            entity.startsWith("#x", ignoreCase = true) -> entity.drop(2).toIntOrNull(16)
            entity.startsWith("#") -> entity.drop(1).toIntOrNull()
            else -> NAMED_ENTITIES[entity]
        }
        if (codePoint != null && Character.isValidCodePoint(codePoint)) String(Character.toChars(codePoint)) else match.value
    }

    private val CHARSET_PATTERN = Regex("""charset\s*=\s*["']?([A-Za-z0-9_\-:.]+)""", RegexOption.IGNORE_CASE)
    private val ENTITY_PATTERN = Regex("""&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,7});""")

    // Named entities that commonly appear in Spanish and English recipe pages.
    private val NAMED_ENTITIES = mapOf(
        "nbsp" to ' '.code, "amp" to '&'.code, "lt" to '<'.code, "gt" to '>'.code, "quot" to '"'.code,
        "apos" to '\''.code, "aacute" to 'á'.code, "eacute" to 'é'.code, "iacute" to 'í'.code,
        "oacute" to 'ó'.code, "uacute" to 'ú'.code, "Aacute" to 'Á'.code, "Eacute" to 'É'.code,
        "Iacute" to 'Í'.code, "Oacute" to 'Ó'.code, "Uacute" to 'Ú'.code, "ntilde" to 'ñ'.code,
        "Ntilde" to 'Ñ'.code, "uuml" to 'ü'.code, "Uuml" to 'Ü'.code, "iquest" to '¿'.code,
        "iexcl" to '¡'.code, "deg" to '°'.code, "frac12" to '½'.code, "frac14" to '¼'.code,
        "frac34" to '¾'.code, "ordm" to 'º'.code, "ordf" to 'ª'.code, "hellip" to '…'.code,
        "ndash" to '–'.code, "mdash" to '—'.code, "rsquo" to '’'.code, "lsquo" to '‘'.code,
        "rdquo" to '”'.code, "ldquo" to '“'.code, "laquo" to '«'.code, "raquo" to '»'.code,
        "middot" to '·'.code, "times" to '×'.code
    )
}
