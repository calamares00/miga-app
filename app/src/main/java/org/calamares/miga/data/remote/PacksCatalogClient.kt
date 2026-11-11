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
import java.security.MessageDigest
import org.calamares.miga.data.io.ReadLimits
import org.calamares.miga.data.io.readTextAtMost

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
    val minAppVersion: Int? = null,
    /** Language of the recipes ("es", "en"); null for a pack meant for every language. */
    val language: String? = null,
    /** Series the pack belongs to ("Cocina del mundo", "Specials"...), used to group packs. */
    val series: String? = null,
    /** Shared by the Spanish and English versions of a pack; the install counter adds them up. */
    val family: String? = null,
    /** SHA-256 of the ZIP in hexadecimal; a download that does not match is refused. */
    val sha256: String? = null
)

/**
 * The packs to list for an app in [language]: those in that language or without one, plus the
 * installed ones whatever their language, so they can still be updated after switching language.
 */
fun List<PackEntryDto>.visibleFor(language: String, installedIds: Set<String>): List<PackEntryDto> =
    filter { it.language == null || it.language.equals(language, ignoreCase = true) || it.id in installedIds }

@Serializable
private data class CatalogDto(
    val schemaVersion: Int = 1,
    val packs: List<PackEntryDto> = emptyList()
)

/** Outcome of [PacksCatalogClient.downloadPackZip]. */
sealed interface PackDownloadResult {
    class Success(val bytes: ByteArray) : PackDownloadResult
    /** Network failure, an error from the server or a ZIP over the size limit. */
    data object Failed : PackDownloadResult
    /** The ZIP is not the one its catalogue describes. */
    data object ChecksumMismatch : PackDownloadResult
    /** The address is not https. */
    data object NotHttps : PackDownloadResult
}

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
            if (!isHttps(catalogUrl)) return@withContext CatalogFetchResult.Error(L10n.str(R.string.catalogue_needs_https))
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
                val body = connection.inputStream.use { it.readTextAtMost(ReadLimits.CATALOG_BYTES) }
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
     * Catalogues and packs only travel over https: a pack is recipes and photos that end up in the
     * user's books, and over plain http anyone on the way could swap them. (HttpURLConnection never
     * follows a redirect from https to http.)
     */
    fun isHttps(url: String): Boolean = url.trim().startsWith("https://", ignoreCase = true)

    /**
     * Downloads a pack ZIP ([PackEntryDto.downloadUrl]) of at most [MAX_PACK_BYTES] and checks it
     * against [sha256] when the catalogue gives one.
     */
    suspend fun downloadPackZip(url: String, sha256: String? = null): PackDownloadResult = withContext(Dispatchers.IO) {
        if (!isHttps(url)) return@withContext PackDownloadResult.NotHttps
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = DOWNLOAD_TIMEOUT_MILLIS
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext PackDownloadResult.Failed
                if (connection.contentLengthLong > MAX_PACK_BYTES) return@withContext PackDownloadResult.Failed
                val bytes = connection.inputStream.use { input -> readAtMost(input, MAX_PACK_BYTES) }
                    ?: return@withContext PackDownloadResult.Failed
                if (matchesSha256(bytes, sha256)) PackDownloadResult.Success(bytes) else PackDownloadResult.ChecksumMismatch
            } finally {
                connection.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PackDownloadResult.Failed
        }
    }

    /** True when [expected] is blank (the catalogue gives no hash) or is the SHA-256 of [bytes]. */
    fun matchesSha256(bytes: ByteArray, expected: String?): Boolean {
        if (expected.isNullOrBlank()) return true
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return actual.equals(expected.trim(), ignoreCase = true)
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
