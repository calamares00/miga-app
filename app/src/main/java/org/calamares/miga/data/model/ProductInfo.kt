package org.calamares.miga.data.model

import org.calamares.miga.L10n
import org.calamares.miga.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Details of a scanned product, from Open Food Facts (a collaborative database: data may be
 * incomplete or wrong). Stored as JSON in the shopping list item and synced as is. Allergens and
 * labels keep the Open Food Facts codes without language prefix ("gluten", "milk"...);
 * [ProductLabels] translates them for display.
 */
@Serializable
data class ProductInfo(
    val barcode: String,
    val brand: String? = null,
    val quantity: String? = null,
    /** "a".."e" */
    val nutriScore: String? = null,
    /** Nutri-Score points (-15 best ... 40 worst), when Open Food Facts provides them. */
    val nutriScoreValue: Int? = null,
    /** 1..4 (processing level). */
    val nova: Int? = null,
    /** "a".."e" */
    val ecoScore: String? = null,
    val allergens: List<String> = emptyList(),
    val traces: List<String> = emptyList(),
    val labels: List<String> = emptyList(),
    val analysis: List<String> = emptyList(),
    /**
     * Additive codes ("e330"); null means Open Food Facts has no data, which is not the same as
     * none.
     */
    val additives: List<String>? = null,
    val energyKcal: Double? = null,
    val fat: Double? = null,
    val saturatedFat: Double? = null,
    val carbohydrates: Double? = null,
    val sugars: Double? = null,
    val fiber: Double? = null,
    val proteins: Double? = null,
    val salt: Double? = null,
    val ingredients: String? = null,
    /** Photos (https only): large front image, ingredients and nutrition table. */
    val imageUrl: String? = null,
    val ingredientsImageUrl: String? = null,
    val nutritionImageUrl: String? = null
)

object ProductInfoCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun encode(info: ProductInfo): String = json.encodeToString(ProductInfo.serializer(), info)

    /**
     * Null when [text] is empty or not a valid product (corrupted data or an incompatible future
     * format).
     */
    fun decode(text: String?): ProductInfo? {
        if (text.isNullOrBlank()) return null
        return try {
            json.decodeFromString(ProductInfo.serializer(), text)
        } catch (e: IllegalArgumentException) {
            null // SerializationException extends IllegalArgumentException.
        }
    }
}

/** Texts and colours to display a [ProductInfo]. Pure logic, no Android dependencies. */
object ProductLabels {

    /** Official Nutri-Score colour (ARGB) for a grade letter; null when it is not a valid grade. */
    fun nutriScoreArgb(grade: String?): Long? = when (grade?.lowercase()) {
        "a" -> 0xFF038141
        "b" -> 0xFF85BB2F
        "c" -> 0xFFFECB02
        "d" -> 0xFFEE8100
        "e" -> 0xFFE63E11
        else -> null
    }

    /** "A".."E" when [grade] is a valid Nutri-Score or Eco-Score letter, null otherwise. */
    fun gradeLetter(grade: String?): String? = grade?.trim()?.lowercase()?.takeIf { it.length == 1 && it[0] in 'a'..'e' }?.uppercase()

    /** Colour of the NOVA group (1 = unprocessed ... 4 = ultra-processed). */
    fun novaArgb(nova: Int?): Long? = when (nova) {
        1 -> 0xFF038141
        2 -> 0xFF85BB2F
        3 -> 0xFFEE8100
        4 -> 0xFFE63E11
        else -> null
    }

    fun novaDescription(nova: Int?): String? = when (nova) {
        1 -> L10n.str(R.string.unprocessed_minimally_processed)
        2 -> L10n.str(R.string.processed_culinary_ingredient)
        3 -> L10n.str(R.string.processed_food)
        4 -> L10n.str(R.string.ultra_processed)
        else -> null
    }

    private val allergenNames = mapOf(
        "gluten" to L10n.str(R.string.gluten),
        "milk" to L10n.str(R.string.milk),
        "eggs" to L10n.str(R.string.eggs),
        "nuts" to L10n.str(R.string.tree_nuts),
        "peanuts" to L10n.str(R.string.peanuts),
        "soybeans" to L10n.str(R.string.soy),
        "fish" to L10n.str(R.string.fish),
        "crustaceans" to L10n.str(R.string.crustaceans),
        "molluscs" to L10n.str(R.string.molluscs),
        "celery" to L10n.str(R.string.celery),
        "mustard" to L10n.str(R.string.mustard),
        "sesame-seeds" to L10n.str(R.string.sesame),
        "sulphur-dioxide-and-sulphites" to L10n.str(R.string.sulphites),
        "lupin" to L10n.str(R.string.lupin)
    )

    /** Translated allergen name; unknown codes are shown in a readable form. */
    fun allergenName(code: String): String =
        allergenNames[code] ?: code.replace('-', ' ').replaceFirstChar { it.uppercase() }

    private val labelNames = mapOf(
        "organic" to L10n.str(R.string.organic),
        "no-gluten" to L10n.str(R.string.gluten_free),
        "fair-trade" to L10n.str(R.string.fair_trade),
        "no-lactose" to L10n.str(R.string.lactose_free),
        "no-added-sugar" to L10n.str(R.string.no_added_sugar),
        "no-preservatives" to L10n.str(R.string.no_preservatives),
        "vegan" to L10n.str(R.string.vegan),
        "vegetarian" to L10n.str(R.string.vegetarian),
        "palm-oil-free" to L10n.str(R.string.palm_oil_free)
    )

    /**
     * Known positive labels (product labels and ingredient analysis), translated and without
     * repetitions.
     */
    fun badges(info: ProductInfo): List<String> =
        (info.labels + info.analysis).mapNotNull { labelNames[it] }.distinct()

    /** "name / value" rows of the nutrition table per 100 g, only with the available data. */
    fun nutritionRows(info: ProductInfo): List<Pair<String, String>> = buildList {
        info.energyKcal?.let { add(L10n.str(R.string.energy) to "${formatQuantity(it)} kcal") }
        info.fat?.let { add(L10n.str(R.string.fat) to "${formatQuantity(it)} g") }
        info.saturatedFat?.let { add(L10n.str(R.string.which_saturates) to "${formatQuantity(it)} g") }
        info.carbohydrates?.let { add(L10n.str(R.string.carbohydrate) to "${formatQuantity(it)} g") }
        info.sugars?.let { add(L10n.str(R.string.which_sugars) to "${formatQuantity(it)} g") }
        info.fiber?.let { add(L10n.str(R.string.fibre) to "${formatQuantity(it)} g") }
        info.proteins?.let { add(L10n.str(R.string.protein) to "${formatQuantity(it)} g") }
        info.salt?.let { add(L10n.str(R.string.salt) to "${formatQuantity(it)} g") }
    }
}
