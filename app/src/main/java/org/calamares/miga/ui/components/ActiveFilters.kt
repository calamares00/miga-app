package org.calamares.miga.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
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
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.model.MissingField
import org.calamares.miga.data.model.displayCategoryName
import org.calamares.miga.data.model.RecipeFilter
import org.calamares.miga.data.model.RecipeOrigin
import org.calamares.miga.data.model.SortOption

/**
 * The conditions of [filter] as removable chips, so what is applied is always visible without
 * opening the filters, with "Clear all" at the end. Nothing is drawn when no condition applies.
 * [ignoreFavorites] on the Favourites tab, where "only favourites" is implicit.
 */
@Composable
fun ActiveFilterChips(
    filter: RecipeFilter,
    onChange: (RecipeFilter) -> Unit,
    onClearAll: () -> Unit,
    ignoreFavorites: Boolean = false
) {
    val chips = buildList<Pair<String, RecipeFilter>> {
        if (filter.onlyFavorites && !ignoreFavorites) add(L10n.str(R.string.favourites_2) to filter.copy(onlyFavorites = false))
        filter.maxMinutes?.let { add(L10n.str(R.string.filter_up_to_x_min, it) to filter.copy(maxMinutes = null)) }
        filter.difficulties.forEach { add(it.label to filter.copy(difficulties = filter.difficulties - it)) }
        filter.categoryNames.forEach { add(displayCategoryName(it) to filter.copy(categoryNames = filter.categoryNames - it)) }
        filter.utensils.forEach { add(it to filter.copy(utensils = filter.utensils - it)) }
        filter.tags.forEach { add("#$it" to filter.copy(tags = filter.tags - it)) }
        filter.ingredients.forEach { add(it to filter.copy(ingredients = filter.ingredients - it)) }
        filter.origins.forEach { add(RecipeOrigin.label(null, it).orEmpty() to filter.copy(origins = filter.origins - it)) }
        if (filter.onlyCooked) add(L10n.str(R.string.search_cond_cooked) to filter.copy(onlyCooked = false))
        if (filter.onlyRated) add(L10n.str(R.string.search_cond_rated) to filter.copy(onlyRated = false))
        filter.bookNames.forEach { add(L10n.str(R.string.search_cond_book_x, it) to filter.copy(bookNames = filter.bookNames - it)) }
        filter.missing.forEach { field ->
            val label = when (field) {
                MissingField.PHOTO -> R.string.stats_without_photo
                MissingField.CATEGORY -> R.string.stats_without_category
                MissingField.INGREDIENTS -> R.string.stats_without_ingredients
                MissingField.STEPS -> R.string.stats_without_steps
                MissingField.TIME -> R.string.stats_without_time
            }
            add(L10n.str(label) to filter.copy(missing = filter.missing - field))
        }
        if (filter.addedSince != null) add(L10n.str(R.string.search_cond_added_this_month) to filter.copy(addedSince = null))
    }
    if (chips.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEach { (label, without) ->
            InputChip(
                selected = true,
                onClick = { onChange(without) },
                label = { Text(label) },
                trailingIcon = {
                    Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.remove_filter_x, label), modifier = Modifier.size(18.dp))
                }
            )
        }
        if (chips.size > 1) {
            AssistChip(onClick = onClearAll, label = { Text(L10n.str(R.string.clear_all)) })
        }
    }
}

/** "12 recipes" and the sort order, which opens a menu: above every list of recipes. */
@Composable
fun ResultsHeader(count: Int, sort: SortOption, onSort: (SortOption) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (count == 1) L10n.str(R.string.recipe_count_one) else L10n.str(R.string.recipe_count_many, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Box {
            TextButton(onClick = { menu = true }) { ButtonContent(Icons.Filled.Sort, sort.label) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                SortOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        leadingIcon = {
                            if (option == sort) Icon(Icons.Filled.Check, contentDescription = null) else Box(modifier = Modifier.size(24.dp))
                        },
                        onClick = { menu = false; onSort(option) }
                    )
                }
            }
        }
    }
}
