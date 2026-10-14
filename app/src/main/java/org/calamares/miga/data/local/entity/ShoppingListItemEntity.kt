package org.calamares.miga.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Shopping list item. Ingredients added from recipes and manual items ("aluminium foil") share this
 * table without a foreign key to any recipe: once merged, a row can combine quantities from several
 * recipes, and it is not affected if a recipe is edited or deleted later.
 *
 * The sync fields share the list with other devices through a server namespace (see
 * SyncConnectionEntity.syncShopping): [uid] is the identity across devices, [updatedAt] decides
 * last write wins, [deletedAt] is a tombstone (local deletion is always logical and purged after
 * upload) and [syncDirty] marks what is left to upload. Without sync they are inert.
 */
@Entity(
    tableName = "shopping_list_items",
    indices = [Index("normalizedName"), Index(value = ["uid"], unique = true)]
)
data class ShoppingListItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** name.trim().lowercase(), used to merge items and to match the ingredient catalogue. */
    val normalizedName: String,
    val quantity: Double?,
    val unit: String?,
    val checked: Boolean = false,
    val createdAt: Long,
    val uid: String = UUID.randomUUID().toString(),
    val updatedAt: Long = createdAt,
    val deletedAt: Long? = null,
    val syncDirty: Boolean = false,
    /** Product photo filled by the barcode scanner; only shown when the user enables it. */
    val imageUrl: String? = null,
    /** List the item belongs to (see ShoppingListEntity); "main" is the default list. */
    val listUid: String = "main",
    val addedBy: String? = null,
    val updatedBy: String? = null,
    /** Scanned product details (ProductInfo as JSON); null when the item was not scanned. */
    val productInfo: String? = null
)
