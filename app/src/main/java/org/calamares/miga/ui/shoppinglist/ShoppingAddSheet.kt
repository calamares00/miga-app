package org.calamares.miga.ui.shoppinglist

import org.calamares.miga.data.model.displayCategoryName
import org.calamares.miga.ui.components.ButtonContent
import org.calamares.miga.L10n
import org.calamares.miga.R
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import org.calamares.miga.data.model.AdditiveRisk
import org.calamares.miga.data.model.DEFAULT_SHOPPING_LIST_UID
import org.calamares.miga.data.model.IngredientCatalogItem
import org.calamares.miga.data.model.ParsedShoppingEntry
import org.calamares.miga.data.model.PredefinedShoppingLists
import org.calamares.miga.data.model.ProductInfo
import org.calamares.miga.data.model.ProductLabels
import org.calamares.miga.data.model.ProductScore
import org.calamares.miga.data.model.ProductScoring
import org.calamares.miga.data.model.ShoppingAisleOrder
import org.calamares.miga.data.model.ShoppingEntryParser
import org.calamares.miga.data.model.ShoppingListGroup
import org.calamares.miga.data.model.ShoppingListInfo
import org.calamares.miga.data.model.ShoppingListItem
import org.calamares.miga.data.model.ShoppingStore
import org.calamares.miga.data.model.ShoppingSuggestions
import org.calamares.miga.data.model.ShoppingTemplate
import org.calamares.miga.data.model.ShoppingVisuals
import org.calamares.miga.data.model.TemplateItem
import org.calamares.miga.data.model.UNCATEGORIZED_INGREDIENT_LABEL
import org.calamares.miga.data.model.formatIngredientText
import org.calamares.miga.data.remote.ScannedProduct
import org.calamares.miga.data.share.ShoppingIntentEvent
import org.calamares.miga.data.share.ShoppingIntents
import org.calamares.miga.data.share.ShoppingListShareCodec
import org.calamares.miga.data.voice.DictationResult
import org.calamares.miga.data.voice.SpeechDictation
import org.calamares.miga.ui.components.rememberDictationLanguage
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tabs of the "Add" sheet: everything other than typing, dictating or scanning lives here instead
 * of in menus.
 */
internal enum class AddTab(val title: String) {
    CATALOG(L10n.str(R.string.catalogue)),
    PRODUCTS(L10n.str(R.string.products)),
    TEMPLATES(L10n.str(R.string.templates)),
    BULK(L10n.str(R.string.bulk))
}

