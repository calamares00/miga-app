package org.calamares.miga.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SyncEntityType { BOOK, RECIPE, PHOTO }

enum class SyncChangeType { UPSERT, DELETE }

/**
 * Sync outbox: one row per book, recipe or photo of a synced book that was created, edited or
 * deleted locally and whose upload is not confirmed yet. Rows are written where the local change is
 * saved (saveRecipe, saveRecipeBook, deleteRecipe, deleteRecipeBook) and removed once the sync
 * engine confirms the upload, so pending changes survive the app being closed or offline.
 */
@Entity(tableName = "pending_sync_changes", indices = [Index("syncConnectionId")])
data class PendingSyncChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncConnectionId: Long,
    /**
     * Stored as [SyncEntityType.name], like every other enum in the database, without
     * TypeConverters.
     */
    val entityType: String,
    val uid: String,
    /** Stored as [SyncChangeType.name]. */
    val changeType: String,
    val createdAt: Long,
    /**
     * Only for [SyncEntityType.PHOTO]: uid of the recipe the photo belongs to, needed to upload a
     * deletion after the photo row is gone. Null otherwise.
     */
    val parentUid: String? = null
)
