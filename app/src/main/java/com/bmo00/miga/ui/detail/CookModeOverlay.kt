package com.bmo00.miga.ui.detail

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bmo00.miga.data.model.Recipe
import java.util.Locale

private data class CookStep(val groupName: String?, val stepNumberInGroup: Int, val instruction: String)

private fun Recipe.flattenSteps(): List<CookStep> =
    stepGroups.flatMap { group -> group.instructions.mapIndexed { idx, instruction -> CookStep(group.name, idx + 1, instruction) } }

@Composable
fun CookModeOverlay(recipe: Recipe, ttsVoiceName: String?, onClose: () -> Unit) {
    val steps = remember(recipe) { recipe.flattenSteps() }
    val ingredientGroups = remember(recipe) { recipe.ingredientGroups.filter { it.ingredients.isNotEmpty() } }
    val hasIngredients = ingredientGroups.isNotEmpty()
    val totalPages = steps.size + (if (hasIngredients) 1 else 0)
    var pageIndex by remember { mutableIntStateOf(0) }
    val view = LocalView.current
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.setLanguage(Locale("es", "ES"))
                if (!ttsVoiceName.isNullOrBlank()) {
                    engine?.voices?.firstOrNull { it.name == ttsVoiceName }?.let { voice -> engine?.setVoice(voice) }
                }
            }
        }
        tts = engine
        onDispose {
            view.keepScreenOn = false
            engine?.stop()
            engine?.shutdown()
        }
    }

    fun goToPage(index: Int) {
        tts?.stop()
        pageIndex = index
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (totalPages == 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Esta receta no tiene pasos.", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(recipe.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        IconButton(onClick = { tts?.stop(); onClose() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cerrar modo cocina")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { (pageIndex + 1f) / totalPages },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    if (hasIngredients && pageIndex == 0) {
                        Text(
                            text = "Ingredientes",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            ingredientGroups.forEach { group ->
                                if (ingredientGroups.size > 1) {
                                    Text(
                                        text = (group.name ?: "Receta principal").uppercase(),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                }
                                group.ingredients.forEach { ingredient ->
                                    Text(
                                        text = "•  ${formatIngredient(ingredient, 1.0)}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        val step = steps[pageIndex - (if (hasIngredients) 1 else 0)]
                        if (step.groupName != null) {
                            Text(
                                text = step.groupName.uppercase(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Paso ${step.stepNumberInGroup}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                val engine = tts ?: return@IconButton
                                if (engine.isSpeaking) engine.stop() else engine.speak(step.instruction, TextToSpeech.QUEUE_FLUSH, null, "cook_step")
                            }) {
                                Icon(
                                    Icons.Filled.VolumeUp,
                                    contentDescription = "Escuchar paso",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = step.instruction,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { if (pageIndex > 0) goToPage(pageIndex - 1) },
                            enabled = pageIndex > 0,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            Icon(Icons.Filled.ArrowBackIosNew, contentDescription = null, modifier = Modifier.height(16.dp))
                            Spacer(modifier = Modifier.height(0.dp))
                            Text(" Anterior")
                        }
                        Button(
                            onClick = { if (pageIndex < totalPages - 1) goToPage(pageIndex + 1) else { tts?.stop(); onClose() } },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            Text(if (pageIndex < totalPages - 1) "Siguiente " else "Terminar")
                            if (pageIndex < totalPages - 1) {
                                Icon(Icons.Filled.ArrowForwardIos, contentDescription = null, modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
