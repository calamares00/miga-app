package org.calamares.miga.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.allergens.Allergen
import org.calamares.miga.data.model.Difficulty
import org.calamares.miga.data.model.RecipeFilter
import org.calamares.miga.data.model.RecipeOrigin
import org.calamares.miga.data.model.displayCategoryName
import org.calamares.miga.data.model.formatDuration

/** Maximum total times offered in the filters, in minutes. */
private val TIME_LIMITS = listOf(15, 30, 60)

/** Chips shown before "See all" in a long list (categories, ingredients...). */
private const val COLLAPSED_CHIPS = 8

/** Allergen toggles per row. */
const val ALLERGENS_PER_ROW = 5

/**
 * The filters of a list of recipes, applied as they change. The quick decisions first (time,
 * difficulty, allergens, category); the long lists (ingredients, utensils, tags, origin) fold away
 * and open when tapped. Sorting is not here but next to the results (see ResultsHeader).
 * [showFavoritesToggle] is false on the Favourites tab. With [resultCount] and [onShowResults], a
 * button at the bottom says how many recipes match and closes the sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterSheetContent(
    filter: RecipeFilter,
    availableCategories: List<String>,
    availableTags: List<String>,
    availableUtensils: List<String>,
    onApply: (RecipeFilter) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    availableIngredients: List<String> = emptyList(),
    availableOrigins: List<String> = emptyList(),
    showFavoritesToggle: Boolean = true,
    resultCount: Int? = null,
    onShowResults: (() -> Unit)? = null
) {
    var categoryNames by remember(filter) { mutableStateOf(filter.categoryNames) }
    var difficulties by remember(filter) { mutableStateOf(filter.difficulties) }
    var utensils by remember(filter) { mutableStateOf(filter.utensils) }
    var tags by remember(filter) { mutableStateOf(filter.tags) }
    var ingredients by remember(filter) { mutableStateOf(filter.ingredients) }
    var origins by remember(filter) { mutableStateOf(filter.origins) }
    var withoutAllergens by remember(filter) { mutableStateOf(filter.withoutAllergens) }
    var allowMayContain by remember(filter) { mutableStateOf(filter.allowMayContain) }
    var coeliacFriendly by remember(filter) { mutableStateOf(filter.coeliacFriendly) }
    var onlyFavorites by remember(filter) { mutableStateOf(filter.onlyFavorites) }
    var maxMinutes by remember(filter) { mutableStateOf(filter.maxMinutes) }
    var onlyCooked by remember(filter) { mutableStateOf(filter.onlyCooked) }
    var onlyRated by remember(filter) { mutableStateOf(filter.onlyRated) }
    var ingredientQuery by remember { mutableStateOf("") }

    val currentFilter = filter.copy(
        categoryNames = categoryNames,
        difficulties = difficulties,
        utensils = utensils,
        tags = tags,
        ingredients = ingredients,
        origins = origins,
        withoutAllergens = withoutAllergens,
        allowMayContain = allowMayContain,
        coeliacFriendly = coeliacFriendly,
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
            // Capped by the window, not the sheet, so the sheet settles (see sheetContentHeight).
            .heightIn(max = sheetContentHeight(0.9f))
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                    withoutAllergens = emptySet(); allowMayContain = false; coeliacFriendly = false
                    if (showFavoritesToggle) onlyFavorites = false
                    onClear()
                }
            ) { Text(L10n.str(R.string.clear_all)) }
        }

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            FilterSection(title = L10n.str(R.string.filter_max_time)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val options = listOf<Int?>(null) + TIME_LIMITS
                    options.forEachIndexed { index, minutes ->
                        SegmentedButton(
                            selected = maxMinutes == minutes,
                            onClick = { maxMinutes = minutes },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            icon = {},
                            label = {
                                Text(
                                    minutes?.let { formatDuration(it) } ?: L10n.str(R.string.filter_any),
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }
            }

            FilterSection(title = L10n.str(R.string.difficulty)) {
                MultiChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    Difficulty.entries.forEachIndexed { index, difficulty ->
                        SegmentedButton(
                            checked = difficulty in difficulties,
                            onCheckedChange = { difficulties = difficulties.toggled(difficulty) },
                            shape = SegmentedButtonDefaults.itemShape(index, Difficulty.entries.size),
                            label = { Text(difficulty.label, maxLines = 1) }
                        )
                    }
                }
            }

            AllergenFilters(
                without = withoutAllergens,
                onToggle = { withoutAllergens = withoutAllergens.toggled(it) },
                allowMayContain = allowMayContain,
                onAllowMayContain = { allowMayContain = it },
                coeliacFriendly = coeliacFriendly,
                onCoeliacFriendly = { coeliacFriendly = it }
            )

            if (availableCategories.isNotEmpty()) {
                FilterSection(title = L10n.str(R.string.category)) {
                    CollapsibleChips(
                        items = availableCategories,
                        selected = categoryNames,
                        label = { displayCategoryName(it) },
                        onToggle = { categoryNames = categoryNames.toggled(it) }
                    )
                }
            }

            Column {
                HorizontalDivider()
                if (availableIngredients.isNotEmpty()) {
                    FoldingSection(L10n.str(R.string.ingredients), ingredients.toList()) {
                        OutlinedTextField(
                            value = ingredientQuery,
                            onValueChange = { ingredientQuery = it },
                            singleLine = true,
                            placeholder = { Text(L10n.str(R.string.filter_find_ingredient)) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        val matching = availableIngredients.filter {
                            ingredientQuery.isBlank() || it.contains(ingredientQuery.trim(), ignoreCase = true)
                        }
                        CollapsibleChips(
                            items = matching,
                            selected = ingredients,
                            label = { it },
                            onToggle = { ingredients = ingredients.toggled(it) },
                            expanded = ingredientQuery.isNotBlank()
                        )
                    }
                }
                if (availableUtensils.isNotEmpty()) {
                    FoldingSection(L10n.str(R.string.utensils), utensils.toList()) {
                        CollapsibleChips(availableUtensils, utensils, { it }, { utensils = utensils.toggled(it) })
                    }
                }
                if (availableTags.isNotEmpty()) {
                    FoldingSection(L10n.str(R.string.tags), tags.map { "#$it" }) {
                        CollapsibleChips(availableTags, tags, { "#$it" }, { tags = tags.toggled(it) })
                    }
                }
                if (availableOrigins.isNotEmpty()) {
                    FoldingSection(L10n.str(R.string.origin), origins.map { RecipeOrigin.countryName(it) }) {
                        CollapsibleChips(
                            items = availableOrigins.sortedBy { RecipeOrigin.countryName(it) },
                            selected = origins,
                            label = { RecipeOrigin.label(null, it).orEmpty() },
                            onToggle = { origins = origins.toggled(it) }
                        )
                    }
                }
            }

            FilterSection(title = L10n.str(R.string.filter_more)) {
                if (showFavoritesToggle) {
                    SwitchRow(L10n.str(R.string.favourites_only), onlyFavorites) { onlyFavorites = it }
                }
                SwitchRow(L10n.str(R.string.filter_only_cooked), onlyCooked) { onlyCooked = it }
                SwitchRow(L10n.str(R.string.filter_only_rated), onlyRated) { onlyRated = it }
            }
        }

        if (resultCount != null && onShowResults != null) {
            HorizontalDivider()
            Button(
                onClick = onShowResults,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    when (resultCount) {
                        0 -> L10n.str(R.string.filter_no_results)
                        1 -> L10n.str(R.string.filter_show_one)
                        else -> L10n.plural(R.plurals.filter_show_n, resultCount)
                    }
                )
            }
        }
    }
}

/**
 * "Coeliac-friendly" and the allergens to leave out, as a grid of icons. The coeliac switch keeps
 * recipes whose gluten depends on the product (the recipe then says what to buy certified);
 * "Without gluten" does not, unless "include those that depend on the product" is on.
 */
