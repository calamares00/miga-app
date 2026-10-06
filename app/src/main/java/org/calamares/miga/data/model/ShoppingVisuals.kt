package org.calamares.miga.data.model

import java.text.Normalizer

/** Look of a shopping list category: an emoji and an accent colour (ARGB). */
data class CategoryStyle(val emoji: String, val argb: Long)

/**
 * Shopping list icons and colours without network or resources: system emojis and a fixed palette.
 * Pure logic so it can be unit tested.
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
        "otros" to CategoryStyle("🛒", 0xFF7B8794),
        // The same categories with the English catalogue names (see IngredientCatalogSeedEn).
        "fruit" to CategoryStyle("🍎", 0xFFE5604B),
        "vegetables" to CategoryStyle("🥦", 0xFF5C9E4B),
        "pulses" to CategoryStyle("🫘", 0xFFA67B4F),
        "grains" to CategoryStyle("🌾", 0xFFC9A227),
        "meat" to CategoryStyle("🥩", 0xFFC0504D),
        "fish" to CategoryStyle("🐟", 0xFF3E8EB5),
        "seafood" to CategoryStyle("🦐", 0xFFE08A5B),
        "dairy" to CategoryStyle("🥛", 0xFF6FA8DC),
        "eggs" to CategoryStyle("🥚", 0xFFD9A441),
        "nuts" to CategoryStyle("🥜", 0xFF9C6B3F),
        "seeds" to CategoryStyle("🌻", 0xFFCFA12B),
        "oils and fats" to CategoryStyle("🫒", 0xFF8A9A2B),
        "herbs and spices" to CategoryStyle("🌿", 0xFF4F9A6B),
        "sauces and condiments" to CategoryStyle("🧂", 0xFFB5543C),
        "flours" to CategoryStyle("🍞", 0xFFC79A5B),
        "sugars and sweeteners" to CategoryStyle("🍯", 0xFFD9962B),
        "baking" to CategoryStyle("🧁", 0xFFD1709F),
        "tinned and jarred" to CategoryStyle("🥫", 0xFF7F8C8D),
        "fermented" to CategoryStyle("🫙", 0xFF8E6BB5),
        "drinks" to CategoryStyle("🥤", 0xFF3FA39B),
        "other" to CategoryStyle("🛒", 0xFF7B8794)
    )

    private val fallbackPalette = longArrayOf(0xFF6B8EAD, 0xFFAD6B8E, 0xFF8EAD6B, 0xFFAD8E6B, 0xFF6BAD9A, 0xFF9A6BAD)

    /**
     * Style for [categoryName]. Categories created by the user get a stable colour derived from
     * their name.
     */
    fun categoryStyle(categoryName: String): CategoryStyle {
        val key = normalize(categoryName)
        categoryStyles[key]?.let { return it }
        if (key == normalize(UNCATEGORIZED_INGREDIENT_LABEL)) return categoryStyles.getValue("otros")
        return CategoryStyle("🛍️", fallbackPalette[Math.floorMod(key.hashCode(), fallbackPalette.size)])
    }

    /**
     * Keywords (lowercase, without accents) mapped to an emoji. The first whole-word match wins, so
     * more specific entries go first.
     */
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
        listOf("detergente", "jabon", "lejia", "fregasuelos") to "🧴",
        // English
        listOf("tomato", "tomatoes") to "🍅",
        listOf("milk") to "🥛",
        listOf("cheese") to "🧀",
        listOf("butter") to "🧈",
        listOf("egg", "eggs") to "🥚",
        listOf("bread", "baguette", "loaf") to "🥖",
        listOf("apple", "apples") to "🍎",
        listOf("pear", "pears") to "🍐",
        listOf("banana", "bananas") to "🍌",
        listOf("orange", "oranges", "mandarin", "mandarins") to "🍊",
        listOf("lemon", "lemons", "limes") to "🍋",
        listOf("strawberry", "strawberries") to "🍓",
        listOf("grape", "grapes") to "🍇",
        listOf("watermelon") to "🍉",
        listOf("pineapple") to "🍍",
        listOf("peach", "peaches") to "🍑",
        listOf("cherry", "cherries") to "🍒",
        listOf("avocado", "avocados") to "🥑",
        listOf("carrot", "carrots") to "🥕",
        listOf("potato", "potatoes") to "🥔",
        listOf("onion", "onions", "leek") to "🧅",
        listOf("garlic") to "🧄",
        listOf("pepper", "peppers") to "🫑",
        listOf("aubergine", "eggplant") to "🍆",
        listOf("cucumber") to "🥒",
        listOf("lettuce", "spinach", "rocket", "kale") to "🥬",
        listOf("broccoli", "cauliflower") to "🥦",
        listOf("sweetcorn", "corn") to "🌽",
        listOf("mushroom", "mushrooms") to "🍄",
        listOf("chicken", "turkey") to "🍗",
        listOf("ham", "bacon", "pancetta") to "🥓",
        listOf("beef", "pork", "meat", "steak", "chop", "lamb") to "🥩",
        listOf("salmon", "tuna", "hake", "cod", "fish", "bass", "bream") to "🐟",
        listOf("prawn", "prawns", "shrimp", "seafood") to "🦐",
        listOf("rice") to "🍚",
        listOf("pasta", "spaghetti", "macaroni", "noodles") to "🍝",
        listOf("water") to "💧",
        listOf("coffee") to "☕",
        listOf("tea") to "🍵",
        listOf("beer", "beers") to "🍺",
        listOf("wine") to "🍷",
        listOf("juice", "soda") to "🧃",
        listOf("oil") to "🫒",
        listOf("salt") to "🧂",
        listOf("sugar", "honey") to "🍯",
        listOf("biscuits", "biscuit", "cookies", "cookie") to "🍪",
        listOf("yoghurt", "yogurt") to "🥣",
        listOf("ice") to "🍨",
        listOf("napkins", "tissues", "paper") to "🧻",
        listOf("detergent", "soap", "bleach") to "🧴"
    )

    /**
     * Emoji for an item: the one for its most recognisable word, or its category emoji when none
     * matches.
     */
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
