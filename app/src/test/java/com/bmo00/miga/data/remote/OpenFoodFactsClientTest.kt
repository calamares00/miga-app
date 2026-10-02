package com.bmo00.miga.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenFoodFactsClientTest {

    @Test
    fun `spanish name wins over the generic one and the small front image is used`() {
        val body = """{"status":1,"product":{"product_name":"Whole milk","product_name_es":"Leche entera","image_front_small_url":"https://images.openfoodfacts.org/a.jpg"}}"""
        val product = OpenFoodFactsClient.parseProductResponse(body, "8410000000000")
        assertEquals("Leche entera", product?.name)
        assertEquals("https://images.openfoodfacts.org/a.jpg", product?.imageUrl)
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
}
