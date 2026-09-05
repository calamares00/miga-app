package com.bmo00.miga.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
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

/** Repositorio de GitHub ("owner/repo") con el catálogo por defecto; el usuario puede cambiarlo en Ajustes. */
const val DEFAULT_PACKS_CATALOG_REPO = "bmo00/miga-packs"

private const val TIMEOUT_MILLIS = 8000
// Descargar un ZIP de recetas con fotos puede tardar más que la simple lectura del catálogo.
private const val DOWNLOAD_TIMEOUT_MILLIS = 30000

/**
 * Cliente del catálogo de packs de recetas descargables: un repositorio de GitHub (configurable
 * en Ajustes, ver SettingsRepository.observePacksCatalogRepo) con un catalog.json y un ZIP por
 * pack, servidos vía raw.githubusercontent.com (sin límite de peticiones, a diferencia de
 * api.github.com que ya usa UpdateChecker). Mismo estilo que UpdateChecker/GeminiVisionClient:
 * HttpURLConnection crudo + kotlinx.serialization, sin librería de red nueva.
 */
object PacksCatalogClient {

    private val json = Json { ignoreUnknownKeys = true }

    fun catalogUrlFor(repoPath: String): String =
        "https://raw.githubusercontent.com/${repoPath.trim().trim('/')}/main/catalog.json"

    suspend fun fetchCatalog(repoPath: String): CatalogFetchResult = withContext(Dispatchers.IO) {
        if (repoPath.isBlank()) {
            return@withContext CatalogFetchResult.Error("Configura el repositorio del catálogo en Ajustes")
        }
        try {
            val connection = URL(catalogUrlFor(repoPath)).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            try {
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext CatalogFetchResult.Error(
                        "No se pudo cargar el catálogo (código $responseCode). Revisa el repositorio configurado en Ajustes."
                    )
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val catalog = json.decodeFromString(CatalogDto.serializer(), body)
                CatalogFetchResult.Success(catalog.packs)
            } finally {
                connection.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CatalogFetchResult.Error(e.message ?: e::class.simpleName ?: "Error desconocido")
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
