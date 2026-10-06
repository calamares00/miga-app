package org.calamares.miga.data.remote

import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.BuildConfig
import org.calamares.miga.data.model.ProductInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ScannedProduct(val barcode: String, val name: String, val imageUrl: String?, val info: ProductInfo)

sealed interface ProductLookupResult {
    data class Found(val product: ScannedProduct) : ProductLookupResult
    data object NotFound : ProductLookupResult
    data class Error(val reason: String) : ProductLookupResult
}

sealed interface ProductSearchResult {
    data class Success(val products: List<ScannedProduct>) : ProductSearchResult
    data class Error(val reason: String) : ProductSearchResult
}

private const val OFF_BASE = "https://world.openfoodfacts.org/api/v2/product"
private const val TIMEOUT_MILLIS = 8000

/**
 * Busca un producto por su código de barras en Open Food Facts (base de datos abierta y colaborativa;
 * sin cuenta ni clave). Solo se llama cuando el usuario escanea un código a propósito. Mismo estilo
 * que PacksCatalogClient: HttpURLConnection + kotlinx.serialization.
 */
object OpenFoodFactsClient {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun lookup(barcode: String): ProductLookupResult = withContext(Dispatchers.IO) {
        if (!isValidBarcode(barcode)) return@withContext ProductLookupResult.NotFound
        try {
            val connection = URL("$OFF_BASE/$barcode.json?fields=$PRODUCT_FIELDS").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                // Open Food Facts pide identificar a la app en el User-Agent.
                connection.setRequestProperty("User-Agent", "Miga/${BuildConfig.VERSION_NAME} (miga@calamares.org)")
                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_NOT_FOUND) return@withContext ProductLookupResult.NotFound
                if (code != HttpURLConnection.HTTP_OK) return@withContext ProductLookupResult.Error(L10n.str(R.string.open_food_facts_responded_code, code))
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val product = parseProductResponse(body, barcode)
                if (product == null) ProductLookupResult.NotFound else ProductLookupResult.Found(product)
            } finally {
                connection.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ProductLookupResult.Error(e.message ?: e::class.simpleName ?: L10n.str(R.string.couldnt_reach_open_food_facts))
        }
    }

    /**
     * Busca productos por nombre (p. ej. "leche", "galletas digestive"), los más escaneados primero.
     * Open Food Facts pide no lanzar búsquedas en cada pulsación de tecla: se llama solo al enviar.
     * Con [spainOnly] solo salen productos que se venden en España.
     */
    suspend fun search(query: String, spainOnly: Boolean): ProductSearchResult = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext ProductSearchResult.Success(emptyList())
        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val countryFilter = if (spainOnly) "&tagtype_0=countries&tag_contains_0=contains&tag_0=spain" else ""
            val url = "https://world.openfoodfacts.org/cgi/search.pl?search_terms=$encoded&search_simple=1&action=process&json=1&page_size=$SEARCH_PAGE_SIZE&sort_by=unique_scans_n&lc=es$countryFilter&fields=code,$PRODUCT_FIELDS"
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = SEARCH_TIMEOUT_MILLIS
                connection.readTimeout = SEARCH_TIMEOUT_MILLIS
                connection.setRequestProperty("User-Agent", "Miga/${BuildConfig.VERSION_NAME} (miga@calamares.org)")
                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    return@withContext ProductSearchResult.Error(L10n.str(R.string.open_food_facts_responded_code, code))
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                ProductSearchResult.Success(parseSearchResponse(body))
            } finally {
                connection.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ProductSearchResult.Error(e.message ?: e::class.simpleName ?: L10n.str(R.string.couldnt_search_open_food_facts))
        }
    }

    /** Productos de una respuesta de búsqueda; se saltan los que no tienen código de barras válido o nombre. */
    internal fun parseSearchResponse(body: String): List<ScannedProduct> {
        val products = json.parseToJsonElement(body).jsonObject["products"] as? JsonArray ?: return emptyList()
        return products.mapNotNull { element ->
            val product = element as? JsonObject ?: return@mapNotNull null
            val code = (product["code"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (!isValidBarcode(code)) null else parseProductObject(product, code)
        }
    }

    /** Solo dígitos, 8 a 14 (EAN-8, UPC-A, EAN-13, GTIN-14): evita construir una URL con texto arbitrario. */
    internal fun isValidBarcode(barcode: String): Boolean = barcode.length in 8..14 && barcode.all { it in '0'..'9' }

    /** null si el producto no existe o no tiene ningún nombre aprovechable. */
    internal fun parseProductResponse(body: String, barcode: String): ScannedProduct? {
        val root = json.parseToJsonElement(body).jsonObject
        val status = (root["status"] as? JsonPrimitive)?.contentOrNull
        if (status == "0") return null
        val product = root["product"] as? JsonObject ?: return null
        return parseProductObject(product, barcode)
    }

    /** Ficha de un objeto "product" de Open Food Facts (de una consulta por código o de una búsqueda). */
    internal fun parseProductObject(product: JsonObject, barcode: String): ScannedProduct? {
        fun text(key: String): String? = (product[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        fun https(key: String): String? = text(key)?.takeIf { it.startsWith("https://") }
        /** Etiquetas tipo "en:gluten" -> "gluten" (se descarta el prefijo de idioma). */
        fun tags(key: String): List<String> = (product[key] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.substringAfter(':')?.trim()?.takeIf { tag -> tag.isNotEmpty() } }
        val nutriments = product["nutriments"] as? JsonObject
        fun nutrient(key: String): Double? = (nutriments?.get(key) as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

        val brand = text("brands")?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() }
        val name = text("product_name_es") ?: text("product_name") ?: text("generic_name_es") ?: brand ?: return null
        val smallImage = https("image_front_small_url") ?: https("image_small_url")
        val info = ProductInfo(
            barcode = barcode,
            brand = brand,
            quantity = text("quantity")?.take(60),
            nutriScore = text("nutriscore_grade")?.lowercase()?.takeIf { it.length == 1 && it[0] in 'a'..'e' },
            nutriScoreValue = (text("nutriscore_score")?.toDoubleOrNull() ?: nutrient("nutrition-score-fr_100g"))?.toInt()?.takeIf { it in -20..45 },
            nova = text("nova_group")?.toDoubleOrNull()?.toInt()?.takeIf { it in 1..4 },
            ecoScore = text("ecoscore_grade")?.lowercase()?.takeIf { it.length == 1 && it[0] in 'a'..'e' },
            allergens = tags("allergens_tags").take(MAX_TAGS),
            traces = tags("traces_tags").take(MAX_TAGS),
            labels = tags("labels_tags").take(MAX_TAGS),
            analysis = tags("ingredients_analysis_tags").take(MAX_TAGS),
            additives = if (product.containsKey("additives_tags")) tags("additives_tags").take(MAX_TAGS) else null,
            energyKcal = nutrient("energy-kcal_100g"),
            fat = nutrient("fat_100g"),
            saturatedFat = nutrient("saturated-fat_100g"),
            carbohydrates = nutrient("carbohydrates_100g"),
            sugars = nutrient("sugars_100g"),
            fiber = nutrient("fiber_100g"),
            proteins = nutrient("proteins_100g"),
            salt = nutrient("salt_100g"),
            ingredients = (text("ingredients_text_es") ?: text("ingredients_text"))?.take(MAX_INGREDIENTS_CHARS),
            imageUrl = https("image_front_url") ?: smallImage,
            ingredientsImageUrl = https("image_ingredients_url"),
            nutritionImageUrl = https("image_nutrition_url")
        )
        return ScannedProduct(barcode, name, smallImage, info)
    }
}

private const val SEARCH_PAGE_SIZE = 20
private const val SEARCH_TIMEOUT_MILLIS = 15000
private const val PRODUCT_FIELDS = "product_name,product_name_es,generic_name_es,brands,quantity,nutriscore_grade,nutriscore_score,additives_tags,nova_group,ecoscore_grade,allergens_tags,traces_tags,labels_tags,ingredients_analysis_tags,nutriments,ingredients_text_es,ingredients_text,image_front_url,image_front_small_url,image_small_url,image_ingredients_url,image_nutrition_url"
private const val MAX_TAGS = 40
private const val MAX_INGREDIENTS_CHARS = 1500
