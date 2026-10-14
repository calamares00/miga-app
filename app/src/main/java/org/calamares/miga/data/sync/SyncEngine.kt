package org.calamares.miga.data.sync

import org.calamares.miga.L10n
import org.calamares.miga.R
import android.content.Context
import org.calamares.miga.data.local.PhotoStorage
import org.calamares.miga.data.local.entity.PendingSyncChangeEntity
import org.calamares.miga.data.local.entity.SyncChangeType
import org.calamares.miga.data.local.entity.SyncEntityType
import org.calamares.miga.data.model.SyncConnection
import org.calamares.miga.data.repository.RecipeRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SyncOutcome {
    data class Success(val pulled: Int, val pushed: Int) : SyncOutcome
    data class Error(val reason: String) : SyncOutcome
}

/**
 * Runs the sync of one connection.
 *
 * It first downloads the server changes since the last known revision, applying last-write-wins by
 * `updatedAt` (see [RecipeRepository.applyRemoteBookUpsert] and
 * [RecipeRepository.applyRemoteRecipeUpsert]). Book upserts are applied before recipes and book
 * deletions after them, so the foreign key on `recipes.recipeBookId` always holds. Photos and book
 * covers are applied after recipes, so their recipe already exists locally.
 *
 * Then it uploads the outbox of pending local changes, resolving any 409 with the server copy. When
 * the connection shares the shopping list ([SyncConnection.syncShopping]), its items arrive in the
 * same changes response and local pending items are uploaded afterwards (see [syncShoppingList]).
 *
 * A [Context] is only needed to store or delete photo files (see [PhotoStorage]).
 */
class SyncEngine(private val repository: RecipeRepository) {

    /**
     * Only one sync at a time in the whole app: automatic triggers (on save, on open, periodic) can
     * overlap.
     */
    suspend fun syncConnection(context: Context, connectionId: Long): SyncOutcome =
        syncLock.withLock { syncConnectionLocked(context, connectionId) }

    private suspend fun syncConnectionLocked(context: Context, connectionId: Long): SyncOutcome {
        val connection = repository.getSyncConnectionOnce(connectionId)
            ?: return SyncOutcome.Error(L10n.str(R.string.connection_not_found))

        val pulled = when (val fetch = SyncClient.fetchChanges(connection, connection.lastSyncedRevision)) {
            is SyncFetchResult.Error -> {
                repository.markSyncError(connectionId, fetch.reason)
                return SyncOutcome.Error(fetch.reason)
            }
            is SyncFetchResult.Success -> {
                val failedPhotoRevisions = applyChanges(context, connection, fetch.changes)
                if (connection.syncShopping) applyRemoteShopping(fetch.changes)
                // If a photo could not be downloaded because of a transient failure the cursor does
                // not move past it, so the next sync retries it (applying changes is idempotent)
                // instead of losing it.
                repository.markSyncSuccess(connectionId, SyncCursor.next(fetch.changes.latestRevision, failedPhotoRevisions))
                fetch.changes.books.size + fetch.changes.recipes.size + fetch.changes.photos.size
            }
        }

        var pushed = 0
        for (change in repository.getPendingSyncChanges(connectionId)) {
            if (pushOne(context, connection, change)) {
                repository.clearPendingSyncChange(change.id)
                pushed++
            }
        }

        if (connection.syncShopping) pushed += syncShoppingList(connection)

        return SyncOutcome.Success(pulled, pushed)
    }

    /** Lists are applied before items: an item of a new list needs that list to exist locally. */
    private suspend fun applyRemoteShopping(changes: ChangesResponseDto) {
        repository.applyRemoteShoppingLists(changes.shoppingLists)
        repository.applyRemoteShoppingItems(changes.shoppingItems)
    }

