package org.calamares.miga.data.repository

import org.calamares.miga.data.model.KitchenEquipment

/**
 * Entries the app creates by itself on the first start (categories, kitchen equipment, ingredient
 * categories and ingredients), so the settings can tell them apart from the user's own.
 */
object DefaultCatalog {

    fun categories(language: String): List<String> = if (language == "es") listOf(
        "Entrantes", "Ensaladas", "Sopas y cremas", "Arroces", "Pastas", "Legumbres", "Verduras", "Carnes",
        "Pescados y mariscos", "Huevos", "Panes y masas", "Salsas", "Postres", "Bebidas"
    ) else listOf(
        "Starters", "Salads", "Soups", "Rice", "Pasta", "Legumes", "Vegetables", "Meat",
        "Fish and seafood", "Eggs", "Breads and doughs", "Sauces", "Desserts", "Drinks"
    )

    /** Categories created by older versions, still shown as defaults. */
    private val LEGACY_CATEGORIES = listOf("Postres", "Cremas", "Pastas", "Desserts", "Soups", "Pasta")

    private fun keys(names: List<String>): Set<String> = names.map(KitchenEquipment::key).toSet()

    private val categoryKeys by lazy { keys(categories("es") + categories("en") + LEGACY_CATEGORIES) }
    private val equipmentKeys by lazy { keys(KitchenEquipment.defaults("es") + KitchenEquipment.defaults("en")) }
    private val ingredientSeeds by lazy { IngredientCatalogSeed.forLanguage("es") + IngredientCatalogSeed.forLanguage("en") }
    private val ingredientCategoryKeys by lazy { keys(ingredientSeeds.map { it.first }) }
    private val ingredientKeys by lazy { keys(ingredientSeeds.flatMap { it.second }) }

    fun isDefaultCategory(name: String) = KitchenEquipment.key(name) in categoryKeys
    fun isDefaultEquipment(name: String) = KitchenEquipment.key(name) in equipmentKeys
    fun isDefaultIngredientCategory(name: String) = KitchenEquipment.key(name) in ingredientCategoryKeys
    fun isDefaultIngredient(name: String) = KitchenEquipment.key(name) in ingredientKeys
}
