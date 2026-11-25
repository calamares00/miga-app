package org.calamares.miga.data.model

import java.text.Normalizer

/**
 * A recipe category the app brings. Categories answer one question, "what kind of dish is it?":
 * one per recipe, in plural with a capital letter; cuisines, diets and occasions are tags.
 *
 * @param synonyms other names for it, in both languages (compared without case or accents, and
 *   allowing singular and plural): a category of the user's with one of them is merged into it.
 * @param keywords words in a recipe's name that file it here when a mixed category is split.
 */
class BuiltInCategory(
    val es: String,
    val en: String,
    private val descriptionEs: String,
    private val descriptionEn: String,
    val synonyms: List<String> = emptyList(),
    val keywords: List<String> = emptyList()
) {
    fun name(language: String) = if (language == "es") es else en
    fun description(language: String) = if (language == "es") descriptionEs else descriptionEn
}

/**
 * The app's recipe categories ([ALL], in the order they are offered), how to recognise a user's
 * category that means one of them, and how to file a recipe when a category mixing several kinds
 * of dish ("Platos principales", "Arroces y pastas") is split. Pure logic, unit tested.
 */
object RecipeCategories {

    val APPETIZERS = BuiltInCategory(
        "Aperitivos y tapas", "Appetizers and tapas",
        "Tapas, pinchos, dips y picoteo", "Tapas, finger food, dips and snacks",
        synonyms = listOf("aperitivos", "tapas", "picoteo", "pinchos", "pintxos", "canapes", "dips", "snacks", "botanas", "aperitivos y tapas", "appetizers", "appetisers", "finger food", "nibbles"),
        keywords = listOf(
            "croqueta", "tapa", "pincho", "pintxo", "canape", "montadito", "banderilla", "bravas", "nachos", "hummus", "humus",
            "guacamole", "dip", "pate", "pate de", "tosta", "boquerones en vinagre", "gilda", "chips", "palomitas", "rollitos",
            "fingers", "nuggets", "samosa", "spring rolls", "summer rolls", "dumplings", "gyoza", "empanadilla", "croquettes", "popcorn", "appetizer"
        )
    )
    val STARTERS = BuiltInCategory(
        "Entrantes", "Starters",
        "Primeros platos ligeros", "Light first courses",
        synonyms = listOf("entrantes", "primeros", "primeros platos", "entradas", "entremeses", "starters", "first courses", "entrees")
    )
    val SALADS = BuiltInCategory(
        "Ensaladas", "Salads",
        "Ensaladas y ensaladillas", "Salads of every kind",
        synonyms = listOf("ensaladas", "ensaladillas", "salads"),
        keywords = listOf("ensalada", "ensaladilla", "tabule", "tabbouleh", "coleslaw", "salad", "poke", "bowl")
    )
    val SOUPS = BuiltInCategory(
        "Sopas y cremas", "Soups",
        "Sopas, cremas, purés y caldos para tomar", "Soups, creams and broths",
        synonyms = listOf("sopas", "cremas", "sopas y cremas", "cremas y sopas", "purés", "soups", "creams", "soups and creams"),
        keywords = listOf(
            "sopa", "crema de", "vichyssoise", "gazpacho", "salmorejo", "ajoblanco", "consome", "pure de verduras", "pure de calabaza",
            "porrusalda", "caldo", "minestrone", "ramen", "pho", "miso", "harira", "soup", "broth", "chowder", "bisque", "veloute"
        )
    )
    val STEWS = BuiltInCategory(
        "Guisos y potajes", "Stews",
        "Platos de cuchara, estofados y cocidos", "Stews, casseroles and hotpots",
        synonyms = listOf("guisos", "potajes", "guisos y potajes", "estofados", "cocidos", "guisados", "recetas de cuchara", "platos de cuchara", "cuchara", "stews", "casseroles", "hotpots", "spoon dishes"),
        keywords = listOf(
            "guiso", "guisado", "potaje", "estofado", "cocido", "olla", "puchero", "caldereta", "marmitako", "fabada", "michirones",
            "rancho", "chili", "goulash", "gulash", "ragout", "ragu de", "curry", "tajine", "stew", "casserole", "hotpot"
        )
    )
    val RICE = BuiltInCategory(
        "Arroces", "Rice",
        "Paellas, risottos y arroces", "Paellas, risottos and rice dishes",
        synonyms = listOf("arroces", "arroz", "paellas", "risottos", "rice", "rice dishes"),
        keywords = listOf("arroz", "paella", "risotto", "pilaf", "biryani", "jambalaya", "sushi", "onigiri", "rice")
    )
    val PASTA = BuiltInCategory(
        "Pastas", "Pasta",
        "Pasta, fideos y lasañas", "Pasta, noodles and lasagnes",
        synonyms = listOf("pastas", "pasta", "fideos", "noodles", "pasta y fideos"),
        keywords = listOf(
            "pasta", "espagueti", "espaguetis", "spaghetti", "macarrones", "lasana", "canelones", "tallarines", "fideos", "fideua",
            "noquis", "gnocchi", "raviolis", "ravioli", "tortellini", "penne", "fettuccine", "linguine", "tagliatelle", "carbonara",
            "noodles", "lasagne", "lasagna", "macaroni", "mac and cheese", "pad thai", "yakisoba"
        )
    )
    val LEGUMES = BuiltInCategory(
        "Legumbres", "Legumes",
        "Lentejas, garbanzos y alubias", "Lentils, chickpeas and beans",
        synonyms = listOf("legumbres", "legumes", "pulses", "beans"),
        keywords = listOf(
            "lentejas", "garbanzos", "alubias", "judias blancas", "judiones", "habas", "frijoles", "fabada", "pochas", "dal", "dahl",
            "lentils", "chickpeas", "beans", "falafel"
        )
    )
    val VEGETABLES = BuiltInCategory(
        "Verduras", "Vegetables",
        "Verduras y hortalizas como plato", "Vegetable dishes",
        synonyms = listOf("verduras", "hortalizas", "verduras y hortalizas", "vegetales", "vegetables", "veggies", "vegetable dishes"),
        keywords = listOf(
            "verdura", "berenjena", "calabacin", "pisto", "espinacas", "acelgas", "coliflor", "brocoli", "alcachofa", "setas",
            "champinones", "judias verdes", "pimientos", "menestra", "escalivada", "ratatouille", "parrillada de verduras",
            "puerros", "esparragos", "calabaza", "vegetable", "vegetables", "aubergine", "eggplant", "courgette", "zucchini",
            "cauliflower", "broccoli", "mushrooms", "spinach"
        )
    )
    val EGGS = BuiltInCategory(
        "Huevos", "Eggs",
        "Tortillas, revueltos y huevos", "Omelettes, scrambled and other egg dishes",
        synonyms = listOf("huevos", "tortillas", "eggs", "egg dishes", "omelettes"),
        keywords = listOf("huevo", "tortilla", "revuelto", "frittata", "shakshuka", "omelette", "egg", "eggs", "quiche")
    )
    val FISH = BuiltInCategory(
        "Pescados y mariscos", "Fish and seafood",
        "Pescado, marisco y moluscos", "Fish, shellfish and molluscs",
        synonyms = listOf("pescados", "mariscos", "pescado", "marisco", "pescados y mariscos", "fish", "seafood", "fish and seafood", "shellfish"),
        keywords = listOf(
            "pescado", "merluza", "bacalao", "salmon", "atun", "bonito", "dorada", "lubina", "sardinas", "boquerones", "rape",
            "lenguado", "rodaballo", "caballa", "trucha", "pez espada", "emperador", "gambas", "langostinos", "mejillones",
            "almejas", "berberechos", "calamares", "sepia", "pulpo", "chipirones", "chopitos", "vieiras", "bogavante", "cigalas",
            "marisco", "ceviche", "fish", "salmon", "cod", "tuna", "hake", "prawns", "shrimp", "mussels", "clams", "squid",
            "octopus", "scallops", "seafood", "lobster", "crab"
        )
    )
    val MEAT = BuiltInCategory(
        "Carnes", "Meat",
        "Carnes y aves", "Meat and poultry",
        synonyms = listOf("carnes", "carne", "carnes y aves", "aves", "pollo", "pavo", "cerdo", "ternera", "cordero", "chicken", "beef", "pork", "carnes rojas", "carnes blancas", "meat", "meats", "poultry", "meat and poultry"),
        keywords = listOf(
            "carne", "pollo", "ternera", "cerdo", "cordero", "solomillo", "lomo", "costillas", "albondigas", "hamburguesa", "filete",
            "chuleta", "chuletas", "pavo", "conejo", "pato", "secreto", "presa", "pluma", "entrecot", "cochinillo", "codillo",
            "carrillada", "carrilleras", "rabo de toro", "callos", "magret", "muslos", "alitas", "pechuga", "contramuslos",
            "flamenquin", "san jacobo", "cachopo", "milanesa", "escalope", "kebab", "tacos", "fajitas", "burrito", "chicken",
            "beef", "pork", "lamb", "meat", "turkey", "steak", "burger", "meatballs", "ribs", "sausages", "duck", "roast"
        )
    )
    val SIDES = BuiltInCategory(
        "Guarniciones", "Sides",
        "Acompañamientos: patatas, purés, verduras de lado", "Side dishes: potatoes, mash, vegetables",
        synonyms = listOf("guarniciones", "acompanamientos", "guarnicion", "sides", "side dishes", "accompaniments"),
        keywords = listOf(
            "guarnicion", "patatas fritas", "patatas asadas", "patatas panaderas", "patatas a lo pobre", "patatas al horno",
            "pure de patata", "pure de patatas", "pure de boniato", "cuscus", "quinoa", "fries", "mashed potatoes", "mash", "side"
        )
    )
    val SAUCES = BuiltInCategory(
        "Salsas", "Sauces",
        "Salsas, aliños y vinagretas", "Sauces, dressings and vinaigrettes",
        synonyms = listOf("salsas", "alinos", "vinagretas", "aderezos", "sauces", "dressings"),
        keywords = listOf(
            "salsa", "alioli", "allioli", "mayonesa", "vinagreta", "pesto", "mojo", "chimichurri", "romesco", "bechamel", "aderezo",
            "alino", "sauce", "dressing", "vinaigrette", "gravy", "mayonnaise", "aioli"
        )
    )
    val BASICS = BuiltInCategory(
        "Bases y caldos", "Basics and stocks",
        "Fondos, caldos, sofritos y preparaciones base", "Stocks, sofritos and basic preparations",
        synonyms = listOf("bases", "basicos", "recetas basicas", "recetas base", "recetas elementales", "elementales", "caldos", "fondos", "caldos y fondos", "bases y caldos", "basics", "stocks", "basics and stocks", "basic recipes"),
        keywords = listOf("fondo", "fumet", "caldo de", "sofrito", "demi glace", "stock", "ghee", "mantequilla clarificada", "masa quebrada", "masa brisa", "pasta brick")
    )
    val BREADS = BuiltInCategory(
        "Panes y masas", "Breads and doughs",
        "Panes, pizzas, empanadas y masas saladas", "Breads, pizzas, pies and savoury doughs",
        synonyms = listOf("panes", "pan", "masas", "panes y masas", "pizzas", "empanadas", "panaderia", "masas saladas", "breads", "bread", "doughs", "breads and doughs", "pizza"),
        keywords = listOf(
            "pan", "pizza", "empanada", "focaccia", "chapata", "baguette", "hogaza", "bollos de pan", "coca de", "pita", "naan",
            "tortillas de trigo", "bagel", "masa de pizza", "bread", "loaf", "flatbread", "calzone"
        )
    )
    val BREAKFAST = BuiltInCategory(
        "Desayunos y meriendas", "Breakfast and brunch",
        "Tostadas, porridge, tortitas y gofres", "Toast, porridge, pancakes and waffles",
        synonyms = listOf("desayunos", "meriendas", "desayunos y meriendas", "brunch", "breakfast", "breakfasts", "breakfast and brunch"),
        keywords = listOf(
            "tortitas", "pancakes", "gofre", "gofres", "waffle", "waffles", "porridge", "gachas", "avena", "overnight oats",
            "granola", "muesli", "tostada", "tostadas", "crepes", "creps", "french toast", "toast"
        )
    )
    val DESSERTS = BuiltInCategory(
        "Postres", "Desserts",
        "Postres de cuchara y de fruta: flan, natillas, mousse", "Spoon and fruit desserts: custards, mousses",
        synonyms = listOf("postres", "postre", "dulces", "desserts", "dessert", "sweets", "puddings"),
        keywords = listOf(
            "flan", "natillas", "arroz con leche", "crema catalana", "mousse", "panna cotta", "tiramisu", "cuajada", "leche frita",
            "torrijas", "compota", "macedonia", "fruta", "yogur", "pudin", "pudding", "trifle", "custard", "creme brulee",
            "fresas con nata", "peras al vino", "manzanas asadas", "gelatina", "trufas", "bombones", "copa de", "postre", "dessert"
        )
    )
    val BAKING = BuiltInCategory(
        "Repostería", "Baking",
        "Horno dulce: tartas, bizcochos, galletas y bollería", "Sweet baking: cakes, sponges, biscuits and pastries",
        synonyms = listOf(
            "reposteria", "reposterias", "reposteria y pasteleria", "pasteleria", "pasteleria y reposteria", "tartas", "pasteles",
            "tartas y pasteles", "bizcochos", "galletas", "bolleria", "dulces de horno", "baking", "cakes", "pastries", "cookies",
            "biscuits", "cakes and biscuits", "bakes"
        ),
        keywords = listOf(
            "tarta", "tartaleta", "pastel", "bizcocho", "galleta", "galletas", "magdalena", "magdalenas", "muffin", "muffins",
            "cupcake", "cupcakes", "brownie", "brownies", "blondie", "roscon", "rosco", "roscos", "rosquilla", "rosquillas",
            "croissant", "cruasan", "bollo", "bolleria", "ensaimada", "palmera", "palmeras", "cookie", "cookies", "cake", "cheesecake",
            "coulant", "donut", "donuts", "churros", "porras", "bunuelos", "pestinos", "polvorones", "mantecados", "turron",
            "plum cake", "carrot cake", "banana bread", "financier", "macarons", "brioche", "panettone", "scones", "cinnamon roll",
            "rollos de canela", "hojaldre", "milhojas", "pionono", "sobaos", "carquinyolis", "cantuccini", "biscotti", "strudel",
            "pie", "tart", "biscuit", "biscuits", "shortbread", "gingerbread", "pastries", "danish pastry", "mona de pascua", "pastas de te", "eclair", "profiteroles",
            "lionesas", "bundt", "flapjack", "galette", "cobbler", "crumble", "bakewell", "baklava", "tiramisu de horno"
        )
    )
    val ICE_CREAM = BuiltInCategory(
        "Helados y sorbetes", "Ice cream and sorbets",
        "Helados, sorbetes, polos y granizados", "Ice creams, sorbets, ice lollies and granitas",
        synonyms = listOf("helados", "sorbetes", "helados y sorbetes", "polos", "granizados", "ice cream", "ice creams", "sorbets", "ice cream and sorbets", "ice lollies", "popsicles"),
        keywords = listOf(
            "helado", "helados", "sorbete", "polo", "polos", "granizado", "granita", "semifrio", "gelato", "ice cream", "sorbet",
            "popsicle", "popsicles", "ice lolly", "ice lollies", "frozen yogurt", "yogur helado", "parfait", "affogato"
        )
    )
    val SMOOTHIES = BuiltInCategory(
        "Batidos y zumos", "Smoothies and juices",
        "Smoothies, batidos y zumos", "Smoothies, shakes and juices",
        synonyms = listOf("batidos", "zumos", "smoothies", "batidos y zumos", "zumos y batidos", "licuados", "jugos", "smoothies and juices", "juices", "shakes", "milkshakes"),
        keywords = listOf("batido", "smoothie", "smoothies", "zumo", "licuado", "jugo", "lassi", "milkshake", "shake", "juice", "green juice")
    )
    val DRINKS = BuiltInCategory(
        "Bebidas", "Drinks",
        "Cócteles, infusiones, limonadas y bebidas calientes", "Cocktails, infusions, lemonades and hot drinks",
        synonyms = listOf("bebidas", "cocteles", "refrescos", "infusiones", "bebidas calientes", "drinks", "beverages", "cocktails", "hot drinks"),
        keywords = listOf(
            "coctel", "cocteles", "mojito", "sangria", "limonada", "infusion", "te", "cafe", "chocolate caliente", "horchata",
            "refresco", "tinto de verano", "agua de", "kombucha", "kefir de agua", "ponche", "margarita", "daiquiri", "pina colada",
            "gin tonic", "vermut", "licor", "orujo", "cocktail", "lemonade", "tea", "coffee", "punch", "hot chocolate", "latte",
            "chai", "eggnog", "mulled wine", "iced tea"
        )
    )
    val PRESERVES = BuiltInCategory(
        "Conservas y mermeladas", "Preserves and jams",
        "Mermeladas, encurtidos y conservas caseras", "Jams, pickles and home preserves",
        synonyms = listOf("conservas", "mermeladas", "encurtidos", "confituras", "conservas y mermeladas", "preserves", "jams", "pickles", "preserves and jams", "chutneys"),
        keywords = listOf(
            "mermelada", "confitura", "encurtido", "encurtidos", "conserva", "en conserva", "escabeche casero", "jalea",
            "dulce de membrillo", "pickles", "pickled", "jam", "marmalade", "preserve", "preserves", "relish", "chucrut"
        )
    )

