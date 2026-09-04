package com.bmo00.miga.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bmo00.miga.data.model.RecipeSummary
import com.bmo00.miga.ui.components.FilterSheetContent
import com.bmo00.miga.ui.components.RecipeCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    viewModel: RecipeListViewModel,
    onBack: () -> Unit,
    onRecipeClick: (Long) -> Unit,
    onEditRecipeClick: (Long) -> Unit,
    onAddRecipeClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val filter by viewModel.filter.collectAsState()
    var showFilters by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var recipeToDelete by remember { mutableStateOf<RecipeSummary?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.bookName) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Volver") }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Exportar este libro (JSON)") },
                            onClick = { showMenu = false; viewModel.exportBookAsJson(context) }
                        )
                        DropdownMenuItem(
                            text = { Text("Exportar este libro (PDF)") },
                            onClick = { showMenu = false; viewModel.exportBookAsPdf(context) }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddRecipeClick, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Nueva receta") })
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = filter.query,
                    onValueChange = viewModel::updateQuery,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Buscar recetas o ingredientes...") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true
                )
                IconButton(onClick = { showFilters = true }) {
                    BadgedBox(badge = { if (filter.isActive) Badge() }) {
                        Icon(Icons.Filled.FilterList, contentDescription = "Filtros")
                    }
                }
            }

            if (uiState.groups.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (uiState.totalCount == 0) {
                            "Aún no tienes recetas.\nPulsa \"Nueva receta\" para añadir la primera."
                        } else {
                            "No hay recetas que coincidan con la búsqueda o los filtros."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.groups.forEach { group ->
                        item(key = "header_${group.categoryName}") {
                            Text(
                                text = "${group.categoryName} (${group.recipes.size})",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        items(group.recipes, key = { it.id }) { recipe ->
                            RecipeCard(
                                recipe = recipe,
                                onClick = { onRecipeClick(recipe.id) },
                                onToggleFavorite = { viewModel.toggleFavorite(recipe.id, recipe.isFavorite) },
                                onEditClick = { onEditRecipeClick(recipe.id) },
                                onDeleteClick = { recipeToDelete = recipe }
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.padding(40.dp)) }
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
                onApply = { newFilter ->
                    viewModel.applyFilter(newFilter)
                    showFilters = false
                },
                onClear = {
                    viewModel.clearFilters()
                    showFilters = false
                }
            )
        }
    }

    recipeToDelete?.let { recipe ->
        AlertDialog(
            onDismissRequest = { recipeToDelete = null },
            title = { Text("Eliminar receta") },
            text = { Text("¿Seguro que quieres eliminar \"${recipe.name}\"? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecipe(recipe.id)
                    recipeToDelete = null
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { recipeToDelete = null }) { Text("Cancelar") }
            }
        )
    }
}
