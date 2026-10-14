package org.calamares.miga.data.export

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** The password does not open the backup. */
class WrongBackupPasswordException : IOException("Wrong backup password")

/** The encrypted backup is damaged or was cut short. */
class CorruptedBackupException(message: String) : IOException(message)

/**
 * Password protection for backups, with the platform's standard cryptography only (no native code
 * or third-party crypto):
 *
 * - The key is derived from the password with PBKDF2-HMAC-SHA256 and a random 16-byte salt,
 *   [DEFAULT_ITERATIONS] iterations (OWASP's recommendation). The password itself is never stored.
 * - The content is encrypted with AES-256-GCM in independent segments, so a large backup with
 *   photos is never held in memory and every segment is authenticated as it is read. This is the
 *   segmented AEAD scheme of Tink's streaming AEAD: each segment's nonce is a random prefix, the
 *   segment number and a "last segment" flag, so segments cannot be reordered, removed or the file
 *   truncated without the decryption failing. The whole header is authenticated as well.
 *
 * File layout: header ("MIGAENC1", version, KDF id, iterations, salt, nonce prefix, segment size)
 * followed by the encrypted segments, each the full segment size plus the 16-byte tag, except the
 * last one, which may be shorter.
 */
object BackupCrypto {

    private val MAGIC = "MIGAENC1".toByteArray(Charsets.US_ASCII)
    private const val FORMAT_VERSION: Byte = 1
    private const val KDF_PBKDF2_SHA256: Byte = 1
    const val DEFAULT_ITERATIONS = 600_000
    private const val SALT_SIZE = 16
    private const val NONCE_PREFIX_SIZE = 7
    private const val TAG_SIZE = 16
    private const val KEY_BITS = 256
    const val DEFAULT_SEGMENT_SIZE = 64 * 1024
    private const val HEADER_SIZE = 8 + 1 + 1 + 4 + SALT_SIZE + NONCE_PREFIX_SIZE + 4

    /** Recommended minimum password length shown to the user. */
    const val MIN_PASSWORD_LENGTH = 8

    /** Whether [start], the first bytes of a file, are those of an encrypted backup. */
    fun isEncrypted(start: ByteArray): Boolean = start.size >= MAGIC.size && MAGIC.indices.all { start[it] == MAGIC[it] }

    /** Bytes needed to recognise an encrypted backup with [isEncrypted]. */
    val signatureSize: Int get() = MAGIC.size

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun nonce(prefix: ByteArray, segment: Int, last: Boolean): ByteArray =
        ByteBuffer.allocate(12).put(prefix).putInt(segment).put((if (last) 1 else 0).toByte()).array()

