package com.bmo00.miga.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Fila de la lista de la compra persistente (una sola lista, no varias). Los ingredientes
 * añadidos desde una receta y los artículos manuales (p.ej. "papel de aluminio") viven en la
 * misma tabla, sin FK a ninguna receta: una vez agregada, una fila puede combinar cantidades de
 * más de una receta (ver RecipeRepository.addIngredientsToShoppingList), así que "receta de
 * origen" no es un concepto representable en una sola fila. Por el mismo motivo, esta fila no se
 * ve afectada si la receta que la originó se edita o se borra después.
 */
@Entity(tableName = "shopping_list_items", indices = [Index("normalizedName")])
data class ShoppingListItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** name.trim().lowercase(), usado tanto para agregación como para casar contra ingredient_catalog. */
    val normalizedName: String,
    val quantity: Double?,
    val unit: String?,
    val checked: Boolean = false,
    val createdAt: Long
)
