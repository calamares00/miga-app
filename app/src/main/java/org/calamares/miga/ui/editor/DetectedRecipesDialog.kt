package org.calamares.miga.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.calamares.miga.L10n
import org.calamares.miga.R

/**
 * Shown when the photos hold more than one complete recipe: lists them (all selected) and offers
 * one recipe each or a single recipe joining them, with the AI's suggestion ([together]) first.
 */
@Composable
fun DetectedRecipesDialog(
    names: List<String>,
    together: Boolean,
    canSaveSeparately: Boolean,
    onJoin: (selected: List<Int>) -> Unit,
    onOneEach: (selected: List<Int>) -> Unit,
    onCancel: () -> Unit
) {
    val selected = remember(names) { mutableStateListOf<Int>().apply { addAll(names.indices) } }
    val chosen = selected.sorted()
    val recommendJoin = together || !canSaveSeparately

    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(L10n.str(R.string.ai_several_recipes_found_x, names.size), style = MaterialTheme.typography.headlineSmall)
                Text(L10n.str(R.string.detected_choose), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Column(modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    names.forEachIndexed { index, name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { if (index in selected) selected.remove(index) else selected.add(index) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = index in selected, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 12.dp))
                        }
                    }
                }

                if (chosen.size > 1) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(14.dp)) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                L10n.str(if (together) R.string.detected_suggest_together else R.string.detected_suggest_separately),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (chosen.size <= 1) {
                        Button(onClick = { onJoin(chosen) }, enabled = chosen.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            Text(L10n.str(R.string.detected_import_selected))
                        }
                    } else {
                        val join: @Composable (Boolean) -> Unit = { primary ->
                            OptionButton(L10n.str(R.string.detected_join), primary) { onJoin(chosen) }
                        }
                        val oneEach: @Composable (Boolean) -> Unit = { primary ->
                            OptionButton(L10n.str(R.string.detected_one_each, chosen.size), primary) { onOneEach(chosen) }
                        }
                        if (recommendJoin) {
                            join(true)
                            if (canSaveSeparately) oneEach(false)
                        } else {
                            oneEach(true)
                            join(false)
                        }
                    }
                    TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text(L10n.str(R.string.cancel)) }
                }
            }
        }
    }
}

@Composable
private fun OptionButton(label: String, primary: Boolean, onClick: () -> Unit) {
    if (primary) {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
    }
}
