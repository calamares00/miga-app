package org.calamares.miga.data.carbs

/**
 * A food of [CarbFoods]: grams of available carbohydrate (without fibre) per 100 g as bought or
 * raw, the usual weight of one piece and, for amounts in spoons or cups, its grams per ml.
 */
class CarbFood(
    val id: String,
    val carbsPer100: Double,
    val gramsPerPiece: Double?,
    val gramsPerMl: Double,
    val patterns: List<List<String>>,
    /** Comes in slices ("2 rebanadas de pan de molde"): a slice is one piece. */
    val sliced: Boolean = false
) {
    /** So little that, without an amount, it does not count (meat, fish, oil, spices...). */
    val free: Boolean get() = carbsPer100 < FREE_PER_100
}

/** Below this per 100 g a food counts as free when the recipe gives no amount. */
const val FREE_PER_100 = 3.0

private fun f(id: String, carbs: Double, names: String, piece: Double? = null, ml: Double = 1.0, sliced: Boolean = false) =
    CarbFood(id, carbs, piece, ml, names.split('|').map { it.split(' ') }, sliced)

/**
 * The foods carbohydrate counting needs, with their names in Spanish and English as search keys
 * (lower case, no accents; see searchKey). A name matches singular or plural, and the longest
 * name wins ("garbanzos cocidos" before "garbanzos", "leche de coco" before "leche").
 *
 * Values: available carbohydrate per 100 g, rounded, from USDA FoodData Central (SR Legacy,
 * public domain: carbohydrate by difference minus fibre), checked one by one against BEDCA, the
 * Spanish food composition database (AESAN), and against the portion tables used in Spanish
 * diabetes education (1 portion = 10 g). Where they disagree the value nearer the Spanish product
 * was taken; the comment above each food says which. Herbs and spices count as nothing: the amounts
 * in a recipe are too small to matter.
 */
