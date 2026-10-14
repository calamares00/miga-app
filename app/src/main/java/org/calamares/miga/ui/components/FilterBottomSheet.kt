package org.calamares.miga.ui.components

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
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
import org.calamares.miga.data.model.RecipeOrigin

/** Maximum total times offered in the filters, in minutes. */
private val TIME_LIMITS = listOf(15, 30, 60)

/** Ingredients shown before "See all": a library can use hundreds. */
private const val COLLAPSED_INGREDIENTS = 24

/**
 * The filters of a list of recipes, applied as they change. Ordered from the quick decisions
 * (time, difficulty) to the detailed ones (ingredients, origin); sorting is not here but next to
 * the results (see ResultsHeader). [showFavoritesToggle] is false on the Favourites tab.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterSheetContent(
    filter: RecipeFilter,
    availableCategories: List<String>,
    availableTags: List<String>,
    availableUtensils: List<String>,
    availableIngredients: List<String> = emptyList(),
    availableOrigins: List<String> = emptyList(),
    onApply: (RecipeFilter) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    showFavoritesToggle: Boolean = true
) {
    var categoryNames by remember(filter) { mutableStateOf(filter.categoryNames) }
    var difficulties by remember(filter) { mutableStateOf(filter.difficulties) }
    var utensils by remember(filter) { mutableStateOf(filter.utensils) }
    var tags by remember(filter) { mutableStateOf(filter.tags) }
    var ingredients by remember(filter) { mutableStateOf(filter.ingredients) }
    var origins by remember(filter) { mutableStateOf(filter.origins) }
    var onlyFavorites by remember(filter) { mutableStateOf(filter.onlyFavorites) }
    var maxMinutes by remember(filter) { mutableStateOf(filter.maxMinutes) }
    var onlyCooked by remember(filter) { mutableStateOf(filter.onlyCooked) }
    var onlyRated by remember(filter) { mutableStateOf(filter.onlyRated) }
    var ingredientQuery by remember { mutableStateOf("") }
    var allIngredients by remember { mutableStateOf(false) }

    val currentFilter = filter.copy(
        categoryNames = categoryNames,
        difficulties = difficulties,
        utensils = utensils,
        tags = tags,
        ingredients = ingredients,
        origins = origins,
        onlyFavorites = onlyFavorites,
        maxMinutes = maxMinutes,
        onlyCooked = onlyCooked,
        onlyRated = onlyRated
    )
    LaunchedEffect(currentFilter) { onApply(currentFilter) }
    val count = currentFilter.activeConditions(ignoreFavorites = !showFavoritesToggle)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (count > 0) L10n.str(R.string.filters_n, count) else L10n.str(R.string.filters),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                enabled = count > 0,
                onClick = {
                    categoryNames = emptySet(); difficulties = emptySet(); utensils = emptySet(); tags = emptySet()
                    ingredients = emptySet(); origins = emptySet(); maxMinutes = null; onlyCooked = false; onlyRated = false
                    if (showFavoritesToggle) onlyFavorites = false
                    onClear()
                }
            ) { Text(L10n.str(R.string.clear_all)) }
        }

        FilterSection(title = L10n.str(R.string.filter_total_time)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TIME_LIMITS.forEach { minutes ->
                    FilterChip(
                        selected = maxMinutes == minutes,
                        onClick = { maxMinutes = if (maxMinutes == minutes) null else minutes },
                        label = { Text(L10n.str(R.string.filter_up_to_x_min, minutes)) }
                    )
                }
            }
        }

        FilterSection(title = L10n.str(R.string.difficulty)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Difficulty.entries.forEach { difficulty ->
                    FilterChip(
                        selected = difficulty in difficulties,
                        onClick = { difficulties = difficulties.toggled(difficulty) },
                        label = { Text(difficulty.label) }
                    )
                }
            }
        }

        if (availableCategories.isNotEmpty()) {
            FilterSection(title = L10n.str(R.string.category)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableCategories.forEach { category ->
                        FilterChip(
                            selected = category in categoryNames,
                            onClick = { categoryNames = categoryNames.toggled(category) },
                            label = { Text(displayCategoryName(category)) }
                        )
                    }
                }
            }
        }

        if (availableUtensils.isNotEmpty()) {
            FilterSection(title = L10n.str(R.string.utensils)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableUtensils.forEach { utensil ->
                        FilterChip(
                            selected = utensil in utensils,
                            onClick = { utensils = utensils.toggled(utensil) },
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
                            onClick = { tags = tags.toggled(tag) },
                            label = { Text(tag) }
                        )
                    }
                }
            }
        }

        if (availableIngredients.isNotEmpty()) {
            FilterSection(title = L10n.str(R.string.ingredients)) {
                OutlinedTextField(
                    value = ingredientQuery,
                    onValueChange = { ingredientQuery = it },
                    singleLine = true,
                    placeholder = { Text(L10n.str(R.string.filter_find_ingredient)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                // The chosen ones first, then those matching what is typed.
                val matching = availableIngredients.filter { ingredientQuery.isBlank() || it.contains(ingredientQuery.trim(), ignoreCase = true) }
                val ordered = matching.filter { it in ingredients } + matching.filter { it !in ingredients }
                val shown = if (allIngredients || ingredientQuery.isNotBlank()) ordered else ordered.take(COLLAPSED_INGREDIENTS)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    shown.forEach { ingredient ->
                        FilterChip(
                            selected = ingredient in ingredients,
                            onClick = { ingredients = ingredients.toggled(ingredient) },
                            label = { Text(ingredient) }
                        )
                    }
                }
                if (shown.size < ordered.size) {
                    TextButton(onClick = { allIngredients = true }) { Text(L10n.str(R.string.see_all_x, ordered.size)) }
                }
            }
        }

        if (availableOrigins.isNotEmpty()) {
            FilterSection(title = L10n.str(R.string.origin)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    availableOrigins.sortedBy { RecipeOrigin.countryName(it) }.forEach { code ->
                        FilterChip(
                            selected = code in origins,
                            onClick = { origins = origins.toggled(code) },
                            label = { Text(RecipeOrigin.label(null, code).orEmpty()) }
                        )
                    }
                }
            }
        }

        HorizontalDivider()

        FilterSection(title = L10n.str(R.string.filter_more)) {
            if (showFavoritesToggle) {
                SwitchRow(L10n.str(R.string.favourites_only), onlyFavorites) { onlyFavorites = it }
            }
            SwitchRow(L10n.str(R.string.filter_only_cooked), onlyCooked) { onlyCooked = it }
            SwitchRow(L10n.str(R.string.filter_only_rated), onlyRated) { onlyRated = it }
        }
    }
}

private fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}
