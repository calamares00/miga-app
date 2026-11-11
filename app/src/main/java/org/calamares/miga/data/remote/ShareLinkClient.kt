package org.calamares.miga.data.remote

import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.calamares.miga.data.export.BackupCrypto
import org.calamares.miga.data.io.LimitedInputStream
import org.calamares.miga.data.io.ReadLimits
import org.calamares.miga.data.io.readTextAtMost
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import org.calamares.miga.data.support.AppDispatchers

/** Encrypted book links (calamares00/miga-stats, shares.js). */
const val SHARE_LINK_HOST = "miga-share.calamares.org"
private const val SHARE_BASE = "https://$SHARE_LINK_HOST"

/** A link to an encrypted book: [id] on the server, [key] only in the link's fragment. */
data class ShareLink(val id: String, val key: String) {
    val url: String get() = "$SHARE_BASE/s/$id#$key"

    companion object {
        private val ID = Regex("[A-Za-z0-9]{12}")
        private val KEY = Regex("[A-Za-z0-9_-]{20,100}")

        /** From "https://miga-share.calamares.org/s/<id>#<key>" or "miga://share/<id>#<key>"; null otherwise. */
        fun parse(uri: Uri?): ShareLink? {
            uri ?: return null
            val id = when {
                uri.scheme == "https" && uri.host == SHARE_LINK_HOST -> uri.pathSegments.takeIf { it.size == 2 && it[0] == "s" }?.get(1)
                uri.scheme == "miga" && uri.host == "share" -> uri.pathSegments.singleOrNull()
                else -> null
            } ?: return null
            val key = uri.fragment ?: return null
            return if (ID.matches(id) && KEY.matches(key)) ShareLink(id, key) else null
        }

        /** A random 256-bit key, URL-safe so it fits in the link. */
        fun newKey(): String {
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}

@Serializable
private data class ShareCreatedDto(val id: String, val deleteToken: String, val expiresAt: Long)

sealed interface ShareUploadResult {
    data class Success(val link: ShareLink, val expiresAt: Long) : ShareUploadResult
    data class Error(val code: Int?) : ShareUploadResult
}

object ShareLinkClient {

    /** The key is random and long, so a slow key derivation adds nothing: keep it quick on the phone. */
    const val KEY_ITERATIONS = 1000
    private const val TIMEOUT_MILLIS = 30_000
    private val json = Json { ignoreUnknownKeys = true }

    /** Encrypts [file] with a new key into [encrypted] (BackupCrypto, readable by the import). */
    fun encrypt(file: File, encrypted: File): String {
        val key = ShareLink.newKey()
        encrypted.outputStream().use { out ->
            BackupCrypto.encrypt(out, key.toCharArray(), KEY_ITERATIONS).use { crypto -> file.inputStream().use { it.copyTo(crypto) } }
        }
        return key
    }

    /** Uploads the already encrypted [encrypted] and returns the link with [key]. */
    suspend fun upload(encrypted: File, key: String, onProgress: (sent: Long, total: Long) -> Unit): ShareUploadResult =
        withContext(AppDispatchers.io) {
            try {
                val connection = URL("$SHARE_BASE/v1/shares").openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.connectTimeout = TIMEOUT_MILLIS
                    connection.readTimeout = TIMEOUT_MILLIS
                    connection.setRequestProperty("Content-Type", "application/octet-stream")
                    val total = encrypted.length()
                    connection.setFixedLengthStreamingMode(total)
                    connection.outputStream.use { out ->
                        encrypted.inputStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var sent = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                sent += read
                                onProgress(sent, total)
                            }
                        }
                    }
                    if (connection.responseCode != HttpURLConnection.HTTP_CREATED) return@withContext ShareUploadResult.Error(connection.responseCode)
                    val body = connection.inputStream.use { it.readTextAtMost(ReadLimits.API_JSON_BYTES) }
                    val dto = json.decodeFromString(ShareCreatedDto.serializer(), body)
                    ShareUploadResult.Success(ShareLink(dto.id, key), dto.expiresAt)
                } finally {
                    connection.disconnect()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                ShareUploadResult.Error(null)
            }
        }

    /** Downloads the encrypted book of [link] to [destination]; false when it is gone or unreachable. */
    suspend fun download(link: ShareLink, destination: File): Boolean = withContext(AppDispatchers.io) {
        try {
            val connection = URL("$SHARE_BASE/v1/shares/${link.id}").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext false
                connection.inputStream.use { input ->
                    destination.outputStream().use { LimitedInputStream(input, ReadLimits.SHARED_BOOK_BYTES).copyTo(it) }
                }
                true
            } finally {
                connection.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            destination.delete()
            false
        }
    }
}
