package org.calamares.miga.data.model

/**
 * Puts a unit in singular or plural to agree with its quantity: "1 taza", "0.5 taza", "3 tazas",
 * "2 cups". Units are stored as written (usually in singular), so the plural is only added when
 * the ingredient is shown.
 *
 * Plural from more than 1, as cooks write it ("½ taza", "1 cup", "1.5 tazas"). Symbols and
 * abbreviations (g, ml, kg, tbsp, cda...) never change; units the app knows go through a table
 * in Spanish and English, which also takes a plural back to singular ("1 tazas" -> "1 taza");
 * other words get the regular plural of the app's language. In units of several words the words
 * before "de"/"of" agree ("cucharada sopera" -> "cucharadas soperas", "diente de ajo" ->
 * "dientes de ajo").
 */
object UnitInflection {

    private val SPANISH = listOf(
        "taza" to "tazas", "cucharada" to "cucharadas", "cucharadita" to "cucharaditas", "pizca" to "pizcas",
        "diente" to "dientes", "lata" to "latas", "sobre" to "sobres", "sobrecito" to "sobrecitos", "vaso" to "vasos",
        "vasito" to "vasitos", "copa" to "copas", "chorro" to "chorros", "chorrito" to "chorritos", "puñado" to "puñados",
        "pellizco" to "pellizcos", "hoja" to "hojas", "rama" to "ramas", "ramita" to "ramitas", "rodaja" to "rodajas",
        "loncha" to "lonchas", "lámina" to "láminas", "trozo" to "trozos", "pieza" to "piezas", "unidad" to "unidades",
        "paquete" to "paquetes", "bolsa" to "bolsas", "botella" to "botellas", "caja" to "cajas", "docena" to "docenas",
        "bandeja" to "bandejas", "tarro" to "tarros", "bote" to "botes", "barra" to "barras", "manojo" to "manojos",
        "brik" to "briks", "pack" to "packs", "cubito" to "cubitos", "cubo" to "cubos", "gota" to "gotas",
        "filete" to "filetes", "porción" to "porciones", "ración" to "raciones", "taco" to "tacos", "tallo" to "tallos",
        "cabeza" to "cabezas", "onza" to "onzas", "libra" to "libras", "kilo" to "kilos", "gramo" to "gramos",
        "litro" to "litros", "mililitro" to "mililitros", "centilitro" to "centilitros", "vaina" to "vainas",
        "nuez" to "nueces", "terrón" to "terrones", "tableta" to "tabletas", "pastilla" to "pastillas",
        "envase" to "envases", "tarrina" to "tarrinas", "cazo" to "cazos", "cacito" to "cacitos",
        "chupito" to "chupitos", "hebra" to "hebras", "bola" to "bolas", "tira" to "tiras", "rebanada" to "rebanadas",
        "gajo" to "gajos", "cuña" to "cuñas", "punta" to "puntas", "medida" to "medidas", "cucharón" to "cucharones",
        // Adjectives that go with them.
        "sopera" to "soperas", "rasa" to "rasas", "colmada" to "colmadas", "grande" to "grandes",
        "mediana" to "medianas", "mediano" to "medianos", "pequeña" to "pequeñas", "pequeño" to "pequeños",
        "generosa" to "generosas", "generoso" to "generosos"
    )

