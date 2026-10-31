package org.calamares.miga.ui.books

import org.calamares.miga.data.model.RecipeBookSummary

/** Which books the list shows: every book, installed packs, the user's own or synced ones. */
enum class BookFilter { ALL, PACKS, LOCAL, SERVER }

fun RecipeBookSummary.matches(filter: BookFilter): Boolean = when (filter) {
    BookFilter.ALL -> true
    BookFilter.PACKS -> isPack
    BookFilter.LOCAL -> !isPack && !isSynced
    BookFilter.SERVER -> isSynced
}

/**
 * A run of books under a header in the books list. [title] is null for a list that is not
 * grouped. [level] 0 is a main group (my books, server, packs); 1 a group inside it (a pack series,
 * a sync connection). A main group with subgroups has no books of its own.
 */
data class BookSection(val key: String, val title: String?, val level: Int, val books: List<RecipeBookSummary>)

/** Texts of the group headers, passed in so that [bookSections] does not depend on resources. */
data class BookSectionLabels(
    val myBooks: String,
    val server: String,
    val packs: String,
    val otherPacks: String
)

/**
 * Splits [books] (already sorted, pinned first) into the sections of the books list. Without
 * [grouped], one untitled section with the books matching [filter]. Grouped: the user's own books,
 * then those on a server (one subgroup per connection when there are several, named from
 * [connectionNames]), then installed packs, one subgroup per series (the largest first, packs
 * without a series last). Empty groups are left out. Order inside every group is kept.
 */
fun bookSections(
    books: List<RecipeBookSummary>,
    filter: BookFilter,
    grouped: Boolean,
    connectionNames: Map<Long, String>,
    labels: BookSectionLabels
): List<BookSection> {
    val visible = books.filter { it.matches(filter) }
    if (!grouped) return if (visible.isEmpty()) emptyList() else listOf(BookSection("all", null, 0, visible))

    val sections = mutableListOf<BookSection>()

    val local = visible.filter { it.matches(BookFilter.LOCAL) }
    if (local.isNotEmpty()) sections += BookSection("local", labels.myBooks, 0, local)

    val synced = visible.filter { it.isSynced }
    if (synced.isNotEmpty()) {
        val byConnection = synced.groupBy { it.syncConnectionId }
        if (byConnection.size == 1) {
            sections += BookSection("server", labels.server, 0, synced)
        } else {
            sections += BookSection("server", labels.server, 0, emptyList())
            byConnection.entries
                .sortedBy { (id, _) -> connectionNames[id]?.lowercase() ?: "" }
                .forEach { (id, group) ->
                    sections += BookSection("server/$id", connectionNames[id] ?: labels.server, 1, group)
                }
        }
    }

    val packs = visible.filter { it.isPack }
    if (packs.isNotEmpty()) {
        sections += BookSection("packs", labels.packs, 0, emptyList())
        packs.groupBy { it.packSeries?.takeIf { series -> series.isNotBlank() } }
            .entries
            .sortedWith(compareBy({ it.key == null }, { -it.value.size }, { it.key?.lowercase() }))
            .forEach { (series, group) ->
                sections += BookSection("packs/${series.orEmpty()}", series ?: labels.otherPacks, 1, group)
            }
    }
    return sections
}
