package org.calamares.miga.data.content

/**
 * Singular and plural of the names of ingredients, products and tags, as Miga writes them: the
 * words before the first preposition change ("Dientes de ajo" -> "Diente de ajo", "almendras
 * crudas" -> "almendra cruda"); acronyms and brands ("BBQ", "Thermomix") and a few words that
 * only make sense in plural ("bebés", "legumbres") are kept.
 */
object ContentNames {

    private val PREPOSITIONS = mapOf(
        "es" to setOf("de", "del", "con", "a", "al", "en", "para", "sin", "por", "y", "o"),
        "en" to setOf("of", "with", "in", "for", "without", "and", "or", "to")
    )

    /** Words never turned into singular: plural-only names and words ending in -s in singular. */
    private val KEEP = mapOf(
        "es" to setOf(
            "bebés", "legumbres", "espaguetis", "natillas", "gachas", "migas", "anís", "cuscús", "ananás",
            "crudités", "lunes", "martes", "miércoles", "jueves", "viernes", "gafas", "tijeras", "pinzas", "gas"
        ),
        "en" to setOf(
            "babies", "molasses", "asparagus", "couscous", "hummus", "swiss", "brussels", "greens", "scissors",
            "tongs", "gas", "chips", "nachos", "noodles"
        )
    )

    /** [name] in singular (see the class). */
    fun singular(name: String, language: String): String = mapHead(name, language) { singularWord(it, language) }

    /** [name] in plural (see the class): for a counted ingredient, "Huevo" × 3 -> "Huevos". */
    fun plural(name: String, language: String): String = mapHead(name, language) { pluralWord(it, language) }

    private fun mapHead(name: String, language: String, change: (String) -> String): String {
        val words = name.split(' ')
        val prepositions = PREPOSITIONS[language] ?: PREPOSITIONS.getValue("en")
        val stop = words.indexOfFirst { it.lowercase() in prepositions }.let { if (it < 0) words.size else it }
        return words.mapIndexed { index, word -> if (index < stop && word.isNotEmpty() && !isAcronymOrBrand(word)) change(word) else word }.joinToString(" ")
    }

    /** "BBQ", "3D", "iPhone": written as they are, never changed. */
    fun isAcronymOrBrand(word: String): Boolean =
        word.any { it.isDigit() } || word.count { it.isUpperCase() } >= 2 || word.drop(1).any { it.isUpperCase() }

    /** A single word in singular: "almendras" -> "almendra", "limones" -> "limón", "nueces" -> "nuez". */
    fun singularWord(word: String, language: String): String {
        val lower = word.lowercase()
        if (lower.length <= 3 || !lower.endsWith("s") || lower in KEEP[language].orEmpty()) return word
        return if (language == "es") spanishSingular(word) else englishSingular(word)
    }

    private fun spanishSingular(word: String): String {
        val lower = word.lowercase()
        return when {
            lower.endsWith("is") || lower.endsWith("us") -> word
            lower.endsWith("ces") -> word.dropLast(3) + "z"
            // "limones" -> "limón", "calabacines" -> "calabacín": the stress mark comes back.
            lower.endsWith("ones") -> word.dropLast(4) + "ón"
            lower.endsWith("ines") -> word.dropLast(4) + "ín"
            lower.endsWith("es") -> {
                val stem = word.dropLast(2)
                // "papeles" -> "papel", "flores" -> "flor", "panes" -> "pan"; but "postres" ->
                // "postre", "saludables" -> "saludable", "tomates" -> "tomate".
                val endsLikeSingular = stem.length >= 2 && stem.last().lowercaseChar() in "lrndj" && stem[stem.length - 2].lowercaseChar() in VOWELS
                if (endsLikeSingular) stem else word.dropLast(1)
            }
            else -> word.dropLast(1)
        }
    }

    private fun englishSingular(word: String): String {
        val lower = word.lowercase()
        return when {
            lower.endsWith("ss") || lower.endsWith("us") || lower.endsWith("is") -> word
            lower.endsWith("ies") -> word.dropLast(3) + "y"
            lower.endsWith("oes") || lower.endsWith("ches") || lower.endsWith("shes") || lower.endsWith("xes") -> word.dropLast(2)
            else -> word.dropLast(1)
        }
    }

    /** A single word in plural: "huevo" -> "huevos", "limón" -> "limones", "berry" -> "berries". */
    fun pluralWord(word: String, language: String): String = ContentCleanup.plural(word, language)

    private const val VOWELS = "aeiouáéíóú"
}
