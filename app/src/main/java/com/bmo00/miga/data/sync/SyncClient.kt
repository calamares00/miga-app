package com.bmo00.miga.data.sync

import com.bmo00.miga.data.model.SyncConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

sealed interface SyncPingResult {
    data object Success : SyncPingResult
    data class Error(val reason: String) : SyncPingResult
}

private const val TIMEOUT_MILLIS = 8000

/**
 * Cliente HTTP del servidor de sincronización self-hosted (ver miga-server). Mismo estilo que
 * PacksCatalogClient/UpdateChecker: HttpURLConnection crudo + kotlinx.serialization, sin
 * librería de red nueva. Por ahora solo expone [ping] (probar una conexión antes de guardarla en
 * Ajustes); el resto de operaciones (subir/bajar libros, recetas y fotos) se añaden junto con el
 * motor de sincronización.
 */
object SyncClient {

    /** Comprueba que la URL/namespace/token de [connection] son válidos y alcanzables. */
    suspend fun ping(connection: SyncConnection): SyncPingResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("${connection.serverUrl}/sync/ping")
            val httpConnection = url.openConnection() as HttpURLConnection
            httpConnection.requestMethod = "GET"
            httpConnection.setRequestProperty("Authorization", "Bearer ${connection.accessToken}")
            httpConnection.connectTimeout = TIMEOUT_MILLIS
            httpConnection.readTimeout = TIMEOUT_MILLIS
            try {
                val responseCode = httpConnection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    SyncPingResult.Success
                } else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    SyncPingResult.Error("Token de acceso inválido o revocado")
                } else {
                    SyncPingResult.Error("El servidor respondió con el código $responseCode")
                }
            } finally {
                httpConnection.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncPingResult.Error(e.message ?: e::class.simpleName ?: "No se pudo conectar con el servidor")
        }
    }
}
