package org.calamares.miga.ui.list

import androidx.compose.material.icons.filled.AutoAwesome
import org.calamares.miga.ui.components.ResultsHeader
import org.calamares.miga.ui.components.ActiveFilterChips
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
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
import androidx.compose.runtime.LaunchedEffect
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
import org.calamares.miga.ui.components.ExportFormatDialog
import org.calamares.miga.ui.components.FilterSheetContent
import org.calamares.miga.ui.components.ImportRecipesDialog
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
    onAddRecipeFromUrl: (String) -> Unit,
    /** Improve the selected recipes with AI, all together (see BulkPolishScreen). */
    onPolishRecipes: (List<Long>) -> Unit = {}
) {
    val aiEnabled = rememberAiEnabled()
    val uiState by viewModel.uiState.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val collapsedCategories by viewModel.collapsedCategories.collectAsState()
    // While searching or filtering every category is shown open, so no result is hidden.
    val collapsingEnabled = filter.query.isBlank() && !filter.isActive
    val selectedIds by viewModel.selectedIds.collectAsState()
    val selectionMode = selectedIds.isNotEmpty()
    var showFilters by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showViewModeMenu by remember { mutableStateOf(false) }
    var recipeToDelete by remember { mutableStateOf<RecipeSummary?>(null) }
    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }
    /** Export asked for the selected recipes (true) or the whole book (false); null when not asked. */
    var exportSelectionOnly by remember { mutableStateOf<Boolean?>(null) }
    val pendingCollection by viewModel.pendingCollection.collectAsState()
    // The search field and filters show only when asked for with the search icon (or while a
    // search or filter is applied), so the book opens straight onto its recipes.
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val searchVisible = searchOpen || filter.query.isNotBlank() || filter.isActive
    val searchFocus = remember { FocusRequester() }
    fun closeSearch() {
        viewModel.updateQuery("")
        viewModel.clearFilters()
        searchOpen = false
    }
    BackHandler(enabled = searchVisible && !selectionMode) { closeSearch() }
    val targetBooks by viewModel.targetBooks.collectAsState()
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
                            Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.cancel_selection))
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::selectAll) {
                            Icon(Icons.Filled.SelectAll, contentDescription = L10n.str(R.string.select_all))
                        }
                        IconButton(onClick = { showBulkEditSheet = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = L10n.str(R.string.bulk_edit))
                        }
                        if (aiEnabled) {
                            IconButton(onClick = {
                                onPolishRecipes(selectedIds.toList())
                                viewModel.clearSelection()
                            }) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = L10n.str(R.string.bulk_polish_action))
                            }
                        }
                        IconButton(onClick = { exportSelectionOnly = true }) {
                            Icon(Icons.Filled.FileDownload, contentDescription = L10n.str(R.string.export_selected))
                        }
                        IconButton(onClick = { showDeleteSelectedConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = L10n.str(R.string.delete_selected))
                        }
                    }
                )
            } else {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    title = { Text(uiState.bookName) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = L10n.str(R.string.back)) }
                    },
                    actions = {
                        if (!searchVisible) {
                            IconButton(onClick = { searchOpen = true }) {
                                Icon(Icons.Filled.Search, contentDescription = L10n.str(R.string.search))
                            }
                        }
                        Box {
                            IconButton(onClick = { showViewModeMenu = true }) {
                                Icon(viewModeIcon(viewMode), contentDescription = L10n.str(R.string.view_x, viewMode.label))
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
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = L10n.str(R.string.more_options)) }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            if (uiState.groups.size > 1) {
                                DropdownMenuItem(
                                    text = { Text(L10n.str(R.string.expand_all)) },
                                    leadingIcon = { Icon(Icons.Filled.UnfoldMore, contentDescription = null) },
                                    onClick = { showMenu = false; viewModel.expandAllCategories() }
                                )
                                DropdownMenuItem(
                                    text = { Text(L10n.str(R.string.collapse_all)) },
                                    leadingIcon = { Icon(Icons.Filled.UnfoldLess, contentDescription = null) },
                                    onClick = { showMenu = false; viewModel.collapseAllCategories() }
                                )
                                HorizontalDivider()
                            }
                            DropdownMenuItem(
                                text = { Text(L10n.str(if (uiState.isPinned) R.string.unpin_this_book else R.string.pin_this_book)) },
                                leadingIcon = { Icon(if (uiState.isPinned) Icons.Outlined.PushPin else Icons.Filled.PushPin, contentDescription = null) },
                                onClick = { showMenu = false; viewModel.togglePinned() }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(L10n.str(R.string.export_book_ellipsis)) },
                                leadingIcon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                                onClick = { showMenu = false; exportSelectionOnly = false }
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode && !uiState.isPackBook) {
                ExtendedFloatingActionButton(onClick = { showNewRecipeSheet = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(L10n.str(R.string.new_recipe)) })
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedVisibility(visible = searchVisible, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = filter.query,
                        onValueChange = viewModel::updateQuery,
                        modifier = Modifier.weight(1f).focusRequester(searchFocus),
                        placeholder = { Text(L10n.str(R.string.search_recipes_ingredients)) },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        singleLine = true
                    )
                    IconButton(onClick = { showFilters = true }) {
                        BadgedBox(badge = { if (filter.isActive) Badge { Text(filter.activeConditions().toString()) } }) {
                            Icon(Icons.Filled.FilterList, contentDescription = L10n.str(R.string.filters))
                        }
                    }
                    IconButton(onClick = { closeSearch() }) {
                        Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.close_search))
                    }
                }
                // Opening the search puts the cursor in the field, with the keyboard.
                LaunchedEffect(searchOpen) {
                    if (searchOpen && filter.query.isEmpty()) runCatching { searchFocus.requestFocus() }
                }
            }

            if (searchVisible) {
                // Same as Search and Favourites: what is applied, how many and in what order.
                ActiveFilterChips(filter = filter, onChange = viewModel::applyFilter, onClearAll = { viewModel.clearFilters() })
                val shownCount = uiState.groups.sumOf { it.recipes.size }
                if (shownCount > 0) {
                    ResultsHeader(count = shownCount, sort = filter.sortOption, onSort = { viewModel.applyFilter(filter.copy(sortOption = it)) })
                }
            }

            if (uiState.groups.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (uiState.totalCount == 0) {
                            L10n.str(R.string.dont_have_recipes_yet_ntap)
                        } else {
                            L10n.str(R.string.no_recipes_match_search_filters)
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
                        val collapsed = collapsingEnabled && group.categoryName in collapsedCategories
                        item(key = "header_${group.categoryName}", span = { GridItemSpan(maxLineSpan) }) {
                            CategoryHeader(
                                name = displayCategoryName(group.categoryName),
                                count = group.recipes.size,
                                collapsed = collapsed,
                                enabled = collapsingEnabled,
                                onToggle = { viewModel.toggleCategory(group.categoryName) }
                            )
                        }
                        if (!collapsed) items(group.recipes, key = { it.id }) { recipe ->
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
                        val collapsed = collapsingEnabled && group.categoryName in collapsedCategories
                        item(key = "header_${group.categoryName}") {
                            CategoryHeader(
                                name = displayCategoryName(group.categoryName),
                                count = group.recipes.size,
                                collapsed = collapsed,
                                enabled = collapsingEnabled,
                                onToggle = { viewModel.toggleCategory(group.categoryName) }
                            )
                        }
                        if (!collapsed) items(group.recipes, key = { it.id }) { recipe ->
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

    exportSelectionOnly?.let { selectionOnly ->
        ExportFormatDialog(
            title = if (selectionOnly) L10n.str(R.string.file_name_selected_recipes) else uiState.bookName,
            onFormat = { format ->
                exportSelectionOnly = null
                if (selectionOnly) viewModel.exportSelected(context, format) else viewModel.exportBook(context, format)
            },
            onDismiss = { exportSelectionOnly = null }
        )
    }

    pendingCollection?.let { collection ->
        ImportRecipesDialog(
            collection = collection,
            // This book first and chosen by default: the file was picked from it.
            books = listOf(viewModel.bookId to uiState.bookName) + targetBooks.map { it.id to it.name },
            initialTarget = viewModel.bookId,
            onConfirm = { target ->
                viewModel.confirmCollectionImport(context, target) { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
            },
            onDismiss = { viewModel.dismissCollectionImport() }
        )
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
            title = { Text(L10n.str(R.string.import_recipe_url)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        L10n.str(R.string.paste_link_recipe_website_ai),
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
                ) { Text(L10n.str(R.string.import_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showUrlImportDialog = false }) { Text(L10n.str(R.string.cancel)) }
            }
        )
    }

    if (showPhotoSourceSheet) {
        ModalBottomSheet(onDismissRequest = { showPhotoSourceSheet = false }, sheetState = photoSheetState) {
            PhotoSourceSheet(
                title = L10n.str(R.string.add_recipe_photo),
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
            title = { Text(L10n.str(R.string.another_page)) },
            text = { Text(L10n.str(R.string.does_recipe_continue_another_photo)) },
            confirmButton = {
                TextButton(onClick = {
                    showAddAnotherPageDialog = false
                    val (contentUri, filePath) = PhotoStorage.createCaptureTarget(context)
                    pendingCameraPath = filePath
                    cameraCaptureLauncher.launch(contentUri)
                }) { Text(L10n.str(R.string.add_another_page)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showAddAnotherPageDialog = false
                        capturedPageUris.clear()
                    }) { Text(L10n.str(R.string.cancel)) }
                    TextButton(onClick = {
                        showAddAnotherPageDialog = false
                        onAddRecipeFromPhoto(capturedPageUris.toList())
                        capturedPageUris.clear()
                    }) { Text(L10n.str(R.string.continue_action)) }
                }
            }
        )
    }

    recipeToDelete?.let { recipe ->
        AlertDialog(
            onDismissRequest = { recipeToDelete = null },
            title = { Text(L10n.str(R.string.delete_recipe)) },
            text = { Text(L10n.str(R.string.sure_want_delete_x_cant, recipe.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecipe(recipe.id)
                    recipeToDelete = null
                }) { Text(L10n.str(R.string.delete_2), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { recipeToDelete = null }) { Text(L10n.str(R.string.cancel)) }
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
            title = { Text(L10n.str(R.string.delete_recipes)) },
            text = { Text(L10n.str(R.string.sure_want_delete_x_recipes, selectedIds.size)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelected()
                    showDeleteSelectedConfirm = false
                }) { Text(L10n.str(R.string.delete_2), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedConfirm = false }) { Text(L10n.str(R.string.cancel)) }
            }
        )
    }
}

private fun viewModeIcon(mode: RecipeListViewMode): ImageVector = when (mode) {
    RecipeListViewMode.COMPACT -> Icons.Filled.ViewHeadline
    RecipeListViewMode.NORMAL -> Icons.Filled.ViewAgenda
    RecipeListViewMode.GRID -> Icons.Filled.GridView
}

/**
 * Category title in a book's recipe list. Tapping it collapses or expands the category; while
 * searching or filtering ([enabled] false) every category stays open and the arrow is hidden.
 */
@Composable
private fun CategoryHeader(name: String, count: Int, collapsed: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (collapsed) -90f else 0f, label = "categoryArrow")
    val state = L10n.str(if (collapsed) R.string.collapsed else R.string.expanded)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onToggle)
            .semantics { if (enabled) stateDescription = state }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Name and count share all the free width, so the arrow always sits at the end of the row.
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                text = "  $count",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (enabled) {
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(rotation)
            )
        }
    }
}
