package com.bmo00.miga.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Supermercado del usuario con su orden de pasillos ([aisleOrder]: nombres de categoría, uno por línea). Solo local, no se sincroniza. */
@Entity(tableName = "shopping_stores")
data class ShoppingStoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Long,
    val aisleOrder: String,
    val createdAt: Long
)
