package org.calamares.miga.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the read-only rule of packs: isPack is derived from packId and never stored separately.
 */
class RecipeBookTest {

    @Test
    fun `a book without packId is not a pack`() {
        val book = RecipeBook(id = 1L, uid = "u", name = "Mi libro", coverPhotoUri = null)
        assertFalse(book.isPack)
    }

    @Test
    fun `a book with packId is a read-only pack`() {
        val book = RecipeBook(id = 1L, uid = "u", name = "Pack", coverPhotoUri = null, packId = "pack-1", packVersion = 2)
        assertTrue(book.isPack)
    }

    @Test
    fun `the summary mirrors the same isPack rule`() {
        val ownBook = RecipeBookSummary(id = 1L, name = "Mío", coverPhotoUri = null, recipeCount = 3)
        val packBook = RecipeBookSummary(id = 2L, name = "Pack", coverPhotoUri = null, recipeCount = 3, packId = "pack-1")
        assertFalse(ownBook.isPack)
        assertTrue(packBook.isPack)
    }
}