    /** In the order they are offered and created on a new install. */
    val ALL: List<BuiltInCategory> = listOf(
        APPETIZERS, STARTERS, SALADS, SOUPS, STEWS, RICE, PASTA, LEGUMES, VEGETABLES, EGGS, FISH, MEAT, SIDES, SAUCES,
        BASICS, BREADS, BREAKFAST, DESSERTS, BAKING, ICE_CREAM, SMOOTHIES, DRINKS, PRESERVES
    )

    /** Added after the first versions: created once on existing installs too. */
    val ADDED_LATER: List<BuiltInCategory> = listOf(APPETIZERS, STEWS, SIDES, BASICS, BREAKFAST, BAKING, ICE_CREAM, SMOOTHIES, PRESERVES)

    /** Dishes these categories are made of: their recipes are filed one by one. */
    /** The kinds of dish a recipe of a mixed category is filed among. */
    private val SAVOURY = listOf(APPETIZERS, SALADS, SOUPS, STEWS, RICE, PASTA, LEGUMES, EGGS, FISH, MEAT, SIDES, VEGETABLES, BREADS, SAUCES, BASICS)
    private val SWEET = listOf(BAKING, ICE_CREAM, BREAKFAST, SMOOTHIES, DRINKS, PRESERVES, DESSERTS)
    private val EVERY_DISH = SAVOURY + SWEET
    private val MAINS = listOf(MEAT, FISH)