    /**
     * Wraps [output] so everything written to it is encrypted with [password]. Closing the returned
     * stream writes the last segment and closes [output].
     */
    fun encrypt(
        output: OutputStream,
        password: CharArray,
        iterations: Int = DEFAULT_ITERATIONS,
        segmentSize: Int = DEFAULT_SEGMENT_SIZE
    ): OutputStream {
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
        val prefix = ByteArray(NONCE_PREFIX_SIZE).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_SIZE)
            .put(MAGIC).put(FORMAT_VERSION).put(KDF_PBKDF2_SHA256).putInt(iterations).put(salt).put(prefix).putInt(segmentSize)
            .array()
        output.write(header)
        return EncryptingStream(output, deriveKey(password, salt, iterations), prefix, header, segmentSize)
    }

    /**
     * Wraps [input], an encrypted backup, so reading it gives the original content. Throws
     * [WrongBackupPasswordException] right away when [password] does not open it, and
     * [CorruptedBackupException] while reading if the file was altered or cut short.
     */
    fun decrypt(input: InputStream, password: CharArray): InputStream {
        val data = DataInputStream(input)
        val header = ByteArray(HEADER_SIZE)
        try {
            data.readFully(header)
        } catch (e: EOFException) {
            throw CorruptedBackupException("Header too short")
        }
        val buffer = ByteBuffer.wrap(header)
        val magic = ByteArray(MAGIC.size).also { buffer.get(it) }
        if (!magic.contentEquals(MAGIC)) throw CorruptedBackupException("Not an encrypted backup")
        val version = buffer.get()
        val kdf = buffer.get()
        if (version != FORMAT_VERSION || kdf != KDF_PBKDF2_SHA256) throw CorruptedBackupException("Unsupported format $version/$kdf")
        val iterations = buffer.int
        val salt = ByteArray(SALT_SIZE).also { buffer.get(it) }
        val prefix = ByteArray(NONCE_PREFIX_SIZE).also { buffer.get(it) }
        val segmentSize = buffer.int
        if (iterations !in 1..10_000_000 || segmentSize !in 1..(16 * 1024 * 1024)) throw CorruptedBackupException("Invalid header")
        val stream = DecryptingStream(data, deriveKey(password, salt, iterations), prefix, header, segmentSize)
        stream.openFirstSegment()
        return stream
    }

    private class EncryptingStream(
        private val output: OutputStream,
        private val key: SecretKeySpec,
        private val prefix: ByteArray,
        private val header: ByteArray,
        segmentSize: Int
    ) : OutputStream() {
        private val buffer = ByteArray(segmentSize)
        private var filled = 0
        private var segment = 0
        private var closed = false

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            check(!closed) { "Stream closed" }
            var offset = off
            var remaining = len
            while (remaining > 0) {
                // A full segment is only sealed once more data arrives, so the last one is always
                // the segment written by close().
                if (filled == buffer.size) flushSegment(last = false)
                val count = minOf(remaining, buffer.size - filled)
                System.arraycopy(b, offset, buffer, filled, count)
                filled += count
                offset += count
                remaining -= count
            }
        }

        private fun flushSegment(last: Boolean) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_SIZE * 8, nonce(prefix, segment, last)))
            cipher.updateAAD(header)
            output.write(cipher.doFinal(buffer, 0, filled))
            segment++
            filled = 0
        }

        override fun close() {
            if (closed) return
            closed = true
            try {
                flushSegment(last = true)
            } finally {
                buffer.fill(0)
                output.close()
            }
        }
    }

    private class DecryptingStream(
        private val input: DataInputStream,
        private val key: SecretKeySpec,
        private val prefix: ByteArray,
        private val header: ByteArray,
        segmentSize: Int
    ) : InputStream() {
        private val encryptedSize = segmentSize + TAG_SIZE
        private var plain = ByteArray(0)
        private var position = 0
        private var segment = 0
        private var finished = false
        /** The first byte of the following segment, read ahead to know whether one exists. */
        private var lookahead = -1

        fun openFirstSegment() {
            try {
                nextSegment()
            } catch (e: CorruptedBackupException) {
                // The first segment only fails to authenticate with the wrong key, as the header is
                // valid by now; a truncated file is reported as damaged.
                if (e.cause is AEADBadTagException) throw WrongBackupPasswordException() else throw e
            }
        }

        private fun readChunk(): ByteArray {
            val chunk = ByteArrayOutputStream(encryptedSize)
            if (lookahead >= 0) chunk.write(lookahead)
            val rest = ByteArray(encryptedSize - chunk.size())
            var read = 0
            while (read < rest.size) {
                val count = input.read(rest, read, rest.size - read)
                if (count < 0) break
                read += count
            }
            chunk.write(rest, 0, read)
            lookahead = input.read()
            return chunk.toByteArray()
        }

        private fun nextSegment() {
            val chunk = readChunk()
            val last = lookahead < 0
            if (!last && chunk.size != encryptedSize) throw CorruptedBackupException("Short segment")
            if (chunk.size < TAG_SIZE) throw CorruptedBackupException("Truncated backup")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE * 8, nonce(prefix, segment, last)))
            cipher.updateAAD(header)
            plain = try {
                cipher.doFinal(chunk)
            } catch (e: AEADBadTagException) {
                throw CorruptedBackupException("Segment $segment does not authenticate").apply { initCause(e) }
            }
            position = 0
            segment++
            finished = last
        }

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (position == plain.size) {
                if (finished) return -1
                nextSegment()
            }
            val count = minOf(len, plain.size - position)
            System.arraycopy(plain, position, b, off, count)
            position += count
            return count
        }

        override fun close() = input.close()
    }
}
