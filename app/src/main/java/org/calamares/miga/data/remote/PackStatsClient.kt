package org.calamares.miga.data.remote

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.calamares.miga.data.io.ReadLimits
import org.calamares.miga.data.io.readTextAtMost

/** Install counter of the official pack catalogue (calamares00/miga-stats). */
const val PACK_STATS_URL = "https://stats.miga.calamares.org"

/** Installs of a pack family (its Spanish and English versions together): in total and in the last 30 days. */
data class PackInstallStats(val installs: Int, val recent: Int)

@Serializable
private data class StatsDto(val families: Map<String, FamilyStatsDto> = emptyMap())

@Serializable
private data class FamilyStatsDto(val installs: Int = 0, val recent: Int = 0)

/** Counter key of a pack: its family, or its id for catalogues without families. */
val PackEntryDto.statsKey: String get() = family?.takeIf { it.isNotBlank() } ?: id

/**
 * Reads and feeds the anonymous install counter. Only the pack id is sent, never anything about the
 * user or the device; failures are ignored, the counter is just a nicety.
 */
object PackStatsClient {

    private const val TIMEOUT_MILLIS = 8000
    private val json = Json { ignoreUnknownKeys = true }

    /** Installs by family, or null when the counter cannot be read. */
    suspend fun fetchStats(): Map<String, PackInstallStats>? = withContext(Dispatchers.IO) {
        try {
            val connection = URL("$PACK_STATS_URL/v1/stats").openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                val body = connection.inputStream.use { it.readTextAtMost(ReadLimits.CATALOG_BYTES) }
                json.decodeFromString(StatsDto.serializer(), body).families
                    .mapValues { (_, f) -> PackInstallStats(f.installs, f.recent) }
            } finally {
                connection.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Adds one install of [packId]; true when the counter answered. */
    suspend fun reportInstall(packId: String): Boolean = withContext(Dispatchers.IO) {
        if (!packId.matches(Regex("[a-z0-9-]{1,64}"))) return@withContext false
        try {
            val connection = URL("$PACK_STATS_URL/v1/packs/$packId/install").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            try {
                connection.responseCode in 200..299
            } finally {
                connection.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }
}
