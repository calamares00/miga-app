package org.calamares.miga.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipe_books")
data class RecipeBookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable identifier (UUID) independent of the local id, used by export/import and sync. */
    val uid: String,
    val name: String,
    val coverPhotoUri: String?,
    val createdAt: Long,
    /** Id of the installed pack (see PacksCatalogClient); null for a user's own editable book. */
    val packId: String? = null,
    /** Installed pack version; null when [packId] is null. */
    val packVersion: Int? = null,
    /**
     * Time of the last real change (name or cover), used by sync to decide which copy is newer.
     * Defaults to [createdAt].
     */
    val updatedAt: Long = createdAt,
    /**
     * Sync connection (see SyncConnectionEntity) the book belongs to; null for a local book. Unlike
     * a pack, a synced book is fully editable.
     */
    val syncConnectionId: Long? = null
)
