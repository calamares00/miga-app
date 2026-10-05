package org.calamares.miga.data.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncInviteCodecTest {

    @Test
    fun `encode then decode gives the same invitation`() {
        val invite = SyncInvite("http://192.168.1.10:8080", "casa", "abc_DEF-123", "Casa")
        assertEquals(invite, SyncInviteCodec.decode(SyncInviteCodec.encode(invite)))
    }

    @Test
    fun `payload carries the Miga join prefix`() {
        assertTrue(SyncInviteCodec.encode(SyncInvite("https://miga.example.com", "amigos", "tok", "")).startsWith(SyncInviteCodec.PREFIX))
    }

    @Test
    fun `foreign or corrupt text is rejected`() {
        assertNull(SyncInviteCodec.decode("https://example.com"))
        assertNull(SyncInviteCodec.decode(SyncInviteCodec.PREFIX + "no-es-base64-válido!!"))
        assertNull(SyncInviteCodec.decode(SyncInviteCodec.PREFIX + "e30")) // "{}" en base64 url-safe: faltan campos
    }

    @Test
    fun `invitation with a non-http url or empty token is rejected`() {
        assertNull(SyncInviteCodec.decode(SyncInviteCodec.encode(SyncInvite("ftp://host", "casa", "tok", ""))))
        assertNull(SyncInviteCodec.decode(SyncInviteCodec.encode(SyncInvite("http://host", "casa", " ", ""))))
    }
}
