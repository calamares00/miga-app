package com.bmo00.miga.data.model

/** Sugerencia al añadir un artículo: viene del historial de compras (con [uses] > 0) o del catálogo de ingredientes. */
data class ShoppingSuggestion(val name: String, val quantity: Double?, val unit: String?, val uses: Int)

object ShoppingSuggestions {

    /**
     * Ordena las sugerencias para [query]: primero las que empiezan por lo escrito, luego las que
     * lo contienen; a igualdad, las del historial (más usadas antes) van antes que las del catálogo.
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
