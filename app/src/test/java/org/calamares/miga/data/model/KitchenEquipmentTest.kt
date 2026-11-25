package org.calamares.miga.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KitchenEquipmentTest {

    @Test
    fun `usual variants become the default name in the app language`() {
        assertEquals("Freidora de aire", KitchenEquipment.canonical("Airfryer", "es"))
        assertEquals("Air fryer", KitchenEquipment.canonical("freidora sin aceite", "en"))
        assertEquals("Olla exprés", KitchenEquipment.canonical("Olla Express", "es"))
        assertEquals("Plancha", KitchenEquipment.canonical("Parrilla / Plancha", "es"))
        assertEquals(listOf("Freidora de aire", "Air fryer"), KitchenEquipment.equivalents("AIRFRYER"))
    }

    @Test
    fun `unknown names are kept as written`() {
        assertEquals("Mandolina", KitchenEquipment.canonical("Mandolina", "es"))
        assertEquals("Máquina de pan", KitchenEquipment.canonical("Máquina de pan", "es"))
        assertEquals("Freidora", KitchenEquipment.canonical("Freidora", "es"))
        assertTrue(KitchenEquipment.equivalents("Mandolina").isEmpty())
    }

    @Test
    fun `parts, models, sizes and adjectives are the appliance`() {
        listOf(
            " Thermomix  TM31 ", "Thermomix TM6", "Vaso de Thermomix", "vaso thermomix", "Vaso de la Thermomix", "Varoma",
            "Cuchillas de la Thermomix", "Thermomix bowl"
        ).forEach { assertEquals(it, "Thermomix", KitchenEquipment.canonical(it, "es")) }
        assertEquals("Horno", KitchenEquipment.canonical("Horno precalentado", "es"))
        assertEquals("Horno", KitchenEquipment.canonical("Horno de convección", "es"))
        assertEquals("Oven", KitchenEquipment.canonical("Fan oven", "en"))
        assertEquals("Freidora de aire", KitchenEquipment.canonical("Cestillo de la freidora de aire", "es"))
        assertEquals("Air fryer", KitchenEquipment.canonical("Air fryer basket", "en"))
        assertEquals("Batidora", KitchenEquipment.canonical("Vaso de la batidora", "es"))
        assertEquals("Robot de cocina", KitchenEquipment.canonical("Cecotec Mambo", "es"))
        assertEquals("Olla exprés", KitchenEquipment.canonical("Olla exprés de 6 litros", "es"))
    }

    @Test
    fun `basics are obvious and dropped from AI results`() {
        assertTrue(KitchenEquipment.isObvious("Cuchillo"))
        assertTrue(KitchenEquipment.isObvious("Sartén"))
        assertTrue(KitchenEquipment.isObvious("frying pan"))
        assertFalse(KitchenEquipment.isObvious("Horno"))
        listOf("Tijeras", "Tijeras de cocina", "Sartén grande antiadherente", "Molde de silicona", "Bandeja de horno",
            "Papel de horno", "Rallador", "Varillas", "Mortero", "Rodillo", "Mixing bowl", "Baking tray", "Kitchen scissors")
            .forEach { assertTrue(it, KitchenEquipment.isObvious(it)) }
        listOf("Vaso de Thermomix", "Olla exprés", "Olla lenta", "Batidora", "Mandolina", "Sifón", "Paellera", "Máquina de pan", "Termómetro")
            .forEach { assertFalse(it, KitchenEquipment.isObvious(it)) }
        assertEquals(
            listOf("Horno", "Freidora de aire"),
            KitchenEquipment.clean(listOf("Cuchillo", "horno", "Airfryer", "Freidora de aire", " ", "Bol", "Horno precalentado", "Tijeras"), "es")
        )
    }

    @Test
    fun `defaults have no obvious items and are their own canonical names`() {
        for (language in listOf("es", "en")) {
            KitchenEquipment.defaults(language).forEach { name ->
                assertFalse(name, KitchenEquipment.isObvious(name))
                assertEquals(name, KitchenEquipment.canonical(name, language))
            }
        }
    }
}
