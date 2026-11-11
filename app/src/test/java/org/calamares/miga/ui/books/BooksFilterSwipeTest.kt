package org.calamares.miga.ui.books

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.calamares.miga.MigaApp
import org.calamares.miga.data.local.entity.RecipeBookEntity
import org.calamares.miga.ui.list.TestMigaApp
import org.calamares.miga.ui.theme.MigaTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A horizontal swipe on the books moves between the filters, and stops at the first and last. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class, qualifiers = "w411dp-h891dp-420dpi")
class BooksFilterSwipeTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: MigaApp = ApplicationProvider.getApplicationContext()
    private lateinit var viewModel: RecipeBooksViewModel

    @Before
    fun setUp() {
        runBlocking {
            app.database.recipeBookDao().insert(RecipeBookEntity(uid = "mine", name = "Familia", coverPhotoUri = null, createdAt = 0L))
            app.database.recipeBookDao().insert(RecipeBookEntity(uid = "pack", name = "Arroces", coverPhotoUri = null, createdAt = 0L, packId = "arroces"))
        }
        viewModel = RecipeBooksViewModel(app.repository, app.settingsRepository)
        compose.setContent {
            MigaTheme(darkTheme = false) {
                RecipeBooksScreen(viewModel = viewModel, onBookClick = {}, onAddBookClick = {}, onEditBookClick = {})
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Familia")).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun swipe(left: Boolean) {
        // The screen's own window: a first-run dialog (what's new) may be open in another one.
        compose.onAllNodes(isRoot())[0].performTouchInput { if (left) swipeLeft() else swipeRight() }
        compose.waitForIdle()
    }

    @Test
    fun `swiping walks the filters in order and stops at both ends`() {
        swipe(left = false)
        assertEquals(BookFilter.ALL, viewModel.filter.value)

        val order = BookFilter.entries.drop(1)
        order.forEach { expected ->
            swipe(left = true)
            assertEquals(expected, viewModel.filter.value)
        }
        // Past the last one nothing changes (the list only stretches).
        swipe(left = true)
        assertEquals(BookFilter.entries.last(), viewModel.filter.value)

        swipe(left = false)
        assertEquals(BookFilter.entries[BookFilter.entries.size - 2], viewModel.filter.value)
    }

    @Test
    fun `tapping a filter chip moves the pages too`() {
        viewModel.setFilter(BookFilter.PACKS)
        compose.waitForIdle()
        swipe(left = false)
        assertEquals(BookFilter.LOCAL, viewModel.filter.value)
    }
}
