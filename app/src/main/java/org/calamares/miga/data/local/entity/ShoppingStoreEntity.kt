package org.calamares.miga.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * User supermarket with its aisle order ([aisleOrder]: category names, one per line). Local only,
 * not synced.
 */
@Entity(tableName = "shopping_stores")
data class ShoppingStoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Long,
    val aisleOrder: String,
    val createdAt: Long
)
