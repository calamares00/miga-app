package org.calamares.miga.data.model

import java.text.Normalizer

/**
 * Text helpers for cooking mode: the parts of a step worth spotting from a distance (times,
 * temperatures, speeds and heat) and the ingredients a step uses.
 */
object CookModeText {

    private const val NUMBER = """\d+(?:[.,]\d+)?(?:\s*(?:-|–|a|o|to|or|y|and)\s*\d+(?:[.,]\d+)?)?"""

    /**
     * "10 minutos", "8-10 min", "1 o 2 minutos", "1 hora", "30 s", "2 h", "toda la noche". An
     * abbreviation keeps its dot only inside a sentence ("10 min. y"), not the full stop at its end.
     */
    private val DURATION = Regex(
        """(?<![\p{L}\d])(?:$NUMBER\s*(?:horas?|hours?|minutos?|minutes?|segundos?|seconds?|(?:hrs?|mins?|secs?|seg|h|s)(?:\.(?=\s*[a-záéíóúñ\d]))?)|toda la noche|overnight)(?![\p{L}\d])""",
        RegexOption.IGNORE_CASE
    )

    /** "180 ºC", "180°", "200 grados", "350 °F", "180 degrees Celsius". */
    private val TEMPERATURE = Regex(
        """(?<![\p{L}\d])\d{2,3}\s?(?:[º°]\s?[CF]?|grados(?:\s+(?:centígrados|celsius|fahrenheit))?|degrees(?:\s+(?:celsius|fahrenheit))?)(?![\p{L}\d])""",
        RegexOption.IGNORE_CASE
    )

    /** Food processor speed: "velocidad 4", "vel. 2", "velocidad 3½", "velocidad 5-10", "velocidad cuchara", "speed 5". */
    private val SPEED = Regex(
        """(?<![\p{L}\d])(?:velocidad|vel\.?|speed)\s*(?:cuchara|spoon|\d+(?:[.,]\d+)?[½¼¾]?(?:\s*-\s*\d+[½¼¾]?)?)(?![\p{L}\d])""",
        RegexOption.IGNORE_CASE
    )

    /** Heat: "fuego medio", "fuego medio-fuerte", "fuego al máximo", "a fuego mínimo", "medium heat". */
    private val HEAT = Regex(
        """(?<![\p{L}\d])(?:fuego\s+(?:muy\s+)?(?:medio(?:[\s-](?:alto|bajo|fuerte|suave|vivo))?|lento|bajo|suave|alto|fuerte|vivo|máximo|maximo|mínimo|minimo)|fuego\s+al?\s+(?:máximo|maximo|mínimo|minimo|tope)|(?:low|medium|medium-high|medium-low|high|full|maximum|max)\s+heat)(?![\p{L}\d])""",
        RegexOption.IGNORE_CASE
    )

    /** A hob's or appliance's setting: "nivel 6", "nivel 9 de 9", "potencia 7", "level 5 of 9". */
    private val LEVEL = Regex(
        """(?<![\p{L}\d])(?:nivel|potencia|level|power)\s+\d{1,2}(?:\s*(?:de|of|/)\s*\d{1,2})?(?![\p{L}\d])""",
        RegexOption.IGNORE_CASE
    )

    /** Between two highlights, an opening bracket: "fuego al máximo (nivel 9 de 9)". */
    private val JOINABLE_GAP = Regex("""\s*\(\s*""")

    /** Ranges of [text] to highlight, sorted and without overlaps. */
    fun highlights(text: String): List<IntRange> {
        val ranges = listOf(DURATION, TEMPERATURE, SPEED, HEAT, LEVEL)
            .flatMap { pattern -> pattern.findAll(text).map { it.range } }
            .sortedWith(compareBy<IntRange> { it.first }.thenByDescending { it.last })
        val merged = mutableListOf<IntRange>()
        for (range in ranges) {
            val last = merged.lastOrNull()
            if (last != null && range.first <= last.last) {
                merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
            } else {
                merged += range
            }
        }
        // "fuego al máximo (nivel 9 de 9)" reads as one setting: one highlight, its brackets included.
        val joined = mutableListOf<IntRange>()
        for (range in merged) {
            val last = joined.lastOrNull()
            if (last != null && JOINABLE_GAP.matches(text.substring(last.last + 1, range.first))) {
                joined[joined.lastIndex] = last.first..range.last
            } else {
                joined += range
            }
        }
        return joined.map { range ->
            val inside = text.substring(range.first, range.last + 1)
            val open = inside.count { it == '(' } > inside.count { it == ')' }
            if (open && text.getOrNull(range.last + 1) == ')') range.first..range.last + 1 else range
        }
    }

