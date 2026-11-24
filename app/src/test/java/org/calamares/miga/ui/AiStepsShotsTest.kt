package org.calamares.miga.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.calamares.miga.data.ai.AiProgress
import org.calamares.miga.data.ai.AiStepLine
import org.calamares.miga.ui.components.AiProgressView
import org.calamares.miga.ui.list.TestMigaApp
import org.calamares.miga.ui.theme.MigaTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The assistant's steps while importing a YouTube video and a reel, for review (MIGA_SHOTS_DIR). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class, qualifiers = "es-rES-w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AiStepsShotsTest {

    @get:Rule
    val compose = createComposeRule()

    private val out: String? = System.getenv("MIGA_SHOTS_DIR")

    @Test
    fun steps() {
        assumeTrue(out != null)
        val now = System.currentTimeMillis()
        var progress by mutableStateOf<AiProgress?>(null)
        compose.setContent {
            MigaTheme(darkTheme = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Box(contentAlignment = Alignment.Center) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(8.dp)) {
                            AiProgressView(progress, "…", Modifier.padding(horizontal = 24.dp, vertical = 28.dp).widthIn(max = 320.dp), spinnerSize = 32.dp)
                        }
                    }
                }
            }
        }
        progress = AiProgress(
            step = "Gemini está viendo el vídeo", startedAt = now - 40_000, stepStartedAt = now - 25_000,
            done = listOf(
                AiStepLine("Descargando la página del vídeo"),
                AiStepLine("La descripción del vídeo no trae la receta"),
                AiStepLine("Leyendo la web del autor (cocinaconcoqui.com)"),
                AiStepLine("La página no trae la receta en formato estándar")
            )
        )
        compose.waitForIdle()
        captureScreenRoboImage("$out/1_youtube.png")
        progress = AiProgress(
            step = "Groq está leyendo la transcripción", startedAt = now - 30_000, stepStartedAt = now - 4_000,
            done = listOf(
                AiStepLine("Descargando la publicación"),
                AiStepLine("El texto de la publicación no trae la receta"),
                AiStepLine("Descargando el vídeo y sacando su audio"),
                AiStepLine("Gemini está escuchando el audio: límite de uso alcanzado", failed = true),
                AiStepLine("Transcribiendo el audio con Groq")
            )
        )
        compose.waitForIdle()
        captureScreenRoboImage("$out/2_reel.png")
    }
}
