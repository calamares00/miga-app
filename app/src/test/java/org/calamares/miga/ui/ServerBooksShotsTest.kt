package org.calamares.miga.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.calamares.miga.MigaApp
import org.calamares.miga.data.local.entity.SyncConnectionEntity
import org.calamares.miga.data.model.RecipeBookDraft
import org.calamares.miga.data.sync.BookCatalogDto
import org.calamares.miga.data.sync.ServerBookDto
import org.calamares.miga.ui.list.TestMigaApp
import org.calamares.miga.ui.sync.ServerBooksScreen
import org.calamares.miga.ui.sync.ServerBooksViewModel
import org.calamares.miga.ui.theme.MigaTheme
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.net.InetSocketAddress

/** The server's books screen against a fake server, for review. Off in normal runs (MIGA_SHOTS_DIR). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class, qualifiers = "es-rES-w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ServerBooksShotsTest {

    @get:Rule
    val compose = createComposeRule()

    private val out: String? = System.getenv("MIGA_SHOTS_DIR")
    private val app: MigaApp = ApplicationProvider.getApplicationContext()
    private val json = Json { encodeDefaults = true }
    private val mb = 1024L * 1024

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/sync/books") { exchange ->
            val body = json.encodeToString(
                BookCatalogDto.serializer(),
                BookCatalogDto(
                    selective = true, followNewBooks = true,
                    books = listOf(
                        ServerBookDto("cenas", "Cenas rápidas", coverColor = "MUSTARD", coverEmoji = "🍳", recipeCount = 22, bytes = 18 * mb, createdBy = "Móvil de Luis", followed = true),
                        ServerBookDto("bebes", "Comida para bebés", coverColor = "BLUE", coverEmoji = "🍼", recipeCount = 12, bytes = 9 * mb, createdBy = "Móvil de Marta"),
                        ServerBookDto("navidad", "Navidad", coverColor = "WINE", coverEmoji = "🎄", recipeCount = 19, bytes = 27 * mb, createdBy = "Móvil de Marta"),
                        ServerBookDto("panes", "Panes y masas", coverColor = "GARDEN", coverEmoji = "🥖", recipeCount = 1, bytes = 2 * mb, createdBy = "Móvil de Luis"),
                        ServerBookDto("postres", "Postres", coverColor = "PINK", coverEmoji = "🍰", recipeCount = 31, bytes = 40 * mb, createdBy = "Tablet cocina", followed = true)
                    )
                )
            ).toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        start()
    }

    @After
    fun stop() = server.stop(0)

    @Test
    fun serverBooks() {
        assumeTrue(out != null)
        val connectionId = runBlocking {
            val id = app.database.syncConnectionDao().insert(
                SyncConnectionEntity(label = "Casa", serverUrl = "http://127.0.0.1:${server.address.port}", namespaceId = "casa", accessToken = "token", createdAt = 0)
            )
            listOf("cenas" to "Cenas rápidas", "postres" to "Postres").forEach { (uid, name) ->
                val bookId = app.repository.books.saveRecipeBook(RecipeBookDraft(name = name, coverPhotoUri = null))
                val book = app.database.recipeBookDao().getOnce(bookId)!!
                app.database.recipeBookDao().update(book.copy(uid = uid, syncConnectionId = id))
            }
            id
        }
        val viewModel = ServerBooksViewModel(app.repository, connectionId, firstTime = false, context = app)
        compose.setContent { MigaTheme(darkTheme = false) { Surface(Modifier.fillMaxSize()) { ServerBooksScreen(viewModel, onBack = {}) } } }
        compose.waitUntil(10_000) { !viewModel.state.value.loading }
        compose.waitForIdle()
        captureScreenRoboImage("$out/1_libros_del_servidor.png")
        compose.onNodeWithText("Navidad").performClick()
        compose.waitForIdle()
        captureScreenRoboImage("$out/2_marcar_navidad.png")
        compose.onNodeWithText("Cenas rápidas").performClick()
        compose.waitForIdle()
        captureScreenRoboImage("$out/3_quitar_cenas.png")
    }
}
