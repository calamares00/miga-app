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

    private val travel = listOf(
        "Our week in Iceland: glaciers, waterfalls and black beaches" to "",
        "Mis amigas del colegio en el pueblo más bonito del mundo" to "",
        "Unboxing the new phone and first impressions after a week" to "",
        "Ruta en bici por los Pirineos con toda la familia" to ""
    )

    @Test
    fun `a channel about travel is not about cooking`() {
        assertTrue(CookingFilter.isOffTopicSource("Viajando con Marta @viajandoconmarta", travel))
    }

    @Test
    fun `a cooking channel is about cooking`() {
        assertFalse(CookingFilter.isOffTopicSource("Lola @lola", listOf(
            "Paella de verduras en 30 minutos, sin trucos" to "",
            "Lentejas con verduras para toda la semana" to "",
            "Creamy croquettes like my grandma used to make them" to "",
            "Mis amigas del colegio en el pueblo más bonito del mundo" to ""
        )))
    }

    @Test
    fun `a cooking name is enough`() {
        assertFalse(CookingFilter.isOffTopicSource("Cocina de Lola @cocinadelola", travel))
        assertFalse(CookingFilter.isOffTopicSource("Directo al Paladar directoalpaladar.com", travel))
        assertFalse(CookingFilter.isOffTopicSource("Coqui cocinaconcoqui.com", travel))
    }

    @Test
    fun `too few posts to judge`() {
        assertFalse(CookingFilter.isOffTopicSource("Marta @marta", travel.take(2)))
        // Short captions say nothing.
        assertFalse(CookingFilter.isOffTopicSource("Marta @marta", listOf("Hoy" to "", "Carbonara" to "", "🍝" to "", "Domingo" to "")))
    }

    @Test
    fun `a recipe website's addresses count`() {
        val posts = listOf(
            "Karlos Arguiñano te enseña a preparar un relleno" to "https://www.hogarmania.com/cocina/recetas/relleno_23.html",
            "El truco de Karlos Arguiñano para el día de Navidad" to "https://www.hogarmania.com/cocina/recetas/navidad_24.html",
            "Cómo preparar un menú para toda la familia sin estrés" to "https://www.hogarmania.com/cocina/recetas/familia_25.html"
        )
        assertFalse(CookingFilter.isOffTopicSource("Hogarmania hogarmania.com", posts))
        val gadgets = listOf(
            "Análisis del nuevo móvil plegable: lo hemos probado a fondo" to "https://www.tecno.example/moviles/plegable",
            "Las mejores ofertas en portátiles de esta semana para estudiar" to "https://www.tecno.example/ofertas/portatiles",
            "Así será la próxima consola: todo lo que sabemos hasta hoy" to "https://www.tecno.example/consolas/proxima"
        )
        assertTrue(CookingFilter.isOffTopicSource("Tecno tecno.example", gadgets))
    }
}

