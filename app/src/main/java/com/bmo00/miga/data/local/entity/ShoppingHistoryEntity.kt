package com.bmo00.miga.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Historial de artículos que el usuario ha añadido a mano a la lista de la compra (una fila por
 * nombre normalizado). Sobrevive a vaciar la lista y alimenta las sugerencias al escribir y los
 * chips de "frecuentes": nombre tal como lo escribió, última cantidad/unidad y cuántas veces.
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
