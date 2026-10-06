package org.calamares.miga.ui.components

import org.calamares.miga.data.model.displayCategoryName
import org.calamares.miga.L10n
import org.calamares.miga.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.calamares.miga.data.model.Difficulty
import org.calamares.miga.data.model.RecipeFilter
import org.calamares.miga.data.model.SortOption

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterSheetContent(
    filter: RecipeFilter,
    availableCategories: List<String>,
    availableTags: List<String>,
    availableUtensils: List<String>,
    availableIngredients: List<String> = emptyList(),
    onApply: (RecipeFilter) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var categoryNames by remember(filter) { mutableStateOf(filter.categoryNames) }
    var difficulties by remember(filter) { mutableStateOf(filter.difficulties) }
    var utensils by remember(filter) { mutableStateOf(filter.utensils) }
    var tags by remember(filter) { mutableStateOf(filter.tags) }
    var ingredients by remember(filter) { mutableStateOf(filter.ingredients) }
    var onlyFavorites by remember(filter) { mutableStateOf(filter.onlyFavorites) }
    var sortOption by remember(filter) { mutableStateOf(filter.sortOption) }

    val currentFilter = filter.copy(
        categoryNames = categoryNames,
        difficulties = difficulties,
        utensils = utensils,
        tags = tags,
        ingredients = ingredients,
        onlyFavorites = onlyFavorites,
        sortOption = sortOption
    )
    LaunchedEffect(currentFilter) { onApply(currentFilter) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(L10n.str(R.string.filter_sort), style = MaterialTheme.typography.titleLarge)

        if (availableCategories.isNotEmpty()) {
            FilterSection(title = L10n.str(R.string.category)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableCategories.forEach { category ->
                        FilterChip(
                            selected = category in categoryNames,
                            onClick = {
                                categoryNames = if (category in categoryNames) categoryNames - category else categoryNames + category
                            },
                            label = { Text(displayCategoryName(category)) }
                        )
                    }
                }
            }
        }

        FilterSection(title = L10n.str(R.string.difficulty)) {
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
            FilterSection(title = L10n.str(R.string.utensils)) {
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
            FilterSection(title = L10n.str(R.string.tags)) {
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

        if (availableIngredients.isNotEmpty()) {
            FilterSection(title = L10n.str(R.string.ingredients)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableIngredients.forEach { ingredient ->
                        FilterChip(
                            selected = ingredient in ingredients,
                            onClick = { ingredients = if (ingredient in ingredients) ingredients - ingredient else ingredients + ingredient },
                            label = { Text(ingredient) }
                        )
                    }
                }
            }
        }

        FilterSection(title = L10n.str(R.string.sort)) {
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
            Text(L10n.str(R.string.favourites_only), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = onlyFavorites, onCheckedChange = { onlyFavorites = it })
        }

        TextButton(
            onClick = {
                categoryNames = emptySet(); difficulties = emptySet()
                utensils = emptySet(); tags = emptySet(); ingredients = emptySet(); onlyFavorites = false
                sortOption = SortOption.NAME_ASC
                onClear()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(L10n.str(R.string.clear)) }
    }
}

@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}
