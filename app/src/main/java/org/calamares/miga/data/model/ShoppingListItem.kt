package org.calamares.miga.data.model

import org.calamares.miga.L10n
import org.calamares.miga.R

/**
 * "Sin categoría" para un ingrediente de la lista de la compra sin match en el catálogo (ver
 * data/local/dao/IngredientCategoryDao.kt). Constante propia, no [UNCATEGORIZED_CATEGORY_LABEL]
 * de RecipeFiltering.kt: son dos taxonomías distintas (categoría de receta vs. de ingrediente)
 * que ya conviven hoy sin compartir constante (ManageIngredientsScreen.kt tiene el mismo literal
 * hardcodeado aparte).
 */
const val UNCATEGORIZED_INGREDIENT_LABEL = "Sin categoría"

/** Uid de la lista de la compra por defecto: existe siempre, sin fila propia, y es la que usaban todos los artículos antes de poder tener varias. */
const val DEFAULT_SHOPPING_LIST_UID = "main"
val DEFAULT_SHOPPING_LIST_NAME: String get() = L10n.str(R.string.compra)

data class ShoppingListItem(
    val id: Long,
    val name: String,
    val quantity: Double?,
    val unit: String?,
    val checked: Boolean,
    val categoryName: String,
    val imageUrl: String? = null,
    val uid: String = "",
    /** Quién lo añadió / quién lo marcó por última vez, si lo indicó (lista compartida); null si no consta. */
    val addedBy: String? = null,
    val updatedBy: String? = null,
    /** Ficha de Open Food Facts si el artículo se añadió con el escáner. */
    val productInfo: ProductInfo? = null
)

/** Una lista de la compra (la por defecto o una adicional creada por el usuario). */
data class ShoppingListInfo(val uid: String, val name: String)

data class ShoppingListGroup(
    val categoryName: String,
    val items: List<ShoppingListItem>
)
