package org.calamares.miga.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.runBlocking
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.MigaApp
import org.calamares.miga.data.ai.AiProvider
import org.calamares.miga.data.allergens.Allergen
import org.calamares.miga.data.export.RecipeExporter
import org.calamares.miga.data.health.AllergenDisplay
import org.calamares.miga.data.health.HealthPrefs
import org.calamares.miga.data.health.PortionPlan
import org.calamares.miga.data.local.entity.RecipeBookEntity
import org.calamares.miga.data.repository.copyRecipeToBook
import org.calamares.miga.ui.list.TestMigaApp
import org.calamares.miga.ui.navigation.MigaNavHost
import org.calamares.miga.ui.theme.MigaTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Screenshots of the real app for the website and the store listing: the app's navigation (what
 * MainActivity shows once the welcome has been seen) with recipe packs from miga-packs installed
 * as the app installs them, driven through its interface like a user would. Off in normal runs;
 * run recordRoborazziDebug with MIGA_SHOTS_DIR (output folder) and MIGA_PACKS_DIST
 * (miga-packs/dist) set.
 */
abstract class StoreShotsTest(
    /** Packs to install, the first one being where the family book's recipes come from. */
    private val packs: List<String>,
    private val familyBook: String,
    /** Recipes copied to the family book: the first one is its cover and is opened. */
    private val familyRecipes: List<String>,
    private val healthRecipe: String,
    private val darkRecipe: String,
    private val shopping: List<Triple<String, Double?, String?>>
) {

    @get:Rule
    val compose = createComposeRule()

    private val app: MigaApp = ApplicationProvider.getApplicationContext()
    private val out: String? = System.getenv("MIGA_SHOTS_DIR")
    private val dist: String? = System.getenv("MIGA_PACKS_DIST")

    /** Installs a pack ZIP with the version in its file name, so the catalogue offers no update. */
    private fun installPack(id: String): Long = runBlocking {
        val zip = File(dist!!).listFiles()!!.filter { it.name.startsWith("$id-v") }.maxBy { it.name }
        val version = zip.name.removePrefix("$id-v").removeSuffix(".zip").toInt()
        RecipeExporter.importPackFromBytes(app, zip.readBytes(), app.repository, id, version, null)
        app.repository.books.findRecipeBookByPackId(id)!!.id
    }

    private fun recipeIn(bookId: Long, name: String) = runBlocking {
        app.repository.getRecipesForBookOnce(bookId).first { it.name == name }
    }

    private fun log(message: String) = File(out!!, "progress.log").appendText("${System.currentTimeMillis() / 1000} $message\n")

    private fun waitFor(text: String, substring: Boolean = true) =
        compose.waitUntil(15_000) { compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }

    private fun tap(text: String, substring: Boolean = true) {
        log("tap $text")
        waitFor(text, substring)
        compose.onAllNodesWithText(text, substring = substring).onFirst().performClick()
        settle()
    }

    /** By its label or, when it only shows an icon, by its description. */
    private fun tapIcon(description: String) {
        log("tap icon $description")
        val matcher = hasContentDescription(description).or(hasText(description))
        runCatching { compose.waitUntil(15_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() } }.onFailure {
            val roots = compose.onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes()
            log("not found: $description; roots ${roots.size}")
            roots.indices.forEach { index ->
                log(compose.onAllNodes(androidx.compose.ui.test.isRoot())[index].printToString())
            }
            throw it
        }
        compose.onAllNodes(matcher).onFirst().performClick()
        settle()
    }

    /** The floating button at the bottom right (its label is not in the semantics tree). */
    private fun tapFab() {
        log("tap fab")
        val root = compose.onAllNodes(androidx.compose.ui.test.isRoot())[0]
        val bounds = root.fetchSemanticsNode().boundsInRoot
        root.performTouchInput { click(androidx.compose.ui.geometry.Offset(bounds.right * 0.8f, bounds.bottom * 0.95f)) }
        settle()
    }

    private fun scrollTo(text: String) {
        waitFor(text)
        compose.onAllNodesWithText(text, substring = true).onFirst().performScrollTo()
        settle()
    }

    private fun back() {
        log("back")
        Espresso.pressBack()
        settle()
    }

    /** Lets animations and photos finish before a shot. */
    private fun settle() {
        compose.waitForIdle()
        Thread.sleep(1200)
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        log("shot $name")
        settle()
        captureScreenRoboImage("$out/$name.png")
    }

    private var dark by mutableStateOf(false)


    private fun prepareLibrary() = runBlocking {
        // The default categories, utensils and products are set up when the app starts.
        val start = System.currentTimeMillis()
        while (!app.settingsRepository.areDefaultsSetUp() && System.currentTimeMillis() - start < 30_000) Thread.sleep(200)
        app.settingsRepository.setOnboardingDone()
        // The assistant ready (a key is enough to show its options; nothing is sent), but no
        // automatic health or nutrition check, which would call it on every recipe.
        app.settingsRepository.setAiHealthEnabled(false)
        app.settingsRepository.setAiNutritionEnabled(false)
        app.settingsRepository.setApiKey(AiProvider.GEMINI, "shots-key-not-used")
        val source = installPack(packs.first())
        packs.drop(1).forEach {
            installPack(it)
            log("installed $it")
        }
        // A book of one's own, as a family would start it: a few recipes copied from the packs.
        val family = app.database.recipeBookDao().insert(
            RecipeBookEntity(uid = "abuela", name = familyBook, coverPhotoUri = null, createdAt = System.currentTimeMillis())
        )
        familyRecipes.forEach { app.repository.copyRecipeToBook(app, recipeIn(source, it), family) }
        val cover = app.repository.getRecipesForBookOnce(family).first { it.name == familyRecipes.first() }.coverPhotoUri
        val dao = app.database.recipeBookDao()
        dao.update(dao.findByUid("abuela")!!.copy(coverPhotoUri = cover))
        dao.setPinned(family, true)
        log("library ready")
    }

    private fun str(id: Int) = L10n.str(id)

    @Test
    fun tour() {
        assumeTrue(out != null && dist != null)
        prepareLibrary()
        compose.setContent {
            MigaTheme(darkTheme = dark) { Surface(modifier = Modifier.fillMaxSize()) { MigaNavHost() } }
        }
        waitFor(familyBook)
        shot("01_books")

        tap(familyBook)
        waitFor(familyRecipes.first())
        shot("02_book")

        tap(familyRecipes.first())
        waitFor(str(R.string.ingredients))
        shot("03_recipe")
        scrollTo(str(R.string.method))
        shot("04_recipe_steps")

        tapIcon(str(R.string.cooking_mode))
        shot("05_cook_mode")
        back()
        back()
        back()

        // A weekly shop, written as people write it, sorted by the supermarket's aisles.
        runBlocking {
            app.repository.shopping.addIngredientsToShoppingList(shopping.map { (name, quantity, unit) -> Ingredient(name, quantity, unit) })
        }
        tap(str(R.string.shopping), substring = false)
        shot("06_shopping")

        tap(str(R.string.search), substring = false)
        shot("07_search")
        tapIcon(str(R.string.filters))
        tap(str(R.string.filter_coeliac))
        tap(Allergen.EGGS.label, substring = false)
        shot("08_filters")
        back()

        // Health: only my allergens, coeliacs, carbohydrate portions and a plan.
        runBlocking {
            app.settingsRepository.setHealthPrefs(
                HealthPrefs(allergenDisplay = AllergenDisplay.MINE, myAllergens = setOf(Allergen.EGGS, Allergen.MILK), coeliac = true)
            )
            app.settingsRepository.setCarbCountingEnabled(true)
            app.settingsRepository.setPortionPlan(PortionPlan.EXAMPLE_1500)
        }
        tap(str(R.string.settings), substring = false)
        tap(str(R.string.settings_health), substring = false)
        shot("09_health_settings")
        back()

        tap(str(R.string.books), substring = false)
        tap(familyBook)
        tap(healthRecipe)
        waitFor(str(R.string.carb_title))
        shot("10_recipe_health")
        tap(str(R.string.plan_fit_action))
        shot("11_fit_meal")
        back()
        back()
        tapFab()
        shot("12_new_recipe")
        back()
        back()
        tapIcon(str(R.string.ideas))
        shot("14_ideas")
        back()
        tap(str(R.string.books), substring = false)
        tap(familyBook)

        dark = true
        tap(darkRecipe)
        shot("13_recipe_dark")
        log("done")
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class, qualifiers = "es-rES-w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreShotsEsTest : StoreShotsTest(
    packs = listOf("cocina-espanola", "tapas-espanolas", "arroces", "cocina-italiana", "panaderia", "saludable-vegetariano"),
    familyBook = "Recetas de la abuela",
    familyRecipes = listOf("Pollo al ajillo", "Cocido madrileño", "Lentejas estofadas con chorizo", "Fabada asturiana", "Torrijas", "Arroz con leche", "Tarta de Santiago"),
    healthRecipe = "Lentejas estofadas con chorizo",
    darkRecipe = "Tarta de Santiago",
    shopping = listOf(
        Triple("tomates", 1.0, "kg"), Triple("plátanos", 6.0, null), Triple("limones", 3.0, null), Triple("lechuga", 1.0, null),
        Triple("cebollas", 2.0, null), Triple("merluza", 600.0, "g"), Triple("carne picada de ternera", 500.0, "g"),
        Triple("leche", 2.0, "l"), Triple("yogures naturales", 4.0, null), Triple("huevos", 12.0, null), Triple("arroz", 1.0, "kg"),
        Triple("lentejas pardinas", 500.0, "g"), Triple("aceite de oliva virgen extra", 1.0, "l"), Triple("café", 1.0, "paquete")
    )
)

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class, qualifiers = "en-rGB-w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreShotsEnTest : StoreShotsTest(
    packs = listOf("spanish-cooking", "spanish-tapas", "rice-dishes", "italian-cooking", "bakery", "healthy-vegetarian"),
    familyBook = "Grandma's recipes",
    familyRecipes = listOf("Garlic chicken", "Cocido madrileño", "Lentil stew with chorizo", "Fabada asturiana", "Torrijas", "Rice pudding", "Santiago almond cake"),
    healthRecipe = "Lentil stew with chorizo",
    darkRecipe = "Santiago almond cake",
    shopping = listOf(
        Triple("tomatoes", 1.0, "kg"), Triple("bananas", 6.0, null), Triple("lemons", 3.0, null), Triple("lettuce", 1.0, null),
        Triple("onions", 2.0, null), Triple("hake", 600.0, "g"), Triple("minced beef", 500.0, "g"),
        Triple("milk", 2.0, "l"), Triple("natural yoghurts", 4.0, null), Triple("eggs", 12.0, null), Triple("rice", 1.0, "kg"),
        Triple("brown lentils", 500.0, "g"), Triple("extra virgin olive oil", 1.0, "l"), Triple("coffee", 1.0, "packet")
    )
)
