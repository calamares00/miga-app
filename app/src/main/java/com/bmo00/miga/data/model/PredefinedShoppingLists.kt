package com.bmo00.miga.data.model

/**
 * Listas de la compra típicas ya preparadas para añadir con un toque. Van dentro de la app (sin red);
 * sus [ShoppingTemplate.id] son negativos para distinguirlas de las plantillas del usuario (Room, id > 0).
 */
object PredefinedShoppingLists {

    private fun list(id: Long, name: String, items: String) =
        ShoppingTemplate(id, name, ShoppingEntryParser.parse(items).map { TemplateItem.of(it) })

    val ALL: List<ShoppingTemplate> = listOf(
        list(-1, "🧺 Despensa básica", "Arroz, Pasta, Aceite de oliva, Sal, Azúcar, Harina, Lentejas, Garbanzos, Tomate frito, Atún en lata, Café, Galletas"),
        list(-2, "🥐 Desayuno", "Leche, Pan, Mantequilla, Mermelada, Cereales, Café, Zumo de naranja, Yogur, Huevos, Fruta"),
        list(-3, "🥗 Frescos de la semana", "Tomates, Lechuga, Zanahorias, Cebolla, Patatas, Pimientos, Calabacín, Manzanas, Plátanos, Naranjas, Pollo, Pescado"),
        list(-4, "🍖 Barbacoa", "Chorizo, Morcilla, Costillas, Hamburguesas, Pan de bocadillo, Carbón, Pimientos, Cerveza, Refresco, Hielo, Servilletas"),
        list(-5, "🎄 Cena de Navidad", "Marisco, Cordero, Turrón, Polvorones, Vino, Cava, Piña, Frutos secos, Patatas, Pimientos del piquillo, Gambas"),
        list(-6, "🥂 Aperitivo y fiesta", "Patatas fritas, Aceitunas, Frutos secos, Queso, Jamón, Pan, Cerveza, Refresco, Hielo, Vasos"),
        list(-7, "🧴 Limpieza e higiene", "Detergente, Lavavajillas, Fregasuelos, Lejía, Papel higiénico, Papel de cocina, Bolsas de basura, Jabón de manos, Champú, Gel de ducha, Pasta de dientes"),
        list(-8, "🌱 Cena vegetariana", "Tofu, Garbanzos, Espinacas, Champiñones, Berenjena, Calabacín, Arroz, Quinoa, Aguacate, Tomate")
    )
}
