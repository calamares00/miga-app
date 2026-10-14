package org.calamares.miga.data.sync

import org.calamares.miga.L10n
import org.calamares.miga.R
import java.net.URI

/**
 * Security warnings for the sync server URL. The app allows plain HTTP because the server usually
 * lives on the home network; outside it (a public IP or domain) the token and recipes would travel
 * unencrypted, so the user is warned. Pure logic.
 */
object ServerUrlSecurity {

    val INSECURE_WARNING: String get() = L10n.str(R.string.unencrypted_connection_http_outside_local)

    /** True when [url] uses http:// and its host is not on a local or private network. */
    fun isInsecurePublic(url: String): Boolean {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true)) return false
        val host = runCatching { URI(trimmed).host }.getOrNull()?.lowercase()?.trim('[', ']') ?: return false
        return !isLocalHost(host)
    }

    private fun isLocalHost(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home.arpa") || host.endsWith(".internal")) return true
        if (!host.contains('.') && !host.contains(':')) return true // Bare host name without a domain ("nas")
        if (host.contains(':')) return host == "::1" || host.startsWith("fe80") || host.startsWith("fc") || host.startsWith("fd")
        val parts = host.split('.').map { it.toIntOrNull() ?: return false }
        if (parts.size != 4) return false
        val (a, b) = parts[0] to parts[1]
        return a == 10 || a == 127 ||
            (a == 192 && b == 168) ||
            (a == 172 && b in 16..31) ||
            (a == 169 && b == 254) ||
            (a == 100 && b in 64..127) // CGNAT range, used by VPNs such as Tailscale
    }
}
