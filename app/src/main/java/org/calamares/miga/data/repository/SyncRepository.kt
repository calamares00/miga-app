package org.calamares.miga.data.repository

import androidx.room.withTransaction
import org.calamares.miga.data.export.IngredientDto
import org.calamares.miga.data.export.IngredientGroupDto
import org.calamares.miga.data.export.StepGroupDto
import org.calamares.miga.data.local.AppDatabase
import org.calamares.miga.data.local.PhotoStorage
import org.calamares.miga.data.local.TokenCipher
import org.calamares.miga.data.local.entity.IngredientEntity
import org.calamares.miga.data.local.entity.PendingSyncChangeEntity
import org.calamares.miga.data.local.entity.RecipeBookEntity
import org.calamares.miga.data.local.entity.RecipeEntity
import org.calamares.miga.data.local.entity.RecipePhotoEntity
import org.calamares.miga.data.local.entity.RecipeTagCrossRef
import org.calamares.miga.data.local.entity.RecipeUtensilCrossRef
import org.calamares.miga.data.local.entity.StepEntity
import org.calamares.miga.data.local.entity.SyncChangeType
import org.calamares.miga.data.local.entity.SyncConnectionEntity
import org.calamares.miga.data.local.entity.SyncEntityType
import org.calamares.miga.data.model.KitchenEquipment
import org.calamares.miga.data.model.RecipeOrigin
import org.calamares.miga.data.model.Difficulty
import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.data.model.IngredientGroup
import org.calamares.miga.data.model.StepGroup
import org.calamares.miga.data.model.SyncConnection
import org.calamares.miga.data.sync.BookSyncDto
import org.calamares.miga.data.sync.PhotoMetaDto
import org.calamares.miga.data.sync.RECIPE_SYNC_SCHEMA
import org.calamares.miga.data.sync.RecipeSyncDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Sync servers: the connections, the outbox of local changes waiting to be uploaded, what is sent
 * for a book or a recipe and how the changes received are applied (see SyncEngine). Reached
 * through [RecipeRepository.sync].
 */
