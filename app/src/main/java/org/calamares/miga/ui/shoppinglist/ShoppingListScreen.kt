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
import android.widget.Toast
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
import androidx.compose.foundation.layout.ime
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

private const val QR_SIZE_PX = 720
private const val CART_KEY = "__cart__"
private const val SHARED_SYNC_INTERVAL_MILLIS = 20_000L

/**
 * Shopping list. The top area only holds what is used daily (type, dictate, scan and an "Explore"
 * button that opens the catalogue, Open Food Facts, templates and bulk add in one sheet). The title
 * switches lists and the menu keeps share, stores, photos and clear.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(viewModel: ShoppingListViewModel) {
    val groups by viewModel.groups.collectAsState()
    val history by viewModel.history.collectAsState()
    val catalogNames by viewModel.catalogNames.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val imagesEnabled by viewModel.imagesEnabled.collectAsState()
    val stores by viewModel.stores.collectAsState()
    val selectedStore by viewModel.selectedStore.collectAsState()
    val categoryNames by viewModel.ingredientCategoryNames.collectAsState()
    val lists by viewModel.lists.collectAsState()
    val listCounts by viewModel.listCounts.collectAsState()
    val selectedListUid by viewModel.selectedListUid.collectAsState()
    val author by viewModel.author.collectAsState()
    val intentEvent by ShoppingIntents.event.collectAsState()
    val currentListName = lists.firstOrNull { it.uid == selectedListUid }?.name ?: L10n.str(R.string.shopping)

    var quickText by remember { mutableStateOf("") }
    var shopMode by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showStoreMenu by remember { mutableStateOf(false) }
    var addSheetTab by remember { mutableStateOf<AddTab?>(null) }
    var addSheetQuery by remember { mutableStateOf("") }
    var showLists by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var showStores by remember { mutableStateOf(false) }
    var editingStore by remember { mutableStateOf<ShoppingStore?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var showSaveTemplate by remember { mutableStateOf(false) }
    var showNewTemplate by remember { mutableStateOf(false) }
    var editingTemplateId by remember { mutableStateOf<Long?>(null) }
    var templateTarget by remember { mutableStateOf<TemplateItem?>(null) }
    var productDetail by remember { mutableStateOf<ShoppingListItem?>(null) }
    var qrShareEntries by remember { mutableStateOf<List<ParsedShoppingEntry>?>(null) }
    var scannedEntries by remember { mutableStateOf<List<ParsedShoppingEntry>?>(null) }
    var collapsed by remember { mutableStateOf(setOf<String>()) }
    val dictationLanguage = rememberDictationLanguage()
    val quickAddFocus = remember { FocusRequester() }

    val view = LocalView.current
    DisposableEffect(shopMode) {
        view.keepScreenOn = shopMode
        onDispose { view.keepScreenOn = false }
    }
    val pendingGroups = remember(groups, selectedStore) {
        val pending = groups.map { group -> ShoppingListGroup(group.categoryName, group.items.filter { !it.checked }) }.filter { it.items.isNotEmpty() }
        ShoppingAisleOrder.sort(pending, selectedStore?.aisleOrder.orEmpty())
    }
    val cartItems = remember(groups) { groups.flatMap { it.items }.filter { it.checked }.sortedBy { it.name.lowercase() } }
    val pendingCount = pendingGroups.sumOf { it.items.size }
    val totalCount = cartItems.size + pendingCount
    val pendingNames = remember(groups) {
        groups.flatMap { it.items }.filter { !it.checked }.map { it.name.trim().lowercase() }.toSet()
    }
    fun toggleCollapsed(key: String) {
        collapsed = if (key in collapsed) collapsed - key else collapsed + key
    }

    var listening by remember { mutableStateOf(false) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    /** An open sheet covers the snackbar, so a toast is used instead. */
    fun announce(message: String) {
        if (addSheetTab != null || editingTemplateId != null || productDetail != null) Toast.makeText(context, message, Toast.LENGTH_SHORT).show() else showMessage(message)
    }

    fun openAddSheet(tab: AddTab, query: String = "") {
        addSheetQuery = query
        addSheetTab = tab
    }

    DisposableEffect(Unit) { onDispose { recognizer?.destroy() } }

    /**
     * While the screen is visible, pull other people's changes every few seconds (the periodic
     * background sync runs every 15 minutes). Does nothing without a shared connection.
     */
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.syncSharedListsOnce(context)
                delay(SHARED_SYNC_INTERVAL_MILLIS)
            }
        }
    }

    // Notices about items other people add to the shared list.
    LaunchedEffect(Unit) {
        viewModel.remoteAdditions.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    // Text shared to Miga or the widget button (see ShoppingIntents).
    LaunchedEffect(intentEvent) {
        when (val event = intentEvent) {
            is ShoppingIntentEvent.SharedText -> {
                val entries = ShoppingEntryParser.parse(event.text)
                if (entries.isEmpty()) showMessage(L10n.str(R.string.no_items_were_recognised_shared)) else scannedEntries = entries
                ShoppingIntents.consume()
            }
            ShoppingIntentEvent.QuickAdd -> {
                shopMode = false
                delay(200) // gives the add field time to be composed again
                runCatching { quickAddFocus.requestFocus() }
                ShoppingIntents.consume() // last: consuming it earlier would cancel this coroutine (the key changes)
            }
            null -> Unit
        }
    }

    val typedEntry = remember(quickText) {
        if (quickText.isBlank() || quickText.any { it == ',' || it == ';' || it == '\n' }) null
        else ShoppingEntryParser.parse(quickText).firstOrNull()
    }
    val suggestions = remember(quickText, typedEntry, history, catalogNames) {
        if (typedEntry == null) emptyList() else ShoppingSuggestions.rank(typedEntry.name, history, catalogNames)
    }

    fun submitQuick() {
        if (viewModel.addEntries(quickText) > 0) quickText = ""
    }

    fun beginListening() {
        if (!SpeechDictation.isAvailable(context)) {
            showMessage(L10n.str(R.string.speech_recognition_isnt_available_device))
            return
        }
        recognizer?.destroy()
        listening = true
        recognizer = SpeechDictation.startListening(context, dictationLanguage) { result ->
            listening = false
            when (result) {
                is DictationResult.Success -> {
                    val count = viewModel.addEntries(result.text, splitOnY = true)
                    showMessage(if (count == 1) L10n.str(R.string.added_x, result.text) else L10n.str(R.string.added_x_items, count))
                }
                is DictationResult.Error -> showMessage(result.reason)
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginListening() else showMessage(L10n.str(R.string.cant_dictate_without_microphone_permission))
    }

    fun onMicClick() {
        if (listening) {
            recognizer?.stopListening()
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) beginListening() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val listQrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents != null) {
            val decoded = ShoppingListShareCodec.decode(contents)
            if (decoded == null) showMessage(L10n.str(R.string.qr_code_isnt_miga_list)) else scannedEntries = decoded
        }
    }

    val productScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents != null) viewModel.addScannedProduct(contents.trim()) { showMessage(it) }
    }

    fun launchProductScan() {
        productScanLauncher.launch(
            ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.PRODUCT_CODE_TYPES)
                setPrompt(L10n.str(R.string.point_product_barcode))
                setBeepEnabled(false)
                setOrientationLocked(false)
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.navigationBars).exclude(WindowInsets.ime),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Row(
                        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable { showLists = true }.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                if (selectedListUid == DEFAULT_SHOPPING_LIST_UID) L10n.str(R.string.shopping_list_2) else currentListName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (lists.size > 1) {
                                Text(
                                    if (selectedListUid == DEFAULT_SHOPPING_LIST_UID) currentListName else L10n.str(R.string.shopping_list_2),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = L10n.str(R.string.switch_list))
                    }
                },
                actions = {
                    IconButton(onClick = { shopMode = !shopMode }) {
                        Icon(
                            Icons.Filled.ShoppingBag,
                            contentDescription = if (shopMode) L10n.str(R.string.leave_shop_mode) else L10n.str(R.string.shop_mode),
                            tint = if (shopMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = L10n.str(R.string.more_options)) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(L10n.str(R.string.share_2)) },
                            leadingIcon = { Icon(Icons.Filled.Share, null) },
                            onClick = { showMenu = false; showShare = true }
                        )
                        DropdownMenuItem(
                            text = { Text(L10n.str(R.string.supermarkets_2)) },
                            leadingIcon = { Icon(Icons.Filled.Store, null) },
                            onClick = { showMenu = false; showStores = true }
                        )
                        DropdownMenuItem(
                            text = { Text(if (imagesEnabled) L10n.str(R.string.hide_photos) else L10n.str(R.string.show_photos)) },
                            leadingIcon = { Icon(if (imagesEnabled) Icons.Filled.HideImage else Icons.Filled.Photo, null) },
                            onClick = { showMenu = false; viewModel.setImagesEnabled(!imagesEnabled) }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(L10n.str(R.string.clear_list), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            enabled = totalCount > 0,
                            onClick = { showMenu = false; showClearAllConfirm = true }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!shopMode) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = quickText,
                        onValueChange = { quickText = it },
                        modifier = Modifier.weight(1f).focusRequester(quickAddFocus),
                        placeholder = { Text(L10n.str(R.string.add_2_kg_tomatoes_milk)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitQuick() }),
                        trailingIcon = {
                            if (quickText.isBlank()) {
                                Row {
                                    IconButton(onClick = { onMicClick() }) {
                                        Icon(
                                            imageVector = if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                                            contentDescription = if (listening) L10n.str(R.string.stop_listening) else L10n.str(R.string.dictate_items),
                                            tint = if (listening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { launchProductScan() }) {
                                        Icon(Icons.Filled.QrCodeScanner, contentDescription = L10n.str(R.string.scan_product_barcode))
                                    }
                                }
                            } else {
                                IconButton(onClick = { submitQuick() }) {
                                    Icon(Icons.Filled.AddCircle, contentDescription = L10n.str(R.string.add_list), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    FilledTonalIconButton(onClick = { openAddSheet(AddTab.CATALOG) }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Filled.GridView, contentDescription = L10n.str(R.string.explore_catalogue_open_food_facts))
                    }
                }

                if (quickText.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        suggestions.forEach { suggestion ->
                            SuggestionChip(
                                onClick = {
                                    viewModel.addSuggestion(suggestion, typedEntry)
                                    quickText = ""
                                },
                                label = { Text(suggestion.name) }
                            )
                        }
                        AssistChip(
                            onClick = { openAddSheet(AddTab.PRODUCTS, typedEntry?.name ?: quickText.trim()) },
                            label = { Text(L10n.str(R.string.search_product)) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }

                if (lists.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        lists.forEach { list ->
                            val pending = listCounts[list.uid] ?: 0
                            FilterChip(
                                selected = list.uid == selectedListUid,
                                onClick = { viewModel.selectList(list.uid) },
                                label = { Text(if (pending > 0) "${list.name} · $pending" else list.name) }
                            )
                        }
                    }
                }
            }

            if (totalCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (shopMode) L10n.str(R.string.swipe_tick_x_x, cartItems.size, totalCount) else L10n.str(R.string.x_buy_x_trolley, pendingCount, cartItems.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        TextButton(onClick = { if (stores.isEmpty()) showStores = true else showStoreMenu = true }) {
                            selectedStore?.let { store ->
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(store.argb)))
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(selectedStore?.name ?: L10n.str(R.string.sort_shop), style = MaterialTheme.typography.labelLarge)
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = showStoreMenu, onDismissRequest = { showStoreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(L10n.str(R.string.no_shop_category)) },
                                onClick = { showStoreMenu = false; viewModel.selectStore(0L) },
                                trailingIcon = { if (selectedStore == null) Icon(Icons.Filled.Check, null) }
                            )
                            stores.forEach { store ->
                                DropdownMenuItem(
                                    text = { Text(store.name) },
                                    leadingIcon = { Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color(store.argb))) },
                                    trailingIcon = { if (store.id == selectedStore?.id) Icon(Icons.Filled.Check, null) },
                                    onClick = { showStoreMenu = false; viewModel.selectStore(store.id) }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(L10n.str(R.string.manage_supermarkets)) },
                                leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                onClick = { showStoreMenu = false; showStores = true }
                            )
                        }
                    }
                }
                LinearProgressIndicator(
                    progress = { cartItems.size.toFloat() / totalCount },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }

            if (totalCount == 0) {
                EmptyShoppingList(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    onCatalog = { openAddSheet(AddTab.CATALOG) },
                    onTemplates = { openAddSheet(AddTab.TEMPLATES) },
                    onSearch = { openAddSheet(AddTab.PRODUCTS) },
                    onScan = { launchProductScan() }
                )
            } else {
                fun rowFor(shoppingItem: ShoppingListItem): @Composable () -> Unit = {
                    ShoppingListRow(
                        item = shoppingItem,
                        shopMode = shopMode,
                        showImage = imagesEnabled,
                        note = authorNote(shoppingItem, author),
                        onOpenProduct = if (shoppingItem.productInfo != null) ({ productDetail = shoppingItem }) else null,
                        onCheckedChange = { viewModel.setChecked(shoppingItem.id, it) },
                        onDelete = {
                            viewModel.deleteItem(shoppingItem) {
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(L10n.str(R.string.item_removed), actionLabel = L10n.str(R.string.undo))
                                    if (result == SnackbarResult.ActionPerformed) viewModel.restoreItem(shoppingItem)
                                }
                            }
                        }
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    pendingGroups.forEach { group ->
                        item(key = "header_${group.categoryName}") {
                            CategoryHeader(
                                title = displayCategoryName(group.categoryName),
                                count = group.items.size,
                                style = ShoppingVisuals.categoryStyle(group.categoryName),
                                collapsed = group.categoryName in collapsed,
                                onToggle = { toggleCollapsed(group.categoryName) }
                            )
                        }
                        if (group.categoryName !in collapsed) {
                            items(group.items, key = { it.id }) { shoppingItem -> rowFor(shoppingItem)() }
                        }
                    }
                    if (cartItems.isNotEmpty()) {
                        item(key = "header_cart") {
                            CategoryHeader(
                                title = L10n.str(R.string.trolley),
                                count = cartItems.size,
                                style = org.calamares.miga.data.model.CategoryStyle("🛒", 0xFF7B8794),
                                collapsed = CART_KEY in collapsed,
                                onToggle = { toggleCollapsed(CART_KEY) },
                                trailing = { TextButton(onClick = { viewModel.clearChecked() }) { Text(L10n.str(R.string.remove)) } }
                            )
                        }
                        if (CART_KEY !in collapsed) {
                            items(cartItems, key = { it.id }) { shoppingItem -> rowFor(shoppingItem)() }
                        }
                    }
                    item { Spacer(modifier = Modifier.padding(40.dp)) }
                }
            }
        }
    }

    addSheetTab?.let { tab ->
        ShoppingAddSheet(
            viewModel = viewModel,
            initialTab = tab,
            initialQuery = addSheetQuery,
            pendingNames = pendingNames,
            listIsEmpty = totalCount == 0,
            onMessage = { announce(it) },
            onSaveToTemplate = { templateTarget = it },
            onEditTemplate = { id -> addSheetTab = null; viewModel.clearSearch(); editingTemplateId = id },
            onNewTemplate = { showNewTemplate = true },
            onSaveListAsTemplate = { showSaveTemplate = true },
            onDismiss = {
                addSheetTab = null
                viewModel.clearSearch()
            }
        )
    }

    editingTemplateId?.let { id ->
        val template = templates.firstOrNull { it.id == id }
        if (template != null) {
            TemplateEditorSheet(
                viewModel = viewModel,
                template = template,
                onMessage = { announce(it) },
                onDismiss = { editingTemplateId = null }
            )
        }
    }

    templateTarget?.let { item ->
        TemplatePickerDialog(
            item = item,
            templates = templates,
            onPick = { template ->
                viewModel.addToTemplate(template.id, item)
                templateTarget = null
                announce(L10n.str(R.string.saved_x, template.name))
            },
            onCreate = { name ->
                viewModel.createTemplate(name, item)
                templateTarget = null
                announce(L10n.str(R.string.template_x_created_x, name, item.name))
            },
            onDismiss = { templateTarget = null }
        )
    }

    if (showNewTemplate) {
        NameDialog(
            title = L10n.str(R.string.new_template),
            placeholder = L10n.str(R.string.e_g_weekly_shop),
            confirmLabel = L10n.str(R.string.create),
            supporting = L10n.str(R.string.then_add_open_food_facts),
            onConfirm = { name ->
                showNewTemplate = false
                viewModel.createTemplate(name) { id ->
                    addSheetTab = null
                    viewModel.clearSearch()
                    editingTemplateId = id
                }
            },
            onDismiss = { showNewTemplate = false }
        )
    }

    if (showSaveTemplate) {
        NameDialog(
            title = L10n.str(R.string.save_template),
            placeholder = L10n.str(R.string.e_g_weekly_shop),
            confirmLabel = L10n.str(R.string.save),
            supporting = L10n.str(R.string.x_items_list_their_photos, totalCount),
            onConfirm = { name ->
                viewModel.saveTemplate(name)
                showSaveTemplate = false
                announce("Plantilla \"$name\" guardada")
            },
            onDismiss = { showSaveTemplate = false }
        )
    }

    productDetail?.let { item ->
        val info = item.productInfo
        if (info != null) {
            ProductDetailSheet(
                name = item.name,
                imageUrl = item.imageUrl,
                info = info,
                onDismiss = { productDetail = null },
                actions = {
                    OutlinedButton(onClick = { templateTarget = item.toTemplateItem() }) {
                        ButtonContent(Icons.Filled.BookmarkAdd, L10n.str(R.string.save_template_2))
                    }
                }
            )
        }
    }

    if (showLists) {
        ShoppingListsSheet(
            lists = lists,
            listCounts = listCounts,
            selectedListUid = selectedListUid,
            author = author,
            onSelect = { viewModel.selectList(it); showLists = false },
            onCreate = { viewModel.createList(it); showLists = false },
            onRename = { uid, name -> viewModel.renameList(uid, name) },
            onDelete = { viewModel.deleteList(it) },
            onAuthorChange = { viewModel.setAuthor(it) },
            onDismiss = { showLists = false }
        )
    }

    if (showShare) {
        AlertDialog(
            onDismissRequest = { showShare = false },
            icon = { Icon(Icons.Filled.Share, contentDescription = null) },
            title = { Text(L10n.str(R.string.share)) },
            text = {
                Column {
                    ShareOption(Icons.Filled.Share, L10n.str(R.string.send_text), L10n.str(R.string.whatsapp_email_notes)) {
                        showShare = false
                        viewModel.share(context)
                    }
                    ShareOption(Icons.Filled.QrCode2, L10n.str(R.string.show_qr), L10n.str(R.string.send_another_phone_miga)) {
                        showShare = false
                        val pending = viewModel.pendingEntries()
                        if (pending.isEmpty()) showMessage(L10n.str(R.string.there_no_items_share)) else qrShareEntries = pending
                    }
                    ShareOption(Icons.Filled.QrCodeScanner, L10n.str(R.string.receive_via_qr), L10n.str(R.string.scan_qr_code_another_miga)) {
                        showShare = false
                        listQrLauncher.launch(
                            ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setPrompt(L10n.str(R.string.point_miga_list_qr_code))
                                setBeepEnabled(false)
                                setOrientationLocked(false)
                            }
                        )
                    }
                    Text(
                        L10n.str(R.string.edit_same_list_other_people),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showShare = false }) { Text(L10n.str(R.string.close)) } }
        )
    }

    if (showStores) {
        AlertDialog(
            onDismissRequest = { showStores = false },
            title = { Text(L10n.str(R.string.supermarkets)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        L10n.str(R.string.each_supermarket_keeps_own_aisle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    stores.forEach { store ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showStores = false; editingStore = store }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(Color(store.argb)))
                            Text(store.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 12.dp))
                            IconButton(onClick = { viewModel.deleteStore(store.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = L10n.str(R.string.delete_x, store.name))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showStores = false
                    editingStore = ShoppingStore(0L, "", ShoppingAisleOrder.PALETTE[0], emptyList())
                }) { Text(L10n.str(R.string.add_supermarket)) }
            },
            dismissButton = { TextButton(onClick = { showStores = false }) { Text(L10n.str(R.string.close)) } }
        )
    }

    editingStore?.let { store ->
        StoreEditorSheet(
            store = store,
            categoryNames = categoryNames,
            onSave = { saved ->
                viewModel.saveStore(saved, select = true)
                editingStore = null
            },
            onDismiss = { editingStore = null }
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text(L10n.str(R.string.clear_list)) },
            text = { Text(L10n.str(R.string.sure_want_delete_items_x, currentListName)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearAllConfirm = false
                    viewModel.clearAll()
                }) { Text(L10n.str(R.string.clear_2), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showClearAllConfirm = false }) { Text(L10n.str(R.string.cancel)) } }
        )
    }

    qrShareEntries?.let { entries ->
        val payload = remember(entries) { ShoppingListShareCodec.encode(entries) }
        val qrBitmap = remember(payload) { renderQrBitmap(payload, QR_SIZE_PX) }
        AlertDialog(
            onDismissRequest = { qrShareEntries = null },
            title = { Text(L10n.str(R.string.list_qr_code)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = L10n.str(R.string.qr_code_x_items, entries.size),
                            modifier = Modifier.size(260.dp)
                        )
                        Text(
                            L10n.str(R.string.x_items_buy_other_phone, entries.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(L10n.str(R.string.list_too_big_qr_code))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { qrShareEntries = null }) { Text(L10n.str(R.string.close)) } }
        )
    }

    scannedEntries?.let { entries ->
        AlertDialog(
            onDismissRequest = { scannedEntries = null },
            title = { Text(L10n.str(R.string.list_received)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(L10n.str(R.string.x_items_added_list_quantities, entries.size))
                    entries.take(8).forEach { entry ->
                        Text("• ${formatIngredientText(entry.name, entry.quantity, entry.unit)}", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (entries.size > 8) Text(L10n.str(R.string.x_more, entries.size - 8), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addParsedEntries(entries)
                    scannedEntries = null
                }) { Text(L10n.str(R.string.add)) }
            },
            dismissButton = { TextButton(onClick = { scannedEntries = null }) { Text(L10n.str(R.string.cancel)) } }
        )
    }
}

@Composable
private fun EmptyShoppingList(
    modifier: Modifier,
    onCatalog: () -> Unit,
    onTemplates: () -> Unit,
    onSearch: () -> Unit,
    onScan: () -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) { Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🛒", fontSize = 56.sp)
        Text(
            L10n.str(R.string.list_empty),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            L10n.str(R.string.type_dictate_what_need_above),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.widthIn(max = 320.dp)) {
            FilledTonalButton(onClick = onTemplates, modifier = Modifier.fillMaxWidth()) {
                ButtonContent(Icons.Filled.Bookmarks, L10n.str(R.string.use_template))
            }
            OutlinedButton(onClick = onCatalog, modifier = Modifier.fillMaxWidth()) {
                ButtonContent(Icons.Filled.GridView, L10n.str(R.string.catalogue_2))
            }
            OutlinedButton(onClick = onSearch, modifier = Modifier.fillMaxWidth()) {
                ButtonContent(Icons.Filled.Search, L10n.str(R.string.search_open_food_facts))
            }
            OutlinedButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                ButtonContent(Icons.Filled.QrCodeScanner, L10n.str(R.string.scan_product))
            }
        }
    } }
}

