package org.calamares.miga.ui.editor

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.calamares.miga.data.voice.DictationResult
import org.calamares.miga.data.voice.SpeechDictation

@Composable
fun IngredientsEditor(viewModel: RecipeEditorViewModel) {
    val availableIngredientNames by viewModel.availableIngredientNames.collectAsState()

    Section(title = L10n.str(R.string.ingredientes)) {
        viewModel.ingredientGroups.forEachIndexed { groupIndex, group ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (group.name != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = group.name.orEmpty(),
                            onValueChange = { group.name = it },
                            label = { Text(L10n.str(R.string.nombre_sub_receta_ej_salsa)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.removeIngredientGroup(groupIndex) }) {
                            Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.quitar_sub_receta))
                        }
                    }
                } else {
                    Text(L10n.str(R.string.ingredientes_principales), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                group.ingredients.forEachIndexed { rowIndex, row ->
                    IngredientRow(
                        row = row,
                        availableNames = availableIngredientNames,
                        onRemove = { viewModel.removeIngredientRow(groupIndex, rowIndex) }
                    )
                }

                TextButton(onClick = { viewModel.addIngredientRow(groupIndex) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(L10n.str(R.string.anadir_ingrediente))
                }
            }
            if (groupIndex < viewModel.ingredientGroups.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }

        TextButton(onClick = { viewModel.addIngredientSubGroup() }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(L10n.str(R.string.anadir_sub_receta_ej_salsa))
        }
    }
}

@Composable
private fun IngredientRow(row: IngredientRowUi, availableNames: List<String>, onRemove: () -> Unit) {
    var showSuggestions by remember { mutableStateOf(false) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = row.quantity,
                onValueChange = { row.quantity = it },
                label = { Text(L10n.str(R.string.cant)) },
                modifier = Modifier.weight(0.7f),
                singleLine = true
            )
            OutlinedTextField(
                value = row.unit,
                onValueChange = { row.unit = it },
                label = { Text(L10n.str(R.string.ud)) },
                modifier = Modifier.weight(0.8f),
                singleLine = true
            )
            OutlinedTextField(
                value = row.name,
                onValueChange = { row.name = it; showSuggestions = true },
                label = { Text(L10n.str(R.string.ingrediente)) },
                modifier = Modifier.weight(1.5f),
                singleLine = true
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.quitar_ingrediente))
            }
        }

        if (showSuggestions && row.name.isNotBlank()) {
            val suggestions = availableNames.filter {
                it.contains(row.name, ignoreCase = true) && !it.equals(row.name, ignoreCase = true)
            }
            if (suggestions.isNotEmpty()) {
                Column {
                    suggestions.take(5).forEach { suggestion ->
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { row.name = suggestion; showSuggestions = false }
                                .padding(vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StepsEditor(viewModel: RecipeEditorViewModel) {
    val context = LocalContext.current
    val speechAvailable = remember { SpeechDictation.isAvailable(context) }
    val dictationLanguage = org.calamares.miga.ui.components.rememberDictationLanguage()
    // Solo puede haber una grabación activa a la vez (un único SpeechRecognizer); se destruye al
    // terminar (éxito, error o cancelación) y también si la pantalla se abandona a mitad.
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

    Section(title = L10n.str(R.string.preparacion)) {
        viewModel.stepGroups.forEachIndexed { groupIndex, group ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (group.name != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = group.name.orEmpty(),
                            onValueChange = { group.name = it },
                            label = { Text(L10n.str(R.string.nombre_sub_receta_ej_salsa)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.removeStepGroup(groupIndex) }) {
                            Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.quitar_sub_receta))
                        }
                    }
                } else {
                    Text(L10n.str(R.string.pasos_principales), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                group.steps.forEachIndexed { rowIndex, row ->
                    Column {
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "${rowIndex + 1}.",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                            OutlinedTextField(
                                value = row.text,
                                onValueChange = { row.text = it },
                                label = { Text(L10n.str(R.string.paso_x, rowIndex + 1)) },
                                modifier = Modifier.weight(1f),
                                minLines = 1
                            )
                            if (speechAvailable) {
                                IconButton(
                                    onClick = { onMicClick(row) },
                                    enabled = !row.isTranscribing && (recordingRow == null || recordingRow === row)
                                ) {
                                    when {
                                        row.isTranscribing -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        row.isRecording -> Icon(Icons.Filled.Stop, contentDescription = L10n.str(R.string.detener_dictado), tint = MaterialTheme.colorScheme.error)
                                        else -> Icon(Icons.Filled.Mic, contentDescription = L10n.str(R.string.dictar_paso_voz))
                                    }
                                }
                            }
                            IconButton(onClick = { viewModel.removeStepRow(groupIndex, rowIndex) }) {
                                Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.quitar_paso))
                            }
                        }
                        row.dictationError?.let { reason ->
                            Text(
                                reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 24.dp)
                            )
                        }
                    }
                }

                TextButton(onClick = { viewModel.addStepRow(groupIndex) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(L10n.str(R.string.anadir_paso))
                }
            }
            if (groupIndex < viewModel.stepGroups.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }

        TextButton(onClick = { viewModel.addStepSubGroup() }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(L10n.str(R.string.anadir_pasos_sub_receta_ej))
        }
    }
}
