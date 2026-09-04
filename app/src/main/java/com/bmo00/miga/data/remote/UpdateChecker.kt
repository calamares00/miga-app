package com.bmo00.miga.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Serializable
private data class GithubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val draft: Boolean = false
)

data class UpdateInfo(
    val latestVersion: String,
    val releaseUrl: String
)

// Se pide la lista de releases (no /releases/latest) porque las releases automáticas de cada
// commit se publican como pre-release hasta que exista una build "release" firmada de verdad;
// /releases/latest de GitHub ignora las pre-release, así que dejaría de encontrar nada.
private const val RELEASES_URL = "https://api.github.com/repos/bmo00/app-miga/releases?per_page=1"
private const val TIMEOUT_MILLIS = 8000

/** Comprueba en la GitHub Release más reciente del repositorio si hay una versión más nueva que la actual. */
object UpdateChecker {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(RELEASES_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val releases = json.decodeFromString(ListSerializer(GithubReleaseDto.serializer()), body)
                val release = releases.firstOrNull { !it.draft } ?: return@runCatching null
                val latestVersion = release.tagName.removePrefix("v")
                if (isNewerVersion(current = currentVersion, latest = latestVersion)) {
                    UpdateInfo(latestVersion, release.htmlUrl)
                } else {
                    null
                }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(currentParts.size, latestParts.size)) {
            val currentPart = currentParts.getOrElse(i) { 0 }
            val latestPart = latestParts.getOrElse(i) { 0 }
            if (latestPart != currentPart) return latestPart > currentPart
        }
        return false
    }
}
