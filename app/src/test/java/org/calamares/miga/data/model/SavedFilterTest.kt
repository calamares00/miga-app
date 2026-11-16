package org.calamares.miga.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedFilterTest {

    @Test
    fun `a saved search keeps the sheet's conditions but not the statistics ones`() {
        val filter = RecipeFilter(
            query = " tortilla ",
            categoryNames = setOf("Cenas"),
            difficulties = setOf(Difficulty.EASY),
            ingredients = setOf("huevos"),
            onlyFavorites = true,
            sortOption = SortOption.RECENT,
            maxMinutes = 30
        )
        val saved = SavedFilter.from("Cenas rápidas", filter)
        val restored = SavedFilter.decode(SavedFilter.encode(listOf(saved))).single().toFilter()
        assertEquals(filter.copy(query = "tortilla", maxMinutes = null), restored)
    }

    @Test
    fun `broken stored text reads as no saved searches`() {
        assertTrue(SavedFilter.decode("{not json").isEmpty())
        assertTrue(SavedFilter.decode(null).isEmpty())
    }
}