class SyncRepository internal constructor(
    private val db: AppDatabase,
    private val recipeRepository: RecipeRepository,
    private val shopping: ShoppingRepository,
    private val onSyncChangeEnqueued: (connectionId: Long) -> Unit
) {

    private val recipeDao = db.recipeDao()
    private val recipeBookDao = db.recipeBookDao()
    private val syncConnectionDao = db.syncConnectionDao()
    private val pendingSyncChangeDao = db.pendingSyncChangeDao()

    // --- Sync server (self-hosted namespaces) ---

    fun observeSyncConnections(): Flow<List<SyncConnection>> =
        syncConnectionDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getSyncConnectionOnce(id: Long): SyncConnection? = syncConnectionDao.getOnce(id)?.toDomain()

    suspend fun addSyncConnection(label: String, serverUrl: String, namespaceId: String, accessToken: String): Long =
        syncConnectionDao.insert(
            SyncConnectionEntity(
                label = label.trim(),
                serverUrl = serverUrl.trim().trimEnd('/'),
                namespaceId = namespaceId.trim(),
                accessToken = TokenCipher.encrypt(accessToken.trim()),
                createdAt = System.currentTimeMillis()
            )
        )

    /**
     * Removes the connection. Its books become local (no content is deleted) and any pending upload
     * for it is discarded.
     */
    suspend fun removeSyncConnection(id: Long) = db.withTransaction {
        recipeBookDao.clearSyncConnection(id)
        pendingSyncChangeDao.clearAllForConnection(id)
        syncConnectionDao.getOnce(id)?.let { syncConnectionDao.delete(it) }
        shopping.purgeShoppingTombstonesIfNotShared()
    }

    suspend fun markSyncSuccess(connectionId: Long, revision: Long) =
        syncConnectionDao.markSynced(connectionId, revision, System.currentTimeMillis())

    suspend fun markSyncError(connectionId: Long, reason: String) = syncConnectionDao.markError(connectionId, reason)

    /**
     * Links an existing user book to a connection. It is synced read-write (unlike a pack) and
     * queued for a full upload, photos included, on the next sync.
     */
    suspend fun linkBookToSyncConnection(bookId: Long, connectionId: Long) {
        val linked = db.withTransaction {
            val book = recipeBookDao.getOnce(bookId) ?: return@withTransaction false
            recipeBookDao.update(book.copy(syncConnectionId = connectionId, updatedAt = System.currentTimeMillis()))
            enqueueBookResyncLocked(bookId, connectionId)
            true
        }
        if (linked) onSyncChangeEnqueued(connectionId)
    }

    /**
     * Queues the whole book again (metadata, every recipe and every photo) for upload to
     * [connectionId]. Must run inside an open transaction, hence "Locked"; it is used by
     * [linkBookToSyncConnection], [enqueueFullBookResync] and [enqueueFullConnectionResync]. It is
     * never called from automatic or periodic sync, since re-queueing everything each time would
     * waste battery and data; [org.calamares.miga.data.sync.SyncEngine.pushRecipePhotosIfPresent]
     * heals missing photos cheaply instead.
     */
    private suspend fun enqueueBookResyncLocked(bookId: Long, connectionId: Long) {
        val book = recipeBookDao.getOnce(bookId) ?: return
        recipeDao.backfillPhotoUids() // without a uid, photos older than sync would be skipped below
        enqueueSyncChange(connectionId, SyncEntityType.BOOK, book.uid, SyncChangeType.UPSERT)
        recipeDao.getAllWithDetailsForBookOnce(bookId).forEach { details ->
            enqueueSyncChange(connectionId, SyncEntityType.RECIPE, details.recipe.uid, SyncChangeType.UPSERT)
            recipeDao.getPhotosOnce(details.recipe.id).forEach { photo ->
                photo.uid?.let {
                    enqueueSyncChange(connectionId, SyncEntityType.PHOTO, it, SyncChangeType.UPSERT, parentUid = details.recipe.uid)
                }
            }
        }
    }

    /**
     * Forces a full re-upload (book, recipes and photos) of an already linked book. Used by the
     * manual "Sync now" button in the book editor to repair books linked before their photos were
     * queued too (see [enqueueBookResyncLocked]).
     */
    suspend fun enqueueFullBookResync(bookId: Long, connectionId: Long) {
        db.withTransaction { enqueueBookResyncLocked(bookId, connectionId) }
        onSyncChangeEnqueued(connectionId)
    }

    /**
     * Like [enqueueFullBookResync], for every book linked to [connectionId] at once. Used by the
     * connection's manual "Sync now" button.
     */
    suspend fun enqueueFullConnectionResync(connectionId: Long) {
        val books = recipeBookDao.findBySyncConnectionId(connectionId)
        if (books.isEmpty()) return
        db.withTransaction { books.forEach { enqueueBookResyncLocked(it.id, connectionId) } }
        onSyncChangeEnqueued(connectionId)
    }

    /**
     * Resets the connection cursor to 0 so the next sync downloads all server content again. This
     * is idempotent and recovers anything missed, such as photos whose download failed. Used by the
     * manual "Sync now" buttons.
     */
    suspend fun resetSyncCursor(connectionId: Long) = syncConnectionDao.resetCursor(connectionId)

    /**
     * Assigns a uid to old photos that lack one (see [RecipeDao.backfillPhotoUids]). Called on app
     * start.
     */
    suspend fun ensurePhotoUids() = recipeDao.backfillPhotoUids()

    /** Stops syncing a book. It becomes local; nothing is deleted locally or on the server. */
    suspend fun unlinkBookFromSyncConnection(bookId: Long) {
        val book = recipeBookDao.getOnce(bookId) ?: return
        recipeBookDao.update(book.copy(syncConnectionId = null))
    }

    // --- Outbox: queue local changes for upload ---

    internal suspend fun enqueueSyncChange(
        connectionId: Long,
        entityType: SyncEntityType,
        uid: String,
        changeType: SyncChangeType,
        parentUid: String? = null
    ) {
        pendingSyncChangeDao.enqueue(
            PendingSyncChangeEntity(
                syncConnectionId = connectionId,
                entityType = entityType.name,
                uid = uid,
                changeType = changeType.name,
                createdAt = System.currentTimeMillis(),
                parentUid = parentUid
            )
        )
    }

    suspend fun getPendingSyncChanges(connectionId: Long): List<PendingSyncChangeEntity> =
        pendingSyncChangeDao.getPendingForConnection(connectionId)

    suspend fun clearPendingSyncChange(id: Long) = pendingSyncChangeDao.clear(id)

    // --- Build the sync DTO of a local book or recipe (for upload) ---

    suspend fun getRecipeBookSyncDto(bookId: Long): BookSyncDto? {
        val book = recipeBookDao.getOnce(bookId) ?: return null
        return BookSyncDto(uid = book.uid, name = book.name, hasCoverPhoto = book.coverPhotoUri != null, updatedAt = book.updatedAt)
    }

    suspend fun getRecipeBookSyncDtoByUid(uid: String): BookSyncDto? {
        val book = recipeBookDao.findByUid(uid) ?: return null
        return getRecipeBookSyncDto(book.id)
    }

    /**
     * Used to upload a book cover (see [SyncEngine.pushBookCoverIfPresent]); null when the book has
     * none.
     */
    suspend fun getRecipeBookCoverUri(bookUid: String): String? = recipeBookDao.findByUid(bookUid)?.coverPhotoUri

    suspend fun getRecipeSyncDtoByUid(uid: String): RecipeSyncDto? {
        val entity = recipeDao.findByUid(uid) ?: return null
        return getRecipeSyncDto(entity.id)
    }

    suspend fun getRecipeSyncDto(recipeId: Long): RecipeSyncDto? {
        val details = recipeDao.observeWithDetails(recipeId).first() ?: return null
        val book = recipeBookDao.getOnce(details.recipe.recipeBookId) ?: return null
        val recipe = details.toDomain()
        return RecipeSyncDto(
            uid = recipe.uid,
            bookUid = book.uid,
            name = recipe.name,
            categoryName = recipe.categoryName,
            difficulty = recipe.difficulty.name,
            prepTimeMinutes = recipe.prepTimeMinutes,
            cookTimeMinutes = recipe.cookTimeMinutes,
            servings = recipe.servings,
            notes = recipe.notes,
            source = recipe.source,
            // Favourites are personal: never uploaded, so they do not reach other devices.
            isFavorite = false,
            ingredientGroups = recipe.ingredientGroups.map { g ->
                IngredientGroupDto(g.name, g.ingredients.map { IngredientDto(it.name, it.quantity, it.unit, it.optional) })
            },
            stepGroups = recipe.stepGroups.map { g -> StepGroupDto(g.name, g.instructions) },
            tags = recipe.tags,
            utensils = recipe.utensils,
            updatedAt = details.recipe.updatedAt,
            rating = null, // personal: never uploaded (see RecipeSyncDto)
            origin = recipe.origin,
            originCountry = recipe.originCountry,
            schema = RECIPE_SYNC_SCHEMA
        )
    }

    /** What is needed to upload a single local photo (see [SyncEngine.pushPhotoChange]). */
    data class PhotoPushInfo(val uri: String, val recipeUid: String, val isCover: Boolean, val position: Int)

    suspend fun getPhotoPushInfo(photoUid: String): PhotoPushInfo? {
        val photo = recipeDao.findPhotoByUid(photoUid) ?: return null
        val recipe = recipeDao.getRecipeOnce(photo.recipeId) ?: return null
        return PhotoPushInfo(photo.uri, recipe.uid, photo.isCover, photo.position)
    }

    /**
     * Every current photo of a recipe, ready to upload (see
     * [SyncEngine.pushRecipePhotosIfPresent]). Photos without a `uid` (a nullable column from the
     * migration that newer photos always fill in) are skipped.
     */
    suspend fun getRecipePhotosForPush(recipeUid: String): List<Pair<String, PhotoPushInfo>> {
        recipeDao.backfillPhotoUids() // heals old photos without a uid, which would otherwise never be uploaded
        val recipe = recipeDao.findByUid(recipeUid) ?: return emptyList()
        return recipeDao.getPhotosOnce(recipe.id).mapNotNull { photo ->
            val uid = photo.uid ?: return@mapNotNull null
            uid to PhotoPushInfo(photo.uri, recipeUid, photo.isCover, photo.position)
        }
    }

    /**
     * Used before downloading a remote photo, so it is not downloaded (leaving an orphan file) when
     * it already exists locally.
     */
    suspend fun hasLocalPhoto(photoUid: String): Boolean = recipeDao.findPhotoByUid(photoUid) != null

    // --- Applying changes received from the server (used by SyncEngine) ---
    //
    // Books are processed in two passes, upserts first and deletions last, because of the RESTRICT
    // foreign key on recipes.recipeBookId: a new synced recipe needs its book to exist locally, and
    // a book cannot be deleted while it still has local recipes (which is also why recipes are
    // processed before book deletions).

    /**
     * Returns the local book id after applying the upsert, or null when it was ignored (the local
     * copy was newer, or it is a tombstone).
     */
    suspend fun applyRemoteBookUpsert(connectionId: Long, dto: BookSyncDto, firstSync: Boolean = false): Long? = db.withTransaction {
        if (dto.deletedAt != null) return@withTransaction null
        val existing = recipeBookDao.findByUid(dto.uid)
        if (existing != null && existing.updatedAt > dto.updatedAt) {
            // On the first sync of a new connection, a local book the server already knows (same
            // uid, typically restored from a backup after reinstalling) is the same book: it is
            // linked again, as the user would do by hand, instead of staying local while its
            // recipes and photos keep arriving from the server.
            if (firstSync && existing.syncConnectionId == null && existing.packId == null) {
                recipeBookDao.update(existing.copy(syncConnectionId = connectionId))
                enqueueBookResyncLocked(existing.id, connectionId)
            }
            return@withTransaction null
        }
        if (existing == null) {
            recipeBookDao.insert(
                RecipeBookEntity(
                    uid = dto.uid,
                    name = dto.name,
                    coverPhotoUri = null,
                    createdAt = dto.updatedAt,
                    updatedAt = dto.updatedAt,
                    syncConnectionId = connectionId
                )
            )
        } else {
            recipeBookDao.update(existing.copy(name = dto.name, updatedAt = dto.updatedAt, syncConnectionId = connectionId))
            existing.id
        }
    }

    suspend fun applyRemoteBookDeletion(dto: BookSyncDto) = db.withTransaction {
        if (dto.deletedAt == null) return@withTransaction
        val existing = recipeBookDao.findByUid(dto.uid) ?: return@withTransaction
        if (existing.updatedAt > dto.deletedAt) return@withTransaction
        recipeDao.deleteAllForBook(existing.id)
        recipeBookDao.delete(existing.id)
    }

    /**
     * Like [applyRemoteBookDeletion], for an "unlinked" tombstone ([BookSyncDto.unlinked]). The
     * book and all its recipes and photos are kept; only the sync link is cut, so it becomes a
     * normal editable local book no longer managed by [SyncEngine].
     */
    suspend fun applyRemoteBookUnlink(dto: BookSyncDto) = db.withTransaction {
        if (dto.deletedAt == null) return@withTransaction
        val existing = recipeBookDao.findByUid(dto.uid) ?: return@withTransaction
        if (existing.updatedAt > dto.deletedAt) return@withTransaction
        recipeBookDao.update(existing.copy(syncConnectionId = null))
    }

    /**
     * Applies a book cover that [SyncEngine] has already downloaded and stored at [localUri]. No
     * last-write-wins check of its own (that was decided when applying the book). Returns the
     * previous file uri, if different, so the engine can delete it.
     */
    suspend fun applyRemoteBookCover(bookId: Long, localUri: String): String? {
        val book = recipeBookDao.getOnce(bookId) ?: return null
        val oldUri = book.coverPhotoUri
        recipeBookDao.update(book.copy(coverPhotoUri = localUri))
        return oldUri?.takeIf { it != localUri }
    }

    suspend fun applyRemoteRecipeUpsert(dto: RecipeSyncDto): Long? = db.withTransaction {
        if (dto.deletedAt != null) return@withTransaction null
        val bookId = recipeBookDao.findByUid(dto.bookUid)?.id ?: return@withTransaction null
        val existing = recipeDao.findByUid(dto.uid)
        if (existing != null && existing.updatedAt > dto.updatedAt) return@withTransaction null

        val categoryId = dto.categoryName?.takeIf { it.isNotBlank() }?.let { recipeRepository.resolveCategoryId(it) }
        val domainIngredientGroups = dto.ingredientGroups.map { g -> IngredientGroup(g.name, g.ingredients.map { Ingredient(it.name, it.quantity, it.unit, it.optional) }) }
        val domainStepGroups = dto.stepGroups.map { g -> StepGroup(g.name, g.instructions) }
        val newFingerprint = recipeRepository.computeHealthFingerprint(domainIngredientGroups, domainStepGroups)
        val keepHealth = existing != null && existing.healthFingerprint == newFingerprint

        val recipeId = if (existing == null) {
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
                    isFavorite = false, // personal: never taken from the server
                    timesCooked = 0,
                    createdAt = dto.updatedAt,
                    updatedAt = dto.updatedAt,
                    rating = null, // personal: never taken from the server
                    origin = dto.origin,
                    originCountry = RecipeOrigin.normalizeCountry(dto.originCountry)
                )
            )
        } else {
            recipeDao.updateRecipe(
                existing.copy(
                    name = dto.name.trim(),
                    categoryId = categoryId,
                    recipeBookId = bookId,
                    difficulty = Difficulty.parse(dto.difficulty).name,
                    prepTimeMinutes = dto.prepTimeMinutes,
                    cookTimeMinutes = dto.cookTimeMinutes,
                    servings = dto.servings,
                    notes = dto.notes,
                    source = dto.source,
                    // isFavorite stays as this device has it (personal, see RecipeSyncDto).
                    updatedAt = dto.updatedAt,
                    // rating stays as this device has it (personal, see RecipeSyncDto).
                    // An older server does not know the fields and returns null: keep what the device has.
                    origin = if (dto.schema >= RECIPE_SYNC_SCHEMA) dto.origin else dto.origin ?: existing.origin,
                    originCountry = if (dto.schema >= RECIPE_SYNC_SCHEMA) {
                        RecipeOrigin.normalizeCountry(dto.originCountry)
                    } else {
                        RecipeOrigin.normalizeCountry(dto.originCountry) ?: existing.originCountry
                    },
                    healthColor = if (keepHealth) existing.healthColor else null,
                    healthDescription = if (keepHealth) existing.healthDescription else null,
                    healthFingerprint = if (keepHealth) existing.healthFingerprint else null,
                    healthAnalyzedAt = if (keepHealth) existing.healthAnalyzedAt else null,
                    nutritionCalories = if (keepHealth) existing.nutritionCalories else null,
                    nutritionProteinGrams = if (keepHealth) existing.nutritionProteinGrams else null,
                    nutritionCarbsGrams = if (keepHealth) existing.nutritionCarbsGrams else null,
                    nutritionFatGrams = if (keepHealth) existing.nutritionFatGrams else null,
                    nutritionFingerprint = if (keepHealth) existing.nutritionFingerprint else null,
                    nutritionAnalyzedAt = if (keepHealth) existing.nutritionAnalyzedAt else null
                )
            )
            existing.id
        }

        recipeDao.deleteIngredients(recipeId)
        var ingredientPosition = 0
        val ingredientEntities = dto.ingredientGroups.flatMap { group ->
            group.ingredients.map { ingredient ->
                IngredientEntity(
                    recipeId = recipeId, groupName = group.name, position = ingredientPosition++,
                    name = ingredient.name.trim(), quantity = ingredient.quantity,
                    unit = ingredient.unit?.trim()?.takeIf { it.isNotBlank() }, optional = ingredient.optional
                )
            }
        }
        if (ingredientEntities.isNotEmpty()) recipeDao.insertIngredients(ingredientEntities)

        recipeDao.deleteSteps(recipeId)
        var stepPosition = 0
        val stepEntities = dto.stepGroups.flatMap { group ->
            group.instructions.map { instruction -> StepEntity(recipeId = recipeId, groupName = group.name, position = stepPosition++, instruction = instruction.trim()) }
        }
        if (stepEntities.isNotEmpty()) recipeDao.insertSteps(stepEntities)

        recipeDao.deleteTagCrossRefs(recipeId)
        val tagIds = dto.tags.filter { it.isNotBlank() }.map { recipeRepository.resolveTagId(it) }
        if (tagIds.isNotEmpty()) recipeDao.insertTagCrossRefs(tagIds.map { RecipeTagCrossRef(recipeId, it) })

        recipeDao.deleteUtensilCrossRefs(recipeId)
        val utensilIds = KitchenEquipment.clean(dto.utensils, recipeRepository.appLanguage()).map { recipeRepository.resolveUtensilId(it) }.distinct()
        if (utensilIds.isNotEmpty()) recipeDao.insertUtensilCrossRefs(utensilIds.map { RecipeUtensilCrossRef(recipeId, it) })

        recipeId
    }

    suspend fun applyRemoteRecipeDeletion(dto: RecipeSyncDto) = db.withTransaction {
        if (dto.deletedAt == null) return@withTransaction
        val existing = recipeDao.findByUid(dto.uid) ?: return@withTransaction
        if (existing.updatedAt > dto.deletedAt) return@withTransaction
        recipeDao.deleteRecipe(existing.id)
    }

    /**
     * Applies a single photo that [SyncEngine] has already downloaded and stored at [localUri]. No
     * last-write-wins check, since photos are only added or removed, never edited in place: an
     * existing row with that uid only gets its position and cover flag updated.
     */
    suspend fun applyRemotePhotoUpsert(dto: PhotoMetaDto, localUri: String): Boolean {
        if (dto.deletedAt != null) return false
        val recipe = recipeDao.findByUid(dto.recipeUid) ?: return false
        val existing = recipeDao.findPhotoByUid(dto.uid)
        if (existing != null) {
            recipeDao.updatePhotoMetaByUid(dto.uid, dto.isCover, dto.position)
        } else {
            recipeDao.insertPhotos(
                listOf(RecipePhotoEntity(recipeId = recipe.id, uri = localUri, position = dto.position, isCover = dto.isCover, uid = dto.uid))
            )
        }
        return true
    }

    /**
     * Deletes the local row of a photo deleted on the server and returns its
     * [RecipePhotoEntity.uri] so the sync engine can delete the file; null when there was nothing
     * to delete.
     */
    /**
     * Applies the cover flag and position of a server photo this phone already has, unless the
     * recipe or the photo still has local changes waiting to be uploaded (those win).
     */
    suspend fun applyRemotePhotoMeta(dto: PhotoMetaDto) {
        if (dto.deletedAt != null) return
        if (pendingSyncChangeDao.hasPendingForUid(dto.recipeUid) || pendingSyncChangeDao.hasPendingForUid(dto.uid)) return
        recipeDao.updatePhotoMetaByUid(dto.uid, dto.isCover, dto.position)
    }

    /**
     * Handles a downloaded server photo whose bytes are identical to a photo the recipe already has
     * under another uid: the same picture stored twice, for example after restoring a backup made
     * before backups kept photo uids. Instead of adding a duplicate, every device keeps the smaller
     * of the two uids: the local row takes the server's uid when it is smaller, and the other uid
     * is queued for deletion on the server. Returns false when there is no such twin, so the caller
     * stores the photo as new.
     */
    suspend fun mergeRemotePhotoTwin(dto: PhotoMetaDto, bytes: ByteArray): Boolean {
        val recipe = recipeDao.findByUid(dto.recipeUid) ?: return false
        val twin = recipeDao.getPhotosOnce(recipe.id).firstOrNull { photo ->
            photo.uid != dto.uid && PhotoStorage.readBytes(photo.uri)?.contentEquals(bytes) == true
        } ?: return false
        val connectionId = recipeBookDao.getOnce(recipe.recipeBookId)?.syncConnectionId
        db.withTransaction {
            val twinUid = twin.uid
            if (twinUid == null || dto.uid < twinUid) {
                recipeDao.updatePhotoUid(twin.id, dto.uid)
                recipeDao.updatePhotoMetaByUid(dto.uid, dto.isCover, dto.position)
                if (connectionId != null && twinUid != null) {
                    enqueueSyncChange(connectionId, SyncEntityType.PHOTO, twinUid, SyncChangeType.DELETE, parentUid = dto.recipeUid)
                }
            } else if (connectionId != null) {
                // The local uid wins: make sure the server has it before its copy goes away.
                enqueueSyncChange(connectionId, SyncEntityType.PHOTO, twinUid, SyncChangeType.UPSERT, parentUid = dto.recipeUid)
                enqueueSyncChange(connectionId, SyncEntityType.PHOTO, dto.uid, SyncChangeType.DELETE, parentUid = dto.recipeUid)
            }
        }
        return true
    }

    suspend fun applyRemotePhotoDeletion(dto: PhotoMetaDto): String? {
        if (dto.deletedAt == null) return null
        val existing = recipeDao.findPhotoByUid(dto.uid) ?: return null
        recipeDao.deletePhotoByUid(dto.uid)
        return existing.uri
    }
}
