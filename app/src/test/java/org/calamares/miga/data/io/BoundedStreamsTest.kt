package org.calamares.miga.data.io

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BoundedStreamsTest {

    @Test
    fun `reads a stream within the limit`() {
        val bytes = ByteArray(10_000) { it.toByte() }
        assertArrayEquals(bytes, ByteArrayInputStream(bytes).readBytesAtMost(10_000))
        assertEquals("hola", ByteArrayInputStream("hola".toByteArray()).readTextAtMost(4))
    }

    @Test(expected = TooLargeException::class)
    fun `a stream over the limit fails`() {
        ByteArrayInputStream(ByteArray(10_001)).readBytesAtMost(10_000)
    }

    @Test(expected = TooLargeException::class)
    fun `limited stream stops a copy past the limit`() {
        LimitedInputStream(ByteArrayInputStream(ByteArray(5_000)), 4_096).copyTo(ByteArrayOutputStream())
    }

    @Test
    fun `limited stream lets an exact-size copy through`() {
        val out = ByteArrayOutputStream()
        LimitedInputStream(ByteArrayInputStream(ByteArray(4_096)), 4_096).copyTo(out)
        assertEquals(4_096, out.size())
    }

    @Test(expected = TooLargeException::class)
    fun `a highly compressed ZIP entry is stopped at the limit, not at its expanded size`() {
        val zipBytes = ByteArrayOutputStream().also { buffer ->
            ZipOutputStream(buffer).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                val chunk = ByteArray(1024 * 1024)
                repeat(64) { zip.write(chunk) } // 64 MB of zeros, a few dozen KB compressed
                zip.closeEntry()
            }
        }.toByteArray()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            zip.nextEntry
            zip.readBytesAtMost(1024 * 1024)
        }
    }
}