    /**
     * Shared shopping lists. The first time after enabling it, the whole list is downloaded from
     * the server (the regular cursor has already moved past its revisions); afterwards pending
     * local changes are uploaded. A 409 is resolved with the server copy (last write wins) and a
     * network failure keeps the changes pending for the next sync. Returns how many items were
     * uploaded.
     */
    private suspend fun syncShoppingList(connection: SyncConnection): Int {
        if (!connection.shoppingPulled) {
            if (connection.lastSyncedRevision == 0L) {
                repository.markShoppingInitialPullDone(connection.id) // the regular pull already started from scratch
            } else when (val full = SyncClient.fetchChanges(connection, 0)) {
                is SyncFetchResult.Error -> {
                    repository.markSyncError(connection.id, full.reason)
                    return 0
                }
                is SyncFetchResult.Success -> {
                    applyRemoteShopping(full.changes)
                    repository.markShoppingInitialPullDone(connection.id)
                }
            }
        }
        var pushed = 0
        // Lists first: an item of a new list needs the server to know that list.
        for (list in repository.getDirtyShoppingLists()) {
            val result = if (list.deletedAt != null) {
                SyncClient.deleteShoppingList(connection, list.uid, list.updatedAt)
            } else {
                SyncClient.pushShoppingList(connection, list)
            }
            when (result) {
                is SyncPushResult.Applied -> {
                    repository.markShoppingListSynced(list.uid, list.updatedAt)
                    pushed++
                }
                is SyncPushResult.Conflict -> repository.applyRemoteShoppingLists(listOf(result.serverCopy))
                is SyncPushResult.Error -> {
                    repository.markSyncError(connection.id, result.reason)
                    return pushed
                }
            }
        }
        for (item in repository.getDirtyShoppingItems()) {
            val result = if (item.deletedAt != null) {
                SyncClient.deleteShoppingItem(connection, item.uid, item.updatedAt)
            } else {
                SyncClient.pushShoppingItem(connection, item)
            }
            when (result) {
                is SyncPushResult.Applied -> {
                    repository.markShoppingItemSynced(item.uid, item.updatedAt)
                    pushed++
                }
                is SyncPushResult.Conflict -> repository.applyRemoteShoppingItems(listOf(result.serverCopy))
                is SyncPushResult.Error -> {
                    repository.markSyncError(connection.id, result.reason)
                    return pushed
                }
            }
        }
        return pushed
    }

    /**
     * Applies downloaded changes and returns the revisions of photos that could not be downloaded
     * because of a transient failure.
     */
    private suspend fun applyChanges(context: Context, connection: SyncConnection, changes: ChangesResponseDto): List<Long> {
        val failedPhotoRevisions = mutableListOf<Long>()
        changes.books.filter { it.deletedAt == null }.forEach { dto ->
            val bookId = repository.applyRemoteBookUpsert(connection.id, dto)
            if (bookId != null) applyBookCoverIfPresent(context, connection, bookId, dto)
        }
        // An "unlinked" tombstone (dto.unlinked, see SyncDtos.kt) must not delete anything locally.
        // Recipe and photo rows cascaded from an unlinked book are ignored: the book itself gets
        // its syncConnectionId cleared below, and its recipes and photos only had the link
        // inherited from that book.
        changes.recipes.forEach { dto ->
            when {
                dto.unlinked -> Unit
                dto.deletedAt != null -> repository.applyRemoteRecipeDeletion(dto)
                else -> repository.applyRemoteRecipeUpsert(dto)
            }
        }
        changes.photos.forEach { dto ->
            when {
                dto.unlinked -> Unit
                dto.deletedAt != null -> repository.applyRemotePhotoDeletion(dto)?.let { PhotoStorage.deleteFile(it) }
                !repository.hasLocalPhoto(dto.uid) -> when (val download = SyncClient.downloadPhoto(connection, dto.recipeUid, dto.uid)) {
                    is PhotoDownloadResult.Success -> {
                        val localUri = PhotoStorage.copyBytesToInternalStorage(context, download.bytes)
                        if (localUri != null && !repository.applyRemotePhotoUpsert(dto, localUri)) PhotoStorage.deleteFile(localUri)
                    }
                    // The server knows the photo but not its bytes yet (the uploader has not sent
                    // them). It will arrive as a new change once uploaded, so the cursor does not
                    // need to wait.
                    PhotoDownloadResult.NotFound -> Unit
                    is PhotoDownloadResult.Error -> failedPhotoRevisions.add(dto.revision)
                }
            }
        }
        changes.books.filter { it.deletedAt != null }.forEach { dto ->
            if (dto.unlinked) repository.applyRemoteBookUnlink(dto) else repository.applyRemoteBookDeletion(dto)
        }
        return failedPhotoRevisions
    }

