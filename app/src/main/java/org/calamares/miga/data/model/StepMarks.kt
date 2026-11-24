package org.calamares.miga.data.model

/**
 * How steps and notes are styled, the same for every recipe whatever its source:
 * - times, temperatures, heat and appliance settings in the theme colour, and
 * - the recipe's ingredients in italics,
 * both worked out by the app when showing them ([CookModeText]), never written in the text;
 * - bold, written in the text, only for a warning the cook must not miss ("**sin moverlos**").
 *
 * [withoutRedundantMarks] removes the bold and italics that only repeat what the app shows by
 * itself, as the assistant used to add when polishing a recipe.
 */
object StepMarks {

    private val BOLD = Regex("""(?<!\\)\*\*(?=\S)(.+?)(?<=\S)\*\*""")
    private val ITALIC = Regex("""(?<![\\*])\*(?=[^\s*])([^*\n]+?)(?<=[^\s*\\])\*(?!\*)""")

    /** [text] without bold over a time, temperature, heat or setting, nor italics over an ingredient. */
    fun withoutRedundantMarks(text: String, ingredients: List<Ingredient>): String {
        val unbolded = strip(text, BOLD, 2) { plain -> CookModeText.highlights(plain) }
        return strip(unbolded, ITALIC, 1) { plain -> CookModeText.ingredientMentions(plain, ingredients) }
    }

    /**
     * Removes the [pattern] marks ([width] characters each side) whose text lies wholly inside the
     * ranges [automatic] finds in the visible text.
     */
    private fun strip(text: String, pattern: Regex, width: Int, automatic: (String) -> List<IntRange>): String {
        val parsed = RichText.parse(text)
        val visible = text.indices.filter { !parsed.marker[it] }
        val plainIndex = IntArray(text.length) { -1 }
        visible.forEachIndexed { plain, raw -> plainIndex[raw] = plain }
        val plain = visible.map { text[it] }.joinToString("")
        val covered = BooleanArray(plain.length)
        automatic(plain).forEach { range -> for (i in range) if (i in covered.indices) covered[i] = true }

        val redundant = pattern.findAll(text).filter { match ->
            val inner = match.groups[1]!!.range
            val letters = inner.filter { text[it].isLetterOrDigit() }
            letters.isNotEmpty() && letters.all { plainIndex[it] >= 0 && covered[plainIndex[it]] }
        }.toList()
        if (redundant.isEmpty()) return text
        val out = StringBuilder(text)
        redundant.asReversed().forEach { match ->
            out.delete(match.range.last + 1 - width, match.range.last + 1)
            out.delete(match.range.first, match.range.first + width)
        }
        return out.toString()
    }
}
