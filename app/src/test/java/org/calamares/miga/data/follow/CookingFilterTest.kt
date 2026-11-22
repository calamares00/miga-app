package org.calamares.miga.data.follow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CookingFilterTest {

    @Test
    fun `family and travel posts are off topic`() {
        assertTrue(CookingFilter.isOffTopic("1. Mis amigas del colegio de Balaguer. Qué guapas sois!", "2. Castelló de Farfanya, para mí el pueblo más bonito del mundo. Por más tiempo"))
        assertTrue(CookingFilter.isOffTopic("My holiday in the mountains with the whole family", ""))
    }

    @Test
    fun `cooking posts are kept`() {
        assertFalse(CookingFilter.isOffTopic("Aperitivo de navidad de bolas de langostinos con pistacho", "Todas las recetas las tienes escritas en mi blog"))
        assertFalse(CookingFilter.isOffTopic("Días de lluvia y cuchara", "#asmr #cocinaconcoqui"))
        assertFalse(CookingFilter.isOffTopic("Crispy chicken thighs, the easy way to a perfect dinner", ""))
        assertFalse(CookingFilter.isOffTopic("Creamy croquettes like my grandma used to make them", ""))
        assertFalse(CookingFilter.isOffTopic("Receta de tortilla de patatas jugosa", ""))
    }

    @Test
    fun `short or empty texts are never hidden`() {
        assertFalse(CookingFilter.isOffTopic("Mi primer Wellington", ""))
        assertFalse(CookingFilter.isOffTopic("", ""))
    }

    @Test
    fun `short words only match whole`() {
        assertFalse(CookingFilter.isOffTopic("Echa un poco de sal y mézclalo todo bien despacio", ""))
        assertTrue(CookingFilter.isOffTopic("Salimos del colegio y nos fuimos de excursión juntos", ""))
    }
}
