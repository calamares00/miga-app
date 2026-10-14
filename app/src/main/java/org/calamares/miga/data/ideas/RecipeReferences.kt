package org.calamares.miga.data.ideas

/**
 * References to library recipes inside the free text of an Ideas answer. The model is asked to
 * write them as `[[id]]`, which the app shows as the recipe name linking to the recipe; the user
 * never sees ids. Models do not always comply ("Lentejas (id 12)", "receta #12", "ID: 12"), so
 * [normalize] turns every reference it recognises into the `[[id]]` form and removes the ones that
 * point to recipes that do not exist.
 */
object RecipeReferences {

    /** The canonical form kept in the answer: `[[12]]`. */
    val TOKEN = Regex("""\[\[(\d{1,18})]]""")

    private const val ID_WORDS = """(?:recipe\s*id|recipeid|id|receta|recipe)"""
    private const val NUMBER_SIGNS = """(?:n[º°]\.?|n\.\s?º|núm\.?|número|number|no\.|#)"""

    /** Bracketed forms: `[[12]]`, `[[id:12]]`, `[id 12]`, `[receta #12]`. */
    private val BRACKETED = Regex(
        """\[\[?\s*(?:$ID_WORDS\s*)?(?:$NUMBER_SIGNS\s*)?[:=]?\s*(\d{1,18})\s*]]?""",
        RegexOption.IGNORE_CASE
    )

    /** Parenthesised forms after a name: `(id 12)`, `(ID: 12)`, `(#12)`, `(receta nº 12)`. */
    private val PARENTHESISED = Regex(
        """\(\s*(?:(?:$ID_WORDS)\s*(?:$NUMBER_SIGNS)?|$NUMBER_SIGNS)\s*[:=]?\s*(\d{1,18})\s*\)""",
        RegexOption.IGNORE_CASE
    )

    /** Loose forms in the sentence: `id 12`, `ID: 12`, `recipeId 12`, `receta #12`, `receta nº 12`. */
    private val LOOSE = Regex(
        """(?<![\p{L}\d])(?:(?:recipe\s*id|recipeid|id)\s*(?:$NUMBER_SIGNS)?|(?:receta|recipe)\s*$NUMBER_SIGNS)\s*[:=]?\s*(\d{1,18})(?!\d)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * [text] with every recipe reference as `[[id]]`. When the reference follows the recipe name
     * ("Lentejas (id 12)"), the name and the reference become one `[[id]]`, so the name is not shown
     * twice. References to ids not in [names] are removed.
     */
    fun normalize(text: String, names: Map<Long, String>): String {
        var result = text
        for (pattern in listOf(BRACKETED, PARENTHESISED, LOOSE)) {
            result = replaceReferences(result, pattern, names)
        }
        return tidy(result)
    }

    /** [text] with every `[[id]]` replaced by the recipe name, for places that cannot show links. */
    fun toPlainText(text: String, names: Map<Long, String>): String =
        tidy(normalize(text, names).replace(TOKEN) { match -> names[match.groupValues[1].toLong()].orEmpty() })

    /** Ids referenced in [text], in order of appearance. */
    fun ids(text: String): List<Long> = TOKEN.findAll(text).map { it.groupValues[1].toLong() }.toList()

    private fun replaceReferences(text: String, pattern: Regex, names: Map<Long, String>): String {
        val out = StringBuilder()
        var last = 0
        for (match in pattern.findAll(text)) {
            if (match.range.first < last) continue
            val id = match.groupValues[1].toLongOrNull()
            val name = id?.let { names[it] }
            if (name == null) {
                out.append(text, last, match.range.first)
            } else {
                val nameStart = precedingNameStart(text, last, match.range.first, name)
                out.append(text, last, nameStart ?: match.range.first)
                out.append("[[").append(id).append("]]")
                // Quotes or emphasis closed after the name stay, so "**Lentejas** (id 12)" keeps its pair.
                if (nameStart != null) out.append(text.substring(nameStart + name.length, match.range.first).filter { it in DECORATION })
            }
            last = match.range.last + 1
        }
        out.append(text, last, text.length)
        return out.toString()
    }

    /**
     * Where [name] starts when it appears right before [end] (ignoring spaces, quotes and
     * emphasis markers between them), or null.
     */
    private fun precedingNameStart(text: String, from: Int, end: Int, name: String): Int? {
        var cut = end
        while (cut > from && (text[cut - 1].isWhitespace() || text[cut - 1] in DECORATION)) cut--
        val start = cut - name.length
        if (start < from || !text.regionMatches(start, name, 0, name.length, ignoreCase = true)) return null
        if (start > 0 && text[start - 1].isLetterOrDigit()) return null
        return start
    }

    /** Removes the spaces and empty parentheses left behind by removed references. */
    private fun tidy(text: String): String = text
        .replace(Regex("""\(\s*\)"""), "")
        .replace(Regex("""[ \t]+([,.;:!?)])"""), "$1")
        .replace(Regex("""[ \t]{2,}"""), " ")
        .trim()

    private const val DECORATION = "\"'«»“”*_~"
}
