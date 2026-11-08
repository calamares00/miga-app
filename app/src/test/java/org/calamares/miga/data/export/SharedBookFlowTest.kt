package org.calamares.miga.data.export

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.calamares.miga.data.export.IngredientDto
import org.calamares.miga.data.export.IngredientGroupDto
import org.calamares.miga.data.local.AppDatabase
import org.calamares.miga.data.local.entity.RecipeBookEntity
import org.calamares.miga.data.repository.RecipeRepository
import org.calamares.miga.data.share.BookFileIntents
import org.calamares.miga.data.share.IncomingBookFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** "Share book" on one phone, opened from another app and merged on a second phone. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class SharedBookFlowTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var sender: AppDatabase
    private lateinit var receiver: AppDatabase

    private fun db() = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

    @Before
    fun setUp() {
        sender = db()
        receiver = db()
    }

    @After
    fun tearDown() {
        sender.close()
        receiver.close()
    }

    private fun recipe(uid: String, name: String, updatedAt: Long) = RecipeExportDto(
        uid = uid, name = name, recipeBookName = "Familia", categoryName = null, difficulty = "EASY",
        prepTimeMinutes = 10, cookTimeMinutes = 20, servings = 4, notes = "", source = "", isFavorite = true,
        ingredientGroups = listOf(IngredientGroupDto(null, listOf(IngredientDto("Arroz", 300.0, "g")))),
        stepGroups = emptyList(), tags = emptyList(), utensils = emptyList(), updatedAt = updatedAt
    )

    private suspend fun shareFrom(repository: RecipeRepository, bookId: Long): java.io.File {
        val book = repository.getRecipeBookOnce(bookId)!!
        val file = RecipeExporter.writeMigaFile(context, book.name, book, repository.getRecipesForBookOnce(bookId), singleRecipe = false, onPhoto = { _, _ -> }, forSharing = true)
        assertTrue(file.name.endsWith(".miga"))
        return file
    }

    /** What MainActivity does with "Open with Miga" from WhatsApp, then the books screen. */
    private suspend fun receive(file: java.io.File, type: String = "application/zip"): LibraryImportParseResult.Success {
        BookFileIntents.consume()
        assertTrue(BookFileIntents.handle(context, Intent(Intent.ACTION_VIEW).setDataAndType(Uri.fromFile(file), type)))
        val event = withTimeout(5000) {
            while (BookFileIntents.event.value == null) delay(20)
            BookFileIntents.event.value!!
        }
        BookFileIntents.consume()
        val copy = (event as IncomingBookFile.Ready).file
        val parsed = RecipeExporter.parseLibraryImport(context, Uri.fromFile(copy))
        assertTrue("parse: $parsed", parsed is LibraryImportParseResult.Success)
        return parsed as LibraryImportParseResult.Success
    }

    @Test
    fun `a shared book is imported, then updated when sent again`() = runBlocking {
        val senderRepo = RecipeRepository(sender)
        val receiverRepo = RecipeRepository(receiver)
        val bookId = sender.recipeBookDao().insert(RecipeBookEntity(uid = "book-1", name = "Familia", coverPhotoUri = null, createdAt = 0L))
        senderRepo.saveRecipe(recipe("r1", "Paella", 1000).toDraft(bookId))
        senderRepo.saveRecipe(recipe("r2", "Tortilla", 1000).toDraft(bookId))

        val first = RecipeExporter.importSharedBook(context, receive(shareFrom(senderRepo, bookId)), receiverRepo)
        assertTrue("import: $first", first is SharedBookImportResult.Success)
        first as SharedBookImportResult.Success
        assertEquals(2, first.added)
        val received = receiverRepo.getRecipesForBookOnce(first.bookId)
        assertEquals(setOf("Paella", "Tortilla"), received.map { it.name }.toSet())
        assertTrue("favourites are not shared", received.none { it.isFavorite })

        // The sender edits one recipe and adds another, then sends the book again.
        Thread.sleep(5)
        val paella = senderRepo.getRecipesForBookOnce(bookId).first { it.name == "Paella" }
        senderRepo.saveRecipe(recipe("r1", "Paella valenciana", 0).toDraft(bookId).copy(id = paella.id))
        senderRepo.saveRecipe(recipe("r3", "Gazpacho", 0).toDraft(bookId))
        val second = RecipeExporter.importSharedBook(context, receive(shareFrom(senderRepo, bookId), "application/octet-stream"), receiverRepo)
        second as SharedBookImportResult.Success
        assertEquals(first.bookId, second.bookId)
        assertEquals(1, second.added)
        assertEquals(1, second.updated)
        assertEquals(setOf("Paella valenciana", "Tortilla", "Gazpacho"), receiverRepo.getRecipesForBookOnce(first.bookId).map { it.name }.toSet())
        assertEquals(1, receiverRepo.getAllRecipeBooksOnce().size)
    }
}