    /**
     * Built-in categories that took part of what an older one held: their recipes that clearly
     * belong to the new one are offered to move ("Tarta de queso" from Postres to Repostería).
     */
    private val REDISTRIBUTED: Map<BuiltInCategory, List<BuiltInCategory>> = mapOf(
        DESSERTS to listOf(BAKING, ICE_CREAM, BREAKFAST, SMOOTHIES),
        SOUPS to listOf(STEWS),
        STARTERS to listOf(APPETIZERS),
        DRINKS to listOf(SMOOTHIES),
        SAUCES to listOf(PRESERVES)
    )

    /**
     * Names of categories that say nothing about the kind of dish ("Platos principales", "Otros"):
     * their recipes are filed one by one among every kind of dish.
     */
    private val GENERIC = setOf(
        "platos principales", "principales", "plato principal", "segundos", "segundos platos", "platos", "comidas", "cenas",
        "almuerzos", "otros", "otras", "varios", "varias", "sin categoria", "recetas", "mis recetas", "favoritas", "general",
        "main dishes", "mains", "main courses", "main course", "dinners", "lunches", "other", "others", "misc", "miscellaneous",
        "recipes", "my recipes", "uncategorized", "uncategorised"
    )

    /** Categories for babies: their recipes are filed by dish and keep this as a tag. */
    private val BABY = listOf("papillas", "papillas infantiles", "papilla", "comida para bebes", "bebes", "cocina infantil", "baby food", "purees for babies", "weaning")
    fun babyTag(language: String) = if (language == "es") "bebés" else "baby"

