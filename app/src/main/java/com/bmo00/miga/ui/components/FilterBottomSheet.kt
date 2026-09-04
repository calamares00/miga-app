package com.bmo00.miga.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bmo00.miga.data.model.Difficulty
import com.bmo00.miga.data.model.RecipeFilter
import com.bmo00.miga.data.model.SortOption

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterSheetContent(
    filter: RecipeFilter,
    availableCategories: List<String>,
    availableTags: List<String>,
    availableUtensils: List<String>,
    onApply: (RecipeFilter) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var categoryNames by remember(filter) { mutableStateOf(filter.categoryNames) }
    var difficulties by remember(filter) { mutableStateOf(filter.difficulties) }
    var utensils by remember(filter) { mutableStateOf(filter.utensils) }
    var tags by remember(filter) { mutableStateOf(filter.tags) }
    var onlyFavorites by remember(filter) { mutableStateOf(filter.onlyFavorites) }
    var sortOption by remember(filter) { mutableStateOf(filter.sortOption) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Filtrar y ordenar", style = MaterialTheme.typography.titleLarge)

        if (availableCategories.isNotEmpty()) {
            FilterSection(title = "Categoría") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableCategories.forEach { category ->
                        FilterChip(
                            selected = category in categoryNames,
                            onClick = {
                                categoryNames = if (category in categoryNames) categoryNames - category else categoryNames + category
                            },
                            label = { Text(category) }
                        )
                    }
                }
            }
        }

        FilterSection(title = "Dificultad") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Difficulty.entries.forEach { difficulty ->
                    FilterChip(
                        selected = difficulty in difficulties,
                        onClick = { difficulties = if (difficulty in difficulties) difficulties - difficulty else difficulties + difficulty },
                        label = { Text(difficulty.label) }
                    )
                }
            }
        }

        if (availableUtensils.isNotEmpty()) {
            FilterSection(title = "Utensilios") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableUtensils.forEach { utensil ->
                        FilterChip(
                            selected = utensil in utensils,
                            onClick = { utensils = if (utensil in utensils) utensils - utensil else utensils + utensil },
                            label = { Text(utensil) }
                        )
                    }
                }
            }
        }

        if (availableTags.isNotEmpty()) {
            FilterSection(title = "Etiquetas") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableTags.forEach { tag ->
                        FilterChip(
                            selected = tag in tags,
                            onClick = { tags = if (tag in tags) tags - tag else tags + tag },
                            label = { Text(tag) }
                        )
                    }
                }
            }
        }

        FilterSection(title = "Ordenar por") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortOption.entries.forEach { option ->
                    FilterChip(
                        selected = sortOption == option,
                        onClick = { sortOption = option },
                        label = { Text(option.label) }
                    )
                }
            }
        }

        HorizontalDivider()

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Solo favoritas", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = onlyFavorites, onCheckedChange = { onlyFavorites = it })
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(
                onClick = {
                    categoryNames = emptySet(); difficulties = emptySet()
                    utensils = emptySet(); tags = emptySet(); onlyFavorites = false
                    onClear()
                },
                modifier = Modifier.weight(1f)
            ) { Text("Limpiar") }

            Button(
                onClick = {
                    onApply(
                        filter.copy(
                            categoryNames = categoryNames,
                            difficulties = difficulties,
                            utensils = utensils,
                            tags = tags,
                            onlyFavorites = onlyFavorites,
                            sortOption = sortOption
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            ) { Text("Aplicar") }
        }
    }
}

@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}
