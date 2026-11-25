package org.calamares.miga.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.runBlocking
import org.calamares.miga.MigaApp
import org.calamares.miga.data.local.entity.FollowedSourceEntity
import org.calamares.miga.data.local.entity.SavedPostEntity
import org.calamares.miga.data.local.entity.SourcePostEntity
import org.calamares.miga.ui.favorites.FavoritesFilter
import org.calamares.miga.ui.favorites.FavoritesTab
import org.calamares.miga.ui.favorites.FavoritesViewModel
import org.calamares.miga.ui.favorites.SourceScreen
import org.calamares.miga.ui.favorites.SourceViewModel
import org.calamares.miga.ui.list.TestMigaApp
import org.calamares.miga.ui.theme.MigaTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Favourites with made-up channels, profiles and a website, for review and the website. Pictures
 * are dishes from miga-packs (MIGA_PACKS_PHOTOS, its photos folder) or, without it, gradients.
 * Off in normal runs; run recordRoborazziDebug with MIGA_SHOTS_DIR set.
 */
abstract class FavoritesShotsTest(private val texts: Map<String, String>) {

    @get:Rule
    val compose = createComposeRule()

    private val out: String? = System.getenv("MIGA_SHOTS_DIR")
    private val photos: String? = System.getenv("MIGA_PACKS_PHOTOS")
    private val app: MigaApp = ApplicationProvider.getApplicationContext()
    private val dao = app.database.followDao()

    private fun t(key: String) = texts.getValue(key)

