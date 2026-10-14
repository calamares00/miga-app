package org.calamares.miga.data.share

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

/** Everything needed to join a sync server namespace from another device. */
@Serializable
data class SyncInvite(
    @SerialName("u") val serverUrl: String,
    @SerialName("n") val namespaceId: String,
    @SerialName("t") val token: String,
    @SerialName("l") val label: String = ""
)

/**
 * Serialises a namespace invite (server URL, namespace and a new access token) as QR text: a custom
 * prefix plus compact JSON in URL-safe base64. Whoever holds the QR can read and write the
 * namespace, so it is only shown after tapping "Invite" and always carries a new token that can be
 * revoked separately on the server. Pure logic.
 */
object SyncInviteCodec {

    const val PREFIX = "MIGA-JOIN1:"

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(invite: SyncInvite): String =
        PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(
            json.encodeToString(SyncInvite.serializer(), invite).toByteArray(Charsets.UTF_8)
        )

    /**
     * Returns null when [text] is not a valid Miga invite (some other QR code, corrupt data, empty
     * fields...).
     */
    fun decode(text: String): SyncInvite? {
        if (!text.startsWith(PREFIX)) return null
        return try {
            val bytes = Base64.getUrlDecoder().decode(text.removePrefix(PREFIX).trim())
            val invite = json.decodeFromString(SyncInvite.serializer(), String(bytes, Charsets.UTF_8))
            val validUrl = invite.serverUrl.startsWith("http://") || invite.serverUrl.startsWith("https://")
            if (!validUrl || invite.namespaceId.isBlank() || invite.token.isBlank()) null else invite
        } catch (e: IllegalArgumentException) {
            null // Invalid base64 or malformed JSON (SerializationException is an IllegalArgumentException).
        }
    }
}