    fun names(language: String): List<String> = ALL.map { it.name(language) }

    /** The built-in category written [name], in either language. */
    fun named(name: String): BuiltInCategory? {
        val key = normalize(name)
        return ALL.firstOrNull { normalize(it.es) == key || normalize(it.en) == key }
    }

    /** What [name] says about a built-in category, in [language]; null for the user's own. */
    fun description(name: String, language: String): String? = named(name)?.description(language)

    /**
     * The built-in category [name] means: itself, a synonym ("Tartas" -> Repostería, "Smoothies"
     * -> Batidos y zumos) or the same in singular or plural. Null when it is none of them.
     */
    fun builtInFor(name: String): BuiltInCategory? {
        named(name)?.let { return it }
        val key = normalize(name)
        if (key.isEmpty()) return null
        return ALL.firstOrNull { category -> category.synonyms.any { sameWords(normalize(it), key) } }
    }

    /** How a category of the user's that is not a built-in one should be split. */
    sealed interface Split {
        val candidates: List<BuiltInCategory>
        /** Where recipes go when none of the [candidates] is clear; null to leave them where they are. */
        val fallback: BuiltInCategory?
        val tag: String?
    }

    /** "Arroces y pastas": each recipe to one of the parts, the first one when unclear. */
    data class Composite(override val candidates: List<BuiltInCategory>) : Split {
        override val fallback: BuiltInCategory get() = candidates.first()
        override val tag: String? get() = null
    }

