package org.calamares.miga.data.model

data class ParsedShoppingEntry(val name: String, val quantity: Double?, val unit: String?)

/**
 * Parses free text that was typed, pasted or dictated ("2 kg tomatoes, milk; 3 eggs") into shopping
 * list entries. It splits the text into items and detects a leading quantity and unit in each one.
 * Pure logic, so it can be unit tested.
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
        "taza" to "taza", "tazas" to "taza",
        // English
        "kilogram" to "kg", "kilograms" to "kg", "gram" to "g", "grams" to "g",
        "litre" to "l", "litres" to "l", "liter" to "l", "liters" to "l",
        "lb" to "lb", "lbs" to "lb", "pound" to "lb", "pounds" to "lb",
        "oz" to "oz", "ounce" to "oz", "ounces" to "oz",
        "can" to "can", "cans" to "can", "tin" to "tin", "tins" to "tin",
        "jar" to "jar", "jars" to "jar",
        "packet" to "pack", "packets" to "pack", "package" to "pack", "packages" to "pack",
        "bag" to "bag", "bags" to "bag",
        "bottle" to "bottle", "bottles" to "bottle",
        "box" to "box", "boxes" to "box",
        "dozen" to "dozen", "dozens" to "dozen",
        "tray" to "tray", "trays" to "tray",
        "bunch" to "bunch", "bunches" to "bunch",
        "carton" to "carton", "cartons" to "carton",
        "loaf" to "loaf", "loaves" to "loaf",
        "slice" to "slice", "slices" to "slice",
        "tbsp" to "tbsp", "tablespoon" to "tbsp", "tablespoons" to "tbsp",
        "tsp" to "tsp", "teaspoon" to "tsp", "teaspoons" to "tsp",
        "cup" to "cup", "cups" to "cup",
        "unit" to "unit", "units" to "unit", "piece" to "unit", "pieces" to "unit"
    )

    /**
     * Numbers written or spoken as words ("dos kilos de tomates", "a dozen eggs"). Only recognised
     * at the start of an entry.
     */
    private val WORD_NUMBERS: Map<String, Double> = mapOf(
        "un" to 1.0, "una" to 1.0, "uno" to 1.0, "dos" to 2.0, "tres" to 3.0, "cuatro" to 4.0, "cinco" to 5.0,
        "seis" to 6.0, "siete" to 7.0, "ocho" to 8.0, "nueve" to 9.0, "diez" to 10.0, "medio" to 0.5, "media" to 0.5,
        "a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0,
        "six" to 6.0, "seven" to 7.0, "eight" to 8.0, "nine" to 9.0, "ten" to 10.0, "half" to 0.5
    )

    private val NUMBER_PREFIX = Regex("""^(\d+/\d+|\d+(?:[.,]\d+)?|½|¼|¾)(\s*)(.*)$""")

    /**
     * A comma separates items unless it is a decimal separator ("1,5 kg"), i.e. it has a digit on
     * both sides.
     */
    private val SEPARATORS = Regex("""[\n;]+|,(?!\d)|(?<!\d),""")
    private val CONJUNCTION = Regex("""\s+(?:y|and)\s+""", RegexOption.IGNORE_CASE)

    /**
     * When [splitOnY] is true, items are also split on " y " / " and " ("milk and bread"). Useful
     * for dictation, risky for typed text.
     */
    fun parse(text: String, splitOnY: Boolean = false): List<ParsedShoppingEntry> {
        val chunks = text.split(SEPARATORS).flatMap { chunk ->
            if (splitOnY) chunk.split(CONJUNCTION) else listOf(chunk)
        }
        return chunks.map { it.trim() }.filter { it.isNotEmpty() }.map { parseOne(it) }
    }

    private fun parseOne(entry: String): ParsedShoppingEntry {
        val match = NUMBER_PREFIX.matchEntire(entry) ?: return parseWordNumber(entry)
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
            // "7up", "2x1": a number glued to a word that is not a unit is part of the name.
            attached -> ParsedShoppingEntry(entry, null, null)
            else -> ParsedShoppingEntry(rest, quantity, null)
        }
    }

    /**
     * "dos kilos de tomates", "a dozen eggs": a number word followed by a known unit. Without a
     * unit the entry is left as is ("una lechuga" already reads fine).
     */
    private fun parseWordNumber(entry: String): ParsedShoppingEntry {
        val words = entry.trim().split(Regex("""\s+"""), limit = 3)
        if (words.size < 3) return ParsedShoppingEntry(entry, null, null)
        val quantity = WORD_NUMBERS[words[0].lowercase()] ?: return ParsedShoppingEntry(entry, null, null)
        val unit = UNIT_ALIASES[words[1].lowercase().trimEnd('.')] ?: return ParsedShoppingEntry(entry, null, null)
        return ParsedShoppingEntry(stripLeadingDe(words[2]), quantity, unit)
    }

    private fun stripLeadingDe(text: String): String {
        val lower = text.lowercase()
        val stripped = when {
            lower.startsWith("de ") -> text.drop(3)
            lower.startsWith("del ") -> text.drop(4)
            lower.startsWith("of ") -> text.drop(3)
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
