package org.calamares.miga.data.model

import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.repository.DefaultCatalog

/** The kinds of labels managed in Settings > Manage content. */
enum class ContentKind {
    CATEGORY,
    EQUIPMENT,
    TAG,
    INGREDIENT,
    INGREDIENT_CATEGORY;

    val title: String
        get() = when (this) {
            CATEGORY -> L10n.str(R.string.categories)
            EQUIPMENT -> L10n.str(R.string.utensils)
            TAG -> L10n.str(R.string.tags)
            INGREDIENT -> L10n.str(R.string.ingredients)
            INGREDIENT_CATEGORY -> L10n.str(R.string.ingredient_categories)
        }

    /** Whether [name] is one of the entries the app brings, rather than one the user created. */
    fun isDefault(name: String): Boolean = when (this) {
        CATEGORY -> DefaultCatalog.isDefaultCategory(name)
        EQUIPMENT -> DefaultCatalog.isDefaultEquipment(name)
        TAG -> false
        INGREDIENT -> DefaultCatalog.isDefaultIngredient(name)
        INGREDIENT_CATEGORY -> DefaultCatalog.isDefaultIngredientCategory(name)
    }

    /** "3 recipes" or, for ingredient categories, "3 ingredients". */
    fun usageLabel(count: Int): String = when (this) {
        INGREDIENT_CATEGORY -> if (count == 1) L10n.str(R.string.ingredient_count_one) else L10n.str(R.string.ingredient_count_many, count)
        else -> if (count == 1) L10n.str(R.string.recipe_count_one) else L10n.str(R.string.recipe_count_many, count)
    }
}

/** One entry of a [ContentKind] list: [usage] recipes (or ingredients) use it. */
data class ContentItem(val kind: ContentKind, val id: Long, val name: String, val usage: Int) {
    val isDefault: Boolean get() = kind.isDefault(name)
}
