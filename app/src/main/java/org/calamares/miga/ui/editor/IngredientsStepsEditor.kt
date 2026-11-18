package org.calamares.miga.ui.editor

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import org.calamares.miga.data.model.ingredientParts
import org.calamares.miga.data.model.formatIngredientText
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.BorderStroke
import org.calamares.miga.data.allergens.CertifiedFreeMarks
import org.calamares.miga.data.allergens.AllergenAnalysis
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.ContentPaste
import org.calamares.miga.data.model.PastedLists
import org.calamares.miga.ui.components.ReorderableColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.layout.Box
import org.calamares.miga.ui.components.RichTextField
import org.calamares.miga.L10n
import org.calamares.miga.R
import android.Manifest
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.calamares.miga.data.voice.DictationResult
import org.calamares.miga.data.voice.SpeechDictation

@Composable
fun IngredientsEditor(viewModel: RecipeEditorViewModel) {
    val availableIngredientNames by viewModel.availableIngredientNames.collectAsStateWithLifecycle()
    /** Group that the "Paste list" dialog adds to. */
    var pasteInto by remember { mutableStateOf<Int?>(null) }
    pasteInto?.let { groupIndex ->
        PasteListDialog(
            title = L10n.str(R.string.paste_ingredients),
            hint = L10n.str(R.string.paste_ingredients_hint),
            onPaste = { viewModel.pasteIngredients(groupIndex, it); pasteInto = null },
            onDismiss = { pasteInto = null }
        )
    }

    /** The ingredient open for editing; the others show as in the recipe. Blank ones are always open. */
    var editing by remember { mutableStateOf<IngredientRowUi?>(null) }

    Section(title = L10n.str(R.string.ingredients)) {
        viewModel.ingredientGroups.forEachIndexed { groupIndex, group ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (group.name != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = group.name.orEmpty(),
                            onValueChange = { group.name = it },
                            label = { Text(L10n.str(R.string.sub_recipe_name_e_g)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.removeIngredientGroup(groupIndex) }) {
                            Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.remove_sub_recipe))
                        }
                    }
                } else {
                    Text(L10n.str(R.string.main_ingredients), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }

                ReorderableColumn(
                    items = group.ingredients.toList(),
                    onReorder = { viewModel.reorderIngredients(groupIndex, it) },
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) { row, rowIndex, dragHandle, isDragging ->
                    val open = row === editing || (row.name.isBlank() && row.quantity.isBlank() && row.unit.isBlank())
                    if (open) {
                        IngredientEditRow(
                            row = row,
                            availableNames = availableIngredientNames,
                            dragHandle = dragHandle,
                            isDragging = isDragging,
                            onPasteLines = { lines -> viewModel.splitIngredientRow(groupIndex, rowIndex, lines) },
                            onRemove = { viewModel.removeIngredientRow(groupIndex, rowIndex) },
                            onDone = { editing = null }
                        )
                    } else {
                        IngredientListRow(
                            row = row,
                            dragHandle = dragHandle,
                            isDragging = isDragging,
                            onClick = { editing = row },
                            onRemove = { viewModel.removeIngredientRow(groupIndex, rowIndex) }
                        )
                    }
                }

                QuickAddIngredient(onAdd = { text -> viewModel.pasteIngredients(groupIndex, text) })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { pasteInto = groupIndex },
                        label = { Text(L10n.str(R.string.paste_list)) },
                        leadingIcon = { Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    if (groupIndex == viewModel.ingredientGroups.lastIndex) {
                        AssistChip(
                            onClick = { viewModel.addIngredientSubGroup() },
                            label = { Text(L10n.str(R.string.sub_recipe)) },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }
            if (groupIndex < viewModel.ingredientGroups.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        Text(L10n.str(R.string.editor_ingredients_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The ingredient as written in the recipe ("4 dientes de ajo"), with the length of its amount. */
private fun IngredientRowUi.asWritten(): Pair<String, Int> {
    val amount = quantity.trim().replace(',', '.').toDoubleOrNull()
    val shownName = if (optional) "$name ${L10n.str(R.string.ingredient_optional_suffix)}" else name
    if (amount == null) {
        val written = listOf(quantity.trim(), unit.trim(), shownName).filter { it.isNotEmpty() }.joinToString(" ")
        return written to quantity.trim().length
    }
    val text = formatIngredientText(shownName, amount, unit.trim().ifEmpty { null }, forReading = true)
    return text to ingredientParts(shownName, amount, unit.trim().ifEmpty { null }, forReading = true).first.orEmpty().length
}

/** An ingredient as in the recipe ("50 g de panceta"); a tap opens it for editing. */
@Composable
private fun IngredientListRow(
    row: IngredientRowUi,
    dragHandle: Modifier,
    isDragging: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val amountColor = MaterialTheme.colorScheme.primary
    val (written, amountLength) = row.asWritten()
    val styled = buildAnnotatedString {
        append(written)
        if (amountLength > 0) addStyle(SpanStyle(color = amountColor, fontWeight = FontWeight.SemiBold), 0, amountLength)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .draggedLook(isDragging)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClickLabel = L10n.str(R.string.edit_x, row.name), onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DragHandle(dragHandle)
        Text(styled, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp).weight(1f))
        IngredientMenu(row, onRemove)
    }
}

/** The ingredient being edited: the name across the width, amount and unit under it. */
@Composable
private fun IngredientEditRow(
    row: IngredientRowUi,
    availableNames: List<String>,
    dragHandle: Modifier,
    isDragging: Boolean,
    onPasteLines: (List<String>) -> Unit,
    onRemove: () -> Unit,
    onDone: () -> Unit
) {
    var showSuggestions by remember { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).draggedLook(isDragging)
    ) {
        Column(modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DragHandle(dragHandle)
                OutlinedTextField(
                    value = row.name,
                    onValueChange = { text ->
                        // Several lines pasted at once become one ingredient each.
                        val lines = PastedLists.pastedLines(row.name, text)
                        if (lines != null) onPasteLines(lines) else { row.name = text; showSuggestions = true }
                    },
                    label = { Text(L10n.str(R.string.ingredient)) },
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    singleLine = true
                )
            }
            if (showSuggestions && row.name.isNotBlank()) {
                val suggestions = availableNames.filter {
                    it.contains(row.name, ignoreCase = true) && !it.equals(row.name, ignoreCase = true)
                }
                suggestions.take(5).forEach { suggestion ->
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { row.name = suggestion; showSuggestions = false }
                            .padding(start = 40.dp, top = 6.dp, bottom = 6.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 32.dp)) {
                OutlinedTextField(
                    value = row.quantity,
                    onValueChange = { row.quantity = it },
                    label = { Text(L10n.str(R.string.quantity)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = row.unit,
                    onValueChange = { row.unit = it },
                    label = { Text(L10n.str(R.string.unit_full)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onDone() }),
                    modifier = Modifier.weight(1.3f),
                    singleLine = true
                )
                IngredientMenu(row, onRemove)
            }
            if (row.optional) {
                AssistChip(
                    onClick = { row.optional = false },
                    label = { Text(L10n.str(R.string.ingredient_optional)) },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.ingredient_not_optional), modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.padding(start = 32.dp)
                )
            }
        }
    }
}

/** Optional, certified gluten-free or lactose-free, and remove. */
@Composable
private fun IngredientMenu(row: IngredientRowUi, onRemove: () -> Unit) {
    Box {
        var menu by remember { mutableStateOf(false) }
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = L10n.str(R.string.more_options))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(L10n.str(R.string.ingredient_optional)) },
                leadingIcon = { Checkbox(checked = row.optional, onCheckedChange = null) },
                onClick = { row.optional = !row.optional; menu = false }
            )
            // Certified products clear what depends on the product (see AllergenAnalysis):
            // offered only when the ingredient may bring gluten or lactose.
            CertifiedFreeMarks.entries.forEach { mark ->
                val marked = mark.isIn(row.name)
                if (marked || mark.allergen in AllergenAnalysis.ofIngredient(row.name).levels) {
                    DropdownMenuItem(
                        text = { Text(mark.label) },
                        leadingIcon = { Checkbox(checked = marked, onCheckedChange = null) },
                        onClick = { row.name = mark.toggle(row.name); menu = false }
                    )
                }
            }
            DropdownMenuItem(
                text = { Text(L10n.str(R.string.remove_ingredient)) },
                leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
                onClick = { menu = false; onRemove() }
            )
        }
    }
}

/** "200 g de patatas" typed or pasted at the end of the list: amount, unit and name are split. */
@Composable
private fun QuickAddIngredient(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    fun add() {
        if (text.isNotBlank()) onAdd(text)
        text = ""
    }
    OutlinedTextField(
        value = text,
        onValueChange = { value ->
            // A pasted list is added at once, one ingredient per line.
            if (PastedLists.pastedLines(text, value) != null) { onAdd(value); text = "" } else text = value
        },
        placeholder = { Text(L10n.str(R.string.editor_add_ingredient_hint)) },
        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
        trailingIcon = {
            if (text.isNotBlank()) {
                IconButton(onClick = { add() }) { Icon(Icons.Filled.Check, contentDescription = L10n.str(R.string.add_ingredient)) }
            }
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { add() }),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
    )
}

@Composable
fun StepsEditor(viewModel: RecipeEditorViewModel) {
    val context = LocalContext.current
    val speechAvailable = remember { SpeechDictation.isAvailable(context) }
    val dictationLanguage = org.calamares.miga.ui.components.rememberDictationLanguage()
    /**
     * Only one recording can be active at a time (a single SpeechRecognizer). It is destroyed when
     * finished (success, error or cancel) and also when the screen is left midway.
     */
    var activeRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var recordingRow by remember { mutableStateOf<StepRowUi?>(null) }
    var pendingPermissionRow by remember { mutableStateOf<StepRowUi?>(null) }

    fun beginListening(row: StepRowUi) {
        row.dictationError = null
        row.isRecording = true
        recordingRow = row
        activeRecognizer = SpeechDictation.startListening(context, dictationLanguage) { result ->
            row.isRecording = false
            recordingRow = null
            activeRecognizer?.destroy()
            activeRecognizer = null
            when (result) {
                is DictationResult.Success -> viewModel.cleanUpDictatedText(row, result.text)
                is DictationResult.Error -> row.dictationError = result.reason
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val row = pendingPermissionRow
        pendingPermissionRow = null
        if (granted && row != null) beginListening(row)
    }

    fun onMicClick(row: StepRowUi) {
        if (row.isRecording) {
            activeRecognizer?.stopListening()
            return
        }
        if (recordingRow != null || row.isTranscribing) return
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            beginListening(row)
        } else {
            pendingPermissionRow = row
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        onDispose { activeRecognizer?.destroy() }
    }

    var pasteInto by remember { mutableStateOf<Int?>(null) }
    pasteInto?.let { groupIndex ->
        PasteListDialog(
            title = L10n.str(R.string.paste_steps),
            hint = L10n.str(R.string.paste_steps_hint),
            onPaste = { viewModel.pasteSteps(groupIndex, it); pasteInto = null },
            onDismiss = { pasteInto = null }
        )
    }

    Section(title = L10n.str(R.string.method)) {
        viewModel.stepGroups.forEachIndexed { groupIndex, group ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (group.name != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = group.name.orEmpty(),
                            onValueChange = { group.name = it },
                            label = { Text(L10n.str(R.string.sub_recipe_name_e_g)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.removeStepGroup(groupIndex) }) {
                            Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.remove_sub_recipe))
                        }
                    }
                } else {
                    Text(L10n.str(R.string.main_steps), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }

                ReorderableColumn(
                    items = group.steps.toList(),
                    onReorder = { viewModel.reorderSteps(groupIndex, it) },
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) { row, rowIndex, dragHandle, isDragging ->
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.draggedLook(isDragging)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 12.dp, end = 6.dp)) {
                            Box(
                                modifier = Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${rowIndex + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            DragHandle(dragHandle)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            RichTextField(
                                value = row.text,
                                onValueChange = { text ->
                                    // A pasted list becomes one step per line.
                                    val lines = PastedLists.pastedLines(row.text, text)
                                    if (lines != null) viewModel.splitStepRow(groupIndex, rowIndex, lines) else row.text = text
                                },
                                modifier = Modifier.fillMaxWidth().semantics { contentDescription = L10n.str(R.string.step_x, rowIndex + 1) },
                                minLines = 1
                            )
                            row.dictationError?.let { reason ->
                                Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                if (speechAvailable) {
                                    IconButton(
                                        onClick = { onMicClick(row) },
                                        enabled = !row.isTranscribing && (recordingRow == null || recordingRow === row),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        when {
                                            row.isTranscribing -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                            row.isRecording -> Icon(Icons.Filled.Stop, contentDescription = L10n.str(R.string.stop_dictation), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                            else -> Icon(Icons.Filled.Mic, contentDescription = L10n.str(R.string.dictate_step), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                                IconButton(onClick = { viewModel.removeStepRow(groupIndex, rowIndex) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = L10n.str(R.string.remove_step), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { viewModel.addStepRow(groupIndex) },
                        label = { Text(L10n.str(R.string.add_step)) },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    AssistChip(
                        onClick = { pasteInto = groupIndex },
                        label = { Text(L10n.str(R.string.paste_list)) },
                        leadingIcon = { Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    if (groupIndex == viewModel.stepGroups.lastIndex) {
                        AssistChip(
                            onClick = { viewModel.addStepSubGroup() },
                            label = { Text(L10n.str(R.string.sub_recipe)) },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }
            if (groupIndex < viewModel.stepGroups.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }

    }
}

/** Handle to drag an ingredient or step to another place. */
@Composable
private fun DragHandle(dragHandle: Modifier) {
    Box(modifier = dragHandle.size(32.dp), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Filled.DragIndicator,
            contentDescription = L10n.str(R.string.drag_to_reorder),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Lifts the row being dragged above the others. */
@Composable
private fun Modifier.draggedLook(isDragging: Boolean): Modifier =
    if (isDragging) shadow(6.dp, MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium) else this

/** A list pasted or typed at once: one ingredient or step per line, bullets and numbers removed. */
@Composable
private fun PasteListDialog(title: String, hint: String, onPaste: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(hint) },
                minLines = 6,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onPaste(text) }) { Text(L10n.str(R.string.add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.str(R.string.cancel)) } }
    )
}
