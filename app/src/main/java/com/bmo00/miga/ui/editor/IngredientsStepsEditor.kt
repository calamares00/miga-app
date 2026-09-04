package com.bmo00.miga.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun IngredientsEditor(viewModel: RecipeEditorViewModel) {
    val availableIngredientNames by viewModel.availableIngredientNames.collectAsState()

    Section(title = "Ingredientes") {
        viewModel.ingredientGroups.forEachIndexed { groupIndex, group ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (group.name != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = group.name.orEmpty(),
                            onValueChange = { group.name = it },
                            label = { Text("Nombre de la sub-receta (ej. Salsa)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.removeIngredientGroup(groupIndex) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Quitar sub-receta")
                        }
                    }
                } else {
                    Text("Ingredientes principales", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("Añadir ingrediente")
                }
            }
            if (groupIndex < viewModel.ingredientGroups.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }

        TextButton(onClick = { viewModel.addIngredientSubGroup() }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("Añadir sub-receta (ej. una salsa)")
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
                label = { Text("Cant.") },
                modifier = Modifier.weight(0.7f),
                singleLine = true
            )
            OutlinedTextField(
                value = row.unit,
                onValueChange = { row.unit = it },
                label = { Text("Ud.") },
                modifier = Modifier.weight(0.8f),
                singleLine = true
            )
            OutlinedTextField(
                value = row.name,
                onValueChange = { row.name = it; showSuggestions = true },
                label = { Text("Ingrediente") },
                modifier = Modifier.weight(1.5f),
                singleLine = true
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Quitar ingrediente")
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
    Section(title = "Preparación") {
        viewModel.stepGroups.forEachIndexed { groupIndex, group ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (group.name != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = group.name.orEmpty(),
                            onValueChange = { group.name = it },
                            label = { Text("Nombre de la sub-receta (ej. Salsa)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.removeStepGroup(groupIndex) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Quitar sub-receta")
                        }
                    }
                } else {
                    Text("Pasos principales", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                group.steps.forEachIndexed { rowIndex, row ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${rowIndex + 1}.",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        OutlinedTextField(
                            value = row.text,
                            onValueChange = { row.text = it },
                            label = { Text("Paso ${rowIndex + 1}") },
                            modifier = Modifier.weight(1f),
                            minLines = 1
                        )
                        IconButton(onClick = { viewModel.removeStepRow(groupIndex, rowIndex) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Quitar paso")
                        }
                    }
                }

                TextButton(onClick = { viewModel.addStepRow(groupIndex) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Añadir paso")
                }
            }
            if (groupIndex < viewModel.stepGroups.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }

        TextButton(onClick = { viewModel.addStepSubGroup() }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("Añadir pasos de una sub-receta (ej. una salsa)")
        }
    }
}
