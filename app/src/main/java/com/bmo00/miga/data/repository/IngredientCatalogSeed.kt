package com.bmo00.miga.data.repository

/** Catálogo base de ingredientes agrupados por categoría, usado para poblar el catálogo. */
object IngredientCatalogSeed {

    val DEFAULT_INGREDIENTS: List<Pair<String, List<String>>> = listOf(
        "Frutas" to listOf(
            "Manzana", "Pera", "Plátano", "Naranja", "Mandarina", "Limón", "Lima", "Pomelo",
            "Fresa", "Frambuesa", "Mora", "Arándano", "Grosella", "Cereza", "Melocotón",
            "Nectarina", "Albaricoque", "Ciruela", "Paraguayo", "Uva", "Sandía", "Melón",
            "Piña", "Mango", "Papaya", "Kiwi", "Granada", "Higo", "Caqui", "Coco",
            "Aguacate", "Chirimoya", "Maracuyá", "Guayaba", "Lichi", "Dátil", "Membrillo",
            "Níspero", "Fruta de la pasión"
        ),
        "Verduras" to listOf(
            "Tomate", "Tomate cherry", "Lechuga", "Escarola", "Endibia", "Espinaca", "Acelga",
            "Rúcula", "Canónigos", "Col rizada", "Berza", "Brócoli", "Coliflor", "Col blanca",
            "Lombarda", "Coles de Bruselas", "Calabacín", "Berenjena", "Pepino", "Pimiento rojo",
            "Pimiento verde", "Pimiento amarillo", "Puerro", "Cebolla", "Cebolleta", "Ajo",
            "Zanahoria", "Apio", "Rábano", "Remolacha", "Alcachofa", "Espárrago", "Judía verde",
            "Guisantes", "Maíz", "Calabaza", "Hinojo", "Nabo", "Chirivía", "Alcachofa de Jerusalén",
            "Cardo"
        ),
        "Legumbres" to listOf(
            "Lentejas", "Lentejas pardinas", "Lentejas rojas", "Garbanzos", "Garbanzos pedrosillanos",
            "Alubias blancas", "Alubias rojas", "Alubias negras", "Alubias pintas",
            "Judías cannellini", "Soja", "Habas", "Guisantes secos", "Azuki", "Mungo", "Edamame",
            "Altramuz"
        ),
        "Cereales" to listOf(
            "Arroz", "Arroz integral", "Arroz basmati", "Arroz jazmín", "Arroz arborio",
            "Arroz negro", "Avena", "Avena integral", "Trigo", "Espelta", "Centeno", "Cebada",
            "Maíz", "Sorgo", "Mijo", "Teff", "Bulgur", "Cuscús", "Pasta de trigo",
            "Fideos de trigo", "Copos de avena"
        ),
        "Carnes" to listOf(
            "Ternera", "Solomillo de ternera", "Entrecot de ternera", "Carne picada de ternera",
            "Vacuno", "Cerdo", "Lomo de cerdo", "Solomillo de cerdo", "Costilla de cerdo",
            "Carne picada de cerdo", "Cordero", "Chuleta de cordero", "Cabrito", "Conejo",
            "Jabalí", "Ciervo", "Buey", "Rabo de toro", "Panceta", "Carrillera de cerdo",
            "Carrillera de ternera"
        ),
        "Pescados" to listOf(
            "Salmón", "Merluza", "Bacalao", "Lubina", "Dorada", "Atún", "Bonito", "Sardina",
            "Anchoa", "Caballa", "Boquerón", "Trucha", "Rodaballo", "Lenguado", "Rape",
            "Corvina", "Besugo", "Jurel", "Emperador", "Pez espada", "Arenque", "Anguila",
            "Salmonete", "Congrio", "Bacaladilla"
        ),
        "Mariscos" to listOf(
            "Gamba", "Langostino", "Camarón", "Cigala", "Bogavante", "Langosta", "Cangrejo",
            "Centollo", "Buey de mar", "Nécora", "Percebe", "Mejillón", "Almeja", "Berberecho",
            "Navaja", "Ostra", "Vieira", "Sepia", "Calamar", "Pulpo", "Chipirón", "Choco"
        ),
        "Lácteos" to listOf(
            "Leche entera", "Leche semidesnatada", "Leche desnatada", "Leche de cabra", "Nata",
            "Nata para cocinar", "Nata para montar", "Yogur natural", "Yogur griego",
            "Yogur desnatado", "Kéfir", "Mantequilla", "Mantequilla clarificada", "Queso fresco",
            "Queso crema", "Mascarpone", "Ricotta", "Mozzarella", "Burrata", "Parmesano",
            "Grana Padano", "Cheddar", "Gouda", "Emmental", "Edam", "Roquefort", "Cabrales",
            "Manchego"
        ),
        "Huevos" to listOf(
            "Huevo de gallina", "Huevo de codorniz", "Clara de huevo", "Yema de huevo",
            "Huevo líquido", "Clara pasteurizada", "Yema pasteurizada"
        ),
        "Frutos secos" to listOf(
            "Almendra", "Nuez", "Avellana", "Pistacho", "Anacardo", "Nuez de macadamia",
            "Nuez pecana", "Nuez de Brasil", "Piñón", "Cacahuete", "Crema de cacahuete",
            "Crema de almendra", "Crema de anacardo"
        ),
        "Semillas" to listOf(
            "Semillas de chía", "Semillas de lino", "Semillas de sésamo", "Semillas de girasol",
            "Semillas de calabaza", "Semillas de amapola", "Semillas de cáñamo",
            "Semillas de comino", "Semillas de hinojo", "Semillas de mostaza",
            "Semillas de cilantro", "Semillas de nigella"
        ),
        "Aceites y grasas" to listOf(
            "Aceite de oliva virgen extra", "Aceite de oliva", "Aceite de girasol",
            "Aceite de coco", "Aceite de aguacate", "Aceite de sésamo", "Aceite de cacahuete",
            "Aceite de colza", "Aceite de maíz", "Aceite de nuez", "Manteca de cerdo",
            "Grasa de pato"
        ),
        "Hierbas y especias" to listOf(
            "Perejil", "Cilantro", "Albahaca", "Orégano", "Tomillo", "Romero", "Salvia",
            "Hierbabuena", "Menta", "Eneldo", "Estragón", "Laurel", "Cebollino", "Mejorana",
            "Comino", "Pimienta negra", "Pimienta blanca", "Pimienta rosa", "Pimentón dulce",
            "Pimentón picante", "Curry", "Cúrcuma", "Canela", "Nuez moscada", "Clavo",
            "Cardamomo", "Jengibre", "Azafrán", "Vainilla"
        ),
        "Salsas y condimentos" to listOf(
            "Mayonesa", "Kétchup", "Mostaza", "Salsa de soja", "Salsa teriyaki",
            "Salsa Worcestershire", "Salsa barbacoa", "Salsa de tomate", "Salsa picante",
            "Tabasco", "Pesto", "Tahini", "Hummus", "Harissa", "Sriracha", "Vinagreta",
            "Pasta de curry", "Pasta de miso", "Caldo de pollo", "Caldo de verduras", "Sal",
            "Sal marina", "Sal Maldon", "Salsa de pescado"
        ),
        "Harinas" to listOf(
            "Harina de trigo", "Harina integral", "Harina de fuerza", "Harina de espelta",
            "Harina de centeno", "Harina de avena", "Harina de maíz", "Harina de arroz",
            "Harina de garbanzo", "Harina de almendra", "Harina de coco",
            "Harina de trigo sarraceno", "Harina de tapioca", "Fécula de patata", "Maicena"
        ),
        "Azúcares y edulcorantes" to listOf(
            "Azúcar blanco", "Azúcar moreno", "Azúcar glas", "Panela", "Melaza", "Miel",
            "Sirope de arce", "Sirope de agave", "Stevia", "Eritritol", "Xilitol", "Sucralosa",
            "Sacarina", "Dátil triturado"
        ),
        "Repostería" to listOf(
            "Chocolate negro", "Chocolate con leche", "Chocolate blanco", "Cacao en polvo",
            "Chocolate para fundir", "Pepitas de chocolate", "Levadura química",
            "Bicarbonato sódico", "Gelatina", "Agar-agar", "Cremor tártaro",
            "Pasta de almendra", "Pasta de avellana", "Coco rallado", "Virutas de chocolate",
            "Colorante alimentario", "Extracto de vainilla", "Agua de azahar", "Agua de rosas"
        ),
        "Conservas" to listOf(
            "Atún en conserva", "Bonito en conserva", "Sardinas en conserva",
            "Anchoas en conserva", "Mejillones en conserva", "Berberechos en conserva",
            "Pimientos del piquillo", "Pimientos asados", "Tomate triturado",
            "Tomate entero en conserva", "Tomate seco", "Maíz dulce", "Aceitunas verdes",
            "Aceitunas negras", "Pepinillos", "Alcaparras", "Espárragos en conserva",
            "Alcachofas en conserva", "Champiñones en conserva", "Melocotón en almíbar",
            "Piña en almíbar"
        ),
        "Fermentados" to listOf(
            "Chucrut", "Kimchi", "Miso", "Tempeh", "Natto", "Kombucha", "Kéfir", "Yogur",
            "Salsa de soja fermentada", "Vinagre de manzana", "Vinagre de vino",
            "Vinagre de arroz", "Masa madre", "Pepinillos fermentados", "Aceitunas fermentadas"
        ),
        "Bebidas" to listOf(
            "Agua", "Agua con gas", "Agua de coco", "Leche de almendras", "Leche de avena",
            "Leche de soja", "Leche de coco", "Zumo de naranja", "Zumo de manzana",
            "Zumo de limón", "Zumo de tomate", "Café", "Café espresso", "Té negro", "Té verde",
            "Té blanco", "Infusión de manzanilla", "Infusión de menta", "Cacao caliente",
            "Bebida isotónica"
        ),
        "Otros" to listOf(
            "Tofu", "Seitán", "Proteína de soja texturizada", "Caldo de carne",
            "Caldo de pescado", "Fondo de carne", "Fondo de verduras", "Levadura nutricional",
            "Pan rallado", "Panko", "Almidón de tapioca", "Agar-agar", "Gelatina",
            "Pasta de curry", "Pasta de tomate", "Aceitunas", "Trufa negra", "Trufa blanca",
            "Trufa de verano", "Brotes de soja", "Brotes de bambú", "Algas nori", "Wakame",
            "Kombu"
        )
    )
}
