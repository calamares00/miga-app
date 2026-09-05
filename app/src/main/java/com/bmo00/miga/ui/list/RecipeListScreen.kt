package com.bmo00.miga.ui.list

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewHeadline
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bmo00.miga.data.local.PhotoStorage
import com.bmo00.miga.data.model.RecipeListViewMode
import com.bmo00.miga.data.model.RecipeSummary
import com.bmo00.miga.ui.common.BACKUP_MIME_TYPES
import com.bmo00.miga.ui.components.FilterSheetContent
import com.bmo00.miga.ui.components.PhotoSourceSheet
import com.bmo00.miga.ui.components.RecipeCard
import com.bmo00.miga.ui.components.RecipeGridCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    viewModel: RecipeListViewModel,
    onBack: () -> Unit,
    onRecipeClick: (Long) -> Unit,
    onEditRecipeClick: (Long) -> Unit,
    onAddRecipeClick: () -> Unit,
    onAddRecipeFromPhoto: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val selectionMode = selectedIds.isNotEmpty()
    var showFilters by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showViewModeMenu by remember { mutableStateOf(false) }
    var recipeToDelete by remember { mutableStateOf<RecipeSummary?>(null) }
    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }
    var showPhotoSourceSheet by remember { mutableStateOf(false) }
    var pendingCameraPath by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val photoSheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val importRecipeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.importRecipeFile(context, uri) { message ->
                scope.launch { snackbarHostState.showSnackbar(message) }
            }
        }
    }
    val cameraCaptureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pendingCameraPath?.let { onAddRecipeFromPhoto(it) }
        pendingCameraPath = null
    }
    val galleryPickerForPhotoImport = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onAddRecipeFromPhoto(uri.toString())
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size} seleccionadas") },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancelar selección")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.exportSelected(context) }) {
                            Icon(Icons.Filled.FileDownload, contentDescription = "Exportar seleccionadas")
                        }
                        IconButton(onClick = { showDeleteSelectedConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Borrar seleccionadas")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(uiState.bookName) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Volver") }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showViewModeMenu = true }) {
                                Icon(viewModeIcon(viewMode), contentDescription = "Vista: ${viewMode.label}")
                            }
                            DropdownMenu(expanded = showViewModeMenu, onDismissRequest = { showViewModeMenu = false }) {
                                RecipeListViewMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.label) },
                                        leadingIcon = { Icon(viewModeIcon(mode), contentDescription = null) },
                                        onClick = { showViewModeMenu = false; viewModel.setViewMode(mode) }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones") }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Importar receta") },
                                leadingIcon = { Icon(Icons.Filled.UploadFile, null) },
                                onClick = { showMenu = false; importRecipeLauncher.launch(BACKUP_MIME_TYPES) }
                            )
                            DropdownMenuItem(
                                text = { Text("Añadir con foto") },
                                leadingIcon = { Icon(Icons.Filled.AddAPhoto, null) },
                                onClick = { showMenu = false; showPhotoSourceSheet = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Exportar este libro") },
                                onClick = { showMenu = false; viewModel.exportBook(context) }
                            )
                            DropdownMenuItem(
                                text = { Text("Exportar este libro (PDF)") },
                                onClick = { showMenu = false; viewModel.exportBookAsPdf(context) }
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                ExtendedFloatingActionButton(onClick = onAddRecipeClick, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Nueva receta") })
            }
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
            } else if (viewMode == RecipeListViewMode.GRID) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.groups.forEach { group ->
                        item(key = "header_${group.categoryName}", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "${group.categoryName} (${group.recipes.size})",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        items(group.recipes, key = { it.id }) { recipe ->
                            RecipeGridCard(
                                recipe = recipe,
                                selectionMode = selectionMode,
                                isSelected = recipe.id in selectedIds,
                                onClick = {
                                    if (selectionMode) viewModel.toggleSelection(recipe.id) else onRecipeClick(recipe.id)
                                },
                                onLongClick = { viewModel.startSelection(recipe.id) },
                                onToggleFavorite = { viewModel.toggleFavorite(recipe.id, recipe.isFavorite) }
                            )
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { Spacer(modifier = Modifier.padding(40.dp)) }
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
                                compact = viewMode == RecipeListViewMode.COMPACT,
                                selectionMode = selectionMode,
                                isSelected = recipe.id in selectedIds,
                                onClick = {
                                    if (selectionMode) viewModel.toggleSelection(recipe.id) else onRecipeClick(recipe.id)
                                },
                                onLongClick = { viewModel.startSelection(recipe.id) },
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

    if (showPhotoSourceSheet) {
        ModalBottomSheet(onDismissRequest = { showPhotoSourceSheet = false }, sheetState = photoSheetState) {
            PhotoSourceSheet(
                title = "Añadir receta con foto",
                onCameraClick = {
                    showPhotoSourceSheet = false
                    val (contentUri, filePath) = PhotoStorage.createCaptureTarget(context)
                    pendingCameraPath = filePath
                    cameraCaptureLauncher.launch(contentUri)
                },
                onGalleryClick = {
                    showPhotoSourceSheet = false
                    galleryPickerForPhotoImport.launch("image/*")
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

    if (showDeleteSelectedConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedConfirm = false },
            title = { Text("Eliminar recetas") },
            text = { Text("¿Seguro que quieres eliminar ${selectedIds.size} recetas? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelected()
                    showDeleteSelectedConfirm = false
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}

private fun viewModeIcon(mode: RecipeListViewMode): ImageVector = when (mode) {
    RecipeListViewMode.COMPACT -> Icons.Filled.ViewHeadline
    RecipeListViewMode.NORMAL -> Icons.Filled.ViewAgenda
    RecipeListViewMode.GRID -> Icons.Filled.GridView
}