    /** Words that say nothing about which ingredient it is ("de", "picado", "cucharada"...). */
    private val NOT_INGREDIENT_WORDS = setOf(
        "de", "del", "la", "las", "el", "los", "con", "sin", "para", "por", "un", "una", "uno", "unos", "unas", "y", "o",
        "al", "en", "a", "of", "the", "and", "with", "for", "to", "or",
        "fresco", "fresca", "frescos", "frescas", "fresh", "picado", "picada", "picados", "picadas", "chopped",
        "grande", "grandes", "large", "pequeno", "pequena", "pequenos", "pequenas", "small", "mediano", "mediana", "medium",
        "rallado", "rallada", "grated", "molido", "molida", "ground", "entero", "entera", "whole", "troceado", "troceada",
        "cortado", "cortada", "cucharada", "cucharadas", "cucharadita", "cucharaditas", "tablespoon", "tablespoons",
        "teaspoon", "teaspoons", "taza", "tazas", "cup", "cups", "diente", "dientes", "clove", "cloves", "pizca", "pinch",
        "chorro", "chorrito", "splash", "trozo", "trozos", "rodaja", "rodajas", "hoja", "hojas", "rama", "ramas",
        "ramita", "ramitas", "lata", "latas", "bote", "sobre", "vaso", "vasos", "gramos", "tipo", "virgen", "extra",
        "blanco", "blanca", "negro", "negra", "rojo", "roja", "verde", "verdes", "maduro", "madura", "maduros", "maduras",
        "troceados", "troceadas", "cortados", "cortadas", "rallados", "ralladas", "molidos", "molidas", "enteros", "enteras",
        "seco", "seca", "secos", "secas", "dried", "sliced", "diced", "minced", "peeled", "pelado", "pelada", "pelados", "peladas",
        "gusto", "taste", "opcional", "optional", "necesario", "needed"
    )

    private val WORD = Regex("""[\p{L}\d]+""")

    /** Only "de" or "del" between two words of an ingredient: "contramuslos de pollo". */
    private val INGREDIENT_GAP = Regex("""\s+(?:de|del|of)?\s*""", RegexOption.IGNORE_CASE)

    /**
     * Where [text] names one of [ingredients], to show it in italics: the words of an ingredient's
     * name that identify it ("pollo", "ajos", "aceite"), in singular or plural, and the words
     * between two of them joined by "de" ("contramuslos de pollo").
     */
    fun ingredientMentions(text: String, ingredients: List<Ingredient>): List<IntRange> {
        val keys = ingredients
            .flatMap { words(it.name.substringBefore(',')) }
            .filter { it.length >= 3 && it !in NOT_INGREDIENT_WORDS }
            .flatMap { forms(it) }
            .toSet()
        if (keys.isEmpty()) return emptyList()
        val hits = WORD.findAll(text).filter { normalize(it.value) in keys }.map { it.range }.toList()
        val joined = mutableListOf<IntRange>()
        for (range in hits) {
            val last = joined.lastOrNull()
            if (last != null && INGREDIENT_GAP.matches(text.substring(last.last + 1, range.first))) {
                joined[joined.lastIndex] = last.first..range.last
            } else {
                joined += range
            }
        }
        return joined
    }

    /** Lower case, without accents. */
    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    private fun words(text: String): List<String> = normalize(text).split(Regex("[^\\p{L}\\d]+")).filter { it.isNotEmpty() }

    /** The word and its likely singular and plural forms ("huevo" ↔ "huevos", "limon" ↔ "limones"). */
    private fun forms(word: String): Set<String> = buildSet {
        add(word)
        add(word + "s")
        add(word + "es")
        if (word.endsWith("es") && word.length > 4) add(word.dropLast(2))
        if (word.endsWith("s") && word.length > 3) add(word.dropLast(1))
    }

    /**
     * The [ingredients] that [step] mentions: by their whole name or by a word of it that names
     * the ingredient ("aceite de oliva" in "añade el aceite"). Duplicates are listed once.
     */
    fun ingredientsIn(step: String, ingredients: List<Ingredient>): List<Ingredient> {
        val stepWords = words(step).toSet()
        val stepText = " " + words(step).joinToString(" ") + " "
        return ingredients
            .filter { ingredient ->
                val nameWords = words(ingredient.name)
                if (nameWords.isEmpty()) return@filter false
                val wholeName = " " + nameWords.joinToString(" ") + " "
                stepText.contains(wholeName) ||
                    nameWords.filter { it.length >= 3 && it !in NOT_INGREDIENT_WORDS }.any { word -> forms(word).any { it in stepWords } }
            }
            .distinctBy { normalize(it.name) }
    }
}