    /**
     * Returns true when the change is resolved (uploaded, or a conflict was applied) and can leave
     * the outbox; false when it must be retried on the next sync (network or server failure).
     */
    private suspend fun pushOne(context: Context, connection: SyncConnection, change: PendingSyncChangeEntity): Boolean {
        val entityType = runCatching { SyncEntityType.valueOf(change.entityType) }.getOrNull() ?: return true
        val changeType = runCatching { SyncChangeType.valueOf(change.changeType) }.getOrNull() ?: return true
        return when (entityType) {
            SyncEntityType.BOOK -> pushBookChange(context, connection, change.uid, changeType)
            SyncEntityType.RECIPE -> pushRecipeChange(connection, change.uid, changeType)
            SyncEntityType.PHOTO -> pushPhotoChange(context, connection, change, changeType)
        }
    }

    private suspend fun pushBookChange(context: Context, connection: SyncConnection, uid: String, changeType: SyncChangeType): Boolean =
        when (changeType) {
            SyncChangeType.DELETE -> when (val result = SyncClient.deleteBook(connection, uid, System.currentTimeMillis())) {
                is SyncPushResult.Applied -> true
                is SyncPushResult.Conflict -> { applyServerBookCopy(context, connection, result.serverCopy); true }
                is SyncPushResult.Error -> false
            }
            SyncChangeType.UPSERT -> {
                val dto = repository.getRecipeBookSyncDtoByUid(uid)
                if (dto == null) {
                    true // No longer exists locally (deleted after the upload was queued): nothing to do
                } else {
                    when (val result = SyncClient.pushBook(connection, dto)) {
                        is SyncPushResult.Applied -> {
                            if (dto.hasCoverPhoto) pushBookCoverIfPresent(connection, uid)
                            true
                        }
                        is SyncPushResult.Conflict -> { applyServerBookCopy(context, connection, result.serverCopy); true }
                        is SyncPushResult.Error -> false
                    }
                }
            }
        }

    /**
     * Best effort: if the cover upload fails, the book text has already been uploaded and the cover
     * is not retried separately (it has no outbox row of its own).
     */
    private suspend fun pushBookCoverIfPresent(connection: SyncConnection, bookUid: String) {
        val coverUri = repository.getRecipeBookCoverUri(bookUid) ?: return
        val bytes = PhotoStorage.readBytes(coverUri) ?: return
        SyncClient.uploadBookCover(connection, bookUid, bytes)
    }

    /**
     * Downloads and applies the cover of a remote book that was just created or edited, deleting
     * the previous file (if different) so it is not left orphaned.
     */
    private suspend fun applyBookCoverIfPresent(context: Context, connection: SyncConnection, bookId: Long, dto: BookSyncDto) {
        if (!dto.hasCoverPhoto) return
        val bytes = SyncClient.downloadBookCover(connection, dto.uid) ?: return
        val localUri = PhotoStorage.copyBytesToInternalStorage(context, bytes) ?: return
        val oldUri = repository.applyRemoteBookCover(bookId, localUri)
        oldUri?.let { PhotoStorage.deleteFile(it) }
    }

    private suspend fun pushRecipeChange(connection: SyncConnection, uid: String, changeType: SyncChangeType): Boolean =
        when (changeType) {
            SyncChangeType.DELETE -> when (val result = SyncClient.deleteRecipe(connection, uid, System.currentTimeMillis())) {
                is SyncPushResult.Applied -> true
                is SyncPushResult.Conflict -> { applyServerRecipeCopy(result.serverCopy); true }
                is SyncPushResult.Error -> false
            }
            SyncChangeType.UPSERT -> {
                val dto = repository.getRecipeSyncDtoByUid(uid)
                if (dto == null) {
                    true
                } else {
                    when (val result = SyncClient.pushRecipe(connection, dto)) {
                        is SyncPushResult.Applied -> {
                            pushRecipePhotosIfPresent(connection, uid)
                            true
                        }
                        is SyncPushResult.Conflict -> { applyServerRecipeCopy(result.serverCopy); true }
                        is SyncPushResult.Error -> false
                    }
                }
            }
        }

