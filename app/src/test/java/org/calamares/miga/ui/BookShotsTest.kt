package org.calamares.miga.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.calamares.miga.L10n
import org.calamares.miga.MigaApp
import org.calamares.miga.R
import org.calamares.miga.data.model.RecipeBookSummary
import org.calamares.miga.ui.books.BookDedication
import org.calamares.miga.ui.books.RecipeBookCard
import org.calamares.miga.ui.books.RecipeBookEditorScreen
import org.calamares.miga.ui.books.RecipeBookEditorViewModel
import org.calamares.miga.ui.list.TestMigaApp
import org.calamares.miga.ui.navigation.Destinations
import org.calamares.miga.ui.theme.MigaTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The book editor, the covers in the library and a book's dedication, for review. Off in normal
 * runs; run recordRoborazziDebug with MIGA_SHOTS_DIR set.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class, qualifiers = "es-rES-w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BookShotsTest {

    @get:Rule
    val compose = createComposeRule()

    private val out: String? = System.getenv("MIGA_SHOTS_DIR")
    private val app: MigaApp = ApplicationProvider.getApplicationContext()

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(700)
        captureScreenRoboImage("$out/$name.png")
    }

    @Test
    fun editor() {
        assumeTrue(out != null)
        val viewModel = RecipeBookEditorViewModel(app.repository, Destinations.NEW_BOOK_ID)
        var dark by mutableStateOf(false)
        compose.setContent { MigaTheme(darkTheme = dark) { Surface(Modifier.fillMaxSize()) { RecipeBookEditorScreen(viewModel, {}, {}) } } }
        shot("1_new_empty")
        viewModel.name = "Postres de la abuela"
        viewModel.author = "Abuela Carmen"
        viewModel.description = "Las recetas que me enseñó mi madre, para que no se pierdan."
        viewModel.coverColor = "WINE"
        viewModel.coverEmoji = "🍰"
        shot("2_new_filled")
        compose.onAllNodesWithText("🎄").onFirst().performScrollTo()
        shot("3_new_style")
        dark = true
        shot("4_dark")
    }

    @Test
    fun library() {
        assumeTrue(out != null)
        val books = listOf(
            RecipeBookSummary(1, "Postres de la abuela", null, 24, author = "Abuela Carmen", coverColor = "WINE", coverEmoji = "🍰"),
            RecipeBookSummary(2, "De diario", null, 41, coverColor = "GARDEN", coverEmoji = "🥘"),
            RecipeBookSummary(3, "Navidad", null, 12, author = "Familia García", coverColor = "TERRACOTTA", coverEmoji = "🎄"),
            RecipeBookSummary(4, "Cenas rápidas", null, 18, coverEmoji = "🍳"),
            RecipeBookSummary(5, "Panes y masas", null, 9, coverColor = "MUSTARD", coverEmoji = "🍞"),
            RecipeBookSummary(6, "Mi libro", null, 3)
        )
        var page by mutableStateOf(0)
        compose.setContent {
            MigaTheme(darkTheme = false) {
                Surface(Modifier.fillMaxSize()) {
                    if (page == 0) {
                        LazyVerticalGrid(GridCells.Fixed(2), Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(books) { RecipeBookCard(it, selectionMode = false, isSelected = false, onClick = {}, onLongClick = {}) }
                        }
                    } else {
                        Column(Modifier.padding(16.dp)) {
                            Text("Postres de la abuela", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
                            BookDedication("Las recetas que me enseñó mi madre, para que no se pierdan.", "Abuela Carmen")
                        }
                    }
                }
            }
        }
        shot("5_library")
        page = 1
        shot("6_dedication")
    }
}
