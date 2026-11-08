package org.calamares.miga.data.remote

import java.io.File
import kotlin.random.Random
import org.calamares.miga.data.export.BackupCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ShareLinkCryptoTest {

    @Test
    fun `keys are long, random and fit in a link fragment`() {
        val a = ShareLink.newKey()
        val b = ShareLink.newKey()
        assertTrue(a.matches(Regex("[A-Za-z0-9_-]{43}")))
        assertNotEquals(a, b)
    }

    @Test
    fun `a book encrypted for a link decrypts with its key only`() {
        val plain = File.createTempFile("book", ".miga").apply { writeBytes(Random.nextBytes(300_000)) }
        val encrypted = File.createTempFile("book", ".enc")
        try {
            val key = ShareLinkClient.encrypt(plain, encrypted)
            assertTrue(BackupCrypto.isEncrypted(encrypted.readBytes().copyOf(BackupCrypto.signatureSize)))
            val back = BackupCrypto.decrypt(encrypted.inputStream(), key.toCharArray()).use { it.readBytes() }
            assertArrayEquals(plain.readBytes(), back)
            try {
                BackupCrypto.decrypt(encrypted.inputStream(), ShareLink.newKey().toCharArray()).use { it.readBytes() }
                fail("another key must not decrypt the book")
            } catch (e: Exception) {
                // expected: wrong key
            }
        } finally {
            plain.delete()
            encrypted.delete()
        }
    }
}