/**
 * Single sheet for adding items: tappable catalogue, Open Food Facts search (with "save to
 * template"), templates (predefined and the user's) and pasting several items at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShoppingAddSheet(
    viewModel: ShoppingListViewModel,
    initialTab: AddTab,
    initialQuery: String,
    pendingNames: Set<String>,
    listIsEmpty: Boolean,
    onMessage: (String) -> Unit,
    onSaveToTemplate: (TemplateItem) -> Unit,
    onEditTemplate: (Long) -> Unit,
    onNewTemplate: () -> Unit,
    onSaveListAsTemplate: () -> Unit,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableStateOf(initialTab) }
    val catalog by viewModel.catalog.collectAsState()
    val templates by viewModel.templates.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxHeight(0.92f)) {
            TabRow(selectedTabIndex = tab.ordinal, containerColor = Color.Transparent) {
                AddTab.entries.forEach { entry ->
                    Tab(selected = tab == entry, onClick = { tab = entry }, text = { Text(entry.title, maxLines = 1) })
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
                when (tab) {
                    AddTab.CATALOG -> CatalogContent(
                        catalog = catalog,
                        pendingNames = pendingNames,
                        onToggle = { viewModel.toggleCatalogItem(it) }
                    )
                    AddTab.PRODUCTS -> Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        ProductSearchContent(
                            viewModel = viewModel,
                            initialQuery = initialQuery,
                            addDescription = L10n.str(R.string.add_list),
                            onAdd = { product ->
                                viewModel.addSearchedProduct(product)
                                onMessage(L10n.str(R.string.added_x, product.name))
                            },
                            onSaveToTemplate = { product -> onSaveToTemplate(product.toTemplateItem()) }
                        )
                    }
                    AddTab.TEMPLATES -> TemplatesContent(
                        templates = templates,
                        listIsEmpty = listIsEmpty,
                        onApply = { template ->
                            viewModel.applyTemplate(template)
                            onMessage(L10n.str(R.string.added_x_items_x, template.items.size, template.name))
                        },
                        onEdit = onEditTemplate,
                        onNew = onNewTemplate,
                        onSaveList = onSaveListAsTemplate
                    )
                    AddTab.BULK -> BulkContent(
                        onAdd = { text ->
                            val count = viewModel.addEntries(text)
                            onMessage(if (count == 1) L10n.str(R.string.added_1_item) else L10n.str(R.string.added_x_items, count))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogContent(
    catalog: List<IngredientCatalogItem>,
    pendingNames: Set<String>,
    onToggle: (String) -> Unit
) {
    val byCategory = remember(catalog) {
        catalog.groupBy { it.categoryName ?: UNCATEGORIZED_INGREDIENT_LABEL }
            .mapValues { (_, items) -> items.map { it.name }.sortedBy { it.lowercase() } }
    }
    val categories = remember(byCategory) {
        byCategory.keys.sortedWith(compareBy({ it == UNCATEGORIZED_INGREDIENT_LABEL || it == "Otros" || it == "Other" }, { it.lowercase() }))
    }
    var selected by remember { mutableStateOf<String?>(null) }
    val current = selected?.takeIf { it in byCategory } ?: categories.firstOrNull()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            L10n.str(R.string.tap_add_remove_list),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { category ->
                FilterChip(
                    selected = category == current,
                    onClick = { selected = category },
                    label = { Text("${ShoppingVisuals.categoryStyle(category).emoji} ${displayCategoryName(category)}") }
                )
            }
        }
        if (current == null) {
            Text(
                L10n.str(R.string.ingredient_catalogue_empty),
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val style = ShoppingVisuals.categoryStyle(current)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(84.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                gridItems(byCategory.getValue(current), key = { it }) { name ->
                    val inList = name.trim().lowercase() in pendingNames
                    val accent = Color(style.argb)
                    Column(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onToggle(name) }
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = if (inList) 0.35f else 0.14f))
                                .then(if (inList) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(ShoppingVisuals.itemEmoji(name, current), fontSize = 26.sp)
                        }
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Open Food Facts search by name. Tapping a result opens its product sheet; the (+) button adds it
 * ([onAdd]) and the bookmark saves it to a template ([onSaveToTemplate], when offered).
 */
