package org.calamares.miga.data.export

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class BackupCryptoTest {

    // Few iterations and tiny segments keep the tests fast and cover the segment boundaries.
    private val password = "correcto caballo pila grapa".toCharArray()

    private fun encrypt(data: ByteArray, segmentSize: Int = 16, pass: CharArray = password): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypto.encrypt(out, pass, iterations = 1000, segmentSize = segmentSize).use { it.write(data) }
        return out.toByteArray()
    }

    private fun decrypt(data: ByteArray, pass: CharArray = password): ByteArray =
        BackupCrypto.decrypt(ByteArrayInputStream(data), pass).use { it.readBytes() }

    @Test
    fun `round trip across segment boundaries`() {
        listOf(0, 1, 15, 16, 17, 32, 33, 1000).forEach { size ->
            val data = Random(size).nextBytes(size)
            assertArrayEquals("size $size", data, decrypt(encrypt(data)))
        }
    }

    @Test
    fun `the result is recognised and does not reveal the content`() {
        val data = "manifest.json receta de tortilla".toByteArray()
        val encrypted = encrypt(data)
        assertTrue(BackupCrypto.isEncrypted(encrypted))
        assertFalse(BackupCrypto.isEncrypted("PK\u0003\u0004 zip".toByteArray()))
        assertFalse(String(encrypted, Charsets.ISO_8859_1).contains("tortilla"))
    }

    @Test
    fun `a wrong password is reported as such`() {
        val encrypted = encrypt("hola".toByteArray())
        assertThrows(WrongBackupPasswordException::class.java) { decrypt(encrypted, "otra".toCharArray()) }
    }

    @Test
    fun `a truncated or altered backup is detected`() {
        val data = Random(7).nextBytes(100)
        val encrypted = encrypt(data)
        // 100 bytes in 16-byte segments: six full ones and a last one with 4 bytes (plus its tag).
        val truncated = encrypted.copyOf(encrypted.size - (4 + 16))
        assertThrows(CorruptedBackupException::class.java) { decrypt(truncated) }
        val altered = encrypted.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertThrows(CorruptedBackupException::class.java) { decrypt(altered) }
    }

    @Test
    fun `the same content encrypts differently every time`() {
        val data = "igual".toByteArray()
        assertFalse(encrypt(data).contentEquals(encrypt(data)))
    }
}
