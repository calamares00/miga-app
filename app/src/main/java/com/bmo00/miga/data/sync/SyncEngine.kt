package com.bmo00.miga.data.sync

import android.content.Context
import com.bmo00.miga.data.local.PhotoStorage
import com.bmo00.miga.data.local.entity.PendingSyncChangeEntity
import com.bmo00.miga.data.local.entity.SyncChangeType
import com.bmo00.miga.data.local.entity.SyncEntityType
import com.bmo00.miga.data.model.SyncConnection
import com.bmo00.miga.data.repository.RecipeRepository

sealed interface SyncOutcome {
    data class Success(val pulled: Int, val pushed: Int) : SyncOutcome
    data class Error(val reason: String) : SyncOutcome
}

/**
 * Coordina la sincronización de una conexión: baja los cambios del servidor desde la última
 * revisión conocida (aplicando "última escritura gana" por `updatedAt`, ver
 * [RecipeRepository.applyRemoteBookUpsert]/[RecipeRepository.applyRemoteRecipeUpsert]) y sube el
 * outbox de cambios locales pendientes, resolviendo cualquier 409 con la copia del servidor.
 *
 * Los libros se aplican en dos pasadas (altas antes que las recetas, bajas después) para no
 * chocar con la restricción de clave foránea de `recipes.recipeBookId` - ver el comentario en
 * [RecipeRepository] junto a esas funciones. Las fotos (de receta y la portada de cada libro) se
 * aplican después de las recetas (para que la receta a la que pertenecen ya exista localmente) y
 * antes de las bajas de libro.
 *
 * Necesita un [Context] únicamente para guardar/borrar el fichero físico de una foto (o portada)
 * descargada o borrada (ver [PhotoStorage]), siguiendo el mismo convenio del resto de la app de
 * pasar el Context a la función en vez de guardarlo en una clase que no es un componente Android.
 */
class SyncEngine(private val repository: RecipeRepository) {

    suspend fun syncConnection(context: Context, connectionId: Long): SyncOutcome {
        val connection = repository.getSyncConnectionOnce(connectionId)
            ?: return SyncOutcome.Error("Conexión no encontrada")

        val pulled = when (val fetch = SyncClient.fetchChanges(connection, connection.lastSyncedRevision)) {
            is SyncFetchResult.Error -> {
                repository.markSyncError(connectionId, fetch.reason)
                return SyncOutcome.Error(fetch.reason)
            }
            is SyncFetchResult.Success -> {
                applyChanges(context, connection, fetch.changes)
                repository.markSyncSuccess(connectionId, fetch.changes.latestRevision)
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

        return SyncOutcome.Success(pulled, pushed)
    }

    private suspend fun applyChanges(context: Context, connection: SyncConnection, changes: ChangesResponseDto) {
        changes.books.filter { it.deletedAt == null }.forEach { dto ->
            val bookId = repository.applyRemoteBookUpsert(connection.id, dto)
            if (bookId != null) applyBookCoverIfPresent(context, connection, bookId, dto)
        }
        // Un tombstone "desvinculado" (dto.unlinked, ver SyncDtos.kt) no debe borrar nada local:
        // la fila cascada de una receta/foto bajo un libro desvinculado se ignora del todo (el
        // libro se queda con su syncConnectionId a null más abajo, la receta/foto no necesita
        // ningún cambio propio porque nunca tuvo vínculo de sync individual, solo el heredado de
        // pertenecer a un libro sincronizado).
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
                !repository.hasLocalPhoto(dto.uid) -> {
                    val bytes = SyncClient.downloadPhoto(connection, dto.recipeUid, dto.uid) ?: return@forEach
                    val localUri = PhotoStorage.copyBytesToInternalStorage(context, bytes) ?: return@forEach
                    if (!repository.applyRemotePhotoUpsert(dto, localUri)) PhotoStorage.deleteFile(localUri)
                }
            }
        }
        changes.books.filter { it.deletedAt != null }.forEach { dto ->
            if (dto.unlinked) repository.applyRemoteBookUnlink(dto) else repository.applyRemoteBookDeletion(dto)
        }
    }

