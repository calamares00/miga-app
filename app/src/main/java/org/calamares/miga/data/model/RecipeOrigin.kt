package org.calamares.miga.data.model

import java.util.Locale

/**
 * Where a recipe comes from: a free text ("México", "Córdoba", "Cocina tailandesa") and, when
 * known, the ISO 3166-1 alpha-2 code of its country ("MX", "ES", "TH"), which gives the flag.
 */
object RecipeOrigin {

    private val COUNTRY_CODES: Set<String> by lazy { Locale.getISOCountries().toSet() }

    /** [code] in upper case when it is a valid country code, null otherwise. */
    fun normalizeCountry(code: String?): String? = code?.trim()?.uppercase()?.takeIf { it in COUNTRY_CODES }

    /** Flag emoji of a country code ("MX" -> 🇲🇽), built from the regional indicator symbols. */
    fun flag(countryCode: String?): String? {
        val code = normalizeCountry(countryCode) ?: return null
        return code.map { char -> String(Character.toChars(0x1F1E6 + (char - 'A'))) }.joinToString("")
    }

    fun countryName(countryCode: String, locale: Locale = Locale.getDefault()): String =
        Locale("", countryCode).getDisplayCountry(locale).ifBlank { countryCode }

    /** Every country, by name in [locale]. */
    fun countries(locale: Locale = Locale.getDefault()): List<Pair<String, String>> =
        COUNTRY_CODES.map { it to countryName(it, locale) }.sortedBy { KitchenEquipment.key(it.second) }

    /**
     * What to show: the flag and the origin text, or the country name when there is no text
     * ("🇲🇽 México", "🇪🇸 Córdoba"); null when the recipe has neither.
     */
    fun label(origin: String?, countryCode: String?, locale: Locale = Locale.getDefault()): String? {
        val country = normalizeCountry(countryCode)
        val text = origin?.trim()?.takeIf { it.isNotEmpty() } ?: country?.let { countryName(it, locale) } ?: return null
        return listOfNotNull(flag(country), text).joinToString(" ")
    }

    /** Adjectives and places that do not match a country name ("mexicana", "andaluza", "Córdoba"). */
    private val PLACES: Map<String, String> = buildMap {
        fun add(code: String, vararg names: String) = names.forEach { put(KitchenEquipment.key(it), code) }
        add("MX", "mexicana", "mexicano", "mexican", "tex-mex", "texmex")
        add("JP", "japonesa", "japones", "japanese")
        add("CN", "china", "chino", "chinese", "cantonesa", "cantonese", "sichuan")
        add("IN", "india", "indio", "hindu", "indian")
        add("IT", "italiana", "italiano", "italian", "toscana", "siciliana", "napolitana")
        add("FR", "francesa", "frances", "french", "provenzal", "provencal")
        add("ES", "española", "español", "spanish", "andaluza", "andaluz", "andalucia", "cordobesa", "cordobes", "cordoba",
            "sevillana", "sevilla", "granadina", "granada", "malagueña", "malaga", "valenciana", "valencia", "catalana",
            "catalunya", "cataluña", "vasca", "vasco", "pais vasco", "euskadi", "gallega", "galicia", "asturiana", "asturias",
            "madrileña", "madrid", "castellana", "castilla", "manchega", "la mancha", "murciana", "murcia", "canaria",
            "canarias", "extremeña", "extremadura", "aragonesa", "aragon", "navarra", "riojana", "la rioja", "cantabra",
            "cantabria", "balear", "mallorquina", "mallorca", "leonesa", "salmantina", "segoviana", "gaditana", "cadiz",
            "onubense", "huelva", "almeriense", "jiennense", "jaen", "alicantina", "alicante", "zaragozana")
        add("GR", "griega", "griego", "greek")
        add("TH", "tailandesa", "tailandes", "thai")
        add("PE", "peruana", "peruano", "peruvian")
        add("AR", "argentina", "argentino", "argentinian")
        add("MA", "marroqui", "moroccan")
        add("TR", "turca", "turco", "turkish")
        add("LB", "libanesa", "libanes", "lebanese")
        add("KR", "coreana", "coreano", "korean")
        add("VN", "vietnamita", "vietnamese")
        add("DE", "alemana", "aleman", "german")
        add("PT", "portuguesa", "portugues", "portuguese")
        add("GB", "britanica", "britanico", "inglesa", "ingles", "escocesa", "british", "english", "scottish")
        add("US", "estadounidense", "americana", "americano", "american", "cajun", "texana")
        add("CU", "cubana", "cubano", "cuban")
        add("CO", "colombiana", "colombiano", "colombian")
        add("VE", "venezolana", "venezolano", "venezuelan")
        add("CL", "chilena", "chileno", "chilean")
        add("BR", "brasileña", "brasileño", "brazilian")
        add("RU", "rusa", "ruso", "russian")
        add("HU", "hungara", "hungaro", "hungarian")
        add("PL", "polaca", "polaco", "polish")
        add("IE", "irlandesa", "irish")
        add("BE", "belga", "belgian")
        add("CH", "suiza", "suizo", "swiss")
        add("AT", "austriaca", "austrian")
        add("SE", "sueca", "swedish")
        add("IL", "israeli")
        add("ID", "indonesia", "indonesian")
        add("PH", "filipina", "filipino")
        add("ET", "etiope", "ethiopian")
        add("JM", "jamaicana", "jamaican")
    }

    /**
     * The country an origin text refers to, if it can be told: a known adjective or place
     * ("mexicana", "Córdoba"), or a country name in Spanish or English ("Japón", "India").
     */
    fun guessCountry(origin: String): String? {
        val key = KitchenEquipment.key(origin)
        if (key.isEmpty()) return null
        PLACES[key]?.let { return it }
        val words = key.split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        // Whole text first ("pais vasco"), then each word ("cocina mexicana", "Córdoba, España").
        (listOf(key) + words).forEach { candidate -> PLACES[candidate]?.let { return it } }
        val names = countryNameKeys()
        (listOf(key) + words).forEach { candidate -> names[candidate]?.let { return it } }
        return null
    }

    private var nameKeys: Map<String, String>? = null

    private fun countryNameKeys(): Map<String, String> = nameKeys ?: buildMap {
        listOf(Locale("es"), Locale.ENGLISH).forEach { locale ->
            COUNTRY_CODES.forEach { code -> put(KitchenEquipment.key(countryName(code, locale)), code) }
        }
    }.also { nameKeys = it }
}
