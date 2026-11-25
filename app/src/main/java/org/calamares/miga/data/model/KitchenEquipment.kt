package org.calamares.miga.data.model

import java.text.Normalizer

/**
 * The kitchen equipment a recipe needs ("utensils" in the code and database): appliances and
 * special tools that decide whether someone can make it (oven, air fryer, food processor...), not
 * the basics every kitchen has (knife, pot, pan, bowl, fridge).
 */
object KitchenEquipment {

    /** Created once, on the first start, in the language chosen then. */
    fun defaults(language: String): List<String> = if (language == "es") listOf(
        "Horno", "Microondas", "Freidora de aire", "Thermomix", "Robot de cocina", "Olla exprés", "Olla lenta",
        "Batidora", "Amasadora", "Wok", "Plancha", "Barbacoa", "Vaporera"
    ) else listOf(
        "Oven", "Microwave", "Air fryer", "Thermomix", "Food processor", "Pressure cooker", "Slow cooker",
        "Blender", "Stand mixer", "Wok", "Griddle", "Barbecue", "Steamer"
    )

    /** Lower case, without accents or extra spaces, to compare names. */
    fun key(name: String): String =
        Normalizer.normalize(name.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")

    /** Basics every kitchen has, which say nothing about whether the recipe can be made. */
    private val OBVIOUS = setOf(
        "cuchillo", "cuchillos", "tenedor", "tenedores", "cuchara", "cucharas", "cucharon", "cucharilla", "plato", "platos",
        "bol", "boles", "bols", "cuenco", "cuencos", "recipiente", "recipientes", "tabla de cortar", "tabla", "nevera",
        "frigorifico", "congelador", "fuego", "fogon", "cocina", "vitroceramica", "placa", "placa de induccion", "induccion",
        "olla", "ollas", "cazo", "cazuela", "sarten", "sartenes", "espatula", "colador", "vaso", "taza", "jarra",
        "papel de cocina", "papel film", "film transparente", "papel de aluminio", "papel de horno", "papel vegetal",
        "knife", "knives", "fork", "spoon", "spoons", "ladle", "plate", "plates", "bowl", "bowls", "cutting board",
        "chopping board", "fridge", "refrigerator", "freezer", "stove", "hob", "cooktop", "pot", "pots", "saucepan",
        "pan", "frying pan", "skillet", "spatula", "colander", "strainer", "glass", "cup", "jug", "kitchen paper",
        "cling film", "plastic wrap", "aluminium foil", "aluminum foil", "baking paper", "parchment paper"
    )

    /**
     * First words of Spanish names ("Sartén grande", "Molde de silicona") and last words of
     * English ones ("Mixing bowl", "Baking tray") that make an entry a basic, unless the name is
     * an appliance anyway ("Vaso de Thermomix", "Olla exprés").
     */
    private val BASIC_FIRST_WORDS = setOf(
        "cuchillo", "tenedor", "cuchara", "cucharon", "cucharilla", "plato", "bol", "cuenco", "recipiente", "tabla", "nevera",
        "frigorifico", "congelador", "fuego", "fogon", "vitroceramica", "placa", "olla", "cazo", "cazuela", "cacerola", "sarten",
        "espatula", "colador", "escurridor", "vaso", "taza", "jarra", "papel", "film", "tijera", "tijeras", "pelador", "rallador",
        "varilla", "varillas", "batidor", "abrelatas", "embudo", "mortero", "rodillo", "molde", "bandeja", "fuente", "brocha",
        "pincel", "bascula", "balanza", "pinza", "pinzas", "exprimidor", "tupper", "taper", "tarro", "frasco", "botella",
        "trapo", "pano", "guante", "guantes", "palillo", "palillos", "brocheta", "brochetas", "aro", "cortapastas", "manga",
        "medidor", "cucharas", "cuchillos", "platos", "boles", "ollas", "sartenes", "moldes", "bandejas", "fuentes"
    )
    private val BASIC_LAST_WORDS = setOf(
        "knife", "knives", "fork", "spoon", "spoons", "ladle", "plate", "bowl", "bowls", "board", "fridge", "refrigerator",
        "freezer", "stove", "hob", "cooktop", "pot", "pots", "saucepan", "pan", "pans", "skillet", "spatula", "colander",
        "strainer", "sieve", "glass", "cup", "cups", "jug", "paper", "film", "foil", "wrap", "scissors", "peeler", "grater",
        "whisk", "opener", "funnel", "mortar", "pestle", "pin", "tin", "tins", "tray", "dish", "brush", "scale", "scales",
        "tongs", "container", "jar", "bottle", "towel", "gloves", "chopsticks", "skewers", "cutter", "bag"
    )

    /** Common ways of writing the same equipment, mapped to the name used by the defaults. */
    private val ALIASES: Map<String, Pair<String, String>> = buildMap {
        fun add(spanish: String, english: String, vararg variants: String) =
            (variants.toList() + spanish + english).forEach { put(key(it), spanish to english) }
        add("Freidora de aire", "Air fryer", "airfryer", "air-fryer", "freidora sin aceite", "freidora aire", "freidora de aire caliente")
        add("Olla exprés", "Pressure cooker", "olla express", "olla a presion", "olla rapida", "instant pot", "olla programable", "olla gm")
        add("Olla lenta", "Slow cooker", "olla de coccion lenta", "crockpot", "crock pot", "slow-cooker")
        add(
            "Robot de cocina", "Food processor", "procesador de alimentos", "multicooker", "robot", "robot de cocina multifuncion",
            "robot multifuncion", "mambo", "cecotec mambo", "monsieur cuisine", "picadora", "chopper"
        )
        add(
            "Batidora", "Blender", "batidora de mano", "batidora de vaso", "minipimer", "hand blender", "immersion blender",
            "batidora de varillas", "varillas electricas", "hand mixer", "electric whisk", "licuadora", "vaso batidor"
        )
        add("Amasadora", "Stand mixer", "batidora amasadora", "robot amasador", "kitchenaid", "robot de cocina amasador")
        add("Plancha", "Griddle", "parrilla / plancha", "grill / griddle", "plancha electrica", "parrilla")
        add("Barbacoa", "Barbecue", "bbq", "barbecue grill")
        add("Vaporera", "Steamer", "vaporero", "cesta de vapor", "steamer basket")
        add("Microondas", "Microwave", "micro", "microwave oven")
        add("Horno", "Oven", "horno electrico", "horno convencional")
        add("Wok", "Wok")
        add("Thermomix", "Thermomix", "varoma", "tm")
    }

    /** Appliances recognised by the first word of a Spanish name or the last of an English one ("Horno de convección", "Fan oven"). */
    private val DEVICE_FIRST_WORDS = listOf("horno", "microondas", "thermomix", "batidora", "amasadora", "wok", "plancha", "barbacoa", "vaporera", "airfryer")
    private val DEVICE_LAST_WORDS = listOf("oven", "microwave", "thermomix", "blender", "wok", "griddle", "barbecue", "bbq", "steamer", "airfryer")

    /** Parts and accessories of an appliance, named instead of it ("Vaso de Thermomix", "Air fryer basket"). */
    private val PART_PREFIX = Regex("""^(?:vaso|jarra|cuchilla|cuchillas|mariposa|cestillo|cesta|tapa|accesorio|accesorios|bol|recipiente|cubeta)(?: (?:de|del|para)(?: la| el| los| las)?)? """)
    private val PART_SUFFIX = Regex(""" (?:bowl|jug|jar|basket|blade|blades|lid|attachment|accessory|accessories|tray|pan|insert)$""")
    private val QUALIFIERS = Regex("""\b(?:grande|grandes|pequeno|pequena|mediano|mediana|antiadherente|electrico|electrica|potente|precalentado|caliente|large|small|medium|big|nonstick|non stick|electric|preheated|hot)\b""")
    private val MODEL = Regex("""\b(?:tm ?\d+|modelo .*|model .*)\b""")
    private val SIZE = Regex("""\(.*?\)|\b\d+(?:[.,]\d+)?\s*(?:cm|mm|l|litros?|liters?|litres?|ml|w|qt)?\b""")

    /** [key] without sizes, models, adjectives and the part named ("vaso de la thermomix tm6" -> "thermomix"). */
    private fun core(key: String): String {
        var text = key.replace(SIZE, " ").replace(MODEL, " ").replace(QUALIFIERS, " ").replace(Regex("""[^\p{L}\p{N}/ -]"""), " ")
            .replace(Regex("""\s+"""), " ").trim()
        PART_PREFIX.find(text)?.let { match -> text.substring(match.range.last + 1).takeIf { it.isNotBlank() }?.let { text = it } }
        PART_SUFFIX.find(text)?.let { match -> text.substring(0, match.range.first).takeIf { it.isNotBlank() }?.let { text = it } }
        return text.replace(Regex("""\s+(?:de|del|para|con)$"""), "").trim()
    }

    /** The default appliance [name] is, written any usual way, by its parts or with a model; null when none. */
    private fun deviceOf(name: String): Pair<String, String>? {
        val full = key(name)
        ALIASES[full]?.let { return it }
        val core = core(full)
        ALIASES[core]?.let { return it }
        val words = core.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        DEVICE_FIRST_WORDS.firstOrNull { it == words.first() }?.let { return ALIASES[key(it)] }
        DEVICE_LAST_WORDS.firstOrNull { it == words.last() }?.let { return ALIASES[key(it)] }
        return null
    }

    /**
     * The names [name] has in both languages when it is a usual variant of a default, to find it
     * whichever language the existing equipment was created in; empty otherwise.
     */
    fun equivalents(name: String): List<String> = deviceOf(name)?.toList().orEmpty()

    /**
     * Whether [name] is a basic every kitchen has ("Cuchillo", "Sartén grande", "Molde de
     * silicona", "Mixing bowl"), which says nothing about whether a recipe can be made.
     */
    fun isObvious(name: String): Boolean {
        val full = key(name)
        if (full in OBVIOUS) return true
        if (deviceOf(name) != null) return false
        val words = full.replace(Regex("""[^\p{L}\p{N} ]"""), " ").split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return false
        // "Máquina de pan" is Spanish, where the last word is no head.
        val spanish = words.any { it in setOf("de", "del", "para", "con", "al", "a") }
        return words.first() in BASIC_FIRST_WORDS || (!spanish && words.last() in BASIC_LAST_WORDS)
    }

    /**
     * [name] written as the default equipment when it is one of its usual variants, a part of it or
     * a model of it ("Airfryer" -> "Freidora de aire", "Vaso de Thermomix" and "Thermomix TM6" ->
     * "Thermomix", "Horno precalentado" -> "Horno"); any other name is kept as written.
     */
    fun canonical(name: String, language: String): String {
        val trimmed = name.trim().replace(Regex("\\s+"), " ")
        val names = deviceOf(trimmed) ?: return trimmed
        return if (language == "es") names.first else names.second
    }

    /**
     * Equipment proposed by an AI or an import: written as the defaults, without basics and without
     * repetitions.
     */
    fun clean(names: List<String>, language: String): List<String> =
        names.filter { it.isNotBlank() && !isObvious(it) }
            .map { canonical(it, language) }
            .distinctBy { key(it) }
}
