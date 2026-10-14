package org.calamares.miga.data.model

import org.calamares.miga.L10n
import org.calamares.miga.R

/**
 * Stored category name for shopping items that match no catalogue ingredient (see
 * IngredientCategoryDao). It is persisted data, so it stays in Spanish; use [displayCategoryName]
 * to show it. It is separate from the recipe-category label in RecipeFiltering.kt because they
 * belong to different taxonomies.
 */
const val UNCATEGORIZED_INGREDIENT_LABEL = "Sin categoría"

/**
 * Category name for display: the uncategorized label is translated, any other name is user data and
 * shown as is.
 */
fun displayCategoryName(name: String): String =
    if (name == UNCATEGORIZED_INGREDIENT_LABEL) L10n.str(R.string.uncategorized) else name

/**
 * Uid of the default shopping list. It always exists without its own row, and every item used it
 * before multiple lists were supported.
 */
const val DEFAULT_SHOPPING_LIST_UID = "main"
val DEFAULT_SHOPPING_LIST_NAME: String get() = L10n.str(R.string.shopping)

data class ShoppingListItem(
    val id: Long,
    val name: String,
    val quantity: Double?,
    val unit: String?,
    val checked: Boolean,
    val categoryName: String,
    val imageUrl: String? = null,
    val uid: String = "",
    /** Who added the item and who last checked it on a shared list, or null when unknown. */
    val addedBy: String? = null,
    val updatedBy: String? = null,
    /** Open Food Facts product sheet when the item was added with the barcode scanner. */
    val productInfo: ProductInfo? = null
)

/** A shopping list: the default one or an extra list created by the user. */
data class ShoppingListInfo(val uid: String, val name: String)

data class ShoppingListGroup(
    val categoryName: String,
    val items: List<ShoppingListItem>
)
