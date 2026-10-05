package org.calamares.miga.data.sync

import org.calamares.miga.L10n
import org.calamares.miga.R
import java.net.URI

/**
 * Avisos de seguridad para la URL del servidor de sincronización. La app permite HTTP sin cifrar
 * porque el servidor suele vivir en la red de casa; fuera de ella (una IP o dominio públicos) el
 * token y las recetas viajarían en claro, así que se avisa. Lógica pura, sin Android.
 */
object ServerUrlSecurity {

    val INSECURE_WARNING: String get() = L10n.str(R.string.conexion_sin_cifrar_http_fuera)

    /** true si [url] es http:// y su host no es de una red local/privada. */
    fun isInsecurePublic(url: String): Boolean {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true)) return false
        val host = runCatching { URI(trimmed).host }.getOrNull()?.lowercase()?.trim('[', ']') ?: return false
        return !isLocalHost(host)
    }

    private fun isLocalHost(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home.arpa") || host.endsWith(".internal")) return true
        if (!host.contains('.') && !host.contains(':')) return true // nombre de equipo sin dominio ("nas")
        if (host.contains(':')) return host == "::1" || host.startsWith("fe80") || host.startsWith("fc") || host.startsWith("fd")
        val parts = host.split('.').map { it.toIntOrNull() ?: return false }
        if (parts.size != 4) return false
        val (a, b) = parts[0] to parts[1]
        return a == 10 || a == 127 ||
            (a == 192 && b == 168) ||
            (a == 172 && b in 16..31) ||
            (a == 169 && b == 254) ||
            (a == 100 && b in 64..127) // CGNAT, usado por VPN como Tailscale
    }
}
