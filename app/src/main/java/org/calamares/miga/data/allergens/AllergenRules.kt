package org.calamares.miga.data.allergens

import org.calamares.miga.data.allergens.Allergen.CELERY
import org.calamares.miga.data.allergens.Allergen.CRUSTACEANS
import org.calamares.miga.data.allergens.Allergen.EGGS
import org.calamares.miga.data.allergens.Allergen.FISH
import org.calamares.miga.data.allergens.Allergen.GLUTEN
import org.calamares.miga.data.allergens.Allergen.LACTOSE
import org.calamares.miga.data.allergens.Allergen.LUPIN
import org.calamares.miga.data.allergens.Allergen.MILK
import org.calamares.miga.data.allergens.Allergen.MOLLUSCS
import org.calamares.miga.data.allergens.Allergen.MUSTARD
import org.calamares.miga.data.allergens.Allergen.PEANUTS
import org.calamares.miga.data.allergens.Allergen.SESAME
import org.calamares.miga.data.allergens.Allergen.SOY
import org.calamares.miga.data.allergens.Allergen.SULPHITES
import org.calamares.miga.data.allergens.Allergen.TREE_NUTS

/**
 * One entry of [AllergenRules]: names (search keys, see searchKey), what they bring and, in
 * [traces], allergens they do not have but may pick up by cross-contact (buckwheat and gluten).
 */
internal class AllergenRule(
    val patterns: List<List<String>>,
    val effects: Map<Allergen, AllergenLevel>,
    val traces: Set<Allergen> = emptySet()
)

private val Allergen.c get() = this to AllergenLevel.CONTAINS
private val Allergen.m get() = this to AllergenLevel.MAY_CONTAIN

private fun patterns(names: String) = names.split('|').map { name -> name.trim().split(' ').filter { it.isNotEmpty() } }

private fun r(names: String, vararg effects: Pair<Allergen, AllergenLevel>) = AllergenRule(patterns(names), effects.toMap())

/** Foods without the allergen that often carry traces of it (only gluten so far). */
private fun traces(names: String, vararg allergens: Allergen) = AllergenRule(patterns(names), emptyMap(), allergens.toSet())

// Groups used by several entries.
private val MILK_AND_LACTOSE = arrayOf(MILK.c, LACTOSE.c)
/** Butter and aged cheeses: milk, but very little lactose. */
private val MILK_LOW_LACTOSE = arrayOf(MILK.c, LACTOSE.m)
private val STOCK = arrayOf(GLUTEN.m, CELERY.m)
/** Cured sausages and cold meats: flour, milk proteins or lactose are often added. */
private val CURED_MEAT = arrayOf(GLUTEN.m, MILK.m, LACTOSE.m)

/**
 * Ingredient names (Spanish and English, without accents) and the allergens they bring, from the
 * EU list and the AESAN guide on gluten. The longest name found in an ingredient wins, so specific
 * entries correct general ones ("nuez moscada" is not a nut, "leche de coco" is not milk, "trigo
 * sarraceno" is not wheat). An entry with no allergens is such an exception. Plurals are matched
 * too ("huevo" finds "huevos", "nuez" finds "nueces").
 *
 * MAY_CONTAIN marks what may have the allergen as an ingredient depending on the product bought:
 * stock cubes, sausages, spice mixes, baking powder, chocolate... Foods that never have it but may
 * carry traces (buckwheat, maize or rice flour, dry lentils) are only traces. A name containing
 * "sin gluten" or "sin lactosa" clears those (see AllergenAnalysis).
 */
