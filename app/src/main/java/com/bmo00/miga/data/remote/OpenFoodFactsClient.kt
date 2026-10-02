package com.bmo00.miga.data.remote

import com.bmo00.miga.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.net.HttpURLConnection
import java.net.URL

data class ScannedProduct(val barcode: String, val name: String, val imageUrl: String?)

sealed interface ProductLookupResult {
    data class Found(val product: ScannedProduct) : ProductLookupResult
    data object NotFound : ProductLookupResult
    data class Error(val reason: String) : ProductLookupResult
}

private const val OFF_BASE = "https://world.openfoodfacts.org/api/v2/product"
private const val TIMEOUT_MILLIS = 8000

/**
 * Busca un producto por su código de barras en Open Food Facts (base de datos abierta y colaborativa;
 * sin cuenta ni clave). Solo se llama cuando el usuario escanea un código a propósito. Mismo estilo
 * que UpdateChecker/PacksCatalogClient: HttpURLConnection + kotlinx.serialization.
 */
object OpenFoodFactsClient {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun lookup(barcode: String): ProductLookupResult = withContext(Dispatchers.IO) {
        if (!isValidBarcode(barcode)) return@withContext ProductLookupResult.NotFound
        try {
            val fields = "product_name,product_name_es,generic_name_es,brands,image_front_small_url,image_small_url"
            val connection = URL("$OFF_BASE/$barcode.json?fields=$fields").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                // Open Food Facts pide identificar a la app en el User-Agent.
                connection.setRequestProperty("User-Agent", "Miga/${BuildConfig.VERSION_NAME} (https://github.com/bmo00/app-miga)")
                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_NOT_FOUND) return@withContext ProductLookupResult.NotFound
                if (code != HttpURLConnection.HTTP_OK) return@withContext ProductLookupResult.Error("Open Food Facts respondió con el código $code")
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val product = parseProductResponse(body, barcode)
                if (product == null) ProductLookupResult.NotFound else ProductLookupResult.Found(product)
            } finally {
                connection.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ProductLookupResult.Error(e.message ?: e::class.simpleName ?: "No se pudo consultar Open Food Facts")
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
        fun text(key: String): String? = (product[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        val name = text("product_name_es") ?: text("product_name") ?: text("generic_name_es")
            ?: text("brands")?.substringBefore(',')?.trim()
            ?: return null
        val image = (text("image_front_small_url") ?: text("image_small_url"))?.takeIf { it.startsWith("https://") }
        return ScannedProduct(barcode, name, image)
    }
}
