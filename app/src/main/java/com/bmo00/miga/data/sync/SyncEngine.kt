package com.bmo00.miga.data.sync

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
 * [RecipeRepository] junto a esas funciones.
 *
 * No sincroniza fotos todavía (solo el texto de libros/recetas); es la ampliación natural
 * siguiente, reutilizando [SyncClient.uploadPhoto]/[SyncClient.downloadPhoto], ya implementados
 * en el cliente aunque este motor todavía no los invoque.
 */
class SyncEngine(private val repository: RecipeRepository) {

    suspend fun syncConnection(connectionId: Long): SyncOutcome {
        val connection = repository.getSyncConnectionOnce(connectionId)
            ?: return SyncOutcome.Error("Conexión no encontrada")

        val pulled = when (val fetch = SyncClient.fetchChanges(connection, connection.lastSyncedRevision)) {
            is SyncFetchResult.Error -> {
                repository.markSyncError(connectionId, fetch.reason)
                return SyncOutcome.Error(fetch.reason)
            }
            is SyncFetchResult.Success -> {
                applyChanges(connectionId, fetch.changes)
                repository.markSyncSuccess(connectionId, fetch.changes.latestRevision)
                fetch.changes.books.size + fetch.changes.recipes.size
            }
        }

        var pushed = 0
        for (change in repository.getPendingSyncChanges(connectionId)) {
            if (pushOne(connection, change)) {
                repository.clearPendingSyncChange(change.id)
                pushed++
            }
        }

        return SyncOutcome.Success(pulled, pushed)
    }

    private suspend fun applyChanges(connectionId: Long, changes: ChangesResponseDto) {
        changes.books.filter { it.deletedAt == null }.forEach { repository.applyRemoteBookUpsert(connectionId, it) }
        changes.recipes.forEach { dto ->
            if (dto.deletedAt != null) repository.applyRemoteRecipeDeletion(dto) else repository.applyRemoteRecipeUpsert(dto)
        }
        changes.books.filter { it.deletedAt != null }.forEach { repository.applyRemoteBookDeletion(it) }
    }

    /** true si se ha resuelto (subido con éxito, o se ha aplicado un conflicto) y puede quitarse
     *  del outbox; false si hay que reintentarlo en el próximo sync (fallo de red/servidor). */
    private suspend fun pushOne(connection: SyncConnection, change: PendingSyncChangeEntity): Boolean {
        val entityType = runCatching { SyncEntityType.valueOf(change.entityType) }.getOrNull() ?: return true
        val changeType = runCatching { SyncChangeType.valueOf(change.changeType) }.getOrNull() ?: return true
        return when (entityType) {
            SyncEntityType.BOOK -> pushBookChange(connection, change.uid, changeType)
            SyncEntityType.RECIPE -> pushRecipeChange(connection, change.uid, changeType)
            // No implementado todavía (ver cabecera de esta clase): se descarta sin reintentar.
            SyncEntityType.PHOTO -> true
        }
    }

    private suspend fun pushBookChange(connection: SyncConnection, uid: String, changeType: SyncChangeType): Boolean =
        when (changeType) {
            SyncChangeType.DELETE -> when (val result = SyncClient.deleteBook(connection, uid, System.currentTimeMillis())) {
                is SyncPushResult.Applied -> true
                is SyncPushResult.Conflict -> { applyServerBookCopy(connection.id, result.serverCopy); true }
                is SyncPushResult.Error -> false
            }
            SyncChangeType.UPSERT -> {
                val dto = repository.getRecipeBookSyncDtoByUid(uid)
                if (dto == null) {
                    true // ya no existe localmente (se borró después de encolar la subida): nada que hacer
                } else {
                    when (val result = SyncClient.pushBook(connection, dto)) {
                        is SyncPushResult.Applied -> true
                        is SyncPushResult.Conflict -> { applyServerBookCopy(connection.id, result.serverCopy); true }
                        is SyncPushResult.Error -> false
                    }
                }
            }
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

    /** [BookSyncDto.deletedAt] decide si es un alta/edición o un tombstone; las dos funciones se
     *  autoprotegen (cada una ignora el caso que no le corresponde), así que llamar a ambas es
     *  seguro y evita duplicar esa comprobación aquí. */
    private suspend fun applyServerBookCopy(connectionId: Long, copy: BookSyncDto) {
        repository.applyRemoteBookUpsert(connectionId, copy)
        repository.applyRemoteBookDeletion(copy)
    }

    private suspend fun applyServerRecipeCopy(copy: RecipeSyncDto) {
        repository.applyRemoteRecipeUpsert(copy)
        repository.applyRemoteRecipeDeletion(copy)
    }
}
