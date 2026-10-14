package org.calamares.miga.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenFoodFactsClientTest {

    @Test
    fun `spanish name wins over the generic one and the small front image is used`() {
        val body = """{"status":1,"product":{"product_name":"Whole milk","product_name_es":"Leche entera","image_front_small_url":"https://images.openfoodfacts.org/a.jpg"}}"""
        val product = OpenFoodFactsClient.parseProductResponse(body, "8410000000000", language = "es")
        assertEquals("Leche entera", product?.name)
        assertEquals("https://images.openfoodfacts.org/a.jpg", product?.imageUrl)
    }

    @Test
    fun `english app uses the english or generic name`() {
        val body = """{"status":1,"product":{"product_name":"Leche entera","product_name_en":"Whole milk","product_name_es":"Leche entera"}}"""
        assertEquals("Whole milk", OpenFoodFactsClient.parseProductResponse(body, "8410000000000", language = "en")?.name)
        val noEnglish = """{"status":1,"product":{"product_name":"Leche entera","product_name_es":"Leche"}}"""
        assertEquals("Leche entera", OpenFoodFactsClient.parseProductResponse(noEnglish, "8410000000000", language = "en")?.name)
    }

    @Test
    fun `status 0 means the product does not exist`() {
        assertNull(OpenFoodFactsClient.parseProductResponse("""{"status":0,"status_verbose":"product not found"}""", "8410000000000"))
    }

    @Test
    fun `product without any usable name is rejected`() {
        assertNull(OpenFoodFactsClient.parseProductResponse("""{"status":1,"product":{"product_name":"  "}}""", "8410000000000"))
    }

    @Test
    fun `brand is the last resort for the name`() {
        val product = OpenFoodFactsClient.parseProductResponse("""{"status":1,"product":{"brands":"Hacendado, Otra"}}""", "8410000000000")
        assertEquals("Hacendado", product?.name)
    }

    @Test
    fun `non https images are dropped`() {
        val product = OpenFoodFactsClient.parseProductResponse("""{"status":1,"product":{"product_name":"Pan","image_small_url":"http://x/a.jpg"}}""", "8410000000000")
        assertNull(product?.imageUrl)
    }

    @Test
    fun `barcode validation accepts only 8 to 14 digits`() {
        assertTrue(OpenFoodFactsClient.isValidBarcode("8410000000000"))
        assertTrue(OpenFoodFactsClient.isValidBarcode("12345670"))
        assertFalse(OpenFoodFactsClient.isValidBarcode("1234567"))
        assertFalse(OpenFoodFactsClient.isValidBarcode("84100000000a0"))
        assertFalse(OpenFoodFactsClient.isValidBarcode("../../etc"))
    }

    @Test
    fun `full product fills the info sheet`() {
        val body = """
            {"status":1,"product":{
              "product_name_es":"Galletas","brands":"Marca A, Marca B","quantity":"200 g",
              "nutriscore_grade":"c","nova_group":4,"ecoscore_grade":"unknown",
              "allergens_tags":["en:gluten","en:milk"],"traces_tags":["en:nuts"],
              "labels_tags":["en:organic"],"ingredients_analysis_tags":["en:vegetarian","en:palm-oil-free"],
              "nutriments":{"energy-kcal_100g":450,"sugars_100g":"22.5","salt_100g":0.8},
              "ingredients_text_es":"Harina de trigo, azúcar",
              "image_front_url":"https://img/front.jpg","image_front_small_url":"https://img/small.jpg",
              "image_nutrition_url":"https://img/nutri.jpg"
            }}
        """.trimIndent()
        val product = OpenFoodFactsClient.parseProductResponse(body, "8410000000000", language = "es")!!
        val info = product.info
        assertEquals("https://img/small.jpg", product.imageUrl)
        assertEquals("https://img/front.jpg", info.imageUrl)
        assertEquals("Marca A", info.brand)
        assertEquals("200 g", info.quantity)
        assertEquals("c", info.nutriScore)
        assertEquals(4, info.nova)
        assertNull(info.ecoScore)
        assertEquals(listOf("gluten", "milk"), info.allergens)
        assertEquals(listOf("nuts"), info.traces)
        assertEquals(450.0, info.energyKcal!!, 0.001)
        assertEquals(22.5, info.sugars!!, 0.001)
        assertNull(info.fat)
        assertEquals("Harina de trigo, azúcar", info.ingredients)
        assertEquals("https://img/nutri.jpg", info.nutritionImageUrl)
        assertNull(info.ingredientsImageUrl)
    }

    @Test
    fun `invalid grades and out of range nova are dropped`() {
        val body = """{"status":1,"product":{"product_name":"X","nutriscore_grade":"not-applicable","nova_group":9}}"""
        val info = OpenFoodFactsClient.parseProductResponse(body, "8410000000000")!!.info
        assertNull(info.nutriScore)
        assertNull(info.nova)
    }

    @Test
    fun `search response keeps valid products and skips the rest`() {
        val body = """
            {"count":3,"products":[
              {"code":"8410000000000","product_name_es":"Leche entera","brands":"Marca","nutriscore_grade":"b","image_front_small_url":"https://img/l.jpg"},
              {"code":"abc","product_name":"Sin código válido"},
              {"code":"8410000000017","product_name":"  "}
            ]}
        """.trimIndent()
        val results = OpenFoodFactsClient.parseSearchResponse(body, language = "es")
        assertEquals(1, results.size)
        assertEquals("Leche entera", results.single().name)
        assertEquals("8410000000000", results.single().barcode)
        assertEquals("b", results.single().info.nutriScore)
    }

    @Test
    fun `search response without products is empty`() {
        assertEquals(0, OpenFoodFactsClient.parseSearchResponse("""{"count":0}""").size)
    }
}
