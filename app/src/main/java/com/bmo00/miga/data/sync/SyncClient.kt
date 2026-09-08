package com.bmo00.miga.data.sync

import com.bmo00.miga.data.model.SyncConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

sealed interface SyncPingResult {
    data object Success : SyncPingResult
    data class Error(val reason: String) : SyncPingResult
}

sealed interface SyncFetchResult {
    data class Success(val changes: ChangesResponseDto) : SyncFetchResult
    data class Error(val reason: String) : SyncFetchResult
}

/** Resultado de subir (o borrar) un libro/receta/foto: aplicado con éxito (nueva revisión), en
 *  conflicto (el servidor tenía una versión más reciente, se devuelve tal cual para aplicarla
 *  localmente), o un error de red/servidor. */
sealed interface SyncPushResult<T> {
    data class Applied<T>(val revision: Long) : SyncPushResult<T>
    data class Conflict<T>(val serverCopy: T) : SyncPushResult<T>
    data class Error<T>(val reason: String) : SyncPushResult<T>
}

private const val TIMEOUT_MILLIS = 8000
private const val PHOTO_TIMEOUT_MILLIS = 30000

/**
 * Cliente HTTP del servidor de sincronización self-hosted (ver miga-server). Mismo estilo que
 * PacksCatalogClient/UpdateChecker: HttpURLConnection crudo + kotlinx.serialization, sin
 * librería de red nueva, sin reintentos.
 */
