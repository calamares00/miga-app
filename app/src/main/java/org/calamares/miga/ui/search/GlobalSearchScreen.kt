package org.calamares.miga.ui.search

import org.calamares.miga.ui.components.ResultsHeader
import org.calamares.miga.ui.components.ActiveFilterChips
import org.calamares.miga.ui.theme.recipePhotoFrame
import org.calamares.miga.L10n
import org.calamares.miga.R
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.FavoriteBorder
import org.calamares.miga.ui.components.EmptyState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.InputChip
import org.calamares.miga.data.model.MissingField
import org.calamares.miga.data.model.RecipeFilter
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.calamares.miga.ui.components.FilterSheetContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    viewModel: GlobalSearchViewModel,
    onRecipeClick: (Long) -> Unit,
    title: String = L10n.str(R.string.search_recipes_2),
    /** The Favourites tab: only favourites, so that condition is implicit and not offered. */
    favoritesOnly: Boolean = false,
    /** Shows a back arrow: the screen was opened on top of another one (from the statistics). */
    onBack: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val selectionMode = selectedIds.isNotEmpty()
    var showFilters by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    /** In Favourites, "only favourites" is implicit and does not count as an applied filter. */
    val activeCount = filter.activeConditions(ignoreFavorites = favoritesOnly)
    val filtersApplied = activeCount > 0

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.navigationBars).exclude(WindowInsets.ime),
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    title = { Text(L10n.str(R.string.n_selected, selectedIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.cancel_selection))
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.addSelectedToShoppingList() }) {
                            Icon(Icons.Filled.ShoppingCart, contentDescription = L10n.str(R.string.add_shopping_list))
                        }
                    }
                )
            } else {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = L10n.str(R.string.back)) }
                        }
                    },
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // The same header on Search and Favourites: the search field with the filters next to
            // it, the filters applied as chips and, over the results, how many and their order.
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = filter.query,
                    onValueChange = viewModel::updateQuery,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            L10n.str(if (favoritesOnly) R.string.search_in_favourites else R.string.name_ingredient_tag),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (filter.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateQuery("") }) {
                                Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.close_search))
                            }
                        }
                    },
                    singleLine = true
                )
                IconButton(onClick = { showFilters = true }) {
                    BadgedBox(badge = { if (filtersApplied) Badge { Text(activeCount.toString()) } }) {
                        Icon(Icons.Filled.FilterList, contentDescription = L10n.str(R.string.filters))
                    }
                }
            }

            ActiveFilterChips(
                filter = filter,
                onChange = viewModel::applyFilter,
                onClearAll = { viewModel.clearFilters() },
                ignoreFavorites = favoritesOnly
            )

            if (!uiState.isLoading && uiState.results.isNotEmpty()) {
                ResultsHeader(
                    count = uiState.results.size,
                    sort = filter.sortOption,
                    onSort = { viewModel.applyFilter(filter.copy(sortOption = it)) }
                )
            }

            when {
                uiState.isLoading -> Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                uiState.results.isEmpty() -> when {
                    favoritesOnly && !filtersApplied && filter.query.isBlank() -> EmptyState(
                        icon = Icons.Filled.FavoriteBorder,
                        title = L10n.str(R.string.no_favourites_yet),
                        body = L10n.str(R.string.tap_heart_recipe_keep_handy),
                        modifier = Modifier.fillMaxSize().weight(1f)
                    )
                    filter.query.isBlank() && !filter.isActive -> EmptyState(
                        icon = Icons.Filled.Search,
                        title = L10n.str(R.string.search_recipes),
                        body = L10n.str(R.string.name_ingredient_tag_utensil_difficulty),
                        modifier = Modifier.fillMaxSize().weight(1f)
                    )
                    else -> EmptyState(
                        icon = Icons.Filled.SearchOff,
                        title = L10n.str(R.string.no_results),
                        body = L10n.str(R.string.try_other_words_remove_filter),
                        modifier = Modifier.fillMaxSize().weight(1f)
                    ) {
                        if (filtersApplied) OutlinedButton(onClick = { viewModel.clearFilters() }) { Text(L10n.str(R.string.clear_filters)) }
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.results, key = { it.recipeId }) { result ->
                        SearchResultCard(
                            result = result,
                            selectionMode = selectionMode,
                            isSelected = result.recipeId in selectedIds,
                            onClick = {
                                if (selectionMode) viewModel.toggleSelection(result.recipeId) else onRecipeClick(result.recipeId)
                            },
                            onLongClick = { viewModel.startSelection(result.recipeId) }
                        )
                    }
                }
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }, sheetState = sheetState) {
            FilterSheetContent(
                filter = filter,
                availableCategories = uiState.availableCategories,
                availableTags = uiState.availableTags,
                availableUtensils = uiState.availableUtensils,
                availableIngredients = uiState.availableIngredients,
                availableOrigins = uiState.availableOrigins,
                onApply = { newFilter -> viewModel.applyFilter(newFilter) },
                onClear = { viewModel.clearFilters() },
                showFavoritesToggle = !favoritesOnly
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultCard(
    result: SearchResult,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (selectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (isSelected) L10n.str(R.string.selected) else L10n.str(R.string.not_selected),
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (result.photoUri != null) {
                        AsyncImage(
                            model = result.photoUri,
                            contentDescription = result.recipeName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().recipePhotoFrame()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Restaurant,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxSize().padding(16.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = result.recipeName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = result.bookName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Text(
                    text = result.difficultyLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
