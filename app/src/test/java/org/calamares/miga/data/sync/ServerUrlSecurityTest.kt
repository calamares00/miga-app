package org.calamares.miga.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlSecurityTest {

    @Test
    fun `local and private http servers are fine`() {
        listOf(
            "http://192.168.1.10:8080",
            "http://10.0.0.5",
            "http://172.20.1.1:9000",
            "http://localhost:8080",
            "http://nas:8080",
            "http://miga.local",
            "http://100.101.2.3:8080",
            "http://[fd12::1]:8080"
        ).forEach { assertFalse(it, ServerUrlSecurity.isInsecurePublic(it)) }
    }

    @Test
    fun `https is always fine`() {
        assertFalse(ServerUrlSecurity.isInsecurePublic("https://miga.example.com"))
        assertFalse(ServerUrlSecurity.isInsecurePublic("https://85.10.20.30"))
    }

    @Test
    fun `public http servers are flagged`() {
        assertTrue(ServerUrlSecurity.isInsecurePublic("http://miga.example.com"))
        assertTrue(ServerUrlSecurity.isInsecurePublic("http://85.10.20.30:8080"))
        assertTrue(ServerUrlSecurity.isInsecurePublic("HTTP://172.32.0.1"))
    }
}