    /** true si se ha resuelto (subido con éxito, o se ha aplicado un conflicto) y puede quitarse
     *  del outbox; false si hay que reintentarlo en el próximo sync (fallo de red/servidor). */
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
                    true // ya no existe localmente (se borró después de encolar la subida): nada que hacer
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

    /** Best-effort: si falla la subida de la portada, el texto del libro ya se ha subido igualmente
     *  y no se reintenta por separado (no hay una fila propia en el outbox solo para la portada). */
    private suspend fun pushBookCoverIfPresent(connection: SyncConnection, bookUid: String) {
        val coverUri = repository.getRecipeBookCoverUri(bookUid) ?: return
        val bytes = PhotoStorage.readBytes(coverUri) ?: return
        SyncClient.uploadBookCover(connection, bookUid, bytes)
    }

    /** Descarga y aplica la portada de un libro remoto recién dado de alta/editado, borrando el
     *  fichero físico anterior (si había uno distinto) para no dejarlo huérfano. */
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
                        is SyncPushResult.Applied -> true
                        is SyncPushResult.Conflict -> { applyServerRecipeCopy(result.serverCopy); true }
                        is SyncPushResult.Error -> false
                    }
                }
            }
        }

    /** Para un borrado, [PendingSyncChangeEntity.parentUid] es la única forma de saber a qué
     *  receta pertenecía la foto: su fila local ya no existe (se borró junto con el resto de fotos
     *  de la receta al guardarla, ver [RecipeRepository.saveRecipe]). Para un alta, en cambio, la
     *  fila todavía existe y se consulta con [RecipeRepository.getPhotoPushInfo]. */
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
                    true // ya no existe localmente (se borró después de encolar la subida): nada que hacer
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

    /** [BookSyncDto.deletedAt] decide si es un alta/edición o un tombstone; las dos funciones se
     *  autoprotegen (cada una ignora el caso que no le corresponde), así que llamar a ambas es
     *  seguro y evita duplicar esa comprobación aquí. [BookSyncDto.unlinked] decide, dentro de un
     *  tombstone, si además hay que borrar el contenido local o solo cortar el vínculo de sync. */
    private suspend fun applyServerBookCopy(context: Context, connection: SyncConnection, copy: BookSyncDto) {
        val bookId = repository.applyRemoteBookUpsert(connection.id, copy)
        if (bookId != null) applyBookCoverIfPresent(context, connection, bookId, copy)
        if (copy.unlinked) repository.applyRemoteBookUnlink(copy) else repository.applyRemoteBookDeletion(copy)
    }

    private suspend fun applyServerRecipeCopy(copy: RecipeSyncDto) {
        repository.applyRemoteRecipeUpsert(copy)
        if (!copy.unlinked) repository.applyRemoteRecipeDeletion(copy)
    }

    /** Igual que [applyServerBookCopy]/[applyServerRecipeCopy], pero una foto en conflicto necesita
     *  además descargarse (o borrarse) físicamente, no solo aplicar su metadato. */
    private suspend fun applyServerPhotoCopy(context: Context, connection: SyncConnection, copy: PhotoMetaDto) {
        if (copy.unlinked) {
            return
        } else if (copy.deletedAt != null) {
            repository.applyRemotePhotoDeletion(copy)?.let { PhotoStorage.deleteFile(it) }
        } else if (!repository.hasLocalPhoto(copy.uid)) {
            val bytes = SyncClient.downloadPhoto(connection, copy.recipeUid, copy.uid) ?: return
            val localUri = PhotoStorage.copyBytesToInternalStorage(context, bytes) ?: return
            if (!repository.applyRemotePhotoUpsert(copy, localUri)) PhotoStorage.deleteFile(localUri)
        }
    }
}
