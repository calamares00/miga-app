package com.bmo00.miga.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SyncEntityType { BOOK, RECIPE, PHOTO }

enum class SyncChangeType { UPSERT, DELETE }

/**
 * "Outbox" de sincronización: una fila por cada libro/receta/foto de un libro sincronizado que
 * se ha creado, editado o borrado localmente y todavía no se ha confirmado subida al servidor.
 * Se inserta en los mismos sitios donde ya se guarda/borra localmente (saveRecipe,
 * saveRecipeBook, deleteRecipe, deleteRecipeBook) y se borra en cuanto el motor de
 * sincronización confirma la subida - sobrevive a que la app se cierre o esté sin red mientras
 * tanto, a diferencia de un simple reintento en memoria.
 */
@Entity(tableName = "pending_sync_changes", indices = [Index("syncConnectionId")])
data class PendingSyncChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncConnectionId: Long,
    /** Guardado como [SyncEntityType.name] - mismo patrón que el resto de enums de la app
     *  (ej. RecipeEntity.difficulty), sin TypeConverters. */
    val entityType: String,
    val uid: String,
    /** Guardado como [SyncChangeType.name]. */
    val changeType: String,
    val createdAt: Long
)
