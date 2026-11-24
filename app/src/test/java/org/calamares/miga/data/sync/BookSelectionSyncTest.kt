package org.calamares.miga.data.sync

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.calamares.miga.MigaApp
import org.calamares.miga.data.local.entity.SyncChangeType
import org.calamares.miga.data.local.entity.SyncConnectionEntity
import org.calamares.miga.data.model.RecipeBookDraft
import org.calamares.miga.data.model.emptyRecipeDraft
import org.calamares.miga.ui.list.TestMigaApp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.util.Collections

/**
 * Choosing which server books this phone syncs (miga-server 1.3.0) against a small fake server:
 * removing a synced book only removes it here and unfollows it, never deletes it on the server;
 * a followed book is downloaded whole; older servers are left alone.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class)
class BookSelectionSyncTest {

    private val app: MigaApp = ApplicationProvider.getApplicationContext()
    private val repository = app.repository
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Requests received, as "METHOD /path". */
    private val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private var selectionBody: String? = null
    private var supportsSelection = true
    private var followed = mutableListOf("postres", "abuela")

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            val path = exchange.requestURI.path
            requests += "${exchange.requestMethod} $path"
            val body = exchange.requestBody.readBytes().decodeToString()
            val (code, answer) = when {
                path == "/sync/books" && exchange.requestMethod == "GET" ->
                    if (supportsSelection) 200 to catalog() else 404 to "{}"
                path == "/sync/selection" -> {
                    selectionBody = body
                    followed = json.decodeFromString(BookSelectionRequest.serializer(), body).books.toMutableList()
                    200 to catalog()
                }
                path == "/sync/books/navidad/content" -> 200 to json.encodeToString(
                    BookContentDto.serializer(),
                    BookContentDto(
                        BookSyncDto("navidad", "Navidad", updatedAt = 1000, coverEmoji = "🎄"),
                        listOf(RecipeSyncDto(uid = "turron", bookUid = "navidad", name = "Turrón", difficulty = "EASY", servings = 8, updatedAt = 1000))
                    )
                )
                path.startsWith("/sync/changes") -> 200 to """{"latestRevision":0}"""
                path.startsWith("/sync/books/") && exchange.requestMethod == "PUT" -> 200 to """{"revision":1}"""
                path.startsWith("/sync/recipes/") && exchange.requestMethod == "PUT" -> 200 to """{"revision":1}"""
                else -> 404 to "{}"
            }
            val bytes = answer.toByteArray()
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        start()
    }

    private fun catalog(): String = json.encodeToString(
        BookCatalogDto.serializer(),
        BookCatalogDto(
            selective = true,
            followNewBooks = true,
            books = (listOf("abuela", "navidad", "postres") + followed).distinct().map { ServerBookDto(it, it, followed = it in followed) }
        )
    )

    @After
    fun stop() = server.stop(0)

    // Straight into the database: Robolectric has no Android Keystore to encrypt the token, and a
    // plain token is read as one (rows from before encryption).
    private suspend fun connection(): Long = app.database.syncConnectionDao().insert(
        SyncConnectionEntity(label = "Casa", serverUrl = "http://127.0.0.1:${server.address.port}", namespaceId = "casa", accessToken = "token", createdAt = 0)
    )

    private suspend fun syncedBook(connectionId: Long, name: String, recipes: List<String>): Long {
        val bookId = repository.books.saveRecipeBook(RecipeBookDraft(name = name, coverPhotoUri = null))
        recipes.forEach { repository.saveRecipe(emptyRecipeDraft(bookId).copy(name = it)) }
        repository.sync.linkBookToSyncConnection(bookId, connectionId)
        return bookId
    }

    @Test
    fun `deleting a synced book removes it from this phone only and unfollows it`() = runBlocking {
        val connectionId = connection()
        val bookId = syncedBook(connectionId, "Postres", listOf("Flan", "Natillas"))
        val uid = repository.books.getRecipeBookOnce(bookId)!!.uid
        followed = mutableListOf(uid, "abuela")

        // A synced book with recipes can be removed: it is not deleted, it leaves this phone.
        repository.books.deleteRecipeBook(bookId)
        assertNull(repository.books.getRecipeBookOnce(bookId))
        assertTrue(repository.getAllRecipesOnce().none { it.recipeBookId == bookId })
        val pending = repository.sync.getPendingSyncChanges(connectionId)
        assertEquals(listOf(SyncChangeType.UNFOLLOW.name), pending.map { it.changeType })

        SyncEngine(repository).syncConnection(app, connectionId)
        assertTrue("nothing is deleted on the server", requests.none { it.startsWith("DELETE") })
        assertEquals(listOf("abuela"), followed)
        assertTrue(repository.sync.getPendingSyncChanges(connectionId).isEmpty())
    }

    @Test
    fun `an older server cannot choose books, and still nothing is deleted on it`() = runBlocking {
        supportsSelection = false
        val connectionId = connection()
        val bookId = syncedBook(connectionId, "Postres", listOf("Flan"))
        repository.books.deleteRecipeBook(bookId)
        SyncEngine(repository).syncConnection(app, connectionId)
        assertTrue(requests.none { it.startsWith("DELETE") || it.startsWith("PUT /sync/selection") })
        assertTrue(repository.sync.getPendingSyncChanges(connectionId).isEmpty())
    }

    @Test
    fun `a book of one's own that is not synced still has to be empty`() = runBlocking {
        val bookId = repository.books.saveRecipeBook(RecipeBookDraft(name = "Mío", coverPhotoUri = null))
        repository.saveRecipe(emptyRecipeDraft(bookId).copy(name = "Tortilla"))
        val error = runCatching { repository.books.deleteRecipeBook(bookId) }.exceptionOrNull()
        assertTrue(error is org.calamares.miga.data.repository.RecipeBookNotEmptyException)
        assertNotNull(repository.books.getRecipeBookOnce(bookId))
    }

    @Test
    fun `a book started to follow is downloaded whole and linked`() = runBlocking {
        val connectionId = connection()
        assertEquals(0, SyncEngine(repository).downloadBooks(app, connectionId, listOf("navidad")))
        val book = repository.books.observeRecipeBooks().first().single { it.name == "Navidad" }
        assertEquals(connectionId, book.syncConnectionId)
        assertEquals("🎄", book.coverEmoji)
        assertEquals(listOf("Turrón"), repository.getRecipesForBookOnce(book.id).map { it.name })
    }

    @Test
    fun `a book left out can stay as a local copy`() = runBlocking {
        val connectionId = connection()
        val bookId = syncedBook(connectionId, "Postres", listOf("Flan"))
        repository.sync.keepSyncedBookAsLocal(bookId, unfollow = false)
        val book = repository.books.getRecipeBookOnce(bookId)!!
        assertNull(book.syncConnectionId)
        assertEquals(1, repository.getRecipesForBookOnce(bookId).size)
        assertFalse(repository.sync.getPendingSyncChanges(connectionId).any { it.changeType == SyncChangeType.UNFOLLOW.name })
        assertTrue(repository.sync.syncedBooks(connectionId).isEmpty())
    }
}
