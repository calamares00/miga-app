package org.calamares.miga.data.follow

import java.text.Normalizer

/**
 * Tells whether a post of a followed channel or profile has nothing to do with cooking (family
 * photos, travel, a dance...), so Favourites can leave it out. It only reads the post's text and is
 * cautious: a post is off topic only when its text is long enough to judge and mentions no food or
 * cooking word in Spanish, English or Catalan. A short or empty text is kept, since a dish name
 * alone ("Carbonara") says too little.
 */
object CookingFilter {

    /** Shorter texts are never hidden. */
    const val MIN_TEXT_LENGTH = 30

    /** Word beginnings (so plurals and conjugations match), at least 4 letters each. */
    private val STEMS = listOf(
        // Spanish and Catalan
        "cocin", "cuina", "receta", "recepta", "ingredient", "hornea", "horno", "forn", "frit", "guis", "estof", "salte", "asad",
        "plancha", "parrilla", "brasa", "tarta", "pastel", "galleta", "postre", "bizcoch", "magdalena", "repostería", "reposteria",
        "desayun", "almuerz", "comida", "merienda", "aperitiv", "ensalada", "sopa", "crema", "caldo", "arroz", "pasta", "pizza",
        "carne", "pollo", "cerdo", "ternera", "cordero", "pescado", "marisco", "gamba", "langostino", "verdura", "hortaliza", "fruta",
        "huevo", "ous", "tortilla", "queso", "formatge", "mantequilla", "harina", "farina", "masa", "chocolate", "azucar", "aceite",
        "salsa", "marinad", "hervir", "hervid", "cuece", "cocer", "cocid", "amasa", "rebozad", "empanad", "croqueta", "albondiga",
        "legumbre", "lentejas", "garbanzo", "patata", "tomate", "cebolla", "pimiento", "especia", "sabros", "deliciós", "delicios",
        "gastronom", "menú", "tapas", "pan casero", "vegano", "vegetarian", "sin gluten", "tupper", "batch cooking", "thermomix",
        // English
        "cook", "bake", "baking", "recipe", "dinner", "lunch", "breakfast", "dessert", "sauce", "soup", "salad", "chicken", "beef",
        "pork", "fish", "seafood", "noodle", "cheese", "flour", "dough", "cake", "cookie", "bread", "food", "kitchen", "chef",
        "delicious", "tasty", "yummy", "snack", "meal", "dish", "roast", "grill", "fried", "vegan", "foodie", "ingredients",
        "croquett", "spaghetti", "muffin", "stew", "curry", "lentil", "potato", "tomato", "vegetabl", "pancake", "burger",
        "sandwich", "omelette", "pudding", "brownie", "biscuit", "pastry", "gazpacho", "salmon", "prawn", "garlic", "onion"
    )

    /** Short words that must match whole, or they would be found inside others. */
    private val WORDS = listOf(
        "sal", "ajo", "pan", "olla", "wok", "cena", "tapa", "plato", "vino", "cafe", "rice", "egg", "eggs", "oil",
        "salt", "meat", "bbq", "sushi", "taco", "tacos", "paella", "cocido", "fideua", "croissant", "pie", "pies", "hake", "tuna"
    )

    private val PATTERN = Regex(
        "(?<![a-z0-9])(?:" + STEMS.joinToString("|") { Regex.escape(fold(it)) } + ")|(?<![a-z0-9])(?:" +
            WORDS.joinToString("|") { Regex.escape(fold(it)) } + ")(?![a-z0-9])"
    )

    private fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    /** True when the post's [title] and [description] are long enough to judge and are not about food. */
    fun isOffTopic(title: String, description: String): Boolean {
        val text = fold(listOf(title, description).joinToString(" ").replace('#', ' ')).trim()
        if (text.length < MIN_TEXT_LENGTH) return false
        return !PATTERN.containsMatchIn(text)
    }
}