object SyncClient {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun ping(connection: SyncConnection): SyncPingResult = withContext(Dispatchers.IO) {
        try {
            val result = request(connection, "GET", "/sync/ping", body = null)
            when {
                result.code == HttpURLConnection.HTTP_OK -> SyncPingResult.Success
                result.code == HttpURLConnection.HTTP_UNAUTHORIZED -> SyncPingResult.Error("Token de acceso inválido o revocado")
                else -> SyncPingResult.Error("El servidor respondió con el código ${result.code}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncPingResult.Error(e.message ?: e::class.simpleName ?: "No se pudo conectar con el servidor")
        }
    }

    suspend fun fetchChanges(connection: SyncConnection, since: Long): SyncFetchResult = withContext(Dispatchers.IO) {
        try {
            val result = request(connection, "GET", "/sync/changes?since=$since", body = null)
            if (result.code != HttpURLConnection.HTTP_OK) {
                return@withContext SyncFetchResult.Error(errorMessageFor(result))
            }
            SyncFetchResult.Success(json.decodeFromString(ChangesResponseDto.serializer(), result.body))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncFetchResult.Error(e.message ?: e::class.simpleName ?: "Error desconocido")
        }
    }

    suspend fun pushBook(connection: SyncConnection, dto: BookSyncDto): SyncPushResult<BookSyncDto> =
        pushJson(connection, "PUT", "/sync/books/${dto.uid}", BookSyncDto.serializer(), dto, BookSyncDto.serializer())

    suspend fun deleteBook(connection: SyncConnection, uid: String, at: Long): SyncPushResult<BookSyncDto> =
        pushJson<Unit, BookSyncDto>(connection, "DELETE", "/sync/books/$uid?at=$at", null, null, BookSyncDto.serializer())

    suspend fun pushRecipe(connection: SyncConnection, dto: RecipeSyncDto): SyncPushResult<RecipeSyncDto> =
        pushJson(connection, "PUT", "/sync/recipes/${dto.uid}", RecipeSyncDto.serializer(), dto, RecipeSyncDto.serializer())

    suspend fun deleteRecipe(connection: SyncConnection, uid: String, at: Long): SyncPushResult<RecipeSyncDto> =
        pushJson<Unit, RecipeSyncDto>(connection, "DELETE", "/sync/recipes/$uid?at=$at", null, null, RecipeSyncDto.serializer())

    suspend fun uploadBookCover(connection: SyncConnection, bookUid: String, bytes: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val result = request(connection, "PUT", "/sync/books/$bookUid/cover", body = bytes, contentType = "image/jpeg", timeoutMillis = PHOTO_TIMEOUT_MILLIS)
                result.code == HttpURLConnection.HTTP_NO_CONTENT
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
        }

    suspend fun downloadBookCover(connection: SyncConnection, bookUid: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val result = requestBinary(connection, "GET", "/sync/books/$bookUid/cover", timeoutMillis = PHOTO_TIMEOUT_MILLIS)
            if (result?.code == HttpURLConnection.HTTP_OK) result.bytes else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    suspend fun uploadPhoto(
        connection: SyncConnection,
        recipeUid: String,
        photoUid: String,
        bytes: ByteArray,
        contentType: String,
        isCover: Boolean,
        position: Int,
        updatedAt: Long
    ): SyncPushResult<PhotoMetaDto> = withContext(Dispatchers.IO) {
        try {
            val path = "/sync/recipes/$recipeUid/photos/$photoUid?isCover=$isCover&position=$position&updatedAt=$updatedAt"
            val result = request(connection, "PUT", path, body = bytes, contentType = contentType, timeoutMillis = PHOTO_TIMEOUT_MILLIS)
            interpretPushResponse(result, PhotoMetaDto.serializer())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncPushResult.Error(e.message ?: e::class.simpleName ?: "Error desconocido")
        }
    }

    suspend fun downloadPhoto(connection: SyncConnection, recipeUid: String, photoUid: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val result = requestBinary(connection, "GET", "/sync/recipes/$recipeUid/photos/$photoUid", timeoutMillis = PHOTO_TIMEOUT_MILLIS)
            if (result?.code == HttpURLConnection.HTTP_OK) result.bytes else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    suspend fun deletePhoto(connection: SyncConnection, recipeUid: String, photoUid: String, at: Long): SyncPushResult<PhotoMetaDto> =
        pushJson<Unit, PhotoMetaDto>(connection, "DELETE", "/sync/recipes/$recipeUid/photos/$photoUid?at=$at", null, null, PhotoMetaDto.serializer())

    private suspend fun <Req, Res> pushJson(
        connection: SyncConnection,
        method: String,
        path: String,
        requestSerializer: SerializationStrategy<Req>?,
        requestBody: Req?,
        conflictSerializer: DeserializationStrategy<Res>
    ): SyncPushResult<Res> = withContext(Dispatchers.IO) {
        try {
            val bodyBytes = if (requestSerializer != null && requestBody != null) {
                json.encodeToString(requestSerializer, requestBody).toByteArray()
            } else {
                null
            }
            val result = request(connection, method, path, body = bodyBytes, contentType = "application/json")
            interpretPushResponse(result, conflictSerializer)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncPushResult.Error(e.message ?: e::class.simpleName ?: "Error desconocido")
        }
    }

    private fun <T> interpretPushResponse(result: TextResponse, conflictSerializer: DeserializationStrategy<T>): SyncPushResult<T> =
        when (result.code) {
            HttpURLConnection.HTTP_OK -> SyncPushResult.Applied(json.decodeFromString(RevisionDto.serializer(), result.body).revision)
            HttpURLConnection.HTTP_CONFLICT -> SyncPushResult.Conflict(json.decodeFromString(conflictSerializer, result.body))
            else -> SyncPushResult.Error(errorMessageFor(result))
        }

    private data class TextResponse(val code: Int, val body: String)
    private data class BinaryResponse(val code: Int, val bytes: ByteArray)

    private fun request(
        connection: SyncConnection,
        method: String,
        path: String,
        body: ByteArray?,
        contentType: String = "application/json",
        timeoutMillis: Int = TIMEOUT_MILLIS
    ): TextResponse {
        val httpConnection = URL("${connection.serverUrl}$path").openConnection() as HttpURLConnection
        try {
            httpConnection.requestMethod = method
            httpConnection.setRequestProperty("Authorization", "Bearer ${connection.accessToken}")
            httpConnection.connectTimeout = timeoutMillis
            httpConnection.readTimeout = timeoutMillis
            if (body != null) {
                httpConnection.doOutput = true
                httpConnection.setRequestProperty("Content-Type", contentType)
                httpConnection.outputStream.use { it.write(body) }
            }
            val code = httpConnection.responseCode
            val stream = if (code in 200..299) httpConnection.inputStream else httpConnection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return TextResponse(code, text)
        } finally {
            httpConnection.disconnect()
        }
    }

    private fun requestBinary(connection: SyncConnection, method: String, path: String, timeoutMillis: Int): BinaryResponse? {
        val httpConnection = URL("${connection.serverUrl}$path").openConnection() as HttpURLConnection
        try {
            httpConnection.requestMethod = method
            httpConnection.setRequestProperty("Authorization", "Bearer ${connection.accessToken}")
            httpConnection.connectTimeout = timeoutMillis
            httpConnection.readTimeout = timeoutMillis
            val code = httpConnection.responseCode
            val bytes = if (code == HttpURLConnection.HTTP_OK) httpConnection.inputStream.use { it.readBytes() } else ByteArray(0)
            return BinaryResponse(code, bytes)
        } finally {
            httpConnection.disconnect()
        }
    }

    private fun errorMessageFor(result: TextResponse): String =
        runCatching { json.decodeFromString(SyncErrorDto.serializer(), result.body).message }
            .getOrNull()
            ?: "El servidor respondió con el código ${result.code}"
}
