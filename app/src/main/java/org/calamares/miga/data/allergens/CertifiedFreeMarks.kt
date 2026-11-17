package org.calamares.miga.data.allergens

import org.calamares.miga.L10n
import org.calamares.miga.R

/**
 * "Gluten-free" and "lactose-free" marks the editor adds to an ingredient's name when the cook
 * buys a certified product ("chorizo sin gluten"). The name carries it, so it shows wherever the
 * recipe goes (shopping list, PDF, shared books) and AllergenAnalysis reads it.
 */
enum class CertifiedFreeMarks(val allergen: Allergen, private val labelRes: Int, private val phraseRes: Int) {
    GLUTEN_FREE(Allergen.GLUTEN, R.string.ingredient_mark_gluten_free, R.string.ingredient_gluten_free_phrase),
    LACTOSE_FREE(Allergen.LACTOSE, R.string.ingredient_mark_lactose_free, R.string.ingredient_lactose_free_phrase);

    val label: String get() = L10n.str(labelRes)

    private val phrases: List<String> get() = listOf(L10n.str(phraseRes)) + when (this) {
        GLUTEN_FREE -> listOf("sin gluten", "gluten-free", "gluten free")
        LACTOSE_FREE -> listOf("sin lactosa", "lactose-free", "lactose free")
    }

    private fun pattern(phrase: String) = Regex("""(?i)(^|[\s,(])${Regex.escape(phrase)}(?=$|[\s,)])""")

    fun isIn(name: String): Boolean = phrases.any { pattern(it).containsMatchIn(name) }

    /** Adds the mark at the end of [name], or removes it. */
    fun toggle(name: String): String =
        if (isIn(name)) phrases.fold(name) { text, phrase -> pattern(phrase).replace(text, "$1") }.replace(Regex("\\s{2,}"), " ").trim()
        else "${name.trim()} ${L10n.str(phraseRes)}"
}
