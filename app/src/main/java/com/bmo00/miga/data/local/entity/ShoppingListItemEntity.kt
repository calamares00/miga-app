package com.bmo00.miga.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Fila de la lista de la compra persistente (una sola lista, no varias). Los ingredientes
 * añadidos desde una receta y los artículos manuales (p.ej. "papel de aluminio") viven en la
 * misma tabla, sin FK a ninguna receta: una vez agregada, una fila puede combinar cantidades de
 * más de una receta (ver RecipeRepository.addIngredientsToShoppingList), así que "receta de
 * origen" no es un concepto representable en una sola fila. Por el mismo motivo, esta fila no se
 * ve afectada si la receta que la originó se edita o se borra después.
 *
 * Los campos de sincronización ([uid], [updatedAt], [deletedAt], [syncDirty]) sirven para
 * compartir la lista con otras apps Miga a través de un namespace del servidor (ver
 * SyncConnection.syncShopping): [uid] es la identidad estable entre dispositivos, [updatedAt] decide
 * "última escritura gana", [deletedAt] es un tombstone (el borrado local es siempre lógico y se
 * purga tras subirse) y [syncDirty] marca lo que falta por subir. Sin sincronización activa, solo
 * añaden metadatos inertes.
 */
@Entity(
    tableName = "shopping_list_items",
    indices = [Index("normalizedName"), Index(value = ["uid"], unique = true)]
)
data class ShoppingListItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** name.trim().lowercase(), usado tanto para agregación como para casar contra ingredient_catalog. */
    val normalizedName: String,
    val quantity: Double?,
    val unit: String?,
    val checked: Boolean = false,
    val createdAt: Long,
    val uid: String = UUID.randomUUID().toString(),
    val updatedAt: Long = createdAt,
    val deletedAt: Long? = null,
    val syncDirty: Boolean = false,
    /** Foto del producto (la rellena el escáner de código de barras); solo se muestra si el usuario lo activa. */
    val imageUrl: String? = null
)
