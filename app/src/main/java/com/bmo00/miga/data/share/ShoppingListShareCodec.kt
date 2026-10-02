package com.bmo00.miga.data.share

import com.bmo00.miga.data.model.ParsedShoppingEntry
import com.bmo00.miga.data.model.formatQuantity
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Serializa una lista de la compra como un texto compacto (líneas "cantidad<TAB>unidad<TAB>nombre",
 * comprimidas con deflate y en base64 url-safe) para llevarla dentro de un QR, sin necesidad de
 * servidor ni cuenta. Lógica pura, sin Android.
 */
object ShoppingListShareCodec {

    const val PREFIX = "MIGA-LIST1:"
    private const val MAX_ENTRIES = 500
    private const val MAX_DECOMPRESSED_BYTES = 64 * 1024

    /** Un artículo por línea ("cantidad<TAB>unidad<TAB>nombre"); también sirve para guardar plantillas. */
    fun toLines(entries: List<ParsedShoppingEntry>): String = entries
        .filter { it.name.isNotBlank() }
        .take(MAX_ENTRIES)
        .joinToString("\n") { entry ->
            val quantity = entry.quantity?.let { formatQuantity(it) }.orEmpty()
            "$quantity\t${clean(entry.unit.orEmpty())}\t${clean(entry.name)}"
        }

    fun fromLines(text: String): List<ParsedShoppingEntry> =
        text.split("\n").take(MAX_ENTRIES).mapNotNull { line ->
            val parts = line.split("\t")
            if (parts.size != 3 || parts[2].isBlank()) return@mapNotNull null
            ParsedShoppingEntry(
                name = parts[2].trim(),
                quantity = parts[0].toDoubleOrNull(),
                unit = parts[1].trim().ifEmpty { null }
            )
        }

    fun encode(entries: List<ParsedShoppingEntry>): String {
        val text = toLines(entries)
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(deflate(text.toByteArray(Charsets.UTF_8)))
    }

    /** null si [text] no es una lista de Miga válida (otro QR cualquiera, datos corruptos...). */
    fun decode(text: String): List<ParsedShoppingEntry>? {
        if (!text.startsWith(PREFIX)) return null
        val compressed = try {
            Base64.getUrlDecoder().decode(text.removePrefix(PREFIX).trim())
        } catch (e: IllegalArgumentException) {
            return null
        }
        val bytes = inflate(compressed) ?: return null
        return fromLines(String(bytes, Charsets.UTF_8)).ifEmpty { null }
    }

    private fun clean(value: String) = value.replace('\t', ' ').replace('\n', ' ').trim()

    private fun deflate(bytes: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(bytes)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer))
        }
        deflater.end()
        return out.toByteArray()
    }

    private fun inflate(bytes: ByteArray): ByteArray? {
        val inflater = Inflater()
        inflater.setInput(bytes)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        try {
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) return null
                out.write(buffer, 0, count)
                if (out.size() > MAX_DECOMPRESSED_BYTES) return null
            }
        } catch (e: DataFormatException) {
            return null
        } finally {
            inflater.end()
        }
        return out.toByteArray()
    }
}
