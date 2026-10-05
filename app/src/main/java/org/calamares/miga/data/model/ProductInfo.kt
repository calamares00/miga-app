package org.calamares.miga.data.model

import org.calamares.miga.L10n
import org.calamares.miga.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Ficha de un producto escaneado (datos de Open Food Facts, una base de datos colaborativa: puede
 * estar incompleta o tener errores). Se guarda como JSON en el artículo de la lista de la compra y
 * viaja tal cual por la sincronización. Las listas guardan los códigos de Open Food Facts sin
 * prefijo de idioma ("gluten", "milk"...); [ProductLabels] los traduce para mostrarlos.
 */
@Serializable
data class ProductInfo(
    val barcode: String,
    val brand: String? = null,
    val quantity: String? = null,
    /** "a".."e" */
    val nutriScore: String? = null,
    /** Puntos del Nutri-Score (-15 mejor ... 40 peor), si Open Food Facts los da. */
    val nutriScoreValue: Int? = null,
    /** 1..4 (grado de procesado) */
    val nova: Int? = null,
    /** "a".."e" */
    val ecoScore: String? = null,
    val allergens: List<String> = emptyList(),
    val traces: List<String> = emptyList(),
    val labels: List<String> = emptyList(),
    val analysis: List<String> = emptyList(),
    /** Códigos de aditivos ("e330"); null = Open Food Facts no los tiene (no es lo mismo que "ninguno"). */
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
    /** Fotos (solo https): la frontal en grande, la de ingredientes y la de la tabla nutricional. */
    val imageUrl: String? = null,
    val ingredientsImageUrl: String? = null,
    val nutritionImageUrl: String? = null
)

object ProductInfoCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun encode(info: ProductInfo): String = json.encodeToString(ProductInfo.serializer(), info)

    /** null si [text] está vacío o no es una ficha válida (datos corruptos o de una versión futura incompatible). */
    fun decode(text: String?): ProductInfo? {
        if (text.isNullOrBlank()) return null
        return try {
            json.decodeFromString(ProductInfo.serializer(), text)
        } catch (e: IllegalArgumentException) {
            null // SerializationException es una IllegalArgumentException
        }
    }
}

/** Textos y colores para mostrar una [ProductInfo]. Lógica pura (sin Android). */
object ProductLabels {

    /** Color oficial del Nutri-Score por letra (ARGB); null si no es una letra válida. */
    fun nutriScoreArgb(grade: String?): Long? = when (grade?.lowercase()) {
        "a" -> 0xFF038141
        "b" -> 0xFF85BB2F
        "c" -> 0xFFFECB02
        "d" -> 0xFFEE8100
        "e" -> 0xFFE63E11
        else -> null
    }

    /** "A".."E" si [grade] es una letra válida del Nutri-Score o del Eco-Score, si no null. */
    fun gradeLetter(grade: String?): String? = grade?.trim()?.lowercase()?.takeIf { it.length == 1 && it[0] in 'a'..'e' }?.uppercase()

    /** Color del grupo NOVA (1 = sin procesar ... 4 = ultraprocesado). */
    fun novaArgb(nova: Int?): Long? = when (nova) {
        1 -> 0xFF038141
        2 -> 0xFF85BB2F
        3 -> 0xFFEE8100
        4 -> 0xFFE63E11
        else -> null
    }

    fun novaDescription(nova: Int?): String? = when (nova) {
        1 -> L10n.str(R.string.sin_procesar_minimamente_procesado)
        2 -> L10n.str(R.string.ingrediente_culinario_procesado)
        3 -> L10n.str(R.string.alimento_procesado)
        4 -> L10n.str(R.string.ultraprocesado)
        else -> null
    }

    private val allergenNames = mapOf(
        "gluten" to L10n.str(R.string.gluten),
        "milk" to L10n.str(R.string.leche),
        "eggs" to L10n.str(R.string.huevos),
        "nuts" to L10n.str(R.string.frutos_cascara),
        "peanuts" to L10n.str(R.string.cacahuetes),
        "soybeans" to L10n.str(R.string.soja),
        "fish" to L10n.str(R.string.pescado),
        "crustaceans" to L10n.str(R.string.crustaceos),
        "molluscs" to L10n.str(R.string.moluscos),
        "celery" to L10n.str(R.string.apio),
        "mustard" to L10n.str(R.string.mostaza),
        "sesame-seeds" to L10n.str(R.string.sesamo),
        "sulphur-dioxide-and-sulphites" to L10n.str(R.string.sulfitos),
        "lupin" to L10n.str(R.string.altramuces)
    )

    /** Nombre en español de un alérgeno; los desconocidos se muestran con el código legible. */
    fun allergenName(code: String): String =
        allergenNames[code] ?: code.replace('-', ' ').replaceFirstChar { it.uppercase() }

    private val labelNames = mapOf(
        "organic" to L10n.str(R.string.ecologico),
        "no-gluten" to L10n.str(R.string.sin_gluten),
        "fair-trade" to L10n.str(R.string.comercio_justo),
        "no-lactose" to L10n.str(R.string.sin_lactosa),
        "no-added-sugar" to L10n.str(R.string.sin_azucares_anadidos),
        "no-preservatives" to L10n.str(R.string.sin_conservantes),
        "vegan" to L10n.str(R.string.vegano),
        "vegetarian" to L10n.str(R.string.vegetariano),
        "palm-oil-free" to L10n.str(R.string.sin_aceite_palma)
    )

    /** Etiquetas positivas conocidas (etiquetas del producto + análisis de ingredientes), sin repetir y en español. */
    fun badges(info: ProductInfo): List<String> =
        (info.labels + info.analysis).mapNotNull { labelNames[it] }.distinct()

    /** Filas "nombre / valor" de la tabla nutricional por 100 g con solo los datos disponibles. */
    fun nutritionRows(info: ProductInfo): List<Pair<String, String>> = buildList {
        info.energyKcal?.let { add(L10n.str(R.string.energia) to "${formatQuantity(it)} kcal") }
        info.fat?.let { add(L10n.str(R.string.grasas) to "${formatQuantity(it)} g") }
        info.saturatedFat?.let { add(L10n.str(R.string.cuales_saturadas) to "${formatQuantity(it)} g") }
        info.carbohydrates?.let { add(L10n.str(R.string.hidratos_carbono) to "${formatQuantity(it)} g") }
        info.sugars?.let { add(L10n.str(R.string.cuales_azucares) to "${formatQuantity(it)} g") }
        info.fiber?.let { add(L10n.str(R.string.fibra) to "${formatQuantity(it)} g") }
        info.proteins?.let { add(L10n.str(R.string.proteinas) to "${formatQuantity(it)} g") }
        info.salt?.let { add(L10n.str(R.string.sal) to "${formatQuantity(it)} g") }
    }
}