    /** A dish photo from the packs ("pack/name"), or a gradient when the photos are not there. */
    private fun picture(photo: String, from: Int, to: Int, width: Int = 480, height: Int = 360): String {
        photos?.let { dir -> File(dir, "$photo.jpg").takeIf { it.exists() }?.let { return "file://" + it.absolutePath } }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), from, to, Shader.TileMode.CLAMP)
        })
        val file = File(app.cacheDir, photo.replace('/', '_') + ".png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return "file://" + file.absolutePath
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(1200)
        captureScreenRoboImage("$out/$name.png")
    }

    private val warm = 0xFFE9B56F.toInt() to 0xFFB5652D.toInt()

    @Test
    fun favorites() = runBlocking {
        assumeTrue(out != null)
        val now = System.currentTimeMillis()
        val hour = 3_600_000L
        val lola = dao.insertSource(FollowedSourceEntity(network = "YOUTUBE", key = "UCaaaaaaaaaaaaaaaaaaaaaa", handle = "@cocinadelola", name = t("lola"),
            avatarUrl = picture("cocina-mediterranea/gazpacho-andaluz", warm.first, warm.second, 200, 200), url = "https://www.youtube.com/@cocinadelola",
            addedAt = 0, lastOpenedAt = now - 30 * hour, checkedAt = now))
        val pasta = dao.insertSource(FollowedSourceEntity(network = "INSTAGRAM", key = "pastaencasa", handle = "@pastaencasa", name = t("pasta"),
            avatarUrl = picture("cocina-mediterranea/pasta-al-pesto-genoves", warm.first, warm.second, 200, 200), url = "https://www.instagram.com/pastaencasa/",
            addedAt = 0, lastOpenedAt = now - 30 * hour, checkedAt = now))
        val web = dao.insertSource(FollowedSourceEntity(network = "WEB", key = "https://cocina.example/recetas/feed/", handle = "cocinadecasa.example", name = t("web"),
            avatarUrl = picture("panaderia/pan-de-molde", warm.first, warm.second, 200, 200), url = "https://cocinadecasa.example/recetas/",
            addedAt = 0, lastOpenedAt = now - 30 * hour, checkedAt = now, wpApi = "https://cocinadecasa.example/wp-json/wp/v2/", wpCategory = -1))
        dao.replacePosts(lola, listOf(
            SourcePostEntity(sourceId = lola, postKey = "AAAAAAAAAAA", url = "https://www.youtube.com/watch?v=AAAAAAAAAAA", title = t("paella"),
                description = t("paella_text"), thumbnailUrl = picture("arroces/paella-de-verduras", warm.first, warm.second), publishedAt = now - 2 * hour, kind = "VIDEO"),
            SourcePostEntity(sourceId = lola, postKey = "BBBBBBBBBBB", url = "https://www.youtube.com/watch?v=BBBBBBBBBBB", title = t("lentils"),
                description = t("lentils_text"), thumbnailUrl = picture("saludable-batch-cooking/lentejas-con-verduras", warm.first, warm.second), publishedAt = now - 26 * hour, kind = "VIDEO"),
            SourcePostEntity(sourceId = lola, postKey = "CCCCCCCCCCC", url = "https://www.youtube.com/watch?v=CCCCCCCCCCC", title = t("bread"),
                description = "", thumbnailUrl = picture("panaderia/pan-de-molde", warm.first, warm.second), publishedAt = now - 96 * hour, kind = "VIDEO")
        ))
        dao.replacePosts(pasta, listOf(
            SourcePostEntity(sourceId = pasta, postKey = "Reel111", url = "https://www.instagram.com/reel/Reel111/", title = t("carbonara"),
                description = t("carbonara_text"), thumbnailUrl = picture("cocina-italiana/espaguetis-a-la-carbonara", warm.first, warm.second), publishedAt = now - 20 * hour, kind = "REEL"),
            SourcePostEntity(sourceId = pasta, postKey = "Reel222", url = "https://www.instagram.com/reel/Reel222/", title = t("cacio"),
                description = "", thumbnailUrl = picture("cocina-italiana/cacio-e-pepe", warm.first, warm.second), publishedAt = now - 50 * hour, kind = "REEL"),
            SourcePostEntity(sourceId = pasta, postKey = "Reel333", url = "https://www.instagram.com/reel/Reel333/", title = t("pesto"),
                description = "", thumbnailUrl = picture("thermomix-salsas-y-bases/pesto-de-albahaca", warm.first, warm.second), publishedAt = now - 80 * hour, kind = "REEL"),
            SourcePostEntity(sourceId = pasta, postKey = "Reel444", url = "https://www.instagram.com/reel/Reel444/", title = t("croquetas"),
                description = "", thumbnailUrl = picture("tapas/croquetas", warm.first, warm.second), publishedAt = now - 120 * hour, kind = "REEL")
        ))
        dao.replacePosts(web, listOf(
            SourcePostEntity(sourceId = web, postKey = "https://cocinadecasa.example/sopa", url = "https://cocinadecasa.example/sopa/", title = t("soup"),
                description = t("soup_text"), thumbnailUrl = picture("fusion-chino-india/sopa-de-maiz-dulce-y-pollo", warm.first, warm.second), publishedAt = now - 5 * hour, kind = "ARTICLE"),
            SourcePostEntity(sourceId = web, postKey = "https://cocinadecasa.example/magdalenas", url = "https://cocinadecasa.example/magdalenas/", title = t("muffins"),
                description = t("muffins_text"), thumbnailUrl = picture("panaderia/magdalenas", warm.first, warm.second), publishedAt = now - 60 * hour, kind = "ARTICLE"),
            SourcePostEntity(sourceId = web, postKey = "https://cocinadecasa.example/gazpacho", url = "https://cocinadecasa.example/gazpacho/", title = t("gazpacho"),
                description = t("gazpacho_text"), thumbnailUrl = picture("cocina-mediterranea/gazpacho-andaluz", warm.first, warm.second), publishedAt = now - 100 * hour, kind = "ARTICLE")
        ))
        dao.insertSavedPost(SavedPostEntity(network = "INSTAGRAM", postKey = "Reel222", url = "https://www.instagram.com/reel/Reel222/", title = t("cacio"),
            description = "", thumbnailUrl = picture("cocina-italiana/cacio-e-pepe", warm.first, warm.second), author = "@pastaencasa", authorUrl = null, kind = "REEL", savedAt = now))
        dao.insertSavedPost(SavedPostEntity(network = "YOUTUBE", postKey = "DDDDDDDDDDD", url = "https://www.youtube.com/watch?v=DDDDDDDDDDD", title = t("hake"),
            description = "", thumbnailUrl = picture("especial-navidad/merluza-en-salsa-verde", warm.first, warm.second), author = "@cocinadelola", authorUrl = null, kind = "VIDEO", savedAt = now - 1))
        dao.insertSavedPost(SavedPostEntity(network = "WEB", postKey = "https://cocinadecasa.example/gazpacho", url = "https://cocinadecasa.example/gazpacho/", title = t("gazpacho"),
            description = "", thumbnailUrl = picture("cocina-mediterranea/gazpacho-andaluz", warm.first, warm.second), author = t("web"), authorUrl = null, kind = "ARTICLE", savedAt = now - 2))

        val viewModel = FavoritesViewModel(app.repository)
        val sources = mapOf(lola to SourceViewModel(app.repository, lola), pasta to SourceViewModel(app.repository, pasta), web to SourceViewModel(app.repository, web))
        sources.getValue(pasta).grid.value = true
        var source by mutableStateOf<Long?>(null)
        compose.setContent {
            MigaTheme(darkTheme = false) {
                Surface(Modifier.fillMaxSize()) {
                    val id = source
                    if (id == null) {
                        FavoritesTab(viewModel, recipes = { header -> header(); Text("…") }, onRecipeClick = {}, onOpenSource = {}, onImport = { _, _ -> })
                    } else {
                        SourceScreen(sources.getValue(id), onBack = {}, onRecipeClick = {}, onImport = { _, _ -> })
                    }
                }
            }
        }
        shot("20_fav_all")
        viewModel.setFilter(FavoritesFilter.CHANNELS)
        shot("21_fav_channels")
        viewModel.setFilter(FavoritesFilter.POSTS)
        shot("22_fav_posts")
        source = lola
        shot("23_fav_channel")
        source = pasta
        shot("24_fav_profile")
        source = web
        shot("25_fav_website")
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class, qualifiers = "es-rES-w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FavoritesShotsEsTest : FavoritesShotsTest(
    mapOf(
        "lola" to "Cocina de Lola", "pasta" to "Pasta en casa", "web" to "Cocina de casa",
        "paella" to "Paella de verduras en 30 minutos, sin trucos", "paella_text" to "La receta completa, con cantidades, en la descripción.",
        "lentils" to "Lentejas con verduras para toda la semana",
        "lentils_text" to "INGREDIENTES (4 personas):\n- 400 g de lentejas\n- 2 zanahorias\n\nPREPARACIÓN:\n1. Sofreímos las verduras.\n2. Añadimos las lentejas.",
        "bread" to "Pan de molde casero, esponjoso y fácil",
        "carbonara" to "Carbonara de verdad: sin nata y en 10 minutos", "carbonara_text" to "Ingredientes (2 personas): 200 g de espaguetis, 2 yemas…",
        "cacio" to "Cacio e pepe cremoso, el truco del agua", "pesto" to "Pesto de albahaca en mortero", "croquetas" to "Croquetas cremosas como las de la abuela",
        "hake" to "Merluza en salsa verde como la de mi abuela",
        "soup" to "Sopa de pollo y maíz al estilo asiático", "soup_text" to "Reconfortante, con jengibre, en 25 minutos.",
        "muffins" to "Magdalenas esponjosas de toda la vida", "muffins_text" to "El desayuno del domingo, con su copete.",
        "gazpacho" to "Gazpacho andaluz", "gazpacho_text" to "Fresquito, para todo el verano."
    )
)

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class, qualifiers = "en-rGB-w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FavoritesShotsEnTest : FavoritesShotsTest(
    mapOf(
        "lola" to "Lola's Kitchen", "pasta" to "Pasta at home", "web" to "Home cooking",
        "paella" to "Vegetable paella in 30 minutes, no tricks", "paella_text" to "The full recipe, with quantities, in the description.",
        "lentils" to "Lentils with vegetables for the whole week",
        "lentils_text" to "INGREDIENTS (serves 4):\n- 400 g lentils\n- 2 carrots\n\nMETHOD:\n1. Fry the vegetables.\n2. Add the lentils.",
        "bread" to "Easy homemade sandwich loaf",
        "carbonara" to "Real carbonara: no cream, in 10 minutes", "carbonara_text" to "Ingredients (serves 2): 200 g spaghetti, 2 egg yolks…",
        "cacio" to "Creamy cacio e pepe, the pasta water trick", "pesto" to "Basil pesto in a mortar", "croquetas" to "Creamy croquettes like grandma's",
        "hake" to "Hake in green sauce like my grandma's",
        "soup" to "Asian-style chicken and sweetcorn soup", "soup_text" to "Comforting, with ginger, in 25 minutes.",
        "muffins" to "Classic fluffy muffins", "muffins_text" to "Sunday breakfast, with a proper dome.",
        "gazpacho" to "Andalusian gazpacho", "gazpacho_text" to "Chilled, for the whole summer."
    )
)