object CarbFoods {
    val all: List<CarbFood> = listOf(
        // Cereals and flours
        // USDA 79, BEDCA 86, portion tables 67
        f("rice", 78.0, "arroz|arroz bomba|arroz redondo|arroz largo|arroz basmati|arroz jazmin|arroz arborio|arroz carnaroli|arroz glutinoso|arroz para sushi|rice|basmati rice|jasmine rice|arborio rice|long grain rice|short grain rice|sushi rice|glutinous rice", ml = 0.85),
        // USDA 73, BEDCA 81
        f("brown_rice", 73.0, "arroz integral|brown rice", ml = 0.85),
        // USDA 28 (cooked)
        f("cooked_rice", 28.0, "arroz cocido|arroz hervido|arroz blanco cocido|arroz de la vispera|cooked rice|leftover rice|steamed rice", ml = 0.8),
        // USDA 58, BEDCA 66, portion tables 67
        f("oats", 60.0, "avena|copos de avena|harina de avena|salvado de avena|oats|rolled oats|oat flakes|oatmeal|oat flour|porridge oats", ml = 0.4),
        // USDA 72, portion tables 67
        f("couscous", 72.0, "cuscus|cus cus|couscous", ml = 0.7),
        // USDA 63
        f("bulgur", 63.0, "bulgur|trigo bulgur", ml = 0.7),
        // USDA 57, BEDCA 49
        f("quinoa", 55.0, "quinoa|quinua", ml = 0.75),
        // USDA 74, BEDCA 72, portion tables 77
        f("wheat_flour", 73.0, "harina|harina de trigo|harina de fuerza|harina floja|harina comun|harina de reposteria|harina 00|harina 0|harina panificable|harina leudante|harina de espelta|flour|plain flour|all purpose flour|bread flour|strong flour|self raising flour|cake flour|spelt flour|wheat flour|00 flour", ml = 0.55),
        // USDA 61, BEDCA 63
        f("wholewheat_flour", 62.0, "harina integral|harina de trigo integral|wholemeal flour|whole wheat flour", ml = 0.55),
        // USDA 70, BEDCA 66, portion tables 77
        f("corn_flour", 70.0, "harina de maiz|harina de maiz precocida|masa harina|polenta|semola de maiz|cornmeal|maize flour|corn flour", ml = 0.6),
        // USDA 78, portion tables 77
        f("rice_flour", 78.0, "harina de arroz|harina de arroz glutinoso|rice flour|glutinous rice flour", ml = 0.6),
        // USDA 47
        f("chickpea_flour", 47.0, "harina de garbanzo|chickpea flour|gram flour|besan", ml = 0.55),
        // USDA 90, BEDCA 91 (cornflour); potato starch a little less
        f("starch", 88.0, "maicena|almidon de maiz|fecula de maiz|fecula de patata|fecula|almidon|tapioca|harina de tapioca|arrurruz|chuno|cornstarch|cornflour|corn starch|potato starch|starch|tapioca starch|arrowroot", ml = 0.55),
        // USDA 69, BEDCA 70, portion tables 67
        f("semolina", 70.0, "semola|semola de trigo|semola de trigo duro|semolina", ml = 0.7),
        // USDA 72, BEDCA 71, portion tables 67
        f("pasta", 71.0, "pasta|macarrones|espaguetis|espagueti|tallarines|fideos|fideos finos|fideua|lasana|laminas de lasana|placas de lasana|canelones|placas de canelones|penne|fusilli|farfalle|rigatoni|tagliatelle|linguine|orzo|tortellini|noquis|fideos de huevo|pasta fresca|orecchiette|macaroni|spaghetti|noodles|egg noodles|lasagne|lasagna sheets|cannelloni|fettuccine|gnocchi"),
        // USDA 79
        f("rice_noodles", 79.0, "fideos de arroz|tallarines de arroz|vermicelli de arroz|papel de arroz|obleas de arroz|rice noodles|rice vermicelli|rice paper|rice stick noodles", piece = 8.0),
        // USDA 29 (cooked)
        f("cooked_pasta", 29.0, "pasta cocida|espaguetis cocidos|fideos cocidos|cooked pasta|cooked noodles|cooked spaghetti"),
        // USDA 47, BEDCA 47, portion tables 50
        f("bread", 47.0, "pan|pan de barra|barra de pan|pan blanco|baguette|pan del dia anterior|pan duro|pan de hogaza|hogaza|chapata|pan de pueblo|pan candeal|pan de payes|molletes|mollete|panecillos|panecillo|pan de pita|pita|pan naan|naan|pan de hamburguesa|bollos de hamburguesa|pan de perrito|pan de bocadillo|arepas|arepa|bread|white bread|stale bread|crusty bread|ciabatta|bread rolls|bread roll|pita bread|pitta|burger buns|hot dog buns|buns|sourdough|hoagie rolls|sub rolls", piece = 60.0),
        // USDA 47, BEDCA 46
        f("sliced_bread", 46.0, "pan de molde|rebanadas de pan de molde|pan de molde integral|pan brioche|brioche|sliced bread|sandwich bread|white sandwich bread", piece = 25.0, sliced = true),
        // USDA 37, BEDCA 44
        f("wholemeal_bread", 41.0, "pan integral|wholemeal bread|whole wheat bread|brown bread"),
        // USDA 52, BEDCA 59; rusks are drier
        f("toast", 62.0, "pan tostado|biscotes|picatostes|tostadas|crostones|toast|melba toast|croutons|rusks", piece = 10.0, sliced = true),
        // USDA 68
        f("breadcrumbs", 68.0, "pan rallado|panko|breadcrumbs|bread crumbs", ml = 0.45),
        // USDA 46
        f("tortilla_wheat", 46.0, "tortillas de trigo|tortilla de trigo|wraps|tortillas para burritos|tortillas de harina|flour tortillas|flour tortilla|wheat tortillas", piece = 45.0),
        // USDA 38
        f("tortilla_corn", 38.0, "tortillas de maiz|tortilla de maiz|totopos|nachos|corn tortillas|corn tortilla|tortilla chips", piece = 25.0),
        // USDA 44, BEDCA 32, portion tables 48
        f("puff_pastry", 40.0, "masa de hojaldre|hojaldre|laminas de hojaldre|puff pastry", piece = 230.0),
        // USDA 49; empanadilla wrappers a little more
        f("shortcrust", 52.0, "masa quebrada|masa brisa|masa para empanadillas|obleas de empanadilla|masa filo|pasta filo|masa wonton|obleas|masa para wantan|obleas de wantan|laminas de pasta filo|tapas de empanada|discos de empanada|hojas de brick|masa brick|warqa|obleas para rollitos|shortcrust pastry|pie crust|filo pastry|phyllo|wonton wrappers|dumpling wrappers|spring roll wrappers|filo sheets|empanadilla wrappers|empanada pastry discs|brick pastry sheets", piece = 15.0),
        // USDA 50 (bread dough)
        f("pizza_dough", 48.0, "masa de pizza|pizza dough"),
        // USDA 68, BEDCA 69, portion tables 71
        f("biscuits", 69.0, "galletas|galletas maria|galletas digestive|bizcochos de soletilla|soletillas|galletas tipo maria|biscuits|digestive biscuits|cookies|ladyfingers|graham crackers|rich tea biscuits", piece = 7.0),
        // USDA 85 (corn flakes); muesli and granola have less
        f("cornflakes", 75.0, "copos de maiz|cereales de desayuno|muesli|granola|maiz para palomitas|palomitas|cornflakes|breakfast cereal|popcorn kernels|popcorn", ml = 0.15),
        // Pulses
        // USDA 51, BEDCA 49, portion tables 50
        f("chickpeas", 50.0, "garbanzos|garbanzos secos|chickpeas|dried chickpeas|garbanzo beans", ml = 0.8),
        // USDA 16, BEDCA 14 (canned)
        f("chickpeas_cooked", 15.0, "garbanzos cocidos|garbanzos en conserva|garbanzos de bote|bote de garbanzos|cooked chickpeas|canned chickpeas|tinned chickpeas"),
        // USDA 53, BEDCA 49, portion tables 50
        f("lentils", 50.0, "lentejas|lentejas pardinas|lentejas rojas|lentejas castellanas|lentejas verdes|dal|lentils|red lentils|green lentils|brown lentils|puy lentils", ml = 0.85),
        // USDA 12, BEDCA 11 (canned)
        f("lentils_cooked", 12.0, "lentejas cocidas|lentejas en conserva|cooked lentils|canned lentils"),
        // USDA 45, BEDCA 42, portion tables 50
        f("beans", 44.0, "alubias|alubias blancas|judias blancas|judias pintas|alubias rojas|judias rojas|frijoles|frijoles negros|alubias negras|fabes|judiones|caparrones|pochas|alubias pintas|judia mungo|judias mungo|frijol mungo|beans|white beans|haricot beans|cannellini beans|kidney beans|black beans|pinto beans|borlotti beans|navy beans|butter beans|lima beans|mung beans", ml = 0.8),
        // USDA 16, BEDCA 16
        f("beans_cooked", 16.0, "alubias cocidas|alubias en conserva|judias cocidas|frijoles cocidos|alubias de bote|gandules cocidos|gandules|garrofon cocido|garrofon|cooked beans|canned beans|tinned beans|canned kidney beans|canned black beans|baked beans|pigeon peas"),
        // USDA 9, BEDCA 9, portion tables 12
        f("peas", 9.0, "guisantes|guisantes congelados|guisantes frescos|tirabeques|peas|green peas|frozen peas|garden peas|petit pois|snow peas|mangetout|sugar snap peas", ml = 0.6),
        // BEDCA 56, portion tables 59
        f("split_peas", 56.0, "guisantes secos|guisantes partidos|split peas|dried peas"),
        // USDA 7.5, BEDCA 9 (fresh)
        f("broad_beans", 8.0, "habas|habitas|habas tiernas|broad beans|fava beans"),
        // USDA 3
        f("edamame", 3.0, "edamame|soja verde"),
        // USDA 21, BEDCA 10
        f("soy_beans", 15.0, "soja en grano|habas de soja|soybeans|soy beans"),
        // USDA 0.5, BEDCA 3
        f("tofu", 1.0, "tofu|tofu firme|tofu sedoso|tempeh|seitan|firm tofu|silken tofu"),
        // Tubers and vegetables
        // USDA 36
        f("cassava", 36.0, "yuca|mandioca|name|cassava|yam", piece = 400.0),
        // USDA 5, portion tables 5
        f("turnip", 5.0, "nabo|nabos|nabicol|colinabo|turnip|turnips|swede|rutabaga|kohlrabi", piece = 150.0),
        // USDA 15, BEDCA 15, portion tables 20
        f("potato", 15.0, "patata|patatas|patatas nuevas|patata nueva|papas|patatas para freir|patata agria|patatas de guarnicion|papa criolla|papa|papas criollas|potato|potatoes|new potatoes|baby potatoes|floury potatoes|waxy potatoes|maris piper|russet potatoes", piece = 200.0),
        // USDA 17, BEDCA 23, portion tables 20
        f("sweet_potato", 19.0, "boniato|boniatos|batata|batatas|camote|sweet potato|sweet potatoes", piece = 250.0),
        // USDA 23 (frozen), BEDCA 33 (par-fried), portion tables 29
        f("fries", 28.0, "patatas fritas|patatas fritas congeladas|patatas prefritas|chips|french fries|fries|frozen chips"),
        // USDA 51, BEDCA 50, portion tables 56
        f("crisps", 50.0, "patatas chips|patatas de bolsa|krupuk|chips de gambas|crisps|potato chips|prawn crackers"),
        // USDA 75, BEDCA 79
        f("mashed_flakes", 75.0, "copos de pure de patata|pure de patata en copos|instant mashed potato|potato flakes"),
        // USDA 17 (sweet corn)
        f("corn", 15.0, "maiz|maiz dulce|granos de maiz|mazorcas de maiz|mazorca|elote|choclo|choclos|choclo cocido|choclo desgranado|maiz pozolero|corn|sweetcorn|sweet corn|corn kernels|corn on the cob|hominy|drained precooked hominy", piece = 200.0, ml = 0.7),
        // USDA 48 (roasted), BEDCA 37 (raw), portion tables 40
        f("chestnuts", 40.0, "castanas|chestnuts", piece = 10.0),
        // USDA 8, BEDCA 5, portion tables 10
        f("onion", 7.0, "cebolla|cebollas|cebolla morada|cebolla roja|cebolla blanca|cebolla dulce|cebolla de figueres|chalota|chalotas|chalotes|cebollitas|cebollitas francesas|cebolla perla|onion|onions|red onion|white onion|brown onion|yellow onion|sweet onion|shallot|shallots|pearl onions|baby onions", piece = 150.0),
        // USDA 5, BEDCA 2
        f("spring_onion", 4.0, "cebolleta|cebolletas|cebollino|cebollinos|cebolla tierna|cebolla china|spring onion|spring onions|scallion|scallions|green onions|chives", piece = 30.0),
        // USDA 12, BEDCA 4, portion tables 4
        f("leek", 6.0, "puerro|puerros|leek|leeks", piece = 150.0),
        // USDA 31, BEDCA 24, portion tables 25
        f("garlic", 27.0, "ajo|ajos|ajo picado|cabeza de ajos|ajo morado|garlic|garlic cloves|minced garlic", piece = 5.0),
        // USDA 7, BEDCA 7, portion tables 10
        f("carrot", 7.0, "zanahoria|zanahorias|zanahorias baby|carrot|carrots|baby carrots", piece = 80.0),
        // USDA 7, BEDCA 6, portion tables 10
        f("beetroot", 7.0, "remolacha|remolachas|remolacha cocida|beetroot|beets|beet|cooked beetroot", piece = 150.0),
        // USDA 13, portion tables 13
        f("parsnip", 13.0, "chirivia|chirivias|parsnip|parsnips", piece = 150.0),
        // USDA 10 (butternut), BEDCA 6
        f("pumpkin", 7.0, "calabaza|calabaza violin|calabaza moscada|calabaza cacahuete|pumpkin|butternut squash|squash|butternut"),
        // USDA 3, BEDCA 3.5, portion tables 4
        f("tomato", 3.0, "tomate|tomates|tomates maduros|tomate de pera|tomates pera|tomate rallado|tomates cherry|tomate cherry|tomates en rama|tomate natural|tomate pelado|tomates pelados|tomate pelado en conserva|tomate entero en conserva|tomatillos|tomatillo|tomato|tomatoes|cherry tomatoes|plum tomatoes|vine tomatoes|ripe tomatoes|grated tomato|peeled tomatoes|tinned tomatoes|canned tomatoes|chopped tomatoes", piece = 120.0),
        // USDA 5, BEDCA 6
        f("crushed_tomato", 5.0, "tomate triturado|tomate troceado|passata|salsa de tomate natural|crushed tomatoes|tomato passata", ml = 1.05),
        // USDA 15, BEDCA 13
        f("tomato_paste", 14.0, "concentrado de tomate|tomate concentrado|pasta de tomate|doble concentrado de tomate|tomato paste|tomato puree|tomato concentrate", ml = 1.1),
        // USDA 6, BEDCA 5; Spanish tomate frito has sugar
        f("tomato_sauce", 7.0, "tomate frito|salsa de tomate|sofrito de tomate|tomato sauce|marinara sauce|pasta sauce", ml = 1.05),
        // USDA 4, BEDCA 4.5, portion tables 5
        f("pepper", 4.0, "pimiento|pimientos|pimiento rojo|pimiento verde|pimiento amarillo|pimiento italiano|pimientos de padron|pimiento morron|pimientos asados|pimientos del piquillo|nora|noras|pimiento choricero|pepper|peppers|red pepper|green pepper|yellow pepper|bell pepper|bell peppers|roasted peppers|piquillo peppers", piece = 160.0),
        // USDA 4, BEDCA 4, portion tables 4
        f("green_beans", 4.0, "judias verdes|judia verde|judias verdes planas|ejotes|green beans|runner beans|french beans|string beans"),
        // USDA 5, BEDCA 2, portion tables 6
        f("artichoke", 4.0, "alcachofa|alcachofas|corazones de alcachofa|artichoke|artichokes|artichoke hearts", piece = 120.0),
        // USDA 7
        f("celeriac", 7.0, "apionabo|celeriac"),
        // USDA 1.4, BEDCA 1.4
        f("leafy", 1.5, "lechuga|lechugas|escarola|rucula|canonigos|endibia|endibias|espinacas|espinacas frescas|espinacas baby|acelgas|acelga|berros|kale|col rizada|borraja|grelos|cogollos|cogollo|brotes tiernos|ensalada verde|hojas verdes|achicoria|radicchio|hojas de lechuga|alga nori|algas|alga wakame|wakame|nori|hojas de parra|choy sum|cavolo nero|col negra|lettuce|rocket|arugula|spinach|baby spinach|chard|swiss chard|watercress|endive|chicory|salad leaves|mixed leaves|little gem|romaine|nori seaweed|nori sheets|seaweed|dried wakame seaweed|vine leaves"),
        // USDA 3, BEDCA 4
        f("cabbage", 3.5, "col|repollo|col blanca|col lombarda|lombarda|coles de bruselas|col de bruselas|col china|pak choi|bok choy|kimchi|kimchi maduro|jugo del kimchi|chucrut|cabbage|white cabbage|red cabbage|savoy cabbage|brussels sprouts|chinese cabbage|napa cabbage|ripe kimchi|kimchi juice|sauerkraut|coleslaw", piece = 800.0),
        // USDA 4, BEDCA 2
        f("crucifer", 3.0, "brocoli|brecol|coliflor|romanesco|broccoli|cauliflower|tenderstem broccoli", piece = 500.0),
        // USDA 2, BEDCA 2
        f("courgette", 2.5, "calabacin|calabacines|pepino|pepinos|berenjena|berenjenas|apio|apio en rama|ramas de apio|hinojo|rabanos|rabano|esparragos|esparragos verdes|esparragos trigueros|esparragos blancos|cardo|cardos|palmitos|brotes de soja|brotes de bambu|okra|quimbombo|cardo cocido|courgette|courgettes|zucchini|cucumber|cucumbers|aubergine|aubergines|eggplant|celery|celery sticks|fennel|radishes|radish|asparagus|bean sprouts|bamboo shoots|cooked cardoons|cardoons", piece = 250.0),
        // USDA 2, BEDCA 4
        f("mushrooms", 2.5, "hongos|hongos secos|orejas de judas|hongos negros|champinones|champinon|setas|seta|setas variadas|boletus|niscalos|shiitake|setas shiitake|setas de cardo|rebozuelos|trufa|mushrooms|mushroom|button mushrooms|chestnut mushrooms|wild mushrooms|porcini|oyster mushrooms|truffle", piece = 20.0),
        // USDA 4, BEDCA 1
        f("olives", 2.0, "aceitunas|aceituna|aceitunas negras|aceitunas verdes|alcaparras|pepinillos|pepinillos en vinagre|encurtidos|banderillas|olives|olive|black olives|green olives|capers|gherkins|pickles|cornichons", piece = 4.0),
        // USDA 2, BEDCA 0.4
        f("avocado", 1.5, "aguacate|aguacates|avocado|avocados", piece = 150.0),
        // Fruit
        // USDA 11, BEDCA 12, portion tables 13
        f("apple", 12.0, "manzana|manzanas|manzana golden|manzana reineta|manzana verde|manzana granny smith|apple|apples|cooking apples|bramley apples|granny smith", piece = 180.0),
        // USDA 12, BEDCA 11, portion tables 13
        f("pear", 11.0, "pera|peras|pera conferencia|pera de agua|pear|pears", piece = 170.0),
        // USDA 20, BEDCA 20, portion tables 20
        f("banana", 20.0, "platano|platanos|banana|bananas|platano maduro|platano de canarias|platano macho|ripe banana|plantain|plantains", piece = 120.0),
        // USDA 9, BEDCA 9, portion tables 10
        f("orange", 9.0, "naranja|naranjas|mandarina|mandarinas|clementinas|clementina|pomelo|orange|oranges|mandarin|mandarins|clementine|clementines|satsuma|tangerine|grapefruit", piece = 180.0),
        // USDA 6.5, portion tables 8
        f("lemon", 6.0, "limon|limones|lima|limas|lemon|lemons|lime|limes", piece = 100.0),
        // USDA 7, BEDCA 4
        f("citrus_juice", 6.0, "zumo de limon|zumo de lima|jugo de limon|jugo de lima|lemon juice|lime juice", piece = 30.0, ml = 1.03),
        // USDA 10, BEDCA 9
        f("orange_juice", 10.0, "zumo de naranja|jugo de naranja|zumo de mandarina|orange juice", ml = 1.04),
        // USDA 11, BEDCA 11
        f("fruit_juice", 11.0, "zumo de manzana|zumo de pina|zumo de uva|mosto|zumo de frutas|nectar|apple juice|pineapple juice|grape juice|fruit juice", ml = 1.04),
        // USDA 8, BEDCA 9, portion tables 10
        f("peach", 9.0, "melocoton|melocotones|nectarina|nectarinas|paraguayo|paraguayos|albaricoque|albaricoques|ciruela|ciruelas|peach|peaches|nectarine|nectarines|apricot|apricots|plum|plums", piece = 150.0),
        // USDA 6, BEDCA 7, portion tables 7
        f("berries", 6.0, "fresas|fresa|fresones|frambuesas|moras|arandanos|grosellas|frutos rojos|frutos del bosque|strawberries|strawberry|raspberries|blackberries|blueberries|redcurrants|mixed berries|berries|summer fruits", piece = 15.0),
        // USDA 14, BEDCA 13.5, portion tables 13
        f("cherries", 14.0, "cerezas|cereza|picotas|guindas|cherries|cherry", piece = 8.0),
        // USDA 17, BEDCA 16, portion tables 20
        f("grapes", 16.0, "uvas|uva|uvas blancas|uvas negras|grapes|grape|red grapes|green grapes", piece = 5.0),
        // USDA 7, BEDCA 6, portion tables 7
        f("melon", 7.0, "melon|sandia|watermelon|cantaloupe|honeydew"),
        // USDA 12, BEDCA 11.5, portion tables 10
        f("pineapple", 11.0, "pina|pina natural|pineapple|fresh pineapple"),
        // USDA 19, BEDCA 15, portion tables 20
        f("pineapple_syrup", 18.0, "pina en almibar|melocoton en almibar|fruta en almibar|macedonia en almibar|tinned pineapple|canned peaches|fruit in syrup", piece = 50.0),
        // USDA 12, BEDCA 11, portion tables 10
        f("kiwi", 11.0, "kiwi|kiwis|kiwi fruit", piece = 75.0),
        // USDA 13, BEDCA 13, portion tables 14
        f("mango", 13.0, "mango|mangos|papaya|papayas|maracuya|fruta de la pasion|guayaba|chirimoya|caqui|caquis|granada|granadas|higos|higo|lichis|nisperos|mangoes|passion fruit|guava|persimmon|pomegranate|figs|fig|lychees", piece = 300.0),
        // USDA 7, BEDCA 12 (desiccated)
        f("coconut", 9.0, "coco|coco rallado|coco deshidratado|coconut|desiccated coconut|shredded coconut", ml = 0.35),
        // USDA 75, BEDCA 69, portion tables 71
        f("dried_fruit", 70.0, "pasas|uvas pasas|datiles|datil|orejones|ciruelas pasas|higos secos|arandanos secos|fruta confitada|fruta escarchada|raisins|sultanas|currants|dates|medjool dates|dried apricots|prunes|dried figs|dried cranberries|mixed peel|candied fruit|glace cherries", ml = 0.6),
        // USDA 57 minus fibre
        f("tamarind", 52.0, "tamarindo|pulpa de tamarindo|pasta de tamarindo|tamarind|tamarind paste|tamarind pulp|tamarind concentrate", ml = 1.2),
        // Nuts and seeds
        // USDA 9 (almonds), BEDCA 6
        f("nuts", 8.0, "almendras|almendra|almendra molida|almendras molidas|almendra marcona|harina de almendra|nueces|nuez|avellanas|avellana|pinones|pistachos|nueces de macadamia|nueces pecanas|nueces de pecan|frutos secos|frutos secos picados|nueces picadas|almonds|almond|ground almonds|almond flour|flaked almonds|walnuts|walnut|hazelnuts|pine nuts|pistachios|macadamia nuts|pecans|pecan nuts|chopped nuts|mixed nuts|nuts", piece = 3.0, ml = 0.5),
        // USDA 27, BEDCA 32, portion tables 33
        f("cashews", 28.0, "anacardos|anacardo|cashews|cashew nuts", piece = 1.5, ml = 0.55),
        // USDA 8, BEDCA 7
        f("peanuts", 9.0, "cacahuetes|cacahuete|cacahuetes tostados|mani|crema de cacahuete|mantequilla de cacahuete|peanuts|peanut|roasted peanuts|peanut butter", ml = 0.6),
        // USDA 12, BEDCA 10
        f("seeds", 10.0, "sesamo|semillas de sesamo|ajonjoli|tahini|tahin|pipas de girasol|pipas de calabaza|semillas de calabaza|semillas de girasol|semillas de chia|chia|semillas de lino|linaza|semillas de amapola|amapola|semillas|semillas variadas|sesame seeds|sesame|sunflower seeds|pumpkin seeds|pepitas|chia seeds|flaxseed|linseed|poppy seeds|mixed seeds|seeds", ml = 0.6),
        // Dairy
        // USDA 5, BEDCA 5, portion tables 5
        f("milk", 5.0, "leche|leche entera|leche semidesnatada|leche desnatada|leche fresca|leche templada|leche tibia|leche fria|leche de vaca|leche sin lactosa|leche de cabra|suero de leche|buttermilk|milk|whole milk|semi skimmed milk|skimmed milk|full fat milk|warm milk|lactose free milk|goat milk", ml = 1.03),
        // USDA 6, BEDCA 6, portion tables 5
        f("plant_milk", 5.0, "bebida de soja|leche de soja|bebida de avena|leche de avena|bebida de almendra|leche de almendras|bebida de arroz|leche de arroz|bebida vegetal|soy milk|soya milk|oat milk|almond milk|rice milk|plant milk", ml = 1.03),
        // USDA 10
        f("evaporated_milk", 10.0, "leche evaporada|leche en polvo|evaporated milk|milk powder|powdered milk", ml = 1.07),
        // USDA 54, BEDCA 54, portion tables 56
        f("condensed_milk", 54.0, "leche condensada|dulce de leche|condensed milk|sweetened condensed milk", ml = 1.3),
        // USDA 3, BEDCA 3
        f("coconut_milk", 3.0, "leche de coco|crema de coco|nata de coco|coconut milk|coconut cream", ml = 1.0),
        // USDA 3, BEDCA 3.4, portion tables 3
        f("cream", 3.0, "nata|nata para montar|nata liquida|nata para cocinar|nata montada|crema de leche|creme fraiche|crema fresca|nata agria|crema agria|mascarpone|cream|double cream|single cream|whipping cream|heavy cream|light cream|sour cream", ml = 1.0),
        // USDA 5, BEDCA 5, portion tables 4
        f("yogurt", 5.0, "yogur|yogures|yogur natural|yogur griego|yogur natural sin azucar|kefir|yogur de cabra|yogurt|yoghurt|natural yogurt|plain yogurt|greek yogurt", piece = 125.0, ml = 1.05),
        // USDA 2, BEDCA 0.5
        f("cheese", 2.0, "queso|quesos|queso rallado|queso parmesano|parmesano|parmesano rallado|queso manchego|queso curado|queso semicurado|queso tierno|queso fresco|queso de burgos|queso feta|feta|mozzarella|queso mozzarella|queso de cabra|rulo de cabra|queso azul|gorgonzola|roquefort|queso cheddar|cheddar|gruyer|gruyere|emmental|queso emmental|queso gouda|ricotta|requeson|queso crema|queso de untar|queso philadelphia|cuajada|burrata|queso para gratinar|queso en lonchas|halloumi|pecorino|grana padano|queso brie|queso camembert|queso tetilla|queso idiazabal|torta del casar|quark|cheese|parmesan|grated parmesan|goat cheese|goats cheese|blue cheese|stilton|gouda|cream cheese|cottage cheese|brie|camembert|manchego|paneer|fromage frais"),
        // USDA 0.1, BEDCA 0.5
        f("butter", 0.5, "mantequilla|mantequilla blanda|mantequilla derretida|mantequilla sin sal|mantequilla salada|margarina|ghee|mantequilla clarificada|manteca|manteca de cerdo|sebo|butter|unsalted butter|salted butter|melted butter|softened butter|margarine|clarified butter|lard|dripping|suet", ml = 0.95),
        // USDA 23, BEDCA 24, portion tables 25
        f("ice_cream", 24.0, "helado|helado de vainilla|helado de nata|ice cream|vanilla ice cream", ml = 0.55),
        // USDA 23, BEDCA 15, portion tables 15
        f("custard", 17.0, "natillas|crema pastelera|flan|crema inglesa|custard|creme patissiere|pastry cream|creme caramel"),
        // USDA 8, BEDCA 11, portion tables 10
        f("bechamel", 9.0, "bechamel|salsa bechamel|white sauce|bechamel sauce", ml = 1.05),
        // Meat, fish and eggs
        // USDA 0.7
        f("egg", 1.0, "huevo|huevos|huevos camperos|huevos grandes|huevos medianos|huevos l|huevos m|yema|yemas|yemas de huevo|clara|claras|claras de huevo|huevos duros|huevo duro|huevos cocidos|huevos de codorniz|egg|eggs|large eggs|medium eggs|free range eggs|egg yolk|egg yolks|yolks|egg white|egg whites|hard boiled eggs|quail eggs", piece = 55.0),
        // USDA 0, BEDCA 0
        f("meat", 0.0, "carne|carne picada|carne picada de ternera|carne picada de cerdo|carne picada mixta|ternera|filetes de ternera|aguja de ternera|morcillo|morcillo de ternera|carrillada|carrilleras|rabo de toro|rabo|vacuno|buey|chuleton|entrecot|solomillo|solomillo de ternera|solomillo de cerdo|lomo|lomo de cerdo|cinta de lomo|cerdo|costillas|costillas de cerdo|costilla de cerdo|costilla|panceta|panceta curada|panceta fresca|tocino|bacon|beicon|papada|secreto iberico|presa iberica|pluma iberica|cordero|pierna de cordero|paletilla de cordero|chuletas de cordero|chuletillas|cabrito|conejo|liebre|pollo|pollo troceado|pollo entero|pechuga de pollo|pechugas de pollo|muslos de pollo|contramuslos de pollo|contramuslos de pollo deshuesados|contramuslos|alitas de pollo|alas de pollo|pavo|pechuga de pavo|pato|magret de pato|codorniz|codornices|perdiz|perdices|higado|higados de pollo|mollejas|callos|pata de cerdo|manitas de cerdo|oreja de cerdo|jamon|jamon serrano|jamon iberico|jamon cocido|jamon york|paleta|lacon|chorizo|chorizo para guisar|morcilla|morcilla de burgos|salchichas|salchichas frescas|salchichas de frankfurt|longaniza|butifarra|sobrasada|salchichon|fuet|lomo embuchado|cecina|mortadela|pepperoni|carne de cerdo|carne de ternera|carne de cordero|carne de pollo|carne de pavo|carne de vacuno|magro de cerdo|ternera para guisar|falda de ternera|pecho de ternera|lomo bajo|lagarto iberico|abanico iberico|frankfurt|salchicha china|salchicha andouille|lap cheong|corazon de res|matambre|osobuco|sobrebarriga|falda de res|tira de asado|cochinillo|picantones|lengua|tuetano|huesos de tuetano|carne de res|res|caracoles|pata de ternera|meat|beef|minced beef|ground beef|beef mince|pork mince|ground pork|mince|steak|sirloin|fillet|brisket|short ribs|oxtail|stewing beef|braising steak|pork|pork shoulder|pork belly|pork loin|pork chops|pork tenderloin|ribs|spare ribs|streaky bacon|pancetta|lamb|lamb shoulder|leg of lamb|lamb chops|minced lamb|rabbit|chicken|whole chicken|chicken breast|chicken breasts|chicken thighs|boneless chicken thighs|chicken drumsticks|chicken wings|chicken legs|turkey|turkey breast|duck|duck breast|quail|liver|chicken livers|ham|cured ham|prosciutto|serrano ham|black pudding|sausages|sausage|salami|veal|venison|goat|mutton|lardons|gammon|frankfurters|hot dogs|chinese sausage|andouille sausage|suckling pig|poussin|pig ear|pigs ear|trotters|pig trotters|ox tongue|tongue|marrow bones|calf foot|snails|cooked snails"),
        // USDA 0, BEDCA 0
        f("fish", 0.0, "pescado|pescados|merluza|bacalao|bacalao desalado|bacalao fresco|lomos de bacalao|lomos de merluza|rape|salmon|lomos de salmon|salmon ahumado|atun|atun en aceite|atun en lata|latas de atun|bonito|bonito del norte|bonito en aceite|emperador|pez espada|dorada|doradas|lubina|lubinas|besugo|rodaballo|lenguado|gallo|sardinas|sardina|boquerones|anchoas|anchoa|caballa|jurel|trucha|truchas|salmonete|salmonetes|cabracho|mero|pescadilla|cazon|raya|congrio|abadejo|panga|tilapia|arenque|arenques|palometa|sepia|sepias|calamar|calamares|calamar limpio|chipirones|pulpo|pota|sepia o calamar limpio|gambas|gamba|gambas peladas|langostinos|langostino|cigalas|cigala|carabineros|bogavante|langosta|mejillones|mejillon|almejas|almeja|berberechos|navajas|zamburinas|vieiras|ostras|percebes|necoras|centollo|cangrejo|cangrejos|buey de mar|erizos|huevas|caviar|angulas|chanquetes|marisco|mariscos|fumet|caldo de pescado|fish|white fish|cod|hake|haddock|monkfish|salmon fillets|smoked salmon|tuna|tinned tuna|canned tuna|swordfish|sea bream|sea bass|turbot|sole|plaice|sardines|anchovies|anchovy|mackerel|trout|red mullet|halibut|pollock|herring|kippers|squid|calamari|cuttlefish|octopus|prawns|shrimp|king prawns|langoustines|lobster|mussels|clams|cockles|scallops|oysters|crab|crab meat|seafood|fish stock"),
        // Fats, seasonings and drinks
        // BEDCA 15 (fried black pudding)
        f("blood_sausage_rice", 15.0, "morcilla de burgos|morcilla de arroz|morcilla de cebolla|rice black pudding", piece = 100.0),
        // BEDCA 6
        f("surimi", 6.0, "surimi|palitos de cangrejo|gulas|crab sticks|seafood sticks", piece = 15.0),
        // USDA 0, BEDCA 0
        f("oil", 0.0, "aceite|aceite de oliva|aceite de oliva virgen extra|aceite de oliva virgen|aceite de oliva suave|aceite de girasol|aceite de girasol para freir|aceite de oliva para freir|aceite para freir|aceite de sesamo|aceite de sesamo tostado|aceite de coco|aceite de cacahuete|aceite vegetal|aceite de colza|aceite de semillas|aove|oil|olive oil|extra virgin olive oil|sunflower oil|vegetable oil|rapeseed oil|canola oil|sesame oil|toasted sesame oil|coconut oil|groundnut oil|peanut oil|oil for frying", ml = 0.92),
        // BEDCA 0
        f("water", 0.0, "agua|agua templada|agua tibia|agua caliente|agua fria|agua hirviendo|hielo|cubitos de hielo|agua con gas|soda|water|warm water|cold water|boiling water|hot water|lukewarm water|ice|ice cubes|sparkling water|soda water", ml = 1.0),
        // BEDCA 0
        f("salt", 0.0, "sal|sal gruesa|sal fina|sal en escamas|sal marina|sal maldon|sal y pimienta|sal y pimienta negra|pimienta|pimienta negra|pimienta blanca|pimienta negra molida|pimienta recien molida|salt|sea salt|flaky salt|coarse salt|kosher salt|salt and pepper|pepper|black pepper|white pepper|freshly ground black pepper"),
        // recipe amounts are too small to count
        f("spices", 0.0, "pimenton|pimenton dulce|pimenton picante|pimenton de la vera|pimenton ahumado|comino|comino molido|cominos|canela|canela molida|canela en rama|nuez moscada|clavo|clavos|clavos de olor|curcuma|curry|curry en polvo|garam masala|cilantro molido|semillas de cilantro|jengibre molido|ajo en polvo|cebolla en polvo|oregano|oregano seco|tomillo|tomillo seco|tomillo fresco|romero|romero fresco|laurel|hoja de laurel|hojas de laurel|azafran|hebras de azafran|colorante alimentario|colorante|perejil|perejil picado|perejil fresco|cilantro|cilantro fresco|cilantro fresco picado|albahaca|albahaca fresca|hierbabuena|menta|menta fresca|eneldo|estragon|cebollino picado|hierbas provenzales|hierbas aromaticas|guindilla|guindillas|cayena|cayenas|chile|chiles|chile rojo|chile verde|chile en polvo|chile seco|chiles secos|jalapenos|jalapeno|copos de chile|pimienta de cayena|cardamomo|anis estrellado|anis|hinojo en semillas|semillas de mostaza|fenogreco|zumaque|za atar|ras el hanout|cinco especias|especias|jengibre|jengibre fresco|jengibre fresco rallado|galanga|hierba limon|lemongrass|hojas de lima kaffir|hojas de curry|pasta de jengibre y ajo|ralladura de limon|ralladura de naranja|ralladura de lima|piel de limon|cascara de limon|vainilla|vaina de vainilla|extracto de vainilla|esencia de vainilla|aroma de vainilla|agua de azahar|gelatina|hojas de gelatina|gelatina neutra|agar agar|levadura quimica|levadura en polvo|polvo de hornear|impulsor|bicarbonato|bicarbonato sodico|cremor tartaro|levadura fresca|levadura fresca de panaderia|levadura seca|levadura seca de panaderia|levadura de panaderia|levadura de cerveza|levadura nutricional|cafe|cafe soluble|cafe molido|te|infusion|cacao puro|cacao en polvo sin azucar|cacao en polvo puro|mejorana|mejorana seca|salvia|salvia fresca|alcaravea|alcaravea molida|achiote|achiote molido|bayas de enebro|enebro|gochugaru|aji molido|kasuri methi|huacatay|condimento cajun|sazonador criollo|sazon con achiote|ramillete de hierbas|bouquet garni|pandan|hojas de pandan|extracto de pandan|citronela|tallos de citronela|flores de jamaica|hibisco|guascas|pimienta en grano|granos de pimienta|pimienta de sichuan|anis en grano|matalahuva|colorante alimentario en gel|ginseng|angelica|paprika|smoked paprika|sweet paprika|cumin|ground cumin|cumin seeds|cinnamon|ground cinnamon|cinnamon stick|nutmeg|cloves|turmeric|curry powder|ground coriander|coriander seeds|ground ginger|garlic powder|onion powder|dried oregano|thyme|rosemary|bay leaf|bay leaves|saffron|parsley|fresh parsley|coriander|fresh coriander|basil|fresh basil|mint|fresh mint|dill|tarragon|chives|mixed herbs|herbs|chilli|chillies|chili|red chilli|green chilli|chilli powder|chili powder|chilli flakes|cayenne pepper|cayenne|cardamom|star anise|fennel seeds|mustard seeds|fenugreek|sumac|five spice|spices|ginger|fresh ginger|galangal|kaffir lime leaves|curry leaves|ginger garlic paste|lemon zest|orange zest|lime zest|vanilla|vanilla pod|vanilla extract|vanilla essence|orange blossom water|gelatine|gelatin|agar|baking powder|baking soda|bicarbonate of soda|cream of tartar|yeast|fresh yeast|dried yeast|instant yeast|active dry yeast|nutritional yeast|coffee|instant coffee|espresso|tea|cocoa powder|unsweetened cocoa powder|cacao powder|marjoram|dried marjoram|sage|fresh sage|caraway|caraway seeds|juniper berries|cajun seasoning|creole seasoning|sazon|pandan leaves|pandan extract|peppercorns|black peppercorns|white peppercorns|sichuan peppercorns|aniseed|dried hibiscus flowers|food colouring|food colourings|gel food colourings|red food colouring|dried guascas|chinese angelica root"),
        // USDA 0.3, BEDCA 0.6
        f("vinegar", 0.5, "vinagre|vinagre de vino|vinagre de vino blanco|vinagre de vino tinto|vinagre de jerez|vinagre de manzana|vinagre de arroz|vinegar|white wine vinegar|red wine vinegar|sherry vinegar|cider vinegar|apple cider vinegar|rice vinegar|rice wine vinegar|malt vinegar", ml = 1.01),
        // balsamic reduction, typical label value
        f("balsamic_cream", 45.0, "crema de vinagre balsamico|reduccion de modena|crema de modena|balsamic glaze|balsamic reduction", ml = 1.2),
        // USDA 1-3.5 (home-made stock)
        f("stock", 1.5, "caldo|caldo de pollo|caldo de carne|caldo de verduras|caldo vegetal|caldo de cocido|caldo casero|fondo|fondo de ave|fondo oscuro|pastilla de caldo|pastillas de caldo|pastilla de caldo de carne|pastilla de caldo de pollo|cubito de caldo|dashi|stock|chicken stock|beef stock|vegetable stock|broth|chicken broth|beef broth|bone broth|stock cube|stock cubes|bouillon|bouillon cube", piece = 10.0, ml = 1.0),
        // USDA 2, BEDCA 5
        f("mustard", 5.0, "mostaza|mostaza de dijon|mostaza antigua|mostaza en grano|salsa worcestershire|salsa perrins|tabasco|salsa picante|sriracha|salsa de pescado|nam pla|mayonesa|alioli|all i oli|salsa tartara|chimichurri|salsa verde|salsa criolla|salsa roja|salsa roja o verde|guacamole|pesto|hogao|sofrito puertorriqueno|recaito|salsa negra|mostaza japonesa|mustard|dijon mustard|wholegrain mustard|english mustard|worcestershire sauce|hot sauce|fish sauce|mayonnaise|mayo|aioli|tartare sauce|green or red salsa|red or green salsa|hot salsa|sofrito|japanese mustard", ml = 1.05),
        // USDA 4, BEDCA 7
        f("soy_sauce", 6.0, "salsa de soja|salsa de soja clara|salsa de soja oscura|salsa de soja baja en sal|tamari|sillao|douchi|judias negras fermentadas|salsa maggi|salsa lizano|nuoc cham|salsa schezwan|pasta de aji amarillo|pasta de aji panca|aji amarillo|aji panca|aji mirasol|rocoto|rocotos|soy sauce|light soy sauce|dark soy sauce|schezwan sauce|aji amarillo paste|aji panca paste|maggi sauce", ml = 1.15),
        // USDA 20 (miso), BEDCA 26
        f("savory_paste", 20.0, "miso|pasta de miso|pasta de curry|pasta de curry rojo|pasta de curry verde|gochujang|doenjang|doubanjiang|pasta de soja fermentada|taucheo|harissa|pasta de chile|sambal|salsa de ostras|salsa de ostion|miso paste|curry paste|red curry paste|green curry paste|fermented soybean paste|chilli paste|sambal oelek|oyster sauce", ml = 1.2),
        // typical label value
        f("mirin", 40.0, "mirin", ml = 1.15),
        // USDA 17, BEDCA 17
        f("balsamic", 17.0, "vinagre balsamico|vinagre de modena|balsamic vinegar", ml = 1.06),
        // USDA 27, BEDCA 22, portion tables 26
        f("ketchup", 25.0, "ketchup|salsa barbacoa|salsa bbq|salsa agridulce|salsa hoisin|salsa teriyaki|salsa de chile dulce|chutney|chutney de mango|tomato ketchup|barbecue sauce|bbq sauce|sweet and sour sauce|hoisin sauce|teriyaki sauce|sweet chilli sauce|mango chutney", ml = 1.15),
        // USDA 2.6 (white wine), BEDCA 0.1
        f("wine", 1.0, "vino|vino blanco|vino tinto|vino rosado|vino de cocinar|vino seco|cava|champan|jerez|vino fino|manzanilla|conac|brandy|ron|whisky|ginebra|vodka|orujo|anis seco|licor|kirsch|ouzo|calvados|licor de anis|pisco|tequila|sake|wine|white wine|red wine|dry white wine|dry wine|rose|sherry|dry sherry|cognac|rum|whiskey|gin|shaoxing wine|anise liqueur", ml = 1.0),
        // USDA 14, BEDCA 14, portion tables 15
        f("sweet_wine", 14.0, "vino dulce|oporto|pedro ximenez|moscatel|vermut|licor de cafe|licor de naranja|amaretto|cointreau|grand marnier|sidra|sweet wine|port|marsala|vermouth|coffee liqueur|orange liqueur|cider", ml = 1.05),
        // USDA 3.5, BEDCA 3, portion tables 4
        f("beer", 3.5, "cerveza|cerveza negra|beer|ale|stout|lager", ml = 1.01),
        // Sugars and sweets
        // USDA 100, BEDCA 100, portion tables 100
        f("sugar", 100.0, "azucar|azucar blanco|azucar blanquilla|azucar moreno|azucar glas|azucar glass|azucar extrafino|azucar de cana|azucar mascabado|panela|azucar invertido|azucar avainillado|azucar de palma|azucar de palma o moreno|azucar perlado|glucosa|dextrosa|fructosa|virutas de colores|grageas de colores|fideos de colores|gominolas|sugar|caster sugar|granulated sugar|white sugar|brown sugar|light brown sugar|dark brown sugar|demerara sugar|muscovado sugar|icing sugar|powdered sugar|confectioners sugar|palm sugar|jaggery|glucose|dextrose|sprinkles|coloured sprinkles|hundreds and thousands|gummy worms", ml = 0.85),
        // USDA 82, BEDCA 77, portion tables 83; syrups a little less
        f("honey", 78.0, "miel|miel de flores|miel de romero|sirope de arce|sirope de agave|sirope|melaza|jarabe de maiz|jarabe de glucosa|golden syrup|caramelo liquido|honey|maple syrup|agave syrup|syrup|molasses|treacle|black treacle|corn syrup|glucose syrup|liquid caramel", ml = 1.4),
        // USDA 68, BEDCA 60, portion tables 71
        f("jam", 64.0, "mermelada|mermelada de fresa|mermelada de albaricoque|mermelada de naranja amarga|confitura|compota|dulce de membrillo|membrillo|jam|strawberry jam|apricot jam|raspberry jam|marmalade|preserves|compote|quince paste", ml = 1.3),
        // USDA 54 (45-59 % cocoa), BEDCA 65, portion tables 67
        f("chocolate", 55.0, "chocolate|chocolate negro|chocolate para fundir|chocolate de cobertura|chocolate con leche|chocolate blanco|pepitas de chocolate|gotas de chocolate|virutas de chocolate|crema de cacao|nocilla|nutella|dark chocolate|plain chocolate|milk chocolate|white chocolate|chocolate chips|cooking chocolate|chocolate spread"),
        // USDA 71, BEDCA 81, portion tables 83
        f("sweet_cocoa", 78.0, "cacao en polvo|cacao azucarado|cacao soluble|colacao|nesquik|drinking chocolate|hot chocolate powder|sweetened cocoa", ml = 0.5),
        // USDA 10, BEDCA 11, portion tables 10
        f("soft_drink", 10.5, "refresco|refresco de cola|coca cola|cola|gaseosa|tonica|refresco de naranja|refresco de limon|limonada|soft drink|lemonade|tonic water|ginger ale|fizzy drink", ml = 1.04),
        // USDA 60 (sponge cake), BEDCA 40
        f("cake", 52.0, "bizcocho|magdalenas|magdalena|sobaos|brownie|bolleria|croissant|cruasan|donut|ensaimada|galletas de chocolate|cookies|churros|porras|bollo|bollos|pan de leche|sponge cake|cake|muffins|muffin|doughnut|cookie|pastries")
    )
}
