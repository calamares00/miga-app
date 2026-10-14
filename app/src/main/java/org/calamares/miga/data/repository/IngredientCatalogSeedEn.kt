package org.calamares.miga.data.repository

/** English version of [IngredientCatalogSeed]: same categories, in the same order, with English names. */
object IngredientCatalogSeedEn {

    val DEFAULT_INGREDIENTS: List<Pair<String, List<String>>> = listOf(
        "Fruit" to listOf(
            "Apple", "Pear", "Banana", "Orange", "Mandarin", "Lemon", "Lime", "Grapefruit",
            "Strawberry", "Raspberry", "Blackberry", "Blueberry", "Redcurrant", "Cherry", "Peach",
            "Nectarine", "Apricot", "Plum", "Flat peach", "Grape", "Watermelon", "Melon",
            "Pineapple", "Mango", "Papaya", "Kiwi", "Pomegranate", "Fig", "Persimmon", "Coconut",
            "Avocado", "Custard apple", "Passion fruit", "Guava", "Lychee", "Date", "Quince",
            "Loquat", "Dragon fruit"
        ),
        "Vegetables" to listOf(
            "Tomato", "Cherry tomato", "Lettuce", "Escarole", "Chicory", "Spinach", "Chard",
            "Rocket", "Lamb's lettuce", "Kale", "Collard greens", "Broccoli", "Cauliflower", "White cabbage",
            "Red cabbage", "Brussels sprouts", "Courgette", "Aubergine", "Cucumber", "Red pepper",
            "Green pepper", "Yellow pepper", "Leek", "Onion", "Spring onion", "Garlic",
            "Carrot", "Celery", "Radish", "Beetroot", "Artichoke", "Asparagus", "Green beans",
            "Peas", "Sweetcorn", "Pumpkin", "Fennel", "Turnip", "Parsnip", "Jerusalem artichoke",
            "Cardoon"
        ),
        "Pulses" to listOf(
            "Lentils", "Brown lentils", "Red lentils", "Chickpeas", "Pedrosillano chickpeas",
            "White beans", "Kidney beans", "Black beans", "Pinto beans",
            "Cannellini beans", "Soya beans", "Broad beans", "Dried peas", "Adzuki beans", "Mung beans", "Edamame",
            "Lupin beans"
        ),
        "Grains" to listOf(
            "Rice", "Brown rice", "Basmati rice", "Jasmine rice", "Arborio rice",
            "Black rice", "Oats", "Wholegrain oats", "Wheat", "Spelt", "Rye", "Barley",
            "Maize", "Sorghum", "Millet", "Teff", "Bulgur", "Couscous", "Wheat pasta",
            "Wheat noodles", "Rolled oats"
        ),
        "Meat" to listOf(
            "Beef", "Beef fillet", "Beef entrecôte", "Minced beef",
            "Veal", "Pork", "Pork loin", "Pork tenderloin", "Pork ribs",
            "Minced pork", "Lamb", "Lamb chop", "Kid goat", "Rabbit",
            "Wild boar", "Venison", "Ox", "Oxtail", "Pancetta", "Pork cheek",
            "Beef cheek"
        ),
        "Fish" to listOf(
            "Salmon", "Hake", "Cod", "Sea bass", "Sea bream", "Tuna", "Bonito", "Sardine",
            "Anchovy", "Mackerel", "Fresh anchovy", "Trout", "Turbot", "Sole", "Monkfish",
            "Meagre", "Red bream", "Horse mackerel", "Swordfish", "Broadbill swordfish", "Herring", "Eel",
            "Red mullet", "Conger eel", "Blue whiting"
        ),
        "Seafood" to listOf(
            "Prawn", "King prawn", "Shrimp", "Langoustine", "Lobster", "Spiny lobster", "Crab",
            "Spider crab", "Brown crab", "Velvet crab", "Goose barnacles", "Mussel", "Clam", "Cockle",
            "Razor clam", "Oyster", "Scallop", "Cuttlefish", "Squid", "Octopus", "Baby squid", "Small cuttlefish"
        ),
        "Dairy" to listOf(
            "Whole milk", "Semi-skimmed milk", "Skimmed milk", "Goat's milk", "Cream",
            "Cooking cream", "Whipping cream", "Natural yoghurt", "Greek yoghurt",
            "Low-fat yoghurt", "Kefir", "Butter", "Ghee", "Fresh cheese",
            "Cream cheese", "Mascarpone", "Ricotta", "Mozzarella", "Burrata", "Parmesan",
            "Grana Padano", "Cheddar", "Gouda", "Emmental", "Edam", "Roquefort", "Cabrales",
            "Manchego"
        ),
        "Eggs" to listOf(
            "Hen's egg", "Quail egg", "Egg white", "Egg yolk",
            "Liquid egg", "Pasteurised egg white", "Pasteurised egg yolk"
        ),
        "Nuts" to listOf(
            "Almond", "Walnut", "Hazelnut", "Pistachio", "Cashew", "Macadamia nut",
            "Pecan", "Brazil nut", "Pine nut", "Peanut", "Peanut butter",
            "Almond butter", "Cashew butter"
        ),
        "Seeds" to listOf(
            "Chia seeds", "Flax seeds", "Sesame seeds", "Sunflower seeds",
            "Pumpkin seeds", "Poppy seeds", "Hemp seeds",
            "Cumin seeds", "Fennel seeds", "Mustard seeds",
            "Coriander seeds", "Nigella seeds"
        ),
        "Oils and fats" to listOf(
            "Extra virgin olive oil", "Olive oil", "Sunflower oil",
            "Coconut oil", "Avocado oil", "Sesame oil", "Peanut oil",
            "Rapeseed oil", "Corn oil", "Walnut oil", "Lard",
            "Duck fat"
        ),
        "Herbs and spices" to listOf(
            "Parsley", "Coriander", "Basil", "Oregano", "Thyme", "Rosemary", "Sage",
            "Spearmint", "Mint", "Dill", "Tarragon", "Bay leaf", "Chives", "Marjoram",
            "Cumin", "Black pepper", "White pepper", "Pink pepper", "Sweet paprika",
            "Hot paprika", "Curry powder", "Turmeric", "Cinnamon", "Nutmeg", "Clove",
            "Cardamom", "Ginger", "Saffron", "Vanilla"
        ),
        "Sauces and condiments" to listOf(
            "Mayonnaise", "Ketchup", "Mustard", "Soy sauce", "Teriyaki sauce",
            "Worcestershire sauce", "Barbecue sauce", "Tomato sauce", "Hot sauce",
            "Tabasco", "Pesto", "Tahini", "Hummus", "Harissa", "Sriracha", "Vinaigrette",
            "Curry paste", "Miso paste", "Chicken stock", "Vegetable stock", "Salt",
            "Sea salt", "Maldon salt", "Fish sauce"
        ),
        "Flours" to listOf(
            "Plain flour", "Wholemeal flour", "Strong flour", "Spelt flour",
            "Rye flour", "Oat flour", "Cornmeal", "Rice flour",
            "Chickpea flour", "Almond flour", "Coconut flour",
            "Buckwheat flour", "Tapioca flour", "Potato starch", "Cornflour"
        ),
        "Sugars and sweeteners" to listOf(
            "White sugar", "Brown sugar", "Icing sugar", "Panela", "Molasses", "Honey",
            "Maple syrup", "Agave syrup", "Stevia", "Erythritol", "Xylitol", "Sucralose",
            "Saccharin", "Date paste"
        ),
        "Baking" to listOf(
            "Dark chocolate", "Milk chocolate", "White chocolate", "Cocoa powder",
            "Melting chocolate", "Chocolate chips", "Baking powder",
            "Bicarbonate of soda", "Gelatine", "Agar-agar", "Cream of tartar",
            "Almond paste", "Hazelnut paste", "Desiccated coconut", "Chocolate shavings",
            "Food colouring", "Vanilla extract", "Orange blossom water", "Rose water"
        ),
        "Tinned and jarred" to listOf(
            "Tinned tuna", "Tinned bonito", "Tinned sardines",
            "Tinned anchovies", "Tinned mussels", "Tinned cockles",
            "Piquillo peppers", "Roasted peppers", "Crushed tomatoes",
            "Tinned whole tomatoes", "Sun-dried tomatoes", "Sweetcorn", "Green olives",
            "Black olives", "Gherkins", "Capers", "Tinned asparagus",
            "Tinned artichokes", "Tinned mushrooms", "Peaches in syrup",
            "Pineapple in syrup"
        ),
        "Fermented" to listOf(
            "Sauerkraut", "Kimchi", "Miso", "Tempeh", "Natto", "Kombucha", "Kefir", "Yoghurt",
            "Fermented soy sauce", "Apple cider vinegar", "Wine vinegar",
            "Rice vinegar", "Sourdough starter", "Fermented pickles", "Fermented olives"
        ),
        "Drinks" to listOf(
            "Water", "Sparkling water", "Coconut water", "Almond milk", "Oat milk",
            "Soy milk", "Coconut milk", "Orange juice", "Apple juice",
            "Lemon juice", "Tomato juice", "Coffee", "Espresso", "Black tea", "Green tea",
            "White tea", "Chamomile tea", "Mint tea", "Hot chocolate",
            "Isotonic drink"
        ),
        "Other" to listOf(
            "Tofu", "Seitan", "Textured soy protein", "Beef stock",
            "Fish stock", "Beef broth", "Vegetable broth", "Nutritional yeast",
            "Breadcrumbs", "Panko", "Tapioca starch", "Agar-agar", "Gelatine",
            "Curry paste", "Tomato paste", "Olives", "Black truffle", "White truffle",
            "Summer truffle", "Bean sprouts", "Bamboo shoots", "Nori seaweed", "Wakame",
            "Kombu"
        )
    )
}
