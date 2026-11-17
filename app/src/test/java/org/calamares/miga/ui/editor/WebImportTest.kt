package org.calamares.miga.ui.editor

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.calamares.miga.MigaApp
import org.calamares.miga.data.ai.AiProvider
import org.calamares.miga.data.local.entity.RecipeBookEntity
import org.calamares.miga.ui.list.TestMigaApp
import org.calamares.miga.ui.navigation.Destinations
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import android.os.Looper

/** Importing from a page with a schema.org Recipe: read without AI, then improved when the assistant is set up. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WebImportTest {

    private val app: MigaApp = ApplicationProvider.getApplicationContext()
    private lateinit var server: HttpServer
    private lateinit var base: String
    private var aiCalls = 0

    private val page = """
        <html><head><script type="application/ld+json">
        {"@context":"https://schema.org","@type":"Recipe","name":"salmorejo cordobes",
         "image":"/foto.jpg","recipeYield":"4","prepTime":"PT15M",
         "recipeIngredient":["1 kg de tomates","200 gr de pan"],
         "recipeInstructions":[{"@type":"HowToStep","text":"tritura los tomates"},{"@type":"HowToStep","text":"añade el pan y tritura"}]}
        </script></head><body></body></html>
    """.trimIndent()

    private fun photo(): ByteArray = ByteArrayOutputStream().use { out ->
        Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }

    /** What "Polish the recipe" answers, as an OpenAI-compatible chat completion. */
    private fun polishAnswer(): String {
        val polished = buildJsonObject {
            put("name", "Salmorejo cordobés")
            put("notes", "")
            put("ingredientGroups", buildJsonArray {
                add(buildJsonObject {
                    put("name", null as String?)
                    put("ingredients", buildJsonArray {
                        add(buildJsonObject { put("name", "tomates"); put("quantity", 1.0); put("unit", "kg") })
                        add(buildJsonObject { put("name", "pan"); put("quantity", 200.0); put("unit", "g") })
                    })
                })
            })
            put("stepGroups", buildJsonArray {
                add(buildJsonObject {
                    put("name", null as String?)
                    put("instructions", buildJsonArray {
                        add(kotlinx.serialization.json.JsonPrimitive("Tritura los *tomates*."))
                        add(kotlinx.serialization.json.JsonPrimitive("Añade el *pan* y tritura de nuevo."))
                    })
                })
            })
            put("changes", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("Unidades normalizadas")) })
        }.toString()
        return buildJsonObject {
            put("choices", buildJsonArray { add(buildJsonObject { putJsonObject("message") { put("content", polished) } }) })
        }.toString()
    }

    @Before
    fun start() = runBlocking {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/receta") { ex -> ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8"); reply(ex, page.toByteArray()) }
            createContext("/foto.jpg") { ex -> ex.responseHeaders.add("Content-Type", "image/png"); reply(ex, photo()) }
            createContext("/v1/chat/completions") { ex -> aiCalls++; ex.requestBody.readBytes(); reply(ex, polishAnswer().toByteArray()) }
            start()
        }
        base = "http://127.0.0.1:${server.address.port}"
        AiProvider.entries.forEach { app.settingsRepository.setApiKey(it, "") }
        app.settingsRepository.setCustomBaseUrl("")
    }

    private fun reply(ex: com.sun.net.httpserver.HttpExchange, bytes: ByteArray) {
        ex.sendResponseHeaders(200, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    @After
    fun stop() = server.stop(0)

    private fun import(): RecipeEditorViewModel {
        val bookId = runBlocking { app.database.recipeBookDao().insert(RecipeBookEntity(uid = "web${System.nanoTime()}", name = "Web", coverPhotoUri = null, createdAt = 0L)) }
        val viewModel = RecipeEditorViewModel(app.repository, app.settingsRepository, Destinations.NEW_RECIPE_ID, bookId)
        viewModel.startUrlImport(app, "$base/receta")
        val deadline = System.currentTimeMillis() + 15_000
        while (viewModel.visionState.value !is VisionState.Loaded && viewModel.visionState.value !is VisionState.Error && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        return viewModel
    }

    @Test
    fun `without the assistant the page's recipe is read as it is, with its photo`() {
        val viewModel = import()
        assertTrue(viewModel.visionState.value.toString(), viewModel.visionState.value is VisionState.Loaded)
        assertEquals("salmorejo cordobes", viewModel.name)
        assertEquals(15, viewModel.prepTimeMinutesText.toInt())
        assertEquals(1, viewModel.photos.size)
        assertNull(viewModel.webPolish)
        assertFalse(viewModel.filledByAi)
        assertEquals(0, aiCalls)
    }

    @Test
    fun `with the assistant it is improved, and the improvement can be undone`() {
        runBlocking {
            app.settingsRepository.setCustomBaseUrl("$base/v1")
            app.settingsRepository.setModel(AiProvider.CUSTOM, "local-model")
        }
        val viewModel = import()
        assertTrue(viewModel.visionState.value.toString(), viewModel.visionState.value is VisionState.Loaded)
        assertEquals(1, aiCalls)
        assertEquals("Salmorejo cordobés", viewModel.name)
        assertEquals("g", viewModel.ingredientGroups.single().ingredients[1].unit)
        assertNotNull(viewModel.webPolish)
        assertTrue(viewModel.filledByAi)

        viewModel.undoWebPolish()
        assertEquals("salmorejo cordobes", viewModel.name)
        assertEquals("tritura los tomates", viewModel.stepGroups.single().steps.first().text)
        assertNull(viewModel.webPolish)
    }
}