    /**
     * Best effort, same pattern as [pushBookCoverIfPresent]: every successful recipe upload
     * re-sends its current photos. This heals photos that were never uploaded for any reason, such
     * as a book linked before [RecipeRepository.linkBookToSyncConnection] queued them. A failed
     * photo is retried on the recipe's next upload.
     */
    private suspend fun pushRecipePhotosIfPresent(connection: SyncConnection, recipeUid: String) {
        repository.getRecipePhotosForPush(recipeUid).forEach { (photoUid, info) ->
            val bytes = PhotoStorage.readBytes(info.uri) ?: return@forEach
            SyncClient.uploadPhoto(
                connection, recipeUid, photoUid, bytes, "image/jpeg", info.isCover, info.position, System.currentTimeMillis()
            )
        }
    }

    /**
     * For a deletion, [PendingSyncChangeEntity.parentUid] is the only way to know which recipe the
     * photo belonged to, because its local row is already gone (see [RecipeRepository.saveRecipe]).
     * For an upsert the row still exists and is read with [RecipeRepository.getPhotoPushInfo].
     */
    private suspend fun pushPhotoChange(context: Context, connection: SyncConnection, change: PendingSyncChangeEntity, changeType: SyncChangeType): Boolean =
        when (changeType) {
            SyncChangeType.DELETE -> {
                val recipeUid = change.parentUid
                if (recipeUid == null) {
                    true
                } else {
                    when (val result = SyncClient.deletePhoto(connection, recipeUid, change.uid, System.currentTimeMillis())) {
                        is SyncPushResult.Applied -> true
                        is SyncPushResult.Conflict -> { applyServerPhotoCopy(context, connection, result.serverCopy); true }
                        is SyncPushResult.Error -> false
                    }
                }
            }
            SyncChangeType.UPSERT -> {
                val info = repository.getPhotoPushInfo(change.uid)
                val bytes = info?.let { PhotoStorage.readBytes(it.uri) }
                if (info == null || bytes == null) {
                    true // No longer exists locally (deleted after the upload was queued): nothing to do
                } else {
                    val result = SyncClient.uploadPhoto(
                        connection, info.recipeUid, change.uid, bytes, "image/jpeg", info.isCover, info.position, System.currentTimeMillis()
                    )
                    when (result) {
                        is SyncPushResult.Applied -> true
                        is SyncPushResult.Conflict -> { applyServerPhotoCopy(context, connection, result.serverCopy); true }
                        is SyncPushResult.Error -> false
                    }
                }
            }
        }

    /**
     * [BookSyncDto.deletedAt] tells an upsert from a tombstone. Both repository functions ignore
     * the case that is not theirs, so calling both is safe. Within a tombstone,
     * [BookSyncDto.unlinked] decides whether local content is deleted or only the sync link is cut.
     */
    private suspend fun applyServerBookCopy(context: Context, connection: SyncConnection, copy: BookSyncDto) {
        val bookId = repository.applyRemoteBookUpsert(connection.id, copy)
        if (bookId != null) applyBookCoverIfPresent(context, connection, bookId, copy)
        if (copy.unlinked) repository.applyRemoteBookUnlink(copy) else repository.applyRemoteBookDeletion(copy)
    }

    private suspend fun applyServerRecipeCopy(copy: RecipeSyncDto) {
        repository.applyRemoteRecipeUpsert(copy)
        if (!copy.unlinked) repository.applyRemoteRecipeDeletion(copy)
    }

    /**
     * Like [applyServerBookCopy] and [applyServerRecipeCopy], but a conflicting photo also has to
     * be downloaded or deleted on disk, not only its metadata.
     */
    private suspend fun applyServerPhotoCopy(context: Context, connection: SyncConnection, copy: PhotoMetaDto) {
        if (copy.unlinked) {
            return
        } else if (copy.deletedAt != null) {
            repository.applyRemotePhotoDeletion(copy)?.let { PhotoStorage.deleteFile(it) }
        } else if (!repository.hasLocalPhoto(copy.uid)) {
            val download = SyncClient.downloadPhoto(connection, copy.recipeUid, copy.uid) as? PhotoDownloadResult.Success ?: return
            val localUri = PhotoStorage.copyBytesToInternalStorage(context, download.bytes) ?: return
            if (!repository.applyRemotePhotoUpsert(copy, localUri)) PhotoStorage.deleteFile(localUri)
        }
    }

    private companion object {
        val syncLock = Mutex()
    }
}
