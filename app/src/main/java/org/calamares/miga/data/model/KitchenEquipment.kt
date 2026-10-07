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

    /** Common ways of writing the same equipment, mapped to the name used by the defaults. */
    private val ALIASES: Map<String, Pair<String, String>> = buildMap {
        fun add(spanish: String, english: String, vararg variants: String) =
            (variants.toList() + spanish + english).forEach { put(key(it), spanish to english) }
        add("Freidora de aire", "Air fryer", "airfryer", "air-fryer", "freidora sin aceite", "freidora aire")
        add("Olla exprés", "Pressure cooker", "olla express", "olla a presion", "olla rapida", "instant pot")
        add("Olla lenta", "Slow cooker", "olla de coccion lenta", "crockpot", "crock pot", "slow-cooker")
        add("Robot de cocina", "Food processor", "procesador de alimentos", "multicooker", "robot")
        add("Batidora", "Blender", "batidora de mano", "batidora de vaso", "minipimer", "hand blender", "immersion blender")
        add("Amasadora", "Stand mixer", "batidora amasadora", "robot amasador", "kitchenaid")
        add("Plancha", "Griddle", "parrilla / plancha", "grill / griddle", "plancha electrica")
        add("Barbacoa", "Barbecue", "bbq", "barbecue grill")
        add("Vaporera", "Steamer", "vaporero", "cesta de vapor", "steamer basket")
        add("Microondas", "Microwave", "micro", "microwave oven")
        add("Horno", "Oven", "horno electrico", "horno convencional")
        add("Wok", "Wok")
    }

    /**
     * The names [name] has in both languages when it is a usual variant of a default, to find it
     * whichever language the existing equipment was created in; empty otherwise.
     */
    fun equivalents(name: String): List<String> = ALIASES[key(name)]?.toList().orEmpty()

    fun isObvious(name: String): Boolean = key(name) in OBVIOUS

    /**
     * [name] written as the default equipment when it is one of its usual variants ("Airfryer" ->
     * "Freidora de aire"); any other name, including brands and models such as "Thermomix TM31", is
     * kept as written.
     */
    fun canonical(name: String, language: String): String {
        val trimmed = name.trim().replace(Regex("\\s+"), " ")
        val names = ALIASES[key(trimmed)] ?: return trimmed
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
