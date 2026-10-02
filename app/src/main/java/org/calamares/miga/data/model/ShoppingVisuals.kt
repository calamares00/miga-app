package org.calamares.miga.data.model

import java.text.Normalizer

/** Aspecto de una categoría de la lista de la compra: un emoji y un color de acento (ARGB). */
data class CategoryStyle(val emoji: String, val argb: Long)

/**
 * Iconos y colores de la lista de la compra, sin red ni recursos: emojis del sistema y una paleta
 * fija. Lógica pura (sin Android) para poder probarla con JUnit.
 */
object ShoppingVisuals {

    private val categoryStyles: Map<String, CategoryStyle> = mapOf(
        "frutas" to CategoryStyle("🍎", 0xFFE5604B),
        "verduras" to CategoryStyle("🥦", 0xFF5C9E4B),
        "legumbres" to CategoryStyle("🫘", 0xFFA67B4F),
        "cereales" to CategoryStyle("🌾", 0xFFC9A227),
        "carnes" to CategoryStyle("🥩", 0xFFC0504D),
        "pescados" to CategoryStyle("🐟", 0xFF3E8EB5),
        "mariscos" to CategoryStyle("🦐", 0xFFE08A5B),
        "lacteos" to CategoryStyle("🥛", 0xFF6FA8DC),
        "huevos" to CategoryStyle("🥚", 0xFFD9A441),
        "frutos secos" to CategoryStyle("🥜", 0xFF9C6B3F),
        "semillas" to CategoryStyle("🌻", 0xFFCFA12B),
        "aceites y grasas" to CategoryStyle("🫒", 0xFF8A9A2B),
        "hierbas y especias" to CategoryStyle("🌿", 0xFF4F9A6B),
        "salsas y condimentos" to CategoryStyle("🧂", 0xFFB5543C),
        "harinas" to CategoryStyle("🍞", 0xFFC79A5B),
        "azucares y edulcorantes" to CategoryStyle("🍯", 0xFFD9962B),
        "reposteria" to CategoryStyle("🧁", 0xFFD1709F),
        "conservas" to CategoryStyle("🥫", 0xFF7F8C8D),
        "fermentados" to CategoryStyle("🫙", 0xFF8E6BB5),
        "bebidas" to CategoryStyle("🥤", 0xFF3FA39B),
        "otros" to CategoryStyle("🛒", 0xFF7B8794)
    )

    private val fallbackPalette = longArrayOf(0xFF6B8EAD, 0xFFAD6B8E, 0xFF8EAD6B, 0xFFAD8E6B, 0xFF6BAD9A, 0xFF9A6BAD)

    /** Estilo de [categoryName]; las categorías creadas por el usuario reciben un color estable según su nombre. */
    fun categoryStyle(categoryName: String): CategoryStyle {
        val key = normalize(categoryName)
        categoryStyles[key]?.let { return it }
        if (key == normalize(UNCATEGORIZED_INGREDIENT_LABEL)) return categoryStyles.getValue("otros")
        return CategoryStyle("🛍️", fallbackPalette[Math.floorMod(key.hashCode(), fallbackPalette.size)])
    }

    /** Palabras (sin tildes, en minúsculas) -> emoji; la primera coincidencia por palabra completa gana, así que lo más específico va antes. */
    private val itemEmojis: List<Pair<List<String>, String>> = listOf(
        listOf("tomate", "tomates") to "🍅",
        listOf("leche") to "🥛",
        listOf("queso", "quesos") to "🧀",
        listOf("mantequilla") to "🧈",
        listOf("huevo", "huevos") to "🥚",
        listOf("pan", "baguette", "barra") to "🥖",
        listOf("manzana", "manzanas") to "🍎",
        listOf("pera", "peras") to "🍐",
        listOf("platano", "platanos", "banana") to "🍌",
        listOf("naranja", "naranjas", "mandarina", "mandarinas") to "🍊",
        listOf("limon", "limones", "lima") to "🍋",
        listOf("fresa", "fresas") to "🍓",
        listOf("uva", "uvas") to "🍇",
        listOf("sandia") to "🍉",
        listOf("melon") to "🍈",
        listOf("pina") to "🍍",
        listOf("melocoton", "melocotones") to "🍑",
        listOf("cereza", "cerezas") to "🍒",
        listOf("kiwi", "kiwis") to "🥝",
        listOf("aguacate", "aguacates") to "🥑",
        listOf("coco") to "🥥",
        listOf("zanahoria", "zanahorias") to "🥕",
        listOf("patata", "patatas") to "🥔",
        listOf("cebolla", "cebollas", "cebolleta", "puerro") to "🧅",
        listOf("ajo", "ajos") to "🧄",
        listOf("pimiento", "pimientos") to "🫑",
        listOf("berenjena", "berenjenas") to "🍆",
        listOf("pepino", "pepinos") to "🥒",
        listOf("lechuga", "escarola", "canonigos", "rucula") to "🥬",
        listOf("brocoli", "coliflor") to "🥦",
        listOf("maiz") to "🌽",
        listOf("champinon", "champinones", "seta", "setas") to "🍄",
        listOf("pollo", "pavo") to "🍗",
        listOf("jamon", "bacon", "panceta", "beicon") to "🥓",
        listOf("ternera", "cerdo", "carne", "filete", "chuleta", "lomo") to "🥩",
        listOf("salmon", "atun", "merluza", "bacalao", "pescado", "lubina", "dorada") to "🐟",
        listOf("gamba", "gambas", "langostino", "langostinos", "marisco") to "🦐",
        listOf("arroz") to "🍚",
        listOf("pasta", "espagueti", "espaguetis", "macarrones", "fideos") to "🍝",
        listOf("agua") to "💧",
        listOf("cafe") to "☕",
        listOf("te", "infusion") to "🍵",
        listOf("cerveza", "cervezas") to "🍺",
        listOf("vino") to "🍷",
        listOf("zumo", "refresco") to "🧃",
        listOf("aceite") to "🫒",
        listOf("sal") to "🧂",
        listOf("azucar", "miel") to "🍯",
        listOf("chocolate") to "🍫",
        listOf("galleta", "galletas") to "🍪",
        listOf("yogur", "yogures") to "🥣",
        listOf("helado", "helados") to "🍨",
        listOf("papel", "servilletas", "servilleta") to "🧻",
        listOf("detergente", "jabon", "lejia", "fregasuelos") to "🧴"
    )

    /** Emoji de un artículo: el de su palabra más reconocible o, si no hay, el de su categoría. */
    fun itemEmoji(name: String, categoryName: String): String {
        val words = normalize(name).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        for ((keywords, emoji) in itemEmojis) {
            if (words.any { it in keywords }) return emoji
        }
        return categoryStyle(categoryName).emoji
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}
