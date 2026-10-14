package org.calamares.miga.data.remote

import org.calamares.miga.L10n
import org.calamares.miga.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

@Serializable
data class PackEntryDto(
    val id: String,
    val name: String,
    val author: String,
    val authorGithub: String? = null,
    val description: String = "",
    val coverImageUrl: String? = null,
    val latestVersion: Int,
    val recipeCount: Int = 0,
    val downloadUrl: String,
    val minAppVersion: Int? = null
)

@Serializable
private data class CatalogDto(
    val schemaVersion: Int = 1,
    val packs: List<PackEntryDto> = emptyList()
)

sealed interface CatalogFetchResult {
    data class Success(val packs: List<PackEntryDto>) : CatalogFetchResult
    data class Error(val reason: String) : CatalogFetchResult
}

/**
 * Official packs catalogue. Users can pick another one in Settings (a URL or a GitHub "user/repo").
 */
const val DEFAULT_PACKS_CATALOG = "https://miga.calamares.org/packs/catalog.json"

private const val TIMEOUT_MILLIS = 8000
/** Downloading a recipe ZIP with photos can take longer than reading the catalogue. */
private const val DOWNLOAD_TIMEOUT_MILLIS = 30000
private const val MAX_PACK_BYTES = 50L * 1024 * 1024

/**
 * Client for the catalogue of downloadable recipe packs: a catalog.json with one ZIP per pack. The
 * catalogue can live on a website (a URL, [DEFAULT_PACKS_CATALOG] by default) or in a GitHub
 * repository ("user/repo", served through raw.githubusercontent.com). Cover and download URLs may
 * be relative to the catalogue.
 */
object PacksCatalogClient {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * catalog.json URL for [source]: a URL ("/catalog.json" is appended unless it ends in .json) or
     * a GitHub "user/repo".
     */
    fun catalogUrlFor(source: String): String {
        val trimmed = source.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return if (trimmed.substringBefore('?').endsWith(".json", ignoreCase = true)) trimmed else trimmed.trimEnd('/') + "/catalog.json"
        }
        return "https://raw.githubusercontent.com/${trimmed.trim('/')}/main/catalog.json"
    }

    /** Resolves [ref], absolute or relative ("zips/pack.zip"), against the catalogue URL. */
    fun resolveUrl(catalogUrl: String, ref: String): String =
        runCatching { URI(catalogUrl).resolve(ref.trim()).toString() }.getOrDefault(ref)

    suspend fun fetchCatalog(repoPath: String): CatalogFetchResult = withContext(Dispatchers.IO) {
        if (repoPath.isBlank()) {
            return@withContext CatalogFetchResult.Error(L10n.str(R.string.set_up_catalogue_settings))
        }
        try {
            val catalogUrl = catalogUrlFor(repoPath)
            val connection = URL(catalogUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            try {
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext CatalogFetchResult.Error(
                        L10n.str(R.string.couldnt_load_catalogue_code_x, responseCode)
                    )
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val catalog = json.decodeFromString(CatalogDto.serializer(), body)
                CatalogFetchResult.Success(
                    catalog.packs.map { pack ->
                        pack.copy(
                            downloadUrl = resolveUrl(catalogUrl, pack.downloadUrl),
                            coverImageUrl = pack.coverImageUrl?.let { resolveUrl(catalogUrl, it) }
                        )
                    }
                )
            } finally {
                connection.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CatalogFetchResult.Error(e.message ?: e::class.simpleName ?: L10n.str(R.string.unknown_error))
        }
    }

    /**
     * Downloads a pack ZIP ([PackEntryDto.downloadUrl]). Returns null on failure or when it exceeds
     * [MAX_PACK_BYTES].
     */
    suspend fun downloadPackZip(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = DOWNLOAD_TIMEOUT_MILLIS
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                if (connection.contentLengthLong > MAX_PACK_BYTES) return@withContext null
                connection.inputStream.use { input -> readAtMost(input, MAX_PACK_BYTES) }
            } finally {
                connection.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Reads the whole stream, or returns null when it is longer than [limit] bytes. */
    private fun readAtMost(input: InputStream, limit: Long): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return out.toByteArray()
            if (out.size() + read > limit) return null
            out.write(buffer, 0, read)
        }
    }
}
