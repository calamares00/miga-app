package com.bmo00.miga.data.search

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val TIMEOUT_MILLIS = 15000
private const val MAX_PAGE_TEXT_CHARS = 20000

sealed interface UrlFetchResult {
    data class Success(val text: String) : UrlFetchResult
    data class Error(val reason: String) : UrlFetchResult
}

/**
 * Descarga una página web y extrae su texto legible (sin etiquetas HTML, scripts ni estilos) para
 * pasárselo a un LLM y que extraiga la receta - no hace ningún parseo estructurado de microdatos
 * (schema.org Recipe, JSON-LD...), delega esa interpretación en el propio modelo, igual que ya se
 * hace con el texto reconocido de una foto.
 */
object RecipeUrlFetcher {
    suspend fun fetchReadableText(url: String): UrlFetchResult = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Miga-Recipe-App")
            try {
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext UrlFetchResult.Error("La página respondió con el código $responseCode")
                }
                val html = connection.inputStream.bufferedReader().use { it.readText() }
                val text = extractReadableText(html)
                if (text.isBlank()) {
                    UrlFetchResult.Error("No se pudo extraer texto de la página")
                } else {
                    UrlFetchResult.Success(text.take(MAX_PAGE_TEXT_CHARS))
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UrlFetchResult.Error(e.message ?: e::class.simpleName ?: "No se pudo descargar la página")
        }
    }

    private fun extractReadableText(html: String): String {
        val withoutNoise = html
            .replace(Regex("(?is)<script.*?</script>"), " ")
            .replace(Regex("(?is)<style.*?</style>"), " ")
            .replace(Regex("(?is)<!--.*?-->"), " ")
        val withoutTags = withoutNoise.replace(Regex("(?is)<[^>]+>"), " ")
        val decoded = withoutTags
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
        return decoded.replace(Regex("\\s+"), " ").trim()
    }
}