    /** "Platos principales", "Papillas" or a theme ("Navidad"): each recipe to the kind of dish it is. */
    data class ByDish(override val candidates: List<BuiltInCategory>, override val tag: String?) : Split {
        override val fallback: BuiltInCategory? get() = null
    }

    /**
     * How to split the user's category [name], or null when it is a built-in one or a synonym of
     * one (merged as a whole instead). A theme ("Navidad", "Cocina india") becomes a tag of each
     * of its recipes, as categories only say the kind of dish.
     */
    fun splitFor(name: String, language: String): Split? {
        if (builtInFor(name) != null) return null
        val key = normalize(name)
        if (key.isEmpty()) return null
        val parts = key.split(Regex("""\s*(?:,|\by\b|\band\b|&|/|\+)\s*""")).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size > 1) {
            val builtIns = parts.map { builtInFor(it) }
            if (builtIns.all { it != null }) return Composite(builtIns.filterNotNull().distinct())
        }
        if (BABY.any { sameWords(normalize(it), key) }) return ByDish(EVERY_DISH, babyTag(language))
        if (key in GENERIC) return ByDish(SAVOURY, null)
        return ByDish(EVERY_DISH, name.trim().replace(Regex("""\s+"""), " "))
    }

    /** The built-in categories some recipes of the built-in [category] may move to. */
    fun redistributionOf(category: BuiltInCategory): List<BuiltInCategory> = REDISTRIBUTED[category].orEmpty()

    /**
     * Where the recipe [name] of a category split by [split] goes: one of its candidates; for a
     * composite one, otherwise whatever kind of dish it clearly is, or else its first part. Null
     * when it should stay where it is.
     */
    fun fileRecipe(split: Split, name: String, ingredients: List<String> = emptyList()): BuiltInCategory? =
        classify(name, split.candidates, ingredients)
            ?: (if (split is Composite) classify(name, EVERY_DISH, ingredients) else null)
            ?: split.fallback

    /**
     * Which of [candidates] the recipe called [name] is, by the words in its name: the one whose
     * word comes first ("Arroz con pollo" is a rice, "Pollo con arroz" a meat), and Legumbres
     * rather than Guisos y potajes when it is made of a legume ("Potaje de garbanzos"). Without a
     * clue in the name, the first meat or fish among [ingredients] decides between those two.
     */
    fun classify(name: String, candidates: List<BuiltInCategory>, ingredients: List<String> = emptyList()): BuiltInCategory? {
        val text = normalize(name)
        // The earliest word wins; at the same place the longest ("arroz con leche" over "arroz").
        val found = candidates.flatMap { category ->
            category.keywords.mapNotNull { keyword -> position(text, keyword)?.let { Triple(category, it, keyword.length) } }
        }
        var best = found.minWithOrNull(
            compareBy<Triple<BuiltInCategory, Int, Int>> { it.second }.thenByDescending { it.third }.thenBy { candidates.indexOf(it.first) }
        )?.first
        if (best == STEWS && LEGUMES in candidates && LEGUMES.keywords.any { position(text, it) != null }) best = LEGUMES
        // "Pastel de carne" is no cake: a sweet word with meat or fish in the name
        // gives way to them.
        if (best == BAKING) {
            found.filter { it.first in MAINS }.minByOrNull { it.second }?.let { best = it.first }
        }
        if (best != null) return best
        if (MEAT in candidates || FISH in candidates) {
            ingredients.forEach { ingredient ->
                val words = normalize(ingredient)
                listOf(FISH, MEAT).filter { it in candidates }.firstOrNull { category ->
                    category.keywords.any { position(words, it) != null }
                }?.let { return it }
            }
        }
        return null
    }

    /** Where [keyword] starts in [text] as whole words (allowing a plural ending), or null. */
    private fun position(text: String, keyword: String): Int? =
        patterns.getOrPut(keyword) { Regex("""(?<![\p{L}\p{N}])${Regex.escape(keyword)}(?:s|es)?(?![\p{L}\p{N}])""") }.find(text)?.range?.first

    private val patterns = java.util.concurrent.ConcurrentHashMap<String, Regex>()

    internal fun normalize(name: String): String =
        Normalizer.normalize(name.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
            .replace(Regex("""[^\p{L}\p{N},&/+]+"""), " ")
            .trim()

    /** Same words, allowing singular and plural ("tarta"/"tartas", "pastel"/"pasteles"). */
    private fun sameWords(a: String, b: String): Boolean {
        if (a == b) return a.isNotEmpty()
        val wordsA = a.split(' ')
        val wordsB = b.split(' ')
        return wordsA.size == wordsB.size && wordsA.zip(wordsB).all { (x, y) ->
            val (short, long) = if (x.length <= y.length) x to y else y to x
            x == y || (short.length >= 3 && (long == short + "s" || long == short + "es"))
        }
    }
}
