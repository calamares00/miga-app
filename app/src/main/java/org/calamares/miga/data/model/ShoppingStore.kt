package org.calamares.miga.data.model

import org.calamares.miga.L10n
import java.text.Normalizer

/** Supermercado del usuario: un nombre, un color y el orden en que recorre las categorías (sus pasillos). */
data class ShoppingStore(
    val id: Long,
    val name: String,
    val argb: Long,
    val aisleOrder: List<String>
)

/** Lógica pura (sin Android) del orden de pasillos de una tienda. */
object ShoppingAisleOrder {

    /** Recorrido típico de un súper: frescos primero, congelados/bebidas/limpieza al final. */
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

    /** Nombres sugeridos al crear una tienda; solo texto, sin logos ni marcas. */
    val SUGGESTED_NAMES = listOf("Mercadona", "Lidl", "Aldi", "Dia", "Carrefour", "Eroski", "Alcampo", "Deza")

    /** Colores de acento para elegir (ARGB). */
    val PALETTE = longArrayOf(0xFFC4623E, 0xFF3E8EB5, 0xFF5C9E4B, 0xFF8E6BB5, 0xFFD1709F, 0xFFD9962B, 0xFF3FA39B, 0xFF7B8794)

    /**
     * Ordena los grupos según [order]: primero los que aparecen en la lista, en ese orden; después el resto
     * alfabéticamente con "Sin categoría" al final. Con [order] vacío no toca nada.
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

    /** [order] más las categorías de [allCategories] que aún no estén en él (al final), sin duplicados. */
    fun complete(order: List<String>, allCategories: Collection<String>): List<String> {
        val seen = LinkedHashMap<String, String>()
        (order + allCategories + UNCATEGORIZED_INGREDIENT_LABEL).forEach { name ->
            val key = normalize(name)
            if (key.isNotEmpty() && key !in seen) seen[key] = name
        }
        return seen.values.toList()
    }

    /** Intercambia el elemento [index] con su vecino en [direction] (-1 arriba, +1 abajo); sin cambios si no se puede. */
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
