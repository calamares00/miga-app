package org.calamares.miga.data.remote

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.calamares.miga.data.io.ReadLimits
import org.calamares.miga.data.io.readTextAtMost
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import org.calamares.miga.data.support.AppDispatchers

/** A published version of Miga: its version name ("1.0.6") and its GitHub page. */
data class AppRelease(val versionName: String, val pageUrl: String)

@Serializable
private data class GithubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false
)

/**
 * Finds out whether a newer Miga exists, without Google libraries (the same code is built for
 * F-Droid). CI publishes a GitHub Release ("v1.0.6") together with every Play upload, so the latest
 * stable Release is the latest version; betas are prereleases and are skipped by the API.
 */
object AppUpdateClient {

    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/calamares00/miga-app/releases/latest"
    private const val TIMEOUT_MILLIS = 8000
    private val json = Json { ignoreUnknownKeys = true }

    /** The latest stable release, or null when it cannot be read (offline, rate limit...). */
    suspend fun fetchLatest(): AppRelease? = withContext(AppDispatchers.io) {
        try {
            val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                val body = connection.inputStream.use { it.readTextAtMost(ReadLimits.API_JSON_BYTES) }
                val dto = json.decodeFromString(GithubReleaseDto.serializer(), body)
                if (dto.draft || dto.prerelease) return@withContext null
                AppRelease(
                    versionName = dto.tagName.removePrefix("v"),
                    pageUrl = dto.htmlUrl
                )
            } finally {
                connection.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            null
        }
    }

    /** Whether version name [candidate] ("1.0.10") is newer than [current] ("1.0.9"). Pure logic. */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(version: String) = version.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val difference = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (difference != 0) return difference > 0
        }
        return false
    }
}
