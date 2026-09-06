package com.bmo00.miga.data.model

/**
 * "Sin categoría" para un ingrediente de la lista de la compra sin match en el catálogo (ver
 * data/local/dao/IngredientCategoryDao.kt). Constante propia, no [UNCATEGORIZED_CATEGORY_LABEL]
 * de RecipeFiltering.kt: son dos taxonomías distintas (categoría de receta vs. de ingrediente)
 * que ya conviven hoy sin compartir constante (ManageIngredientsScreen.kt tiene el mismo literal
 * hardcodeado aparte).
 */
const val UNCATEGORIZED_INGREDIENT_LABEL = "Sin categoría"

data class ShoppingListItem(
    val id: Long,
    val name: String,
    val quantity: Double?,
    val unit: String?,
    val checked: Boolean,
    val categoryName: String
)

data class ShoppingListGroup(
    val categoryName: String,
    val items: List<ShoppingListItem>
)
