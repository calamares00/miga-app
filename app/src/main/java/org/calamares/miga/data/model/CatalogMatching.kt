package org.calamares.miga.data.model

/**
 * Finds, among the categories or equipment the user already has, the one a name proposed by the
 * AI refers to, so that "Postre" goes into "Postres" and "Crema" into "Sopas y cremas" instead of
 * creating near duplicates.
 */
object CatalogMatching {

    private val LINK_WORDS = setOf("y", "e", "o", "de", "del", "la", "el", "los", "las", "con", "a", "al", "en", "and", "or", "of", "the", "with")

    /**
     * Possible singular stems of a word: without the final "s", then without a final "e" after a
     * consonant ("postres" and "postre" -> "postr", "panes" -> "pan"), and the Spanish "-ces" plural
     * of words ending in "z" ("arroces" -> "arroz").
     */
    private fun stems(word: String): Set<String> {
        var stem = word
        if (stem.length > 3 && stem.endsWith("s")) stem = stem.dropLast(1)
        if (stem.length > 3 && stem.endsWith("e") && stem[stem.length - 2] !in "aeiou") stem = stem.dropLast(1)
        return buildSet {
            add(stem)
            if (word.length > 4 && word.endsWith("ces")) add(word.dropLast(3) + "z")
        }
    }

    private fun words(name: String): List<Set<String>> =
        KitchenEquipment.key(name).split(Regex("[^\\p{L}\\d]+"))
            .filter { it.isNotEmpty() && it !in LINK_WORDS }
            .map(::stems)

    private fun sameWord(a: Set<String>, b: Set<String>) = a.any { it in b }

    /** Every word of [part] has a matching word in [whole]. */
    private fun covers(whole: List<Set<String>>, part: List<Set<String>>) = part.all { word -> whole.any { sameWord(it, word) } }

    /**
     * The entry of [existing] that [name] refers to: the same words (ignoring case, accents, plural
     * and link words such as "y" or "de"), or else the only entry that contains all the words of
     * the name or whose words are all in it. Null when nothing fits, or when it is ambiguous.
     */
    fun bestMatch(name: String, existing: List<String>): String? {
        val target = words(name)
        if (target.isEmpty()) return null
        existing.firstOrNull { candidate ->
            val words = words(candidate)
            words.size == target.size && covers(words, target)
        }?.let { return it }
        return existing.filter { candidate ->
            val words = words(candidate)
            words.isNotEmpty() && (covers(words, target) || covers(target, words))
        }.singleOrNull()
    }
}
