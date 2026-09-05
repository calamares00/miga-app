package com.bmo00.miga.data.remote

import android.os.Build
import com.bmo00.miga.BuildConfig
import com.bmo00.miga.data.model.UpdateChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Serializable
private data class GithubAssetDto(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String
)

@Serializable
private data class GithubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val name: String? = null,
    val assets: List<GithubAssetDto> = emptyList()
)

data class UpdateInfo(
    val latestVersion: String,
    val releaseUrl: String,
    /** Descarga directa del APK para esta arquitectura/tipo de build; null si no hay uno que encaje. */
    val apkDownloadUrl: String? = null
)

sealed interface UpdateCheckResult {
    data class UpdateFound(val info: UpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Error(val reason: String) : UpdateCheckResult
}

// Canal estable: la Release más reciente que NO es pre-release (softprops/action-gh-release solo
// marca así los builds release firmados, tras fusionar a main). Canal beta: una única Release de
// tag fijo "beta-latest" que el workflow sobrescribe en cada push a una rama de desarrollo.
private const val STABLE_RELEASE_URL = "https://api.github.com/repos/bmo00/app-miga/releases/latest"
private const val BETA_RELEASE_URL = "https://api.github.com/repos/bmo00/app-miga/releases/tags/beta-latest"
private const val TIMEOUT_MILLIS = 8000

// El tag de la beta es fijo ("beta-latest", sin versión), así que la versión real se lee del
// campo "name" de la Release (que el workflow rellena como "Miga vX.Y.Z (beta · rama · sha)")
// en vez del tag_name.
private val VERSION_IN_NAME_REGEX = Regex("""v(\d+(?:\.\d+)+)""")

/** Comprueba en GitHub Releases, en el canal indicado, si hay una versión más nueva que la actual. */
object UpdateChecker {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdate(currentVersion: String, channel: UpdateChannel): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            val url = if (channel == UpdateChannel.BETA) BETA_RELEASE_URL else STABLE_RELEASE_URL
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                try {
                    val responseCode = connection.responseCode
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        // GitHub limita las peticiones sin autenticación a 60/hora por IP; en una
                        // red compartida (móvil, wifi pública) es fácil agotarlo y recibir 403.
                        return@withContext UpdateCheckResult.Error("GitHub respondió con el código $responseCode")
                    }
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val release = json.decodeFromString(GithubReleaseDto.serializer(), body)
                    val latestVersion = VERSION_IN_NAME_REGEX.find(release.name.orEmpty())?.groupValues?.get(1)
                        ?: release.tagName.removePrefix("v")
                    if (isNewerVersion(current = currentVersion, latest = latestVersion)) {
                        val apkDownloadUrl = findApkDownloadUrl(release.assets, latestVersion)
                        UpdateCheckResult.UpdateFound(UpdateInfo(latestVersion, release.htmlUrl, apkDownloadUrl))
                    } else {
                        UpdateCheckResult.UpToDate
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateCheckResult.Error(e.message ?: e::class.simpleName ?: "Error desconocido")
            }
        }

    // El workflow sube un asset por ABI con el patrón "<slug>-v<version>-<abi>-<buildType>.apk"
    // (y "-universal-<buildType>.apk"); los assets sin firmar añaden un sufijo "-unsigned" que
    // este endsWith ya excluye. Ver .github/workflows/android-build.yml (función rename_apks).
    //
    // El tag "beta-latest" es una única Release que el workflow sobrescribe en cada push, pero
    // softprops/action-gh-release NO borra los assets de versiones anteriores cuyo nombre difiere
    // del actual (el nombre incluye la versión, así que cambia en cada push) — se van acumulando
    // APKs de versiones viejas en esa misma Release. Por eso hace falta filtrar también por
    // "-v$version-" y no solo por ABI/tipo de build, o se puede acabar cogiendo con firstOrNull()
    // el asset más antiguo que matchee el sufijo en vez del de la versión que se acaba de detectar.
    private fun findApkDownloadUrl(assets: List<GithubAssetDto>, version: String): String? {
        val buildType = if (BuildConfig.DEBUG) "debug" else "release"
        val abi = Build.SUPPORTED_ABIS.firstOrNull()
        val versionMarker = "-v$version-"
        val exactMatch = abi?.let { a ->
            assets.firstOrNull { it.name.contains(versionMarker) && it.name.endsWith("-$a-$buildType.apk") }
        }
        val universalMatch = assets.firstOrNull {
            it.name.contains(versionMarker) && it.name.endsWith("-universal-$buildType.apk")
        }
        return (exactMatch ?: universalMatch)?.browserDownloadUrl
    }

    /** internal (no private) para poder probarla con un test unitario sin pasar por la red. */
    internal fun isNewerVersion(current: String, latest: String): Boolean {
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