    private val ENGLISH = listOf(
        "cup" to "cups", "tablespoon" to "tablespoons", "teaspoon" to "teaspoons", "pinch" to "pinches",
        "clove" to "cloves", "can" to "cans", "tin" to "tins", "packet" to "packets", "package" to "packages",
        "pack" to "packs", "bag" to "bags", "bottle" to "bottles", "box" to "boxes", "bunch" to "bunches",
        "slice" to "slices", "piece" to "pieces", "stick" to "sticks", "sprig" to "sprigs", "leaf" to "leaves",
        "handful" to "handfuls", "dash" to "dashes", "splash" to "splashes", "drop" to "drops", "jar" to "jars",
        "carton" to "cartons", "loaf" to "loaves", "sheet" to "sheets", "cube" to "cubes", "head" to "heads",
        "stalk" to "stalks", "fillet" to "fillets", "ounce" to "ounces", "pound" to "pounds", "gram" to "grams",
        "kilogram" to "kilograms", "liter" to "liters", "litre" to "litres", "milliliter" to "milliliters",
        "millilitre" to "millilitres", "quart" to "quarts", "pint" to "pints", "gallon" to "gallons",
        "glass" to "glasses", "knob" to "knobs", "unit" to "units", "envelope" to "envelopes", "sachet" to "sachets",
        "scoop" to "scoops", "strip" to "strips", "wedge" to "wedges", "pod" to "pods", "ball" to "balls",
        "heaped" to "heaped", "level" to "level", "large" to "large", "small" to "small", "medium" to "medium"
    )

    /** Lower-case singular -> plural and plural -> singular, both languages. */
    private val TO_PLURAL: Map<String, String> = (SPANISH + ENGLISH).toMap()
    private val TO_SINGULAR: Map<String, String> = (SPANISH + ENGLISH).associate { (singular, plural) -> plural to singular }

    /** Abbreviations long enough to look like words; they never change. */
    private val ABBREVIATIONS = setOf(
        "cda", "cdas", "cdta", "cdtas", "cdita", "cditas", "tbsp", "tbsps", "tbs", "tsp", "tsps", "grs", "lbs", "uds"
    )

    private val ACCENTED = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u')

    /** Words after which the rest of the unit is a complement that never agrees ("diente de ajo"). */
    private val STOP_WORDS = setOf("de", "del", "of")

    /** [unit] agreeing with [quantity] (as shown, already scaled and rounded). */
    fun inflect(unit: String, quantity: Double, language: String): String {
        val plural = quantity > 1.0
        val words = unit.trim().split(' ')
        val stop = words.indexOfFirst { it.lowercase() in STOP_WORDS }.let { if (it == -1) words.size else it }
        return words.mapIndexed { index, word -> if (index < stop) inflectWord(word, plural, language) else word }
            .joinToString(" ")
    }

    private fun inflectWord(word: String, plural: Boolean, language: String): String {
        val lower = word.lowercase()
        val known = if (plural) TO_PLURAL[lower] ?: lower.takeIf { it in TO_SINGULAR }
        else TO_SINGULAR[lower] ?: lower.takeIf { it in TO_PLURAL }
        if (known != null) return matchCase(word, known)
        // Unknown: only whole lower-case words get a regular plural. Symbols, abbreviations
        // ("g", "cda", "tbsp"), brands and words with digits or dots stay as they are, and
        // nothing unknown is taken back to singular (an "s" at the end is not always a plural).
        if (!plural || word.length < 4 || lower in ABBREVIATIONS || !word.all { it.isLetter() && it.isLowerCase() }) return word
        return regularPlural(word, language)
    }

    private fun regularPlural(word: String, language: String): String = when (language) {
        "es" -> when {
            word.endsWith("s") || word.endsWith("x") -> word
            word.endsWith("z") -> word.dropLast(1) + "ces"
            word.last() in "aeiouáéó" -> word + "s"
            // The accent goes away when the stress no longer falls on the last syllable:
            // "cucharón" -> "cucharones".
            word.length > 2 && word[word.length - 2] in ACCENTED -> word.dropLast(2) + ACCENTED.getValue(word[word.length - 2]) + word.last() + "es"
            else -> word + "es"
        }
        else -> when {
            word.endsWith("s") || word.endsWith("x") || word.endsWith("ch") || word.endsWith("sh") -> word + "es"
            word.endsWith("y") && word.length > 1 && word[word.length - 2] !in "aeiou" -> word.dropLast(1) + "ies"
            else -> word + "s"
        }
    }

    /** Keeps a leading capital ("Taza" -> "Tazas"). */
    private fun matchCase(original: String, replacement: String): String =
        if (original.firstOrNull()?.isUpperCase() == true) replacement.replaceFirstChar { it.uppercase() } else replacement
}
