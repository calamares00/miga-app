package org.calamares.miga.data.content

import org.calamares.miga.data.model.ContentItem
import org.calamares.miga.data.model.ContentKind
import org.calamares.miga.data.model.KitchenEquipment
import java.text.Normalizer

enum class CleanupAction { MERGE, RENAME, DELETE }

/**
 * One change the cleanup proposes. [items] are the entries it touches: the ones merged (with the
 * [target] they go into), the one renamed or the one deleted. [newName] is the final name of the
 * merged or renamed entry. [recommended] proposals start ticked; [fromAi] ones come from the AI.
 */
data class CleanupProposal(
    val kind: ContentKind,
    val action: CleanupAction,
    val items: List<ContentItem>,
    val target: ContentItem? = null,
    val newName: String? = null,
    val recommended: Boolean = true,
    val fromAi: Boolean = false
) {
    /** Stable across scans, to keep the user's ticks. */
    val key: String = "${kind.name}:${action.name}:${items.map { it.id }.sorted().joinToString(",")}:${target?.id}:$newName"

    /** Recipes (or ingredients) that will show the change. */
    val affected: Int = items.sumOf { it.usage }
}

/**
 * Finds what can be tidied in the user's content, without AI and without changing anything:
 * duplicates written differently ("Postre" and "postres", "Airfryer" and "Freidora de aire") to
 * merge, names to write the same way as the rest (categories in plural with a capital letter...) and
 * the user's own entries nothing uses, to delete.
 *
 * The entries the app brings are never renamed or deleted; they can be what a duplicate of the
 * user's is merged into. Pure logic, so it can be unit tested.
 */
object ContentCleanup {

    /**
     * @param shoppingNames names on the shopping lists and in the shopping history, lower case:
     *   an ingredient written there is in use even when no recipe has it.
     */
    fun propose(content: Map<ContentKind, List<ContentItem>>, language: String, shoppingNames: Set<String> = emptySet()): List<CleanupProposal> =
        content.flatMap { (kind, items) -> proposeFor(kind, items, language, shoppingNames) }

    private fun proposeFor(kind: ContentKind, items: List<ContentItem>, language: String, shoppingNames: Set<String>): List<CleanupProposal> {
        val proposals = mutableListOf<CleanupProposal>()
        val handled = mutableSetOf<Long>()

        // 1. Duplicates: grouped, the user's ones go into the app's (or the most used) one.
        duplicateGroups(kind, items, language).forEach { group ->
            val target = group.sortedWith(compareByDescending<ContentItem> { it.isDefault }.thenByDescending { it.usage }.thenBy { it.name }).first()
            val sources = group.filter { it.id != target.id && !it.isDefault }
            if (sources.isEmpty()) return@forEach
            val finalName = if (target.isDefault) target.name else normalizedName(kind, target.name, language)
            proposals += CleanupProposal(kind, CleanupAction.MERGE, listOf(target) + sources, target = target, newName = finalName)
            handled += group.map { it.id }
        }

        items.filter { it.id !in handled && !it.isDefault }.forEach { item ->
            val unused = item.usage == 0 && !(kind == ContentKind.INGREDIENT && item.name.lowercase() in shoppingNames)
            if (unused) {
                // 2. The user's own entries nothing uses.
                proposals += CleanupProposal(kind, CleanupAction.DELETE, listOf(item))
            } else {
                // 3. Names written differently from the rest.
                val normalized = normalizedName(kind, item.name, language)
                if (normalized != item.name && items.none { it.id != item.id && it.name.equals(normalized, ignoreCase = true) }) {
                    proposals += CleanupProposal(kind, CleanupAction.RENAME, listOf(item), newName = normalized)
                }
            }
        }
        return proposals
    }

