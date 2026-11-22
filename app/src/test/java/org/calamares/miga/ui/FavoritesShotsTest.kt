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
import androidx.compose.ui.test.junit4.createComposeRule
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
 * Favourites with made-up channels, profiles and posts (pictures drawn here), for review. Off in
 * normal runs; run recordRoborazziDebug with MIGA_SHOTS_DIR set.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class, qualifiers = "es-rES-w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FavoritesShotsTest {

    @get:Rule
    val compose = createComposeRule()

    private val out: String? = System.getenv("MIGA_SHOTS_DIR")
    private val app: MigaApp = ApplicationProvider.getApplicationContext()
    private val dao = app.database.followDao()

    private fun picture(name: String, from: Int, to: Int, width: Int = 480, height: Int = 360): String {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), from, to, Shader.TileMode.CLAMP)
        })
        val file = File(app.cacheDir, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return "file://" + file.absolutePath
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(800)
        captureScreenRoboImage("$out/$name.png")
    }

    @Test
    fun favorites() = runBlocking {
        assumeTrue(out != null)
        val now = System.currentTimeMillis()
        val hour = 3_600_000L
        val lola = dao.insertSource(FollowedSourceEntity(network = "YOUTUBE", key = "UCaaaaaaaaaaaaaaaaaaaaaa", handle = "@cocinadelola", name = "Cocina de Lola",
            avatarUrl = picture("lola", 0xFFF6D7C8.toInt(), 0xFFE08A66.toInt(), 200, 200), url = "https://www.youtube.com/@cocinadelola", addedAt = 0, lastOpenedAt = now - 30 * hour, checkedAt = now))
        val pasta = dao.insertSource(FollowedSourceEntity(network = "INSTAGRAM", key = "pastaencasa", handle = "@pastaencasa", name = "Pasta en casa",
            avatarUrl = picture("pasta", 0xFFD6E7F5.toInt(), 0xFF3E8EB5.toInt(), 200, 200), url = "https://www.instagram.com/pastaencasa/", addedAt = 0, lastOpenedAt = now - 30 * hour, checkedAt = now))
        dao.replacePosts(lola, listOf(
            SourcePostEntity(sourceId = lola, postKey = "AAAAAAAAAAA", url = "https://www.youtube.com/watch?v=AAAAAAAAAAA", title = "Paella de verduras en 30 minutos, sin trucos",
                description = "La receta completa con cantidades está en mi web.", thumbnailUrl = picture("paella", 0xFFE9B56F.toInt(), 0xFFB5652D.toInt()), publishedAt = now - 2 * hour, kind = "VIDEO"),
            SourcePostEntity(sourceId = lola, postKey = "BBBBBBBBBBB", url = "https://www.youtube.com/watch?v=BBBBBBBBBBB", title = "Ensalada templada de lentejas y calabaza",
                description = "INGREDIENTES (4 personas):\n- 400 g de lentejas cocidas\n- 300 g de calabaza\n\nPREPARACIÓN:\n1. Asamos la calabaza.\n2. Mezclamos con las lentejas.",
                thumbnailUrl = picture("ensalada", 0xFFC9D97A.toInt(), 0xFF6F8A2E.toInt()), publishedAt = now - 26 * hour, kind = "VIDEO"),
            SourcePostEntity(sourceId = lola, postKey = "CCCCCCCCCCC", url = "https://www.youtube.com/watch?v=CCCCCCCCCCC", title = "Pan de molde casero, esponjoso y fácil",
                description = "", thumbnailUrl = picture("pan", 0xFFF2C27B.toInt(), 0xFFC27D2A.toInt()), publishedAt = now - 96 * hour, kind = "VIDEO")
        ))
        dao.replacePosts(pasta, listOf(
            SourcePostEntity(sourceId = pasta, postKey = "Reel111", url = "https://www.instagram.com/reel/Reel111/", title = "Carbonara de verdad: sin nata y en 10 minutos",
                description = "Ingredientes (2 personas): 200 g de espaguetis, 2 yemas…", thumbnailUrl = picture("carbonara", 0xFFF0A7A0.toInt(), 0xFFB8463E.toInt(), 360, 520), publishedAt = now - 20 * hour, kind = "REEL"),
            SourcePostEntity(sourceId = pasta, postKey = "Reel222", url = "https://www.instagram.com/reel/Reel222/", title = "Cacio e pepe cremoso, el truco del agua",
                description = "", thumbnailUrl = picture("cacio", 0xFFF5D58C.toInt(), 0xFFC8962F.toInt(), 360, 520), publishedAt = now - 50 * hour, kind = "REEL")
        ))
        dao.insertSavedPost(SavedPostEntity(network = "INSTAGRAM", postKey = "Reel222", url = "https://www.instagram.com/reel/Reel222/", title = "Cacio e pepe cremoso, el truco del agua",
            description = "", thumbnailUrl = picture("cacio", 0xFFF5D58C.toInt(), 0xFFC8962F.toInt(), 360, 520), author = "@pastaencasa", authorUrl = null, kind = "REEL", savedAt = now))
        dao.insertSavedPost(SavedPostEntity(network = "YOUTUBE", postKey = "DDDDDDDDDDD", url = "https://www.youtube.com/watch?v=DDDDDDDDDDD", title = "Merluza en salsa verde como la de mi abuela",
            description = "", thumbnailUrl = picture("merluza", 0xFF9AD0C2.toInt(), 0xFF3F8F80.toInt()), author = "@cocinadelola", authorUrl = null, kind = "VIDEO", savedAt = now - 1))

        val viewModel = FavoritesViewModel(app.repository)
        val sources = mapOf(lola to SourceViewModel(app.repository, lola), pasta to SourceViewModel(app.repository, pasta))
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
        shot("1_all")
        viewModel.setFilter(FavoritesFilter.CHANNELS)
        shot("2_channels")
        viewModel.setFilter(FavoritesFilter.POSTS)
        shot("3_posts")
        source = lola
        shot("4_channel")
        source = pasta
        shot("5_profile")
    }
}
