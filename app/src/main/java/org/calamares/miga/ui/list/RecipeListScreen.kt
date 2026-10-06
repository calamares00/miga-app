package org.calamares.miga.ui.list

import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SelectAll
import org.calamares.miga.data.model.displayCategoryName
import org.calamares.miga.L10n
import org.calamares.miga.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.calamares.miga.data.local.PhotoStorage
import org.calamares.miga.data.model.RecipeListViewMode
import org.calamares.miga.data.model.RecipeSummary
import org.calamares.miga.ui.common.BACKUP_MIME_TYPES
import org.calamares.miga.ui.components.FilterSheetContent
import org.calamares.miga.ui.components.NewRecipeSourceSheet
import org.calamares.miga.ui.components.rememberAiEnabled
import org.calamares.miga.ui.components.PhotoSourceSheet
import org.calamares.miga.ui.components.RecipeCard
import org.calamares.miga.ui.components.RecipeGridCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    viewModel: RecipeListViewModel,
    onBack: () -> Unit,
    onRecipeClick: (Long) -> Unit,
    onEditRecipeClick: (Long) -> Unit,
    onAddRecipeClick: () -> Unit,
    onAddRecipeFromPhoto: (List<String>) -> Unit,
    onAddRecipesBulk: (List<String>) -> Unit,
    onSearchDishClick: () -> Unit,
    onAddRecipeFromUrl: (String) -> Unit
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
    var showBulkEditSheet by remember { mutableStateOf(false) }
    var showPhotoSourceSheet by remember { mutableStateOf(false) }
    var showNewRecipeSheet by remember { mutableStateOf(false) }
    var showUrlImportDialog by remember { mutableStateOf(false) }
    var pendingCameraPath by remember { mutableStateOf<String?>(null) }
    val capturedPageUris = remember { mutableStateListOf<String>() }
    var showAddAnotherPageDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val photoSheetState = rememberModalBottomSheetState()
    val newRecipeSheetState = rememberModalBottomSheetState()
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
        if (success) pendingCameraPath?.let { capturedPageUris.add(it); showAddAnotherPageDialog = true }
        pendingCameraPath = null
    }
    val galleryPickerForPhotoImport = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris -> if (uris.isNotEmpty()) onAddRecipeFromPhoto(uris.map { it.toString() }) }
    val bulkGalleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris -> if (uris.isNotEmpty()) onAddRecipesBulk(uris.map { it.toString() }) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    title = { Text(L10n.str(R.string.n_selected, selectedIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.cancelar_seleccion))
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::selectAll) {
                            Icon(Icons.Filled.SelectAll, contentDescription = L10n.str(R.string.select_all))
                        }
                        IconButton(onClick = { showBulkEditSheet = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = L10n.str(R.string.bulk_edit))
                        }
                        IconButton(onClick = { viewModel.exportSelected(context) }) {
                            Icon(Icons.Filled.FileDownload, contentDescription = L10n.str(R.string.exportar_seleccionadas))
                        }
                        IconButton(onClick = { showDeleteSelectedConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = L10n.str(R.string.borrar_seleccionadas))
                        }
                    }
                )
            } else {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    title = { Text(uiState.bookName) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = L10n.str(R.string.volver)) }
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
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = L10n.str(R.string.mas_opciones)) }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(L10n.str(R.string.exportar_este_libro)) },
                                onClick = { showMenu = false; viewModel.exportBook(context) }
                            )
                            DropdownMenuItem(
                                text = { Text(L10n.str(R.string.exportar_este_libro_pdf)) },
                                onClick = { showMenu = false; viewModel.exportBookAsPdf(context) }
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode && !uiState.isPackBook) {
                ExtendedFloatingActionButton(onClick = { showNewRecipeSheet = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(L10n.str(R.string.nueva_receta)) })
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
                    placeholder = { Text(L10n.str(R.string.buscar_recetas_ingredientes)) },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true
                )
                IconButton(onClick = { showFilters = true }) {
                    BadgedBox(badge = { if (filter.isActive) Badge() }) {
                        Icon(Icons.Filled.FilterList, contentDescription = L10n.str(R.string.filtros))
                    }
                }
            }

            if (uiState.groups.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (uiState.totalCount == 0) {
                            L10n.str(R.string.aun_no_tienes_recetas_pulsa)
                        } else {
                            L10n.str(R.string.no_hay_recetas_coincidan_busqueda)
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
                                text = "${displayCategoryName(group.categoryName)} (${group.recipes.size})",
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
                                onLongClick = { if (!uiState.isPackBook) viewModel.startSelection(recipe.id) },
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
                                text = "${displayCategoryName(group.categoryName)} (${group.recipes.size})",
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
                                readOnly = uiState.isPackBook,
                                onClick = {
                                    if (selectionMode) viewModel.toggleSelection(recipe.id) else onRecipeClick(recipe.id)
                                },
                                onLongClick = { if (!uiState.isPackBook) viewModel.startSelection(recipe.id) },
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
                availableIngredients = uiState.availableIngredients,
                onApply = { newFilter -> viewModel.applyFilter(newFilter) },
                onClear = { viewModel.clearFilters() }
            )
        }
    }

    if (showNewRecipeSheet) {
        ModalBottomSheet(onDismissRequest = { showNewRecipeSheet = false }, sheetState = newRecipeSheetState) {
            NewRecipeSourceSheet(
                onManualClick = { showNewRecipeSheet = false; onAddRecipeClick() },
                onFileClick = { showNewRecipeSheet = false; importRecipeLauncher.launch(BACKUP_MIME_TYPES) },
                onPhotoClick = { showNewRecipeSheet = false; showPhotoSourceSheet = true },
                onBulkPhotoClick = {
                    showNewRecipeSheet = false
                    bulkGalleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onSearchDishClick = { showNewRecipeSheet = false; onSearchDishClick() },
                onUrlClick = { showNewRecipeSheet = false; showUrlImportDialog = true },
                aiEnabled = rememberAiEnabled()
            )
        }
    }

    if (showUrlImportDialog) {
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showUrlImportDialog = false },
            title = { Text(L10n.str(R.string.importar_receta_desde_url)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        L10n.str(R.string.pega_enlace_receta_cualquier_web),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("URL") },
                        placeholder = { Text("https://...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmedUrl = url.trim()
                        showUrlImportDialog = false
                        if (trimmedUrl.isNotBlank()) onAddRecipeFromUrl(trimmedUrl)
                    }
                ) { Text(L10n.str(R.string.importar)) }
            },
            dismissButton = {
                TextButton(onClick = { showUrlImportDialog = false }) { Text(L10n.str(R.string.cancelar)) }
            }
        )
    }

    if (showPhotoSourceSheet) {
        ModalBottomSheet(onDismissRequest = { showPhotoSourceSheet = false }, sheetState = photoSheetState) {
            PhotoSourceSheet(
                title = L10n.str(R.string.anadir_receta_foto),
                onCameraClick = {
                    showPhotoSourceSheet = false
                    val (contentUri, filePath) = PhotoStorage.createCaptureTarget(context)
                    pendingCameraPath = filePath
                    cameraCaptureLauncher.launch(contentUri)
                },
                onGalleryClick = {
                    showPhotoSourceSheet = false
                    galleryPickerForPhotoImport.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )
        }
    }

    if (showAddAnotherPageDialog) {
        AlertDialog(
            onDismissRequest = { showAddAnotherPageDialog = false },
            title = { Text(L10n.str(R.string.otra_pagina)) },
            text = { Text(L10n.str(R.string.receta_continua_otra_foto_puedes)) },
            confirmButton = {
                TextButton(onClick = {
                    showAddAnotherPageDialog = false
                    val (contentUri, filePath) = PhotoStorage.createCaptureTarget(context)
                    pendingCameraPath = filePath
                    cameraCaptureLauncher.launch(contentUri)
                }) { Text(L10n.str(R.string.anadir_otra_pagina)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showAddAnotherPageDialog = false
                        capturedPageUris.clear()
                    }) { Text(L10n.str(R.string.cancelar)) }
                    TextButton(onClick = {
                        showAddAnotherPageDialog = false
                        onAddRecipeFromPhoto(capturedPageUris.toList())
                        capturedPageUris.clear()
                    }) { Text(L10n.str(R.string.continuar)) }
                }
            }
        )
    }

    recipeToDelete?.let { recipe ->
        AlertDialog(
            onDismissRequest = { recipeToDelete = null },
            title = { Text(L10n.str(R.string.eliminar_receta)) },
            text = { Text(L10n.str(R.string.seguro_quieres_eliminar_x_esta, recipe.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecipe(recipe.id)
                    recipeToDelete = null
                }) { Text(L10n.str(R.string.eliminar), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { recipeToDelete = null }) { Text(L10n.str(R.string.cancelar)) }
            }
        )
    }

    if (showBulkEditSheet && selectionMode) {
        val categoryNames by viewModel.categoryNames.collectAsState()
        val targetBooks by viewModel.targetBooks.collectAsState()
        val aiRecalculation by viewModel.aiRecalculationAvailable.collectAsState()
        val showMessage: (String) -> Unit = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
        BulkEditSheet(
            count = selectedIds.size,
            categories = categoryNames,
            books = targetBooks,
            aiAvailable = aiRecalculation,
            onDismiss = { showBulkEditSheet = false },
            onCategory = { viewModel.bulkSetCategory(it, showMessage) },
            onDifficulty = { viewModel.bulkSetDifficulty(it, showMessage) },
            onServings = { servings, scale -> viewModel.bulkSetServings(servings, scale, showMessage) },
            onSource = { viewModel.bulkSetSource(it, showMessage) },
            onFavorite = { viewModel.bulkSetFavorite(it, showMessage) },
            onMove = { viewModel.bulkMoveTo(it, showMessage) },
            onCopy = { viewModel.bulkCopyTo(context, it, showMessage) },
            onShopping = { viewModel.bulkAddToShoppingList(showMessage) },
            onRecalculateAi = { viewModel.bulkRecalculateAi(showMessage) }
        )
    }

    if (showDeleteSelectedConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedConfirm = false },
            title = { Text(L10n.str(R.string.eliminar_recetas)) },
            text = { Text(L10n.str(R.string.seguro_quieres_eliminar_x_recetas, selectedIds.size)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelected()
                    showDeleteSelectedConfirm = false
                }) { Text(L10n.str(R.string.eliminar), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedConfirm = false }) { Text(L10n.str(R.string.cancelar)) }
            }
        )
    }
}

private fun viewModeIcon(mode: RecipeListViewMode): ImageVector = when (mode) {
    RecipeListViewMode.COMPACT -> Icons.Filled.ViewHeadline
    RecipeListViewMode.NORMAL -> Icons.Filled.ViewAgenda
    RecipeListViewMode.GRID -> Icons.Filled.GridView
}
