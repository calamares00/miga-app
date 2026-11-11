package org.calamares.miga.data.export

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.calamares.miga.data.export.IngredientDto
import org.calamares.miga.data.export.IngredientGroupDto
import org.calamares.miga.data.local.AppDatabase
import org.calamares.miga.data.local.entity.RecipeBookEntity
import org.calamares.miga.data.repository.RecipeRepository
import org.calamares.miga.data.repository.copyRecipeToBook
import org.calamares.miga.data.share.BookFileIntents
import org.calamares.miga.data.share.IncomingBookFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    private suspend fun shareFrom(repository: RecipeRepository, bookId: Long, sharedBy: String? = "Ana"): java.io.File {
        val book = repository.books.getRecipeBookOnce(bookId)!!
        val file = RecipeExporter.writeMigaFile(context, book.name, listOf(book), repository.getRecipesForBookOnce(bookId), singleRecipe = false, onPhoto = { _, _ -> }, forSharing = true, sharedBy = sharedBy)
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
        val receivedBook = receiverRepo.books.getRecipeBookOnce(first.bookId)!!
        assertEquals("Ana", receivedBook.sharedBy)
        assertNotNull(receivedBook.sharedAt)
        assertTrue("every recipe keeps who shared it", received.all { it.sharedBy == "Ana" })
        assertNull("the sender's own book is not marked", senderRepo.books.getRecipeBookOnce(bookId)!!.sharedBy)

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
        assertEquals(1, receiverRepo.books.getAllRecipeBooksOnce().size)
    }

    private suspend fun shareRecipe(repository: RecipeRepository, recipeId: Long, sharedBy: String?): java.io.File {
        val recipe = repository.observeRecipe(recipeId).first()!!
        return RecipeExporter.writeMigaFile(context, recipe.name, emptyList(), listOf(recipe), singleRecipe = true, onPhoto = { _, _ -> }, forSharing = true, sharedBy = sharedBy)
    }

    private suspend fun receiveRecipe(file: java.io.File): RecipeImportResult.Success {
        val result = RecipeExporter.importRecipe(context, Uri.fromFile(file))
        assertTrue("import: $result", result is RecipeImportResult.Success)
        return result as RecipeImportResult.Success
    }

    @Test
    fun `a shared recipe goes into an existing book or a new one, keeping who shared it`() = runBlocking {
        val senderRepo = RecipeRepository(sender)
        val receiverRepo = RecipeRepository(receiver)
        val senderBook = sender.recipeBookDao().insert(RecipeBookEntity(uid = "book-1", name = "Familia", coverPhotoUri = null, createdAt = 0L))
        val paellaId = senderRepo.saveRecipe(recipe("r1", "Paella", 1000).toDraft(senderBook))
        val tortillaId = senderRepo.saveRecipe(recipe("r2", "Tortilla", 1000).toDraft(senderBook))
        val mine = receiver.recipeBookDao().insert(RecipeBookEntity(uid = "mine", name = "Mis recetas", coverPhotoUri = null, createdAt = 0L))

        val paella = receiveRecipe(shareRecipe(senderRepo, paellaId, "Ana"))
        val intoExisting = RecipeExporter.importSharedRecipe(receiverRepo, paella.recipe, paella.photos, targetBookId = mine, newBookName = null)
        intoExisting as SharedRecipeImportResult.Success
        assertEquals(mine, intoExisting.bookId)
        assertEquals("Ana", receiverRepo.observeRecipe(intoExisting.recipeId).first()!!.sharedBy)
        assertNull("the book itself was not shared", receiverRepo.books.getRecipeBookOnce(mine)!!.sharedBy)

        val tortilla = receiveRecipe(shareRecipe(senderRepo, tortillaId, null))
        val intoNew = RecipeExporter.importSharedRecipe(receiverRepo, tortilla.recipe, tortilla.photos, targetBookId = null, newBookName = "De Ana")
        intoNew as SharedRecipeImportResult.Success
        assertEquals("De Ana", receiverRepo.books.getRecipeBookOnce(intoNew.bookId)!!.name)
        assertNull(receiverRepo.observeRecipe(intoNew.recipeId).first()!!.sharedBy)

        // Sent again unchanged: nothing is duplicated.
        val again = RecipeExporter.importSharedRecipe(receiverRepo, paella.recipe, paella.photos, targetBookId = null, newBookName = "Otro")
        again as SharedRecipeImportResult.Success
        assertTrue(again.unchanged)
        assertEquals(intoExisting.recipeId, again.recipeId)
        assertEquals(2, receiverRepo.books.getAllRecipeBooksOnce().size)
    }

    @Test
    fun `several books shared together are imported as separate books`() = runBlocking {
        val senderRepo = RecipeRepository(sender)
        val receiverRepo = RecipeRepository(receiver)
        val familia = sender.recipeBookDao().insert(RecipeBookEntity(uid = "book-1", name = "Familia", coverPhotoUri = null, createdAt = 0L))
        val postres = sender.recipeBookDao().insert(RecipeBookEntity(uid = "book-2", name = "Postres", coverPhotoUri = null, createdAt = 0L))
        senderRepo.saveRecipe(recipe("r1", "Paella", 1000).toDraft(familia))
        senderRepo.saveRecipe(recipe("r2", "Flan", 1000).copy(recipeBookName = "Postres").toDraft(postres))
        senderRepo.saveRecipe(recipe("r3", "Natillas", 1000).copy(recipeBookName = "Postres").toDraft(postres))

        val books = listOf(senderRepo.books.getRecipeBookOnce(familia)!!, senderRepo.books.getRecipeBookOnce(postres)!!)
        val recipes = senderRepo.getRecipesForBookOnce(familia) + senderRepo.getRecipesForBookOnce(postres)
        val file = RecipeExporter.writeMigaFile(context, "2 libros", books, recipes, singleRecipe = false, onPhoto = { _, _ -> }, forSharing = true, sharedBy = "Ana")

        val result = RecipeExporter.importSharedBook(context, receive(file), receiverRepo)
        result as SharedBookImportResult.Success
        assertEquals(2, result.bookCount)
        assertEquals(3, result.added)
        val received = receiverRepo.books.getAllRecipeBooksOnce().associateBy { it.name }
        assertEquals(setOf("Familia", "Postres"), received.keys)
        assertEquals(setOf("Paella"), receiverRepo.getRecipesForBookOnce(received.getValue("Familia").id).map { it.name }.toSet())
        assertEquals(setOf("Flan", "Natillas"), receiverRepo.getRecipesForBookOnce(received.getValue("Postres").id).map { it.name }.toSet())
        assertTrue(received.values.all { it.sharedBy == "Ana" })
    }

    @Test
    fun `pack recipes cannot be moved out of the pack, only copied`() = runBlocking {
        val repo = RecipeRepository(receiver)
        val pack = receiver.recipeBookDao().insert(RecipeBookEntity(uid = "pack", name = "Pack", coverPhotoUri = null, createdAt = 0L, packId = "arroces"))
        val mine = receiver.recipeBookDao().insert(RecipeBookEntity(uid = "mine", name = "Mías", coverPhotoUri = null, createdAt = 0L))
        // Saving into a pack is blocked too, so the recipe is put there directly, as a pack install does.
        val recipeId = repo.saveRecipe(recipe("p1", "Arroz negro", 1000).toDraft(mine))
        receiver.recipeDao().updateRecipeBook(recipeId, pack)

        repo.moveRecipeToBook(recipeId, mine)
        assertEquals(pack, repo.observeRecipe(recipeId).first()!!.recipeBookId)

        val copyId = repo.copyRecipeToBook(context, repo.observeRecipe(recipeId).first()!!, mine)
        assertEquals(mine, repo.observeRecipe(copyId).first()!!.recipeBookId)
        assertEquals(pack, repo.observeRecipe(recipeId).first()!!.recipeBookId)
    }
}