@Composable
private fun ShareOption(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Switch, create, rename or delete lists, and set the name used to sign changes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShoppingListsSheet(
    lists: List<ShoppingListInfo>,
    listCounts: Map<String, Int>,
    selectedListUid: String,
    author: String,
    onSelect: (String) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var authorName by remember(author) { mutableStateOf(author) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ShoppingListInfo?>(null) }
    var deleting by remember { mutableStateOf<ShoppingListInfo?>(null) }

    fun saveAuthor() {
        if (authorName.trim() != author) onAuthorChange(authorName)
    }

    ModalBottomSheet(onDismissRequest = { saveAuthor(); onDismiss() }) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            item(key = "title") { Text(L10n.str(R.string.my_lists), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp)) }
            items(lists, key = { "l${it.uid}" }) { list ->
                val selected = list.uid == selectedListUid
                val pending = listCounts[list.uid] ?: 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .clickable { saveAuthor(); onSelect(list.uid) }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (selected) Icons.Filled.CheckCircle else Icons.Filled.ShoppingBag,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(list.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (pending == 0) L10n.str(R.string.nothing_left) else if (pending == 1) L10n.str(R.string.n_1_buy) else L10n.str(R.string.x_buy, pending),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (list.uid != DEFAULT_SHOPPING_LIST_UID) {
                        IconButton(onClick = { renaming = list }) { Icon(Icons.Filled.Edit, contentDescription = L10n.str(R.string.rename_x, list.name)) }
                        IconButton(onClick = { deleting = list }) { Icon(Icons.Filled.Delete, contentDescription = L10n.str(R.string.delete_x, list.name)) }
                    }
                }
            }
            item(key = "new") {
                TextButton(onClick = { creating = true }, modifier = Modifier.padding(top = 4.dp)) {
                    ButtonContent(Icons.Filled.Add, L10n.str(R.string.new_list_2))
                }
            }
            item(key = "author") {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                OutlinedTextField(
                    value = authorName,
                    onValueChange = { authorName = it },
                    label = { Text(L10n.str(R.string.my_name_shared_lists)) },
                    placeholder = { Text(L10n.str(R.string.e_g_ana_optional)) },
                    supportingText = { Text(L10n.str(R.string.everyone_knows_who_added_ticked)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { saveAuthor() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (creating) {
        NameDialog(
            title = L10n.str(R.string.new_list),
            placeholder = L10n.str(R.string.e_g_party_trip_saturday),
            confirmLabel = L10n.str(R.string.create),
            onConfirm = { creating = false; saveAuthor(); onCreate(it) },
            onDismiss = { creating = false }
        )
    }
    renaming?.let { list ->
        NameDialog(
            title = L10n.str(R.string.rename_list),
            initial = list.name,
            placeholder = list.name,
            confirmLabel = L10n.str(R.string.save),
            onConfirm = { onRename(list.uid, it); renaming = null },
            onDismiss = { renaming = null }
        )
    }
    deleting?.let { list ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(L10n.str(R.string.delete_list)) },
            text = { Text(L10n.str(R.string.delete_x_items, list.name)) },
            confirmButton = {
                TextButton(onClick = { onDelete(list.uid); deleting = null }) { Text(L10n.str(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(L10n.str(R.string.cancel)) } }
        )
    }
}
