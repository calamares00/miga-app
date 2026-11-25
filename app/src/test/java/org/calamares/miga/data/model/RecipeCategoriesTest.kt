package org.calamares.miga.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeCategoriesTest {

    @Test
    fun `both languages have the same categories, written the Miga way`() {
        val es = RecipeCategories.names("es")
        val en = RecipeCategories.names("en")
        assertEquals(es.size, en.size)
        assertEquals(es.size, es.toSet().size)
        assertEquals(en.size, en.toSet().size)
        (es + en).forEach { assertTrue(it, it.first().isUpperCase()) }
        RecipeCategories.ALL.forEach { category ->
            assertTrue(category.description("es").isNotBlank())
            assertTrue(category.description("en").isNotBlank())
        }
    }

    @Test
    fun `each synonym and keyword belongs to one category`() {
        val synonyms = RecipeCategories.ALL.flatMap { category -> category.synonyms.map { RecipeCategories.normalize(it) to category.es } }
        val clashes = synonyms.groupBy({ it.first }, { it.second }).filterValues { it.toSet().size > 1 }
        assertEquals(emptyMap<String, List<String>>(), clashes)
    }

    @Test
    fun `the user's categories map to the app's`() {
        val expected = mapOf(
            "Aperitivos" to "Aperitivos y tapas",
            "Carnes y aves" to "Carnes",
            "Sopas" to "Sopas y cremas",
            "Cremas" to "Sopas y cremas",
            "Verduras y hortalizas" to "Verduras",
            "Masas" to "Panes y masas",
            "Panes" to "Panes y masas",
            "Recetas de cuchara" to "Guisos y potajes",
            "Recetas elementales" to "Bases y caldos",
            "Repostería y pastelería" to "Repostería",
            "Reposterías" to "Repostería",
            "Tartas" to "Repostería",
            "Smoothies" to "Batidos y zumos",
            "Desayunos" to "Desayunos y meriendas",
            "Guarniciones" to "Guarniciones",
            "postre" to "Postres",
            "Helados" to "Helados y sorbetes",
            "Mermeladas" to "Conservas y mermeladas",
            "Cakes" to "Repostería",
            "Desserts" to "Postres"
        )
        expected.forEach { (mine, builtIn) -> assertEquals(mine, builtIn, RecipeCategories.builtInFor(mine)?.es) }
        assertNull(RecipeCategories.builtInFor("Platos principales"))
        assertNull(RecipeCategories.builtInFor("Navidad"))
    }

    @Test
    fun `mixed categories are split`() {
        val composite = RecipeCategories.splitFor("Arroces y pastas", "es") as RecipeCategories.Composite
        assertEquals(listOf("Arroces", "Pastas"), composite.candidates.map { it.es })
        val drinks = RecipeCategories.splitFor("Helados, bebidas y refrescos", "es") as RecipeCategories.Composite
        assertEquals(listOf("Helados y sorbetes", "Bebidas"), drinks.candidates.map { it.es })
        assertEquals(listOf("Entrantes", "Ensaladas"), RecipeCategories.splitFor("Entrantes y ensaladas", "es")!!.candidates.map { it.es })

        val mains = RecipeCategories.splitFor("Platos principales", "es")!!
        assertTrue(mains is RecipeCategories.ByDish)
        assertNull(mains.tag)
        assertEquals("bebés", RecipeCategories.splitFor("Papillas infantiles", "es")!!.tag)
        assertEquals("Navidad", RecipeCategories.splitFor("Navidad", "es")!!.tag)
        assertNull(RecipeCategories.splitFor("Postres", "es"))
        assertNull(RecipeCategories.splitFor("Tartas", "es"))
    }

    @Test
    fun `recipes are filed by the first kind of dish in their name`() {
        val mains = RecipeCategories.splitFor("Platos principales", "es")!!
        fun file(name: String, ingredients: List<String> = emptyList()) = RecipeCategories.fileRecipe(mains, name, ingredients)?.es
        assertEquals("Arroces", file("Arroz con pollo"))
        assertEquals("Carnes", file("Pollo con arroz"))
        assertEquals("Pescados y mariscos", file("Merluza en salsa verde"))
        assertEquals("Legumbres", file("Potaje de garbanzos con espinacas"))
        assertEquals("Guisos y potajes", file("Cocido madrileño"))
        assertEquals("Pastas", file("Lasaña de verduras"))
        assertEquals("Ensaladas", file("Ensalada de pasta"))
        assertEquals("Carnes", file("Lo que sobró del domingo", listOf("Patatas", "Muslos de pollo")))
        assertNull(file("Lo que sobró del domingo", listOf("Patatas")))

        val desserts = RecipeCategories.redistributionOf(RecipeCategories.DESSERTS)
        fun dessert(name: String) = RecipeCategories.classify(name, desserts)?.es
        assertEquals("Repostería", dessert("Tarta de queso"))
        assertEquals("Repostería", dessert("Galletas de mantequilla"))
        assertEquals("Repostería", dessert("Roscón de Reyes"))
        assertEquals("Helados y sorbetes", dessert("Helado de vainilla"))
        assertEquals("Helados y sorbetes", dessert("Sorbete de limón"))
        assertNull(dessert("Flan de huevo"))
        assertNull(dessert("Arroz con leche"))
        assertNull(dessert("Natillas caseras"))

        val drinks = RecipeCategories.splitFor("Helados, bebidas y refrescos", "es")!!
        assertEquals("Batidos y zumos", RecipeCategories.fileRecipe(drinks, "Batido de fresa")?.es)
        assertEquals("Bebidas", RecipeCategories.fileRecipe(drinks, "Limonada casera")?.es)
        assertEquals("Helados y sorbetes", RecipeCategories.fileRecipe(drinks, "Polos de sandía")?.es)
        assertEquals("Helados y sorbetes", RecipeCategories.fileRecipe(drinks, "Agua fresca de pepino")?.es)

        val theme = RecipeCategories.splitFor("Navidad", "es")!!
        assertEquals("Postres", RecipeCategories.fileRecipe(theme, "Arroz con leche")?.es)
        assertEquals("Repostería", RecipeCategories.fileRecipe(theme, "Turrón de chocolate")?.es)
        assertEquals("Carnes", RecipeCategories.fileRecipe(theme, "Pastel de carne")?.es)
        assertEquals("Repostería", RecipeCategories.fileRecipe(theme, "Tarta de calabaza")?.es)
    }
}
