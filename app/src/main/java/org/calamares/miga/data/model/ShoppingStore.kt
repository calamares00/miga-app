package org.calamares.miga.data.model

import org.calamares.miga.L10n
import java.text.Normalizer

/**
 * A user's supermarket: a name, a colour and the order in which the categories (aisles) are
 * visited.
 */
data class ShoppingStore(
    val id: Long,
    val name: String,
    val argb: Long,
    val aisleOrder: List<String>
)

/** Pure logic for a store's aisle order. */
object ShoppingAisleOrder {

    /**
     * Typical supermarket route: fresh food first, frozen food, drinks and cleaning products last.
     */
    val TYPICAL_ORDER: List<String>
        get() = if (L10n.locale().language == "es") TYPICAL_ORDER_ES else TYPICAL_ORDER_EN

    private val TYPICAL_ORDER_ES = listOf(
        "Frutas", "Verduras", "Carnes", "Pescados", "Mariscos", "Huevos", "Lácteos", "Fermentados",
        "Cereales", "Legumbres", "Harinas", "Repostería", "Azúcares y edulcorantes",
        "Conservas", "Salsas y condimentos", "Aceites y grasas", "Hierbas y especias",
        "Frutos secos", "Semillas", "Bebidas", "Otros", UNCATEGORIZED_INGREDIENT_LABEL
    )

    private val TYPICAL_ORDER_EN = listOf(
        "Fruit", "Vegetables", "Meat", "Fish", "Seafood", "Eggs", "Dairy", "Fermented",
        "Grains", "Pulses", "Flours", "Baking", "Sugars and sweeteners",
        "Tinned and jarred", "Sauces and condiments", "Oils and fats", "Herbs and spices",
        "Nuts", "Seeds", "Drinks", "Other", UNCATEGORIZED_INGREDIENT_LABEL
    )

    /** Names suggested when creating a store; plain text only, no logos or trademarks. */
    val SUGGESTED_NAMES = listOf("Mercadona", "Lidl", "Aldi", "Dia", "Carrefour", "Eroski", "Alcampo", "Deza")

    /** Accent colours the user can choose from (ARGB). */
    val PALETTE = longArrayOf(0xFFC4623E, 0xFF3E8EB5, 0xFF5C9E4B, 0xFF8E6BB5, 0xFFD1709F, 0xFFD9962B, 0xFF3FA39B, 0xFF7B8794)

    /**
     * Sorts [groups] by [order]: categories present in [order] come first in that order, then the
     * rest alphabetically with the uncategorized group last. An empty [order] leaves the list
     * untouched.
     */
    fun sort(groups: List<ShoppingListGroup>, order: List<String>): List<ShoppingListGroup> {
        if (order.isEmpty()) return groups
        val position = order.withIndex().associate { normalize(it.value) to it.index }
        return groups.sortedWith(
            compareBy<ShoppingListGroup>(
                { position[normalize(it.categoryName)] ?: Int.MAX_VALUE },
                { it.categoryName == UNCATEGORIZED_INGREDIENT_LABEL },
                { it.categoryName.lowercase() }
            )
        )
    }

    /**
     * Returns [order] followed by any category from [allCategories] not yet in it, without
     * duplicates.
     */
    fun complete(order: List<String>, allCategories: Collection<String>): List<String> {
        val seen = LinkedHashMap<String, String>()
        (order + allCategories + UNCATEGORIZED_INGREDIENT_LABEL).forEach { name ->
            val key = normalize(name)
            if (key.isNotEmpty() && key !in seen) seen[key] = name
        }
        return seen.values.toList()
    }

    /**
     * Swaps the element at [index] with its neighbour in [direction] (-1 up, +1 down). Returns
     * [order] unchanged when that is not possible.
     */
    fun move(order: List<String>, index: Int, direction: Int): List<String> {
        val target = index + direction
        if (index !in order.indices || target !in order.indices) return order
        return order.toMutableList().also { list ->
            val item = list[index]
            list[index] = list[target]
            list[target] = item
        }
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}