@Composable
internal fun ProductSearchContent(
    viewModel: ShoppingListViewModel,
    initialQuery: String,
    addDescription: String,
    onAdd: (ScannedProduct) -> Unit,
    onSaveToTemplate: ((ScannedProduct) -> Unit)?
) {
    val state by viewModel.searchState.collectAsState()
    var query by remember { mutableStateOf(initialQuery) }
    var spainOnly by remember { mutableStateOf(true) }
    var addedBarcodes by remember { mutableStateOf(setOf<String>()) }
    var detail by remember { mutableStateOf<ScannedProduct?>(null) }

    fun add(product: ScannedProduct) {
        onAdd(product)
        addedBarcodes = addedBarcodes + product.barcode
    }

    LaunchedEffect(Unit) {
        if (initialQuery.trim().length >= 2) viewModel.searchProducts(initialQuery, spainOnly)
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(L10n.str(R.string.search_open_food_facts_milk)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.searchProducts(query, spainOnly) })
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = spainOnly,
                onClick = { spainOnly = !spainOnly; if (query.trim().length >= 2) viewModel.searchProducts(query, spainOnly) },
                label = { Text(L10n.str(R.string.spain_only)) }
            )
            if (query.trim().length >= 2) {
                AssistChip(onClick = { viewModel.searchProducts(query, spainOnly) }, label = { Text(L10n.str(R.string.search)) })
            }
        }
        when (val current = state) {
            ProductSearchState.Idle -> Text(
                if (onSaveToTemplate != null) {
                    L10n.str(R.string.type_product_tap_search_adds)
                } else {
                    L10n.str(R.string.type_product_tap_search_most)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ProductSearchState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text(L10n.str(R.string.searching))
            }
            is ProductSearchState.Error -> Text(current.reason, color = MaterialTheme.colorScheme.error)
            is ProductSearchState.Results -> if (current.products.isEmpty()) {
                Text(
                    if (spainOnly) L10n.str(R.string.no_results_try_other_name_or_spain) else L10n.str(R.string.no_results_try_other_name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(current.products, key = { it.barcode }) { product ->
                        SearchResultRow(
                            product = product,
                            added = product.barcode in addedBarcodes,
                            addDescription = addDescription,
                            onOpen = { detail = product },
                            onAdd = { add(product) },
                            onSaveToTemplate = onSaveToTemplate?.let { save -> { save(product) } }
                        )
                    }
                }
            }
        }
    }

    detail?.let { product ->
        ProductDetailSheet(
            name = product.name,
            imageUrl = product.imageUrl,
            info = product.info,
            onDismiss = { detail = null },
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { add(product); detail = null }, enabled = product.barcode !in addedBarcodes) {
                        ButtonContent(Icons.Filled.Add, addDescription)
                    }
                    if (onSaveToTemplate != null) {
                        OutlinedButton(onClick = { onSaveToTemplate(product) }) {
                            ButtonContent(Icons.Filled.BookmarkAdd, L10n.str(R.string.template_short))
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun TemplatesContent(
    templates: List<ShoppingTemplate>,
    listIsEmpty: Boolean,
    onApply: (ShoppingTemplate) -> Unit,
    onEdit: (Long) -> Unit,
    onNew: () -> Unit,
    onSaveList: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
    ) {
        item(key = "actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                FilledTonalButton(onClick = onNew) {
                    ButtonContent(Icons.Filled.Add, L10n.str(R.string.new_short))
                }
                OutlinedButton(onClick = onSaveList, enabled = !listIsEmpty) { Text(L10n.str(R.string.save_list)) }
            }
        }
        item(key = "mine_header") { SectionLabel(L10n.str(R.string.my_templates)) }
        if (templates.isEmpty()) {
            item(key = "mine_empty") {
                Text(
                    L10n.str(R.string.dont_have_templates_yet_create),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
        items(templates, key = { "m${it.id}" }) { template ->
            TemplateRow(template = template, onApply = { onApply(template) }, onClick = { onEdit(template.id) })
        }
        item(key = "predefined_header") { SectionLabel(L10n.str(R.string.ready_made), top = 16.dp) }
        items(PredefinedShoppingLists.ALL, key = { "p${it.id}" }) { template ->
            TemplateRow(template = template, onApply = { onApply(template) }, onClick = null)
        }
    }
}

@Composable
internal fun SectionLabel(text: String, top: androidx.compose.ui.unit.Dp = 0.dp) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = top, bottom = 4.dp)
    )
}

@Composable
private fun BulkContent(onAdd: (String) -> Unit) {
    var bulkText by remember { mutableStateOf("") }
    val parsedCount = remember(bulkText) { ShoppingEntryParser.parse(bulkText).size }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            L10n.str(R.string.type_paste_one_item_per),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = bulkText,
            onValueChange = { bulkText = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 5
        )
        Button(
            onClick = { onAdd(bulkText); bulkText = "" },
            enabled = parsedCount > 0,
            modifier = Modifier.align(Alignment.End)
        ) { Text(if (parsedCount > 0) L10n.str(R.string.add_x, parsedCount) else L10n.str(R.string.add)) }
    }
}
