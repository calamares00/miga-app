package org.calamares.miga.data.remote

import org.calamares.miga.L10n
import org.calamares.miga.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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

/** Catálogo oficial de packs; el usuario puede usar otro en Ajustes (una URL o un repositorio "usuario/repo" de GitHub). */
const val DEFAULT_PACKS_CATALOG = "https://miga.calamares.org/packs/catalog.json"

/** Valor que guardaban versiones anteriores como catálogo por defecto; se trata como el oficial. */
const val LEGACY_DEFAULT_PACKS_CATALOG = "bmo00/miga-packs"

private const val TIMEOUT_MILLIS = 8000
// Descargar un ZIP de recetas con fotos puede tardar más que la simple lectura del catálogo.
private const val DOWNLOAD_TIMEOUT_MILLIS = 30000

/**
 * Cliente del catálogo de packs de recetas descargables: un catalog.json con un ZIP por pack. El
 * catálogo puede estar en una web (URL, por defecto [DEFAULT_PACKS_CATALOG]) o en un repositorio de
 * GitHub ("usuario/repo", servido vía raw.githubusercontent.com). Las URLs de portada y descarga del
 * catálogo pueden ser relativas a él. HttpURLConnection crudo + kotlinx.serialization.
 */
object PacksCatalogClient {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * URL del catalog.json para [source]: una URL (si no acaba en .json se le añade /catalog.json)
     * o un repositorio de GitHub "usuario/repo".
     */
    fun catalogUrlFor(source: String): String {
        val trimmed = source.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return if (trimmed.substringBefore('?').endsWith(".json", ignoreCase = true)) trimmed else trimmed.trimEnd('/') + "/catalog.json"
        }
        return "https://raw.githubusercontent.com/${trimmed.trim('/')}/main/catalog.json"
    }

    /** Resuelve [ref] (absoluta o relativa, p. ej. "zips/pack.zip") respecto a la URL del catálogo. */
    fun resolveUrl(catalogUrl: String, ref: String): String =
        runCatching { URI(catalogUrl).resolve(ref.trim()).toString() }.getOrDefault(ref)

    suspend fun fetchCatalog(repoPath: String): CatalogFetchResult = withContext(Dispatchers.IO) {
        if (repoPath.isBlank()) {
            return@withContext CatalogFetchResult.Error(L10n.str(R.string.configura_catalogo_ajustes))
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
                        L10n.str(R.string.no_pudo_cargar_catalogo_codigo, responseCode)
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
            CatalogFetchResult.Error(e.message ?: e::class.simpleName ?: L10n.str(R.string.error_desconocido))
        }
    }

    /** Descarga los bytes del ZIP de un pack ([PackEntryDto.downloadUrl]); null si falla. */
    suspend fun downloadPackZip(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = DOWNLOAD_TIMEOUT_MILLIS
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}
