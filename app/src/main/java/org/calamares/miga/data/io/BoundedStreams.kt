package org.calamares.miga.data.io

import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import org.calamares.miga.L10n
import org.calamares.miga.R

/** Thrown when a stream holds more than the bytes allowed for it (network body, ZIP entry...). */
class TooLargeException(val limitBytes: Long) : IOException() {
    val limitMegabytes: Long get() = limitBytes / (1024 * 1024)

    // Shown as is by the callers that report e.message (sync, catalogue, AI), so it is localized.
    override val message: String
        get() = runCatching { L10n.str(R.string.data_larger_than_x_mb, limitMegabytes) }.getOrDefault("More than $limitMegabytes MB")
}

/**
 * Size limits for data the app does not control: ZIP files can expand ~1000 times, and a broken or
 * hostile server can send an endless body. Reading past a limit fails instead of exhausting memory
 * or storage.
 */
object ReadLimits {
    private const val MB = 1024L * 1024

    /** manifest.json inside a pack, backup or book file, or a plain JSON file. */
    const val MANIFEST_BYTES = 32 * MB

    /** One photo inside a ZIP. */
    const val ZIP_PHOTO_BYTES = 20 * MB

    /** All the photos extracted from one ZIP. */
    const val ZIP_TOTAL_BYTES = 2048 * MB

    /** A book file shared by someone else (".miga", photos included). */
    const val SHARED_BOOK_BYTES = 200 * MB

    /** catalog.json of a pack catalogue. */
    const val CATALOG_BYTES = 2 * MB

    /** JSON answers of the sync server (the first fetch of a large library is the biggest). */
    const val SYNC_JSON_BYTES = 32 * MB

    /** Photos and covers downloaded from the sync server. */
    const val SYNC_BINARY_BYTES = 20 * MB

    /** Answers of AI providers (the OpenRouter model list was 0.8 MB in 2026), Open Food Facts and GitHub. */
    const val API_JSON_BYTES = 16 * MB
}

/** Reads the whole stream, throwing [TooLargeException] when it is longer than [limit] bytes. */
fun InputStream.readBytesAtMost(limit: Long): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = read(buffer)
        if (read < 0) return out.toByteArray()
        if (out.size() + read > limit) throw TooLargeException(limit)
        out.write(buffer, 0, read)
    }
}

/** UTF-8 text of the whole stream, with the same limit as [readBytesAtMost]. */
fun InputStream.readTextAtMost(limit: Long): String = readBytesAtMost(limit).toString(Charsets.UTF_8)

/**
 * Passes reads through and throws [TooLargeException] once more than [limit] bytes have been read,
 * for streams copied straight to a file.
 */
class LimitedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
    private var count = 0L

    override fun read(): Int {
        val byte = super.read()
        if (byte >= 0) count(1)
        return byte
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val read = super.read(b, off, len)
        if (read > 0) count(read.toLong())
        return read
    }

    override fun skip(n: Long): Long = super.skip(n).also { count(it) }

    private fun count(bytes: Long) {
        count += bytes
        if (count > limit) throw TooLargeException(limit)
    }
}
