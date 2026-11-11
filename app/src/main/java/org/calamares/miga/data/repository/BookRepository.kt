package org.calamares.miga.data.repository

import androidx.room.withTransaction
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.export.RecipeExportDto
import org.calamares.miga.data.local.AppDatabase
import org.calamares.miga.data.local.PhotoStorage
import org.calamares.miga.data.local.entity.IngredientEntity
import org.calamares.miga.data.local.entity.RecipeBookEntity
import org.calamares.miga.data.local.entity.RecipeEntity
import org.calamares.miga.data.local.entity.RecipePhotoEntity
import org.calamares.miga.data.local.entity.RecipeTagCrossRef
import org.calamares.miga.data.local.entity.RecipeUtensilCrossRef
import org.calamares.miga.data.local.entity.StepEntity
import org.calamares.miga.data.local.entity.SyncChangeType
import org.calamares.miga.data.local.entity.SyncEntityType
import org.calamares.miga.data.model.KitchenEquipment
import org.calamares.miga.data.model.RecipeOrigin
import org.calamares.miga.data.model.Difficulty
import org.calamares.miga.data.model.HealthColorLevel
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.RecipeBook
import org.calamares.miga.data.model.RecipeBookDraft
import org.calamares.miga.data.model.RecipeBookSummary
import org.calamares.miga.data.model.RecipePhoto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Recipe books and the downloadable packs installed as books. Reached through
 * [RecipeRepository.books].
 */