internal object AllergenRules {
    val all: List<AllergenRule> = listOf(
        // --- Cereals with gluten (wheat, including spelt and khorasan, rye, barley, oats) ---
        r("trigo|harina|harina de trigo|harina de fuerza|harina integral|harina de reposteria|harina leudante|wheat|flour|plain flour|wholemeal flour|self raising flour", GLUTEN.c),
        r("espelta|kamut|khorasan|centeno|cebada|triticale|tritordeum|spelt|rye|barley|malta|extracto de malta|malt", GLUTEN.c),
        // Oats are a cereal with gluten under EU law; only certified oats ("avena sin gluten") are safe.
        r("avena|copos de avena|harina de avena|salvado de avena|oats|oat|oatmeal|rolled oats|porridge oats", GLUTEN.c),
        r("leche de avena|bebida de avena|oat milk|oat drink", GLUTEN.c),
        r("pan|pan rallado|miga de pan|pan de molde|barra de pan|baguette|picatostes|panko|pan de pita|pan de hamburguesa|tostadas|biscote|bread|breadcrumbs|crumbs|croutons|pita|bun|toast", GLUTEN.c),
        r("pasta|espagueti|espaguetis|spaghetti|macarron|macarrones|fideo|tallarin|tallarines|lasana|placas de lasana|canelon|canelones|ravioli|raviolis|tortellini|noquis|gnocchi|penne|fusilli|tagliatelle|linguine|macaroni|noodle", GLUTEN.c),
        // Pastes that are not pasta.
        r("pasta de tomate|concentrado de tomate|tomato paste|pasta de pimiento choricero|carne de pimiento choricero|pasta de ajo|garlic paste", ),
        r("pasta de curry|curry paste|pasta de curry rojo|pasta de curry verde|red curry paste|green curry paste", GLUTEN.m, CRUSTACEANS.m, CELERY.m, MUSTARD.m),
        r("pasta de miso|miso paste", SOY.c, GLUTEN.m),
        r("pasta de almendra|almond paste", TREE_NUTS.c),
        r("pasta filo|masa filo|pasta brick|hojas de brick|filo|phyllo|filo pastry", GLUTEN.c),
        r("pasta fresca|pasta al huevo|fideos al huevo|egg noodles|fresh pasta", GLUTEN.c, EGGS.c),
        r("cuscus|couscous|bulgur|burgol|freekeh|semola|semolina|seitan", GLUTEN.c),
        r("cerveza|beer|ale|stout", GLUTEN.c, SULPHITES.m),
        r("galleta|galletas|biscuit|cookie|cracker", GLUTEN.c, MILK.m, LACTOSE.m, EGGS.m, SOY.m),
        r("bizcocho|magdalena|sponge cake|muffin", GLUTEN.c, EGGS.c, MILK.m, LACTOSE.m),
        r("hojaldre|masa de hojaldre|puff pastry", GLUTEN.c, MILK.m, LACTOSE.m),
        r("masa quebrada|masa brisa|masa de pizza|masa de empanada|obleas|obleas de empanadilla|empanadilla|pastry|shortcrust|pizza dough|pie crust|dumpling wrappers", GLUTEN.c),
        r("tortilla de trigo|tortillas de trigo|wrap|wraps|flour tortilla", GLUTEN.c),
        r("croissant|brioche", GLUTEN.c, EGGS.c, MILK.c, LACTOSE.c),
        r("rebozado|empanado|batter", GLUTEN.c, EGGS.m),
        r("bechamel|besamel|white sauce|bechamel sauce", GLUTEN.c, *MILK_AND_LACTOSE),
        r("salsa de soja|soy sauce|soja sauce|salsa teriyaki|teriyaki", GLUTEN.c, SOY.c),
        r("tamari", SOY.c, GLUTEN.m),
        r("levadura de cerveza|brewer s yeast|brewers yeast", GLUTEN.m),
        // Naturally without gluten (buckwheat is not wheat), but often with traces from
        // cross-contact: no gluten, and coeliacs are told to buy them certified.
        traces("trigo sarraceno|alforfon|harina de trigo sarraceno|buckwheat|buckwheat flour", GLUTEN),
        traces("harina de maiz|harina de arroz|harina de garbanzo|harina de coco|harina de mijo|harina de quinoa|semola de maiz|semola de arroz|polenta|corn flour|rice flour|gram flour|chickpea flour", GLUTEN),
        traces("fideos de arroz|pasta de arroz|rice noodles|rice paper|papel de arroz", GLUTEN),
        r("maicena|maizena|harina fina de maiz|almidon de maiz|fecula de patata|cornstarch|cornflour|potato starch", ),
        // Breakfast cereals of maize or rice usually have barley malt.
        r("copos de maiz|cereales de desayuno|corn flakes|cornflakes|breakfast cereal", GLUTEN.m),
        // Dry lentils often carry cereal grains (AESAN).
        traces("lentejas|lenteja|lentils|lentil", GLUTEN),
        r("levadura quimica|levadura en polvo|polvo de hornear|polvos de hornear|gasificante|impulsor|baking powder", GLUTEN.m),
        r("levadura fresca|levadura de panadero|levadura seca|yeast|dried yeast", ),
        r("caldo|caldos|pastilla de caldo|pastillas de caldo|cubito|cubitos|avecrem|caldo de pollo|caldo de carne|caldo de verduras|stock|stock cube|bouillon|broth", *STOCK),
        r("curry|curri|garam masala|ras el hanout|curry powder", GLUTEN.m, MUSTARD.m, CELERY.m),
        r("tomate frito|salsa de tomate|tomato sauce", GLUTEN.m),
        r("salsa barbacoa|barbecue sauce|bbq sauce", GLUTEN.m, MUSTARD.m, CELERY.m),
        r("chorizo|salchichon|morcilla|fuet|sobrasada|longaniza|butifarra|mortadela|salami|salchicha|salchichas|frankfurt|hamburguesa|jamon cocido|jamon york|fiambre|pavo en lonchas|sausage|sausages|black pudding|ham|cooked ham|burger|hot dog", *CURED_MEAT),
        r("cacao soluble|cacao en polvo azucarado|drinking chocolate|hot chocolate powder", GLUTEN.m, MILK.m, LACTOSE.m, SOY.m),

        // --- Crustaceans ---
        r("gamba|gambas|langostino|langostinos|cigala|bogavante|langosta|cangrejo|buey de mar|necora|camaron|centollo|centolla|carabinero|quisquilla|percebe|krill|shrimp|prawn|lobster|crab|crayfish|langoustine|scampi", CRUSTACEANS.c, SULPHITES.m),
        r("surimi|palitos de cangrejo|crab sticks", FISH.c, CRUSTACEANS.m, EGGS.m, GLUTEN.m),

        // --- Molluscs ---
        r("mejillon|almeja|berberecho|ostra|vieira|calamar|sepia|pulpo|chipiron|caracol|navaja|zamburina|chirla|pota|puntillitas|tinta de calamar|tinta de sepia|mussel|clam|cockle|oyster|scallop|squid|octopus|cuttlefish|snail|calamari|squid ink", MOLLUSCS.c),
        r("salsa de ostras|oyster sauce", MOLLUSCS.c, GLUTEN.m, SOY.m),

        // --- Fish ---
        r("pescado|merluza|bacalao|atun|bonito|salmon|sardina|anchoa|boqueron|dorada|lubina|rape|lenguado|trucha|caballa|jurel|rodaballo|mero|pez espada|emperador|abadejo|panga|tilapia|arenque|huevas|caviar|corvina|besugo|fish|cod|hake|tuna|sardine|anchovy|anchovies|trout|mackerel|haddock|sea bass|monkfish|sole|halibut|herring|swordfish|pollock", FISH.c),
        r("salsa de pescado|fumet|caldo de pescado|fish sauce|fish stock", FISH.c, GLUTEN.m, CELERY.m),
        r("salsa worcestershire|salsa perrins|worcestershire|worcestershire sauce", FISH.c, GLUTEN.m),

        // --- Eggs ---
        r("huevo|yema|clara|huevina|egg|yolk|egg white|egg yolk", EGGS.c),
        r("mayonesa|mahonesa|mayonnaise|mayo", EGGS.c, MUSTARD.m),
        r("alioli|allioli|aioli", EGGS.c),
        r("merengue|meringue", EGGS.c),
        r("salsa cesar|caesar dressing", EGGS.c, FISH.c, *MILK_AND_LACTOSE, MUSTARD.m),

        // --- Peanuts ---
        r("cacahuete|mani|crema de cacahuete|mantequilla de cacahuete|aceite de cacahuete|peanut|peanuts|groundnut|peanut butter|peanut oil", PEANUTS.c),

        // --- Soy ---
        r("soja|soya|tofu|tempeh|edamame|miso|lecitina de soja|bebida de soja|leche de soja|yogur de soja|proteina de soja|soy|soya|soy milk|soy lecithin", SOY.c),
        r("brotes de soja|germinados de soja|bean sprouts|mung bean sprouts", ),

        // --- Milk and lactose ---
        r("leche|leche entera|leche desnatada|leche semidesnatada|nata|nata para montar|nata liquida|crema de leche|crema agria|crema fresca|yogur|yogurt|yoghurt|kefir|requeson|cuajada|suero de leche|leche condensada|leche evaporada|leche en polvo|helado|milk|whole milk|skimmed milk|cream|double cream|single cream|whipping cream|sour cream|creme fraiche|buttermilk|whey|condensed milk|evaporated milk|milk powder|ice cream|yoghurt", *MILK_AND_LACTOSE),
        r("queso fresco|queso de burgos|queso crema|queso de untar|queso batido|ricotta|mozzarella|mascarpone|burrata|cottage|cream cheese|cottage cheese", *MILK_AND_LACTOSE),
        r("queso|quesos|queso curado|queso rallado|queso manchego|manchego|parmesano|grana padano|pecorino|gruyere|emmental|cheddar|feta|roquefort|gorgonzola|brie|camembert|idiazabal|cheese|parmesan|gouda|edam", *MILK_LOW_LACTOSE),
        r("mantequilla|ghee|mantequilla clarificada|butter|clarified butter", *MILK_LOW_LACTOSE),
        r("chocolate|chocolate negro|chocolate fondant|cobertura de chocolate|dark chocolate", GLUTEN.m, MILK.m, LACTOSE.m, SOY.m, TREE_NUTS.m, PEANUTS.m),
        r("chocolate con leche|chocolate blanco|milk chocolate|white chocolate", *MILK_AND_LACTOSE, GLUTEN.m, SOY.m, TREE_NUTS.m, PEANUTS.m),
        r("natillas|flan|custard", *MILK_AND_LACTOSE, EGGS.c),
        // Not milk despite the name.
        r("leche de coco|crema de coco|coconut milk|coconut cream|leche de arroz|bebida de arroz|rice milk", ),
        r("manteca de cacao|manteca de cerdo|manteca|cocoa butter|lard|cremor tartaro|cream of tartar", ),

        r("frutos secos|nuts|mixed nuts", TREE_NUTS.c, PEANUTS.m),
        // --- Tree nuts (almond, hazelnut, walnut, cashew, pecan, Brazil nut, pistachio, macadamia) ---
        r("almendra|avellana|nuez|anacardo|pacana|nuez pecana|nuez de brasil|pistacho|macadamia|nuez de macadamia|praline|mazapan|frangipane|harina de almendra|almendra molida|leche de almendra|bebida de almendra|romesco|almond|hazelnut|walnut|cashew|pecan|brazil nut|pistachio|marzipan|almond milk|ground almonds", TREE_NUTS.c),
        r("turron|nougat", TREE_NUTS.c, EGGS.m, MILK.m, LACTOSE.m),
        r("crema de cacao|crema de avellanas|nocilla|nutella|chocolate spread|hazelnut spread", TREE_NUTS.c, *MILK_AND_LACTOSE, SOY.m),
        r("pesto", *MILK_LOW_LACTOSE, TREE_NUTS.m),
        // Not tree nuts despite the name (pine nuts and coconut are not in the EU list).
        r("nuez moscada|nutmeg|coco|coconut|pinon|pinones|pine nut|pine nuts|castana|chestnut", ),

        // --- Celery ---
        r("apio|apionabo|sal de apio|semillas de apio|celery|celeriac|celery salt|celery seed", CELERY.c),

        // --- Mustard ---
        r("mostaza|mostaza de dijon|mostaza antigua|semillas de mostaza|mustard|dijon|mustard seeds", MUSTARD.c, SULPHITES.m),
        r("vinagreta|vinaigrette", MUSTARD.m, SULPHITES.m),

        // --- Sesame ---
        r("sesamo|ajonjoli|tahini|tahina|pasta de sesamo|hummus|humus|aceite de sesamo|gomasio|semillas de sesamo|sesame|sesame oil|sesame seeds", SESAME.c),

        // --- Sulphites (above 10 mg/kg: wine, dried fruit, vinegar, pickles...) ---
        r("vino|vino blanco|vino tinto|vino rosado|jerez|vino fino|manzanilla de sanlucar|cava|champan|sidra|oporto|vermut|vermu|marsala|madeira|wine|white wine|red wine|sherry|cider|port|vermouth|champagne", SULPHITES.c),
        r("vinagre|vinagre de vino|vinagre de jerez|vinagre de modena|balsamico|vinagre balsamico|vinegar|balsamic", SULPHITES.m),
        r("pasas|uvas pasas|orejones|ciruelas pasas|fruta deshidratada|raisins|sultanas|dried apricots|prunes|dried fruit", SULPHITES.m),
        r("pepinillo|pepinillos|encurtidos|alcaparra|alcaparras|banderillas|pickles|gherkins|capers", SULPHITES.m),
        r("brandy|conac|whisky|ron|rum", SULPHITES.m),

        // --- Lupin ---
        r("altramuz|altramuces|chochos|harina de altramuz|lupin|lupine|lupini", LUPIN.c)
    )
}
