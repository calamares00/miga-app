package org.calamares.miga.data.share

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

/** Lo necesario para unirse a un namespace de un servidor de sincronización desde otra app. */
@Serializable
data class SyncInvite(
    @SerialName("u") val serverUrl: String,
    @SerialName("n") val namespaceId: String,
    @SerialName("t") val token: String,
    @SerialName("l") val label: String = ""
)

/**
 * Serializa una invitación a un namespace (URL del servidor + namespace + token de acceso nuevo)
 * como texto para un QR: prefijo propio + JSON compacto en base64 url-safe. Quien tenga este QR
 * puede leer y escribir en el namespace, así que se muestra solo al pulsar "Invitar" y el token
 * es uno nuevo (revocable por separado en el servidor). Lógica pura, sin Android.
 */
object SyncInviteCodec {

    const val PREFIX = "MIGA-JOIN1:"

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(invite: SyncInvite): String =
        PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(
            json.encodeToString(SyncInvite.serializer(), invite).toByteArray(Charsets.UTF_8)
        )

    /** null si [text] no es una invitación de Miga válida (otro QR cualquiera, datos corruptos, campos vacíos...). */
    fun decode(text: String): SyncInvite? {
        if (!text.startsWith(PREFIX)) return null
        return try {
            val bytes = Base64.getUrlDecoder().decode(text.removePrefix(PREFIX).trim())
            val invite = json.decodeFromString(SyncInvite.serializer(), String(bytes, Charsets.UTF_8))
            val validUrl = invite.serverUrl.startsWith("http://") || invite.serverUrl.startsWith("https://")
            if (!validUrl || invite.namespaceId.isBlank() || invite.token.isBlank()) null else invite
        } catch (e: IllegalArgumentException) {
            null // base64 inválido o JSON malformado/incompleto (SerializationException es una IllegalArgumentException)
        }
    }
}
