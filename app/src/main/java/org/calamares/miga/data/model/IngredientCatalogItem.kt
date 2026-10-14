package org.calamares.miga.data.model

/** Ingredient catalogue row with its category name resolved (null means uncategorized). */
data class IngredientCatalogItem(
    val id: Long,
    val name: String,
    val categoryId: Long?,
    val categoryName: String?
)
