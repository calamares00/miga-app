package com.bmo00.miga.ui.detail

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bmo00.miga.data.model.Recipe

private data class CookStep(val groupName: String?, val stepNumberInGroup: Int, val instruction: String)

private fun Recipe.flattenSteps(): List<CookStep> =
    stepGroups.flatMap { group -> group.instructions.mapIndexed { idx, instruction -> CookStep(group.name, idx + 1, instruction) } }

@Composable
fun CookModeOverlay(recipe: Recipe, onClose: () -> Unit) {
    val steps = remember(recipe) { recipe.flattenSteps() }
    var currentIndex by remember { mutableIntStateOf(0) }
    val view = LocalView.current

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (steps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Esta receta no tiene pasos.", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                val step = steps[currentIndex]
                Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(recipe.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Cerrar modo cocina") }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { (currentIndex + 1f) / steps.size },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    if (step.groupName != null) {
                        Text(
                            text = step.groupName.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        text = "Paso ${step.stepNumberInGroup}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = step.instruction,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { if (currentIndex > 0) currentIndex-- },
                            enabled = currentIndex > 0,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            Icon(Icons.Filled.ArrowBackIosNew, contentDescription = null, modifier = Modifier.height(16.dp))
                            Spacer(modifier = Modifier.height(0.dp))
                            Text(" Anterior")
                        }
                        Button(
                            onClick = { if (currentIndex < steps.lastIndex) currentIndex++ else onClose() },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            Text(if (currentIndex < steps.lastIndex) "Siguiente " else "Terminar")
                            if (currentIndex < steps.lastIndex) {
                                Icon(Icons.Filled.ArrowForwardIos, contentDescription = null, modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
