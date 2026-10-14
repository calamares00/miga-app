package org.calamares.miga.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Additional shopping list (Home, Party...); the default list ("main") has no row. Same sync fields
 * as the items: [updatedAt] for last write wins, [deletedAt] as a tombstone and [syncDirty] for
 * what is left to upload.
 */
@Entity(tableName = "shopping_lists", indices = [Index(value = ["uid"], unique = true)])
data class ShoppingListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val deletedAt: Long? = null,
    val syncDirty: Boolean = false
)
