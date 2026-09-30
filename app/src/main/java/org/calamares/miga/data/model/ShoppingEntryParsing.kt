package org.calamares.miga.data.model

data class ParsedShoppingEntry(val name: String, val quantity: Double?, val unit: String?)

/**
 * Interpreta texto libre escrito, pegado o dictado ("2 kg tomates, leche; 3 huevos") como
 * artículos de la lista de la compra: separa varios artículos y, en cada uno, detecta cantidad y
 * unidad iniciales. Lógica pura, sin Android, para poder probarla con tests unitarios.
 */
object ShoppingEntryParser {

    private val UNIT_ALIASES: Map<String, String> = mapOf(
        "kg" to "kg", "kgs" to "kg", "kilo" to "kg", "kilos" to "kg",
        "g" to "g", "gr" to "g", "grs" to "g", "gramo" to "g", "gramos" to "g",
        "l" to "l", "lt" to "l", "litro" to "l", "litros" to "l",
        "ml" to "ml", "cl" to "cl", "dl" to "dl",
        "ud" to "ud", "uds" to "ud", "unidad" to "ud", "unidades" to "ud",
        "lata" to "lata", "latas" to "lata",
        "bote" to "bote", "botes" to "bote",
        "paquete" to "paquete", "paquetes" to "paquete",
        "bolsa" to "bolsa", "bolsas" to "bolsa",
        "botella" to "botella", "botellas" to "botella",
        "caja" to "caja", "cajas" to "caja",
        "docena" to "docena", "docenas" to "docena",
        "bandeja" to "bandeja", "bandejas" to "bandeja",
        "tarro" to "tarro", "tarros" to "tarro",
        "barra" to "barra", "barras" to "barra",
        "manojo" to "manojo", "manojos" to "manojo",
        "sobre" to "sobre", "sobres" to "sobre",
        "brik" to "brik", "briks" to "brik",
        "pack" to "pack", "packs" to "pack",
        "loncha" to "loncha", "lonchas" to "loncha",
        "cucharada" to "cucharada", "cucharadas" to "cucharada",
        "cucharadita" to "cucharadita", "cucharaditas" to "cucharadita",
        "taza" to "taza", "tazas" to "taza"
    )

    private val NUMBER_PREFIX = Regex("""^(\d+/\d+|\d+(?:[.,]\d+)?|½|¼|¾)(\s*)(.*)$""")

    // Una coma separa artículos salvo que sea un decimal ("1,5 kg"), es decir, con dígito a ambos lados.
    private val SEPARATORS = Regex("""[\n;]+|,(?!\d)|(?<!\d),""")
    private val CONJUNCTION = Regex("""\s+y\s+""", RegexOption.IGNORE_CASE)

    /** [splitOnY] separa también por " y " ("leche y pan"); útil al dictar, arriesgado al escribir. */
    fun parse(text: String, splitOnY: Boolean = false): List<ParsedShoppingEntry> {
        val chunks = text.split(SEPARATORS).flatMap { chunk ->
            if (splitOnY) chunk.split(CONJUNCTION) else listOf(chunk)
        }
        return chunks.map { it.trim() }.filter { it.isNotEmpty() }.map { parseOne(it) }
    }

    private fun parseOne(entry: String): ParsedShoppingEntry {
        val match = NUMBER_PREFIX.matchEntire(entry) ?: return ParsedShoppingEntry(entry, null, null)
        val quantity = parseNumber(match.groupValues[1]) ?: return ParsedShoppingEntry(entry, null, null)
        val attached = match.groupValues[2].isEmpty()
        val rest = match.groupValues[3].trim()
        if (rest.isEmpty()) return ParsedShoppingEntry(entry, null, null)

        val firstToken = rest.takeWhile { !it.isWhitespace() }
        val afterToken = rest.drop(firstToken.length).trim()
        val unit = UNIT_ALIASES[firstToken.lowercase().trimEnd('.')]
        return when {
            unit != null && afterToken.isNotEmpty() -> ParsedShoppingEntry(stripLeadingDe(afterToken), quantity, unit)
            unit != null -> ParsedShoppingEntry(firstToken, quantity, null)
            // "7up", "2x1": número pegado a una palabra que no es unidad, forma parte del nombre.
            attached -> ParsedShoppingEntry(entry, null, null)
            else -> ParsedShoppingEntry(rest, quantity, null)
        }
    }

    private fun stripLeadingDe(text: String): String {
        val lower = text.lowercase()
        val stripped = when {
            lower.startsWith("de ") -> text.drop(3)
            lower.startsWith("del ") -> text.drop(4)
            else -> text
        }.trim()
        return stripped.ifEmpty { text }
    }

    private fun parseNumber(raw: String): Double? = when {
        raw == "½" -> 0.5
        raw == "¼" -> 0.25
        raw == "¾" -> 0.75
        raw.contains('/') -> {
            val parts = raw.split('/')
            val numerator = parts[0].toDoubleOrNull()
            val denominator = parts[1].toDoubleOrNull()
            if (numerator != null && denominator != null && denominator != 0.0) numerator / denominator else null
        }
        else -> raw.replace(',', '.').toDoubleOrNull()
    }
}
