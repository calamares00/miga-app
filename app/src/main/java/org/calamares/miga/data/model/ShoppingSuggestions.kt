package org.calamares.miga.data.model

/**
 * Suggestion shown while adding an item. It comes from the purchase history ([uses] > 0) or from
 * the ingredient catalogue.
 */
data class ShoppingSuggestion(val name: String, val quantity: Double?, val unit: String?, val uses: Int)

object ShoppingSuggestions {

    /**
     * Ranks suggestions for [query]: exact matches first, then prefix matches, then substring
     * matches. Ties favour history entries (most used first) over catalogue names.
     */
    fun rank(query: String, history: List<ShoppingSuggestion>, catalogNames: List<String>, limit: Int = 6): List<ShoppingSuggestion> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()

        val historyNames = history.map { it.name.trim().lowercase() }.toSet()
        val catalog = catalogNames
            .filter { it.trim().lowercase() !in historyNames }
            .map { ShoppingSuggestion(it.trim(), null, null, 0) }

        return (history + catalog)
            .mapNotNull { suggestion ->
                val name = suggestion.name.trim().lowercase()
                val matchKind = when {
                    name == needle -> -1
                    name.startsWith(needle) -> 0
                    name.contains(needle) -> 1
                    else -> return@mapNotNull null
                }
                Triple(matchKind, suggestion, name)
            }
            .sortedWith(
                compareBy<Triple<Int, ShoppingSuggestion, String>> { it.first }
                    .thenByDescending { it.second.uses }
                    .thenBy { it.third }
            )
            .map { it.second }
            .take(limit)
    }
}