class BookRepository internal constructor(
    private val db: AppDatabase,
    private val recipeRepository: RecipeRepository,
    private val sync: SyncRepository,
    private val onSyncChangeEnqueued: (connectionId: Long) -> Unit
) {

    private val recipeDao = db.recipeDao()
    private val recipeBookDao = db.recipeBookDao()

    // --- Recipe books ---

    fun observeRecipeBooks(): Flow<List<RecipeBookSummary>> =
        recipeBookDao.observeAllWithCounts().map { list ->
            list.map {
                RecipeBookSummary(
                    it.book.id, it.book.name, it.book.coverPhotoUri, it.recipeCount,
                    it.book.packId, it.book.packVersion, it.book.syncConnectionId, it.book.isPinned,
                    it.book.packSeries, it.book.sharedBy
                )
            }
        }

    /** Pins a book to the top of the list or unpins it; does not count as a change for sync. */
    suspend fun setRecipeBookPinned(id: Long, pinned: Boolean) = recipeBookDao.setPinned(id, pinned)

    fun observeRecipeBook(id: Long): Flow<RecipeBook?> =
        recipeBookDao.observeOne(id).map { it?.toDomain() }

    suspend fun getRecipeBookOnce(id: Long): RecipeBook? =
        recipeBookDao.getOnce(id)?.toDomain()

    /** Every book, used when exporting the whole library (to include the covers in the ZIP). */
    suspend fun getAllRecipeBooksOnce(): List<RecipeBook> =
        recipeBookDao.observeAllWithCounts().first().map { it.book.toDomain() }

    suspend fun findRecipeBookByPackId(packId: String): RecipeBook? =
        recipeBookDao.findByPackId(packId)?.toDomain()

    /**
     * Stores the series the catalogue gives for each installed pack ([seriesByPackId]), so packs
     * installed before series existed, or moved to another series, are grouped right. Only called
     * with a catalogue the user opened: the app never fetches it in the background.
     */
    suspend fun updatePackSeries(seriesByPackId: Map<String, String?>) {
        seriesByPackId.forEach { (packId, series) ->
            val book = recipeBookDao.findByPackId(packId) ?: return@forEach
            if (series != null && book.packSeries != series) recipeBookDao.update(book.copy(packSeries = series))
        }
    }

    /** Uninstalls the pack [packId] if it is installed (see [uninstallPack]); false if it is not. */
    suspend fun uninstallPackById(packId: String): Boolean {
        val book = recipeBookDao.findByPackId(packId) ?: return false
        uninstallPack(book.id)
        return true
    }

    /**
     * Safeguard: pack books are read-only (see [RecipeBook.isPack]). The UI already blocks editing
     * them; this keeps a pack from being overwritten if anything bypasses it.
     */
    suspend fun saveRecipeBook(draft: RecipeBookDraft): Long {
        if (draft.id != 0L && recipeBookDao.getOnce(draft.id)?.packId != null) return draft.id
        val now = System.currentTimeMillis()
        return if (draft.id == 0L) {
            recipeBookDao.insert(
                RecipeBookEntity(
                    uid = draft.uid ?: UUID.randomUUID().toString(),
                    name = draft.name.trim(),
                    coverPhotoUri = draft.coverPhotoUri,
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            val existing = recipeBookDao.getOnce(draft.id)
            val uid = existing?.uid ?: draft.uid ?: UUID.randomUUID().toString()
            recipeBookDao.update(
                RecipeBookEntity(
                    id = draft.id,
                    uid = uid,
                    name = draft.name.trim(),
                    coverPhotoUri = draft.coverPhotoUri,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                    syncConnectionId = existing?.syncConnectionId,
                    isPinned = existing?.isPinned ?: false,
                    packSeries = existing?.packSeries,
                    sharedBy = existing?.sharedBy,
                    sharedAt = existing?.sharedAt
                )
            )
            existing?.syncConnectionId?.let {
                sync.enqueueSyncChange(it, SyncEntityType.BOOK, uid, SyncChangeType.UPSERT)
                onSyncChangeEnqueued(it)
            }
            draft.id
        }
    }

    /** Throws [RecipeBookNotEmptyException] when the book still contains recipes. */
    suspend fun deleteRecipeBook(id: Long) {
        val count = recipeBookDao.countRecipes(id)
        if (count > 0) throw RecipeBookNotEmptyException(count)
        val book = recipeBookDao.getOnce(id)
        recipeBookDao.delete(id)
        book?.syncConnectionId?.let {
            sync.enqueueSyncChange(it, SyncEntityType.BOOK, book.uid, SyncChangeType.DELETE)
            onSyncChangeEnqueued(it)
        }
    }

    /**
     * Deletes all of the user's own books (not installed read-only packs) together with their
     * recipes, covers and recipe photos. Used to start clean right before restoring a full backup.
     */
    suspend fun wipeUserRecipesAndBooks(): WipeResult {
        val ownBooks = getAllRecipeBooksOnce().filter { !it.isPack }
        val ownBookIds = ownBooks.map { it.id }.toSet()
        val ownRecipes = recipeRepository.getAllRecipesOnce().filter { it.recipeBookId in ownBookIds }
        val photoUris = ownRecipes.flatMap { recipe -> recipe.photos.map { it.uri } } + ownBooks.mapNotNull { it.coverPhotoUri }

        db.withTransaction {
            ownBookIds.forEach { bookId ->
                recipeDao.deleteAllForBook(bookId)
                recipeBookDao.delete(bookId)
            }
        }
        photoUris.forEach { PhotoStorage.deleteFile(it) }
        return WipeResult(bookCount = ownBooks.size, recipeCount = ownRecipes.size)
    }

    /**
     * Used when importing a backup: finds a book by name or creates it. [uid] and [coverPhotoUri]
     * only apply to a new book; an existing book with that name is reused as is, cover included.
     */
    suspend fun getOrCreateRecipeBookIdByName(
        name: String,
        uid: String? = null,
        coverPhotoUri: String? = null,
        sharedBy: String? = null,
        sharedAt: Long? = null
    ): Long {
        val trimmed = name.trim().ifBlank { L10n.str(R.string.untitled) }
        recipeBookDao.findByName(trimmed)?.let { return it.id }
        return recipeBookDao.insert(
            RecipeBookEntity(
                uid = uid?.takeIf { recipeBookDao.findByUid(it) == null } ?: UUID.randomUUID().toString(),
                name = trimmed,
                coverPhotoUri = coverPhotoUri,
                createdAt = System.currentTimeMillis(),
                sharedBy = sharedBy,
                sharedAt = sharedAt
            )
        )
    }

    /** A shared book's book here: the one with [uid], unless it is a pack (packs are never written to). */
    suspend fun findSharedBookId(uid: String): Long? = recipeBookDao.findByUid(uid)?.takeIf { it.packId == null }?.id

    /** A new book that keeps the shared book's [uid] (unless this phone already uses it), even if another has the same name. */
    suspend fun createBookWithUid(name: String, uid: String, coverPhotoUri: String?, sharedBy: String? = null, sharedAt: Long? = null): Long =
        recipeBookDao.insert(
            RecipeBookEntity(
                uid = uid.takeIf { recipeBookDao.findByUid(it) == null } ?: UUID.randomUUID().toString(),
                name = name.trim().ifBlank { L10n.str(R.string.untitled) },
                coverPhotoUri = coverPhotoUri,
                createdAt = System.currentTimeMillis(),
                sharedBy = sharedBy,
                sharedAt = sharedAt
            )
        )

    /** Who shared the book, after importing a newer copy of it. */
    suspend fun setBookSharedBy(bookId: Long, sharedBy: String?, sharedAt: Long?) =
        recipeBookDao.setSharedBy(bookId, sharedBy, sharedAt)

    /** Name of the book a recipe is in (for "you already have it in …"). */
    suspend fun recipeBookName(bookId: Long): String? = recipeBookDao.getOnce(bookId)?.name

    /** What a shared book's import needs to know about a recipe this phone may already have. */
    data class RecipeImportState(val id: Long, val updatedAt: Long, val isFavorite: Boolean, val inPack: Boolean, val bookId: Long)

    suspend fun findRecipeStateByUid(uid: String): RecipeImportState? {
        val recipe = recipeDao.findByUid(uid) ?: return null
        val inPack = recipeBookDao.getOnce(recipe.recipeBookId)?.packId != null
        return RecipeImportState(recipe.id, recipe.updatedAt, recipe.isFavorite, inPack, recipe.recipeBookId)
    }

    // --- Downloadable recipe packs ---

    /**
     * Installs or updates a pack. The book is found by [packId] (never by name, so it never clashes
     * with a user book of the same name). Recipes are created or updated by [RecipeExportDto.uid],
     * keeping isFavorite, timesCooked and createdAt, and those missing from this version are
     * deleted. Unlike [saveRecipe] and [saveRecipeBook] it skips the read-only checks: this is the
     * only legitimate way to write to a pack book.
     */
    suspend fun installOrUpdatePack(
        packId: String,
        packVersion: Int,
        packSeries: String?,
        bookName: String,
        bookUid: String,
        bookCoverUri: String?,
        recipes: List<RecipeExportDto>,
        photosByUid: Map<String, List<RecipePhoto>>
    ): Long = db.withTransaction {
        val now = System.currentTimeMillis()
        val existingBook = recipeBookDao.findByPackId(packId)
        val bookId = if (existingBook == null) {
            recipeBookDao.insert(
                RecipeBookEntity(
                    uid = bookUid,
                    name = bookName,
                    coverPhotoUri = bookCoverUri,
                    createdAt = now,
                    packId = packId,
                    packVersion = packVersion,
                    packSeries = packSeries
                )
            )
        } else {
            recipeBookDao.update(
                existingBook.copy(
                    name = bookName,
                    coverPhotoUri = bookCoverUri ?: existingBook.coverPhotoUri,
                    packVersion = packVersion,
                    packSeries = packSeries ?: existingBook.packSeries
                )
            )
            existingBook.id
        }

        recipes.forEach { dto ->
            val categoryId = dto.categoryName?.takeIf { it.isNotBlank() }?.let { recipeRepository.resolveCategoryId(it) }
            // A recipe only counts as existing if it is still in this book. If the user moved it to
            // another book (allowed for pack recipes), the next update creates a new copy in the
            // pack instead of touching the moved one.
            val existingRecipe = recipeDao.findByUid(dto.uid)?.takeIf { it.recipeBookId == bookId }
            val recipeId = if (existingRecipe == null) {
                recipeDao.insertRecipe(
                    RecipeEntity(
                        uid = dto.uid,
                        name = dto.name.trim(),
                        categoryId = categoryId,
                        recipeBookId = bookId,
                        difficulty = Difficulty.parse(dto.difficulty).name,
                        prepTimeMinutes = dto.prepTimeMinutes,
                        cookTimeMinutes = dto.cookTimeMinutes,
                        servings = dto.servings,
                        notes = dto.notes,
                        source = dto.source,
                        isFavorite = false,
                        timesCooked = 0,
                        createdAt = now,
                        updatedAt = now,
                        origin = dto.origin,
                        originCountry = RecipeOrigin.normalizeCountry(dto.originCountry)
                    )
                )
            } else {
                recipeDao.updateRecipe(
                    existingRecipe.copy(
                        name = dto.name.trim(),
                        categoryId = categoryId,
                        difficulty = Difficulty.parse(dto.difficulty).name,
                        prepTimeMinutes = dto.prepTimeMinutes,
                        cookTimeMinutes = dto.cookTimeMinutes,
                        servings = dto.servings,
                        notes = dto.notes,
                        source = dto.source,
                        updatedAt = now,
                        origin = dto.origin,
                        originCountry = RecipeOrigin.normalizeCountry(dto.originCountry)
                    )
                )
                existingRecipe.id
            }

            recipeDao.deleteIngredients(recipeId)
            var ingredientPosition = 0
            val ingredientEntities = dto.ingredientGroups.flatMap { group ->
                group.ingredients.map { ingredient ->
                    IngredientEntity(
                        recipeId = recipeId,
                        groupName = group.name,
                        position = ingredientPosition++,
                        name = ingredient.name.trim(),
                        quantity = ingredient.quantity,
                        unit = ingredient.unit?.trim()?.takeIf { it.isNotBlank() },
                        optional = ingredient.optional
                    )
                }
            }
            if (ingredientEntities.isNotEmpty()) recipeDao.insertIngredients(ingredientEntities)

            recipeDao.deleteSteps(recipeId)
            var stepPosition = 0
            val stepEntities = dto.stepGroups.flatMap { group ->
                group.instructions.map { instruction ->
                    StepEntity(recipeId = recipeId, groupName = group.name, position = stepPosition++, instruction = instruction.trim())
                }
            }
            if (stepEntities.isNotEmpty()) recipeDao.insertSteps(stepEntities)

            recipeDao.deletePhotos(recipeId)
            val photos = photosByUid[dto.uid].orEmpty()
            if (photos.isNotEmpty()) {
                recipeDao.insertPhotos(
                    photos.mapIndexed { index, photo -> RecipePhotoEntity(recipeId = recipeId, uri = photo.uri, position = index, isCover = photo.isCover) }
                )
            }

            recipeDao.deleteTagCrossRefs(recipeId)
            val tagIds = dto.tags.filter { it.isNotBlank() }.map { recipeRepository.resolveTagId(it) }
            if (tagIds.isNotEmpty()) recipeDao.insertTagCrossRefs(tagIds.map { RecipeTagCrossRef(recipeId, it) })

            recipeDao.deleteUtensilCrossRefs(recipeId)
            val utensilIds = KitchenEquipment.clean(dto.utensils, recipeRepository.appLanguage()).map { recipeRepository.resolveUtensilId(it) }.distinct()
            if (utensilIds.isNotEmpty()) recipeDao.insertUtensilCrossRefs(utensilIds.map { RecipeUtensilCrossRef(recipeId, it) })

            if (dto.health != null) {
                val colorLevel = runCatching { HealthColorLevel.valueOf(dto.health.colorLevel) }.getOrDefault(HealthColorLevel.YELLOW)
                recipeDao.updateHealthRating(recipeId, colorLevel.name, dto.health.description, dto.health.fingerprint, dto.health.analyzedAt)
            }
            dto.nutrition?.let { nutrition ->
                recipeDao.updateNutritionInfo(
                    recipeId,
                    nutrition.caloriesPerServing,
                    nutrition.proteinGrams,
                    nutrition.carbsGrams,
                    nutrition.fatGrams,
                    nutrition.fingerprint,
                    nutrition.analyzedAt
                )
            }
        }

        // The pack owns its content: a recipe that is not in this version is deleted.
        val currentUids = recipes.map { it.uid }
        if (currentUids.isEmpty()) recipeDao.deleteAllForBook(bookId) else recipeDao.deleteRecipesNotInUidSet(bookId, currentUids)

        bookId
    }

    /**
     * Uninstalls a pack: deletes the book and the recipes still in it (those the user moved to
     * another book stay), bypassing the non-empty check of [deleteRecipeBook], plus their photo and
     * cover files unless another recipe or book still uses them.
     */
    suspend fun uninstallPack(bookId: Long) {
        val book = recipeBookDao.getOnce(bookId) ?: return
        val files = recipeRepository.getRecipesForBookOnce(bookId).flatMap { recipe -> recipe.photos.map { it.uri } } +
            listOfNotNull(book.coverPhotoUri)
        db.withTransaction {
            recipeDao.deleteAllForBook(bookId)
            recipeBookDao.delete(bookId)
        }
        val stillUsed = recipeRepository.getAllRecipesOnce().flatMap { recipe -> recipe.photos.map { it.uri } }.toSet() +
            getAllRecipeBooksOnce().mapNotNull { it.coverPhotoUri }
        files.distinct().filter { it !in stillUsed }.forEach { PhotoStorage.deleteFile(it) }
    }
}