@Composable
private fun AllergenFilters(
    without: Set<Allergen>,
    onToggle: (Allergen) -> Unit,
    allowMayContain: Boolean,
    onAllowMayContain: (Boolean) -> Unit,
    coeliacFriendly: Boolean,
    onCoeliacFriendly: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(L10n.str(R.string.allergens_sheet_title), style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (coeliacFriendly) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLowest
                    )
                    .toggleable(value = coeliacFriendly, role = Role.Switch, onValueChange = onCoeliacFriendly)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(Allergen.GLUTEN.iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(L10n.str(R.string.filter_coeliac), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        L10n.str(R.string.filter_coeliac_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = coeliacFriendly, onCheckedChange = null)
            }

            Text(
                L10n.str(R.string.filter_without_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Allergen.entries.chunked(ALLERGENS_PER_ROW).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        row.forEach { allergen ->
                            AllergenToggle(allergen, allergen in without, { onToggle(allergen) }, Modifier.weight(1f))
                        }
                        repeat(ALLERGENS_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            AnimatedVisibility(visible = without.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = allowMayContain, role = Role.Switch, onValueChange = onAllowMayContain),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(L10n.str(R.string.filter_allow_may_contain), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            L10n.str(R.string.filter_allow_may_contain_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = allowMayContain, onCheckedChange = null)
                }
            }
        }
    }
}

/** One allergen of the grid: its icon in a circle, crossed out when the recipes must not have it. */
@Composable
fun AllergenToggle(allergen: Allergen, selected: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val strike = scheme.error
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (selected) scheme.errorContainer else scheme.surfaceContainerLowest)
                .border(if (selected) 2.dp else 1.dp, if (selected) strike else scheme.outlineVariant, CircleShape)
                .drawWithContent {
                    drawContent()
                    if (selected) {
                        val inset = size.minDimension * 0.22f
                        drawLine(
                            strike,
                            Offset(inset, inset),
                            Offset(size.width - inset, size.height - inset),
                            strokeWidth = 2.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(allergen.iconRes),
                contentDescription = null,
                tint = if (selected) scheme.onErrorContainer else scheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            allergen.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) scheme.error else scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Chips for a long list: the chosen ones first, then the rest up to [COLLAPSED_CHIPS] and a
 * "See all" button, unless [expanded].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> CollapsibleChips(
    items: List<T>,
    selected: Set<T>,
    label: (T) -> String,
    onToggle: (T) -> Unit,
    expanded: Boolean = false
) {
    var showAll by remember { mutableStateOf(false) }
    val ordered = items.filter { it in selected } + items.filter { it !in selected }
    val shown = if (showAll || expanded) ordered else ordered.take(maxOf(COLLAPSED_CHIPS, selected.size))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.forEach { item ->
            FilterChip(selected = item in selected, onClick = { onToggle(item) }, label = { Text(label(item)) })
        }
    }
    if (shown.size < ordered.size) {
        TextButton(onClick = { showAll = true }) { Text(L10n.str(R.string.see_all_x, ordered.size)) }
    }
}

/**
 * A long list folded into one line: its title, what is chosen in it and an arrow. Opens when
 * tapped, and starts open when something in it is chosen.
 */
@Composable
private fun FoldingSection(title: String, chosen: List<String>, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(chosen.isNotEmpty()) }
    val arrow by animateFloatAsState(if (open) 180f else 0f, label = "arrow")
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(12.dp))
            Text(
                chosen.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.rotate(arrow))
        }
        AnimatedVisibility(visible = open) {
            Column(modifier = Modifier.padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
        HorizontalDivider()
    }
}

private fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}
