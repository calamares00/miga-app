package org.calamares.miga.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.model.IngredientGroup
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.StepGroup
import org.calamares.miga.data.polish.PolishedRecipe
import org.calamares.miga.ui.components.AiContentNotice
import org.calamares.miga.ui.components.ButtonContent
import org.calamares.miga.ui.components.ErrorMessage
import org.calamares.miga.ui.components.FormattedText

/**
 * "Improve texts with AI": a progress dialog while the model works, then the improved recipe
 * full screen with what changed and a Before / After switch, to apply or discard.
 */
@Composable
internal fun RecipePolishDialog(
    state: PolishState,
    recipe: Recipe,
    onApply: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    /** Hides the progress; the work goes on and a notification says when it is ready. */
    onHide: () -> Unit,
    onCancel: () -> Unit
) {
    when (state) {
        PolishState.Hidden -> Unit
        // Leaving it (back, tapping outside) never loses the work: it goes on in the background.
        PolishState.Loading -> AlertDialog(
            onDismissRequest = onHide,
            icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
            title = { Text(L10n.str(R.string.polish_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            L10n.str(R.string.polish_working),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                    Text(
                        L10n.str(R.string.ai_background_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(onClick = onHide) { Text(L10n.str(R.string.continue_in_background)) } },
            dismissButton = { TextButton(onClick = onCancel) { Text(L10n.str(R.string.cancel)) } }
        )
        PolishState.NotConfigured -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(L10n.str(R.string.polish_title)) },
            text = { Text(L10n.str(R.string.set_up_ai_provider_settings)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(L10n.str(R.string.close)) } }
        )
        is PolishState.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(L10n.str(R.string.polish_title)) },
            text = { ErrorMessage(state.reason, onRetry = onRetry) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(L10n.str(R.string.close)) } }
        )
        is PolishState.Ready -> PolishReview(state.polished, recipe, onApply, onDismiss)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PolishReview(polished: PolishedRecipe, recipe: Recipe, onApply: () -> Unit, onDismiss: () -> Unit) {
    var showBefore by rememberSaveable { mutableStateOf(false) }
    // The dialog window fits between the system bars: drawn behind them, the Apply and Discard
    // buttons ended up under the navigation bar of phones with buttons.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(onBack = onDismiss)
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.discard)) }
                    Text(
                        L10n.str(R.string.polish_review_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    // Also at the top, so it is in sight without scrolling or looking for it.
                    TextButton(onClick = onApply) { Text(L10n.str(R.string.polish_apply)) }
                }
                Text(
                    L10n.str(R.string.polish_review_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AiContentNotice(
                        feature = L10n.str(R.string.polish_title),
                        content = { polished.stepGroups.flatMap { it.instructions }.joinToString("\n") }
                    )
                    if (polished.changes.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    L10n.str(R.string.polish_changes),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                polished.changes.forEach { change ->
                                    Text(
                                        "•  $change",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = showBefore,
                            onClick = { showBefore = true },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text(L10n.str(R.string.polish_before)) }
                        SegmentedButton(
                            selected = !showBefore,
                            onClick = { showBefore = false },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text(L10n.str(R.string.polish_after)) }
                    }
                    if (showBefore) {
                        RecipeTexts(recipe.name, recipe.ingredientGroups, recipe.stepGroups, recipe.notes)
                    } else {
                        RecipeTexts(polished.name, polished.ingredientGroups, polished.stepGroups, polished.notes)
                    }
                    Spacer(modifier = Modifier.padding(4.dp))
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        ButtonContent(Icons.Filled.Close, L10n.str(R.string.discard))
                    }
                    Button(onClick = onApply, modifier = Modifier.weight(1f)) {
                        ButtonContent(Icons.Filled.Check, L10n.str(R.string.polish_apply))
                    }
                }
            }
        }
    }
}

/** Name, ingredients, steps and notes as the recipe shows them, for the comparison. */
@Composable
private fun RecipeTexts(name: String, ingredientGroups: List<IngredientGroup>, stepGroups: List<StepGroup>, notes: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(name, style = MaterialTheme.typography.headlineSmall)
        Text(L10n.str(R.string.ingredients), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        ingredientGroups.forEach { group ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                group.name?.let { Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                group.ingredients.forEach { ingredient ->
                    Text("•  " + formatIngredient(ingredient, 1.0), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Text(L10n.str(R.string.method), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        stepGroups.forEach { group ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                group.name?.let { Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                group.instructions.forEachIndexed { index, instruction ->
                    Row {
                        Text(
                            "${index + 1}.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FormattedText(instruction, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        if (notes.isNotBlank()) {
            Text(L10n.str(R.string.notes), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            FormattedText(notes, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
