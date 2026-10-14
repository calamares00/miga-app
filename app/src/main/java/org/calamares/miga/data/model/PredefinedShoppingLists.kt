package org.calamares.miga.data.model

import org.calamares.miga.L10n

/**
 * Ready-made shopping lists that can be added with one tap. They ship with the app; their
 * [ShoppingTemplate.id] values are negative to tell them apart from the user's templates (Room ids
 * are positive).
 */
object PredefinedShoppingLists {

    private fun list(id: Long, name: String, items: String) =
        ShoppingTemplate(id, name, ShoppingEntryParser.parse(items).map { TemplateItem.of(it) })

    /** The lists in the app language. */
    val ALL: List<ShoppingTemplate>
        get() = if (L10n.locale().language == "es") SPANISH else ENGLISH

    private val ENGLISH: List<ShoppingTemplate> = listOf(
        list(-1, "🧺 Pantry basics", "Rice, Pasta, Olive oil, Salt, Sugar, Flour, Lentils, Chickpeas, Tomato sauce, Tinned tuna, Coffee, Biscuits"),
        list(-2, "🥐 Breakfast", "Milk, Bread, Butter, Jam, Cereal, Coffee, Orange juice, Yoghurt, Eggs, Fruit"),
        list(-3, "🥗 Fresh food for the week", "Tomatoes, Lettuce, Carrots, Onion, Potatoes, Peppers, Courgette, Apples, Bananas, Oranges, Chicken, Fish"),
        list(-4, "🍖 Barbecue", "Sausages, Black pudding, Ribs, Burgers, Bread rolls, Charcoal, Peppers, Beer, Soft drinks, Ice, Napkins"),
        list(-5, "🎄 Christmas dinner", "Seafood, Lamb, Nougat, Shortbread, Wine, Sparkling wine, Pineapple, Nuts, Potatoes, Piquillo peppers, Prawns"),
        list(-6, "🥂 Party snacks", "Crisps, Olives, Nuts, Cheese, Ham, Bread, Beer, Soft drinks, Ice, Cups"),
        list(-7, "🧴 Cleaning and hygiene", "Detergent, Dishwasher tablets, Floor cleaner, Bleach, Toilet paper, Kitchen roll, Bin bags, Hand soap, Shampoo, Shower gel, Toothpaste"),
        list(-8, "🌱 Vegetarian dinner", "Tofu, Chickpeas, Spinach, Mushrooms, Aubergine, Courgette, Rice, Quinoa, Avocado, Tomato")
    )

    private val SPANISH: List<ShoppingTemplate> = listOf(
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
