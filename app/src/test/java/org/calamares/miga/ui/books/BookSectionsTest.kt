package org.calamares.miga.ui.books

import org.calamares.miga.data.model.RecipeBookSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSectionsTest {

    private val labels = BookSectionLabels(myBooks = "Mine", server = "Server", packs = "Packs", otherPacks = "Other packs")

    private fun own(id: Long) = RecipeBookSummary(id, "own $id", null, 0)
    private fun synced(id: Long, connection: Long) = RecipeBookSummary(id, "synced $id", null, 0, syncConnectionId = connection)
    private fun pack(id: Long, series: String?) =
        RecipeBookSummary(id, "pack $id", null, 0, packId = "pack-$id", packVersion = 1, packSeries = series)

    private val books = listOf(
        pack(1, "World cuisine"), own(2), synced(3, 10), pack(4, "Specials"), pack(5, "World cuisine"),
        pack(6, null), own(7)
    )

    private fun titles(sections: List<BookSection>) = sections.map { it.title to it.books.map { book -> book.id } }

    @Test
    fun `without grouping the filter keeps the original order`() {
        assertEquals(
            listOf(null to listOf(1L, 4L, 5L, 6L)),
            titles(bookSections(books, BookFilter.PACKS, grouped = false, emptyMap(), labels))
        )
        assertEquals(
            listOf(null to listOf(2L, 7L)),
            titles(bookSections(books, BookFilter.LOCAL, grouped = false, emptyMap(), labels))
        )
        assertEquals(
            listOf(null to listOf(3L)),
            titles(bookSections(books, BookFilter.SERVER, grouped = false, emptyMap(), labels))
        )
    }

    @Test
    fun `grouped lists own books, server books and packs by series, largest series first`() {
        assertEquals(
            listOf(
                "Mine" to listOf(2L, 7L),
                "Server" to listOf(3L),
                "Packs" to emptyList(),
                "World cuisine" to listOf(1L, 5L),
                "Specials" to listOf(4L),
                "Other packs" to listOf(6L)
            ),
            titles(bookSections(books, BookFilter.ALL, grouped = true, emptyMap(), labels))
        )
    }

    @Test
    fun `several connections get a subgroup each`() {
        val sections = bookSections(
            listOf(synced(1, 10), synced(2, 20), synced(3, 10)), BookFilter.ALL, grouped = true,
            mapOf(10L to "Home", 20L to "Family"), labels
        )
        assertEquals(
            listOf("Server" to emptyList(), "Family" to listOf(2L), "Home" to listOf(1L, 3L)),
            titles(sections)
        )
        assertEquals(listOf(0, 1, 1), sections.map { it.level })
    }

    @Test
    fun `a filter with no books gives no sections`() {
        assertTrue(bookSections(listOf(own(1)), BookFilter.PACKS, grouped = true, emptyMap(), labels).isEmpty())
        assertTrue(bookSections(listOf(own(1)), BookFilter.SERVER, grouped = false, emptyMap(), labels).isEmpty())
    }
}
