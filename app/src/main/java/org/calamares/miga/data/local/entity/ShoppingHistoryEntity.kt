package org.calamares.miga.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * History of items the user typed into the shopping list, one row per normalised name. It survives
 * clearing the list and feeds the suggestions: name as typed, last quantity and unit and how many
 * times it was used.
 */
@Entity(tableName = "shopping_history")
data class ShoppingHistoryEntity(
    @PrimaryKey val normalizedName: String,
    val name: String,
    val lastQuantity: Double?,
    val lastUnit: String?,
    val uses: Int,
    val lastUsedAt: Long
)