    /** Groups of two or more entries that are the same thing written differently. */
    internal fun duplicateGroups(kind: ContentKind, items: List<ContentItem>, language: String): List<List<ContentItem>> {
        val parent = IntArray(items.size) { it }
        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) x = parent[x]
            return x
        }
        val keys = items.map { comparisonKey(kind, it.name, language) }
        for (i in items.indices) for (j in i + 1 until items.size) {
            if (sameName(keys[i], keys[j])) parent[find(j)] = find(i)
        }
        return items.indices.groupBy { find(it) }.values.filter { it.size > 1 }.map { group -> group.map { items[it] } }
    }

    /** Lower case, without accents, punctuation or extra spaces; equipment by its default name. */
    internal fun comparisonKey(kind: ContentKind, name: String, language: String): String {
        val base = if (kind == ContentKind.EQUIPMENT) KitchenEquipment.canonical(name, language) else name
        return Normalizer.normalize(base.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
    }

    /** Same words, allowing singular and plural ("postre"/"postres", "flor"/"flores", "arroz"/"arroces"). */
    internal fun sameName(a: String, b: String): Boolean {
        if (a == b) return a.isNotEmpty()
        val wordsA = a.split(' ')
        val wordsB = b.split(' ')
        return wordsA.size == wordsB.size && wordsA.zip(wordsB).all { (x, y) -> sameWord(x, y) }
    }

    private fun sameWord(x: String, y: String): Boolean {
        if (x == y) return true
        val (short, long) = if (x.length <= y.length) x to y else y to x
        if (short.length < 3) return false
        return long == short + "s" || long == short + "es" ||
            (short.endsWith("z") && long == short.dropLast(1) + "ces") ||
            (short.endsWith("y") && long == short.dropLast(1) + "ies")
    }

    /**
     * How an entry of [kind] is written in Miga: extra spaces removed, a capital first letter and,
     * for categories, a single word in plural ("postre" -> "Postres"). Ingredients and tags keep
     * their words as written (only spaces and the first letter of ingredients change); equipment
     * takes its default name when it is a known variant.
     */
    fun normalizedName(kind: ContentKind, name: String, language: String): String {
        val clean = name.trim().replace(Regex("""\s+"""), " ")
        if (clean.isEmpty()) return clean
        return when (kind) {
            ContentKind.CATEGORY -> capitalized(if (' ' in clean) clean else plural(clean, language))
            ContentKind.EQUIPMENT -> capitalized(KitchenEquipment.canonical(clean, language))
            ContentKind.INGREDIENT, ContentKind.INGREDIENT_CATEGORY -> capitalized(clean)
            ContentKind.TAG -> clean
        }
    }

    private fun capitalized(text: String): String = text.replaceFirstChar { it.uppercase() }

    /**
     * Plural of a single word: Spanish "postre" -> "postres", "arroz" -> "arroces", "jamón" ->
     * "jamones"; English "dessert" -> "desserts", "dish" -> "dishes", "pastry" -> "pastries".
     * Words already in plural, with digits or in capitals (brands, acronyms) are kept.
     */
    internal fun plural(word: String, language: String): String {
        val lower = word.lowercase()
        if (lower.endsWith("s") || word.any { it.isDigit() } || (word.length > 1 && word == word.uppercase())) return word
        return if (language == "es") {
            when {
                lower.last() in "aeiouáéó" -> word + "s"
                lower.endsWith("z") -> word.dropLast(1) + "ces"
                lower.last() in "íú" -> word + "es"
                else -> {
                    // "jamón" -> "jamones": the stress moves, so the accent goes.
                    val accented = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u')
                    val index = word.length - 2
                    val stem = if (index >= 0 && word[index].lowercaseChar() in accented) {
                        word.substring(0, index) + accented.getValue(word[index].lowercaseChar()) + word.substring(index + 1)
                    } else {
                        word
                    }
                    stem + "es"
                }
            }
        } else {
            when {
                lower.endsWith("y") && lower.length > 1 && lower[lower.length - 2] !in "aeiou" -> word.dropLast(1) + "ies"
                lower.endsWith("x") || lower.endsWith("z") || lower.endsWith("ch") || lower.endsWith("sh") -> word + "es"
                else -> word + "s"
            }
        }
    }
}
