package org.calamares.miga.data.content

import org.calamares.miga.data.model.ContentItem
import org.calamares.miga.data.model.ContentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentCleanupTest {

    private var nextId = 1L
    private fun item(kind: ContentKind, name: String, usage: Int) = ContentItem(kind, nextId++, name, usage)

    @Test
    fun `plural of single-word categories`() {
        assertEquals("postres", ContentCleanup.plural("postre", "es"))
        assertEquals("arroces", ContentCleanup.plural("arroz", "es"))
        assertEquals("jamones", ContentCleanup.plural("jamón", "es"))
        assertEquals("Panes", ContentCleanup.plural("Pan", "es"))
        assertEquals("Sopas", ContentCleanup.plural("Sopas", "es"))
        assertEquals("BBQ", ContentCleanup.plural("BBQ", "en"))
        assertEquals("pastries", ContentCleanup.plural("pastry", "en"))
        assertEquals("dishes", ContentCleanup.plural("dish", "en"))
        assertEquals("Desserts", ContentCleanup.plural("Desserts", "en"))
    }

    @Test
    fun `singular and plural, accents and case are the same name`() {
        val key = { name: String -> ContentCleanup.comparisonKey(ContentKind.CATEGORY, name, "es") }
        assertTrue(ContentCleanup.sameName(key("Postre"), key("postres")))
        assertTrue(ContentCleanup.sameName(key("Arroz"), key("arroces")))
        assertTrue(ContentCleanup.sameName(key("Rápido"), key("rapido")))
        assertTrue(ContentCleanup.sameName(key("Sopa  fría"), key("sopas frías")))
        assertFalse(ContentCleanup.sameName(key("Pasta"), key("Pastel")))
        assertFalse(ContentCleanup.sameName(key("Té"), key("Tés de frutas")))
    }

    @Test
    fun `a duplicate of the user's goes into the most used one, renamed the Miga way`() {
        val mine = item(ContentKind.CATEGORY, "merienda", 5)
        val other = item(ContentKind.CATEGORY, "Meriendas saladas", 1)
        val duplicate = item(ContentKind.CATEGORY, "meriendas", 2)
        val proposals = ContentCleanup.propose(mapOf(ContentKind.CATEGORY to listOf(mine, other, duplicate)), "es")
        val merge = proposals.single { it.action == CleanupAction.MERGE }
        assertEquals(mine, merge.target)
        assertEquals("Meriendas", merge.newName)
        assertEquals(setOf(mine.id, duplicate.id), merge.items.map { it.id }.toSet())
    }

    @Test
    fun `entries the app brings are never deleted or renamed, but take in duplicates`() {
        val default = item(ContentKind.CATEGORY, "Postres", 0)
        val mine = item(ContentKind.CATEGORY, "postre", 3)
        val proposals = ContentCleanup.propose(mapOf(ContentKind.CATEGORY to listOf(default, mine)), "es")
        val merge = proposals.single()
        assertEquals(CleanupAction.MERGE, merge.action)
        assertEquals(default, merge.target)
        assertEquals("Postres", merge.newName)
    }

    @Test
    fun `only the user's unused entries are deleted`() {
        val unused = item(ContentKind.TAG, "viejo", 0)
        val used = item(ContentKind.TAG, "rápido", 4)
        val unusedDefault = item(ContentKind.EQUIPMENT, "Horno", 0)
        val shopped = item(ContentKind.INGREDIENT, "Agua de jamaica casera", 0)
        val proposals = ContentCleanup.propose(
            mapOf(
                ContentKind.TAG to listOf(unused, used),
                ContentKind.EQUIPMENT to listOf(unusedDefault),
                ContentKind.INGREDIENT to listOf(shopped)
            ),
            "es",
            shoppingNames = setOf("agua de jamaica casera")
        )
        assertEquals(listOf(unused), proposals.filter { it.action == CleanupAction.DELETE }.flatMap { it.items })
    }

    @Test
    fun `names in use are written the Miga way`() {
        val category = item(ContentKind.CATEGORY, "  cena ", 2)
        val ingredient = item(ContentKind.INGREDIENT, "queso  ahumado de cabra", 1)
        val tag = item(ContentKind.TAG, "Sin Gluten", 1)
        val proposals = ContentCleanup.propose(
            mapOf(ContentKind.CATEGORY to listOf(category), ContentKind.INGREDIENT to listOf(ingredient), ContentKind.TAG to listOf(tag)),
            "es"
        )
        val renames = proposals.filter { it.action == CleanupAction.RENAME }.associate { it.items.single().name to it.newName }
        // Tags go in lower case (and singular), as in the recipe packs.
        assertEquals(mapOf("  cena " to "Cenas", "queso  ahumado de cabra" to "Queso ahumado de cabra", "Sin Gluten" to "sin gluten"), renames)
    }

    @Test
    fun `duplicates end up written by the rule, not by the form used most`() {
        val tags = listOf(item(ContentKind.TAG, "almendras", 13), item(ContentKind.TAG, "almendra", 2))
        val merge = ContentCleanup.propose(mapOf(ContentKind.TAG to tags), "es").single { it.action == CleanupAction.MERGE }
        assertEquals("almendra", merge.newName)
        assertEquals("almendra", merge.target!!.name)
        val starters = listOf(item(ContentKind.TAG, "Aperitivo", 67), item(ContentKind.TAG, "Aperitivos", 3))
        assertEquals("aperitivo", ContentCleanup.propose(mapOf(ContentKind.TAG to starters), "es").single().newName)
        val ingredients = listOf(item(ContentKind.INGREDIENT, "almendras crudas", 9), item(ContentKind.INGREDIENT, "Almendra cruda", 1))
        assertEquals("Almendra cruda", ContentCleanup.propose(mapOf(ContentKind.INGREDIENT to ingredients), "es").single().newName)
    }

    @Test
    fun `typing a name close to an existing one suggests it`() {
        val categories = listOf("Postres", "Sopas y cremas", "Pastas")
        assertEquals("Postres", ContentCleanup.similarExisting(ContentKind.CATEGORY, "postre", categories, "es"))
        assertEquals("Postres", ContentCleanup.similarExisting(ContentKind.CATEGORY, "Postrs", categories, "es"))
        assertEquals("Sopas y cremas", ContentCleanup.similarExisting(ContentKind.CATEGORY, "sopa y crema", categories, "es"))
        assertEquals(null, ContentCleanup.similarExisting(ContentKind.CATEGORY, "Postres", categories, "es"))
        assertEquals(null, ContentCleanup.similarExisting(ContentKind.CATEGORY, "Cenas", categories, "es"))
        assertEquals("Freidora de aire", ContentCleanup.similarExisting(ContentKind.EQUIPMENT, "airfryer", listOf("Horno", "Freidora de aire"), "es"))
    }
}
