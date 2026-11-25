package org.calamares.miga.data.content

import org.calamares.miga.data.model.ContentKind
import org.junit.Assert.assertEquals
import org.junit.Test

/** Singular and plural of ingredient, product and tag names, as Content cleanup writes them. */
class ContentNamesTest {

    private fun es(name: String) = ContentNames.singular(name, "es")

    @Test
    fun `spanish words go to singular`() {
        assertEquals("almendra", es("almendras"))
        assertEquals("almendra cruda", es("almendras crudas"))
        assertEquals("limón", es("limones"))
        assertEquals("calabacín", es("calabacines"))
        assertEquals("nuez", es("nueces"))
        assertEquals("pan", es("panes"))
        assertEquals("flor", es("flores"))
        assertEquals("tomate", es("tomates"))
        assertEquals("postre", es("postres"))
        assertEquals("cena saludable", es("cenas saludables"))
        assertEquals("cebolla", es("Cebollas").lowercase())
        assertEquals("crêpe", es("crêpes"))
        assertEquals("aperitivo", es("aperitivos"))
    }

    @Test
    fun `only the words before the first preposition change`() {
        assertEquals("diente de ajo", es("dientes de ajo"))
        assertEquals("pimiento del piquillo", es("pimientos del piquillo"))
        assertEquals("copo de avena", es("copos de avena"))
    }

    @Test
    fun `plural-only words, acronyms and brands are kept`() {
        assertEquals("bebés", es("bebés"))
        assertEquals("legumbres", es("legumbres"))
        assertEquals("espaguetis", es("espaguetis"))
        assertEquals("anís", es("anís"))
        assertEquals("BBQ", es("BBQ"))
        assertEquals("salsa", es("salsa"))
    }

    @Test
    fun `english words go to singular`() {
        assertEquals("berry", ContentNames.singular("berries", "en"))
        assertEquals("tomato", ContentNames.singular("tomatoes", "en"))
        assertEquals("peach", ContentNames.singular("peaches", "en"))
        assertEquals("raw almond", ContentNames.singular("raw almonds", "en"))
        assertEquals("asparagus", ContentNames.singular("asparagus", "en"))
    }

    @Test
    fun `counted ingredients go to plural`() {
        assertEquals("Huevos", ContentNames.plural("Huevo", "es"))
        assertEquals("Dientes de ajo", ContentNames.plural("Diente de ajo", "es"))
        assertEquals("Limones", ContentNames.plural("Limón", "es"))
        assertEquals("eggs", ContentNames.plural("egg", "en"))
    }

    @Test
    fun `each list is written one way`() {
        fun name(kind: ContentKind, text: String) = ContentCleanup.normalizedName(kind, text, "es")
        assertEquals("aperitivo", name(ContentKind.TAG, "Aperitivos"))
        assertEquals("almendra", name(ContentKind.TAG, "almendras"))
        assertEquals("bebés", name(ContentKind.TAG, "Bebés"))
        assertEquals("carne", name(ContentKind.TAG, "carnes"))
        assertEquals("crêpe", name(ContentKind.TAG, "Crêpes"))
        assertEquals("BBQ", name(ContentKind.TAG, "BBQ"))
        assertEquals("Almendra cruda", name(ContentKind.INGREDIENT, "almendras crudas"))
        assertEquals("Aceite", name(ContentKind.INGREDIENT, "aceite"))
        assertEquals("Agua mineral", name(ContentKind.INGREDIENT, "agua mineral"))
        assertEquals("Postres", name(ContentKind.CATEGORY, "postre"))
    }
}
