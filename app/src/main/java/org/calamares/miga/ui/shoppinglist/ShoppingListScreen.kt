package org.calamares.miga.ui.shoppinglist

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import org.calamares.miga.ui.components.GRID_CONTENT_WIDTH
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.BoxWithConstraints
import org.calamares.miga.ui.components.AdaptiveScaffold
import org.calamares.miga.ui.components.ButtonContent
import org.calamares.miga.L10n
import org.calamares.miga.R
import android.Manifest
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.calamares.miga.data.model.DEFAULT_SHOPPING_LIST_UID
import org.calamares.miga.data.model.ParsedShoppingEntry
import org.calamares.miga.data.model.ShoppingAisleOrder
import org.calamares.miga.data.model.ShoppingEntryParser
import org.calamares.miga.data.model.ShoppingListGroup
import org.calamares.miga.data.model.ShoppingListItem
import org.calamares.miga.data.model.ShoppingStore
import org.calamares.miga.data.model.ShoppingSuggestions
import org.calamares.miga.data.model.TemplateItem
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

/** From this width (Material's "expanded" window) the lists are shown beside the items. */
private val LISTS_PANE_MIN_WIDTH = 840.dp
private val LISTS_PANE_WIDTH = 320.dp
private const val SHARED_SYNC_INTERVAL_MILLIS = 20_000L

/**
 * Shopping list. The top area only holds what is used daily (type, dictate, scan and an "Explore"
 * button that opens the catalogue, Open Food Facts, templates and bulk add in one sheet). The title
 * switches lists and the menu keeps share, stores, photos and clear.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(viewModel: ShoppingListViewModel) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val catalogNames by viewModel.catalogNames.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val imagesEnabled by viewModel.imagesEnabled.collectAsStateWithLifecycle()
    val stores by viewModel.stores.collectAsStateWithLifecycle()
    val selectedStore by viewModel.selectedStore.collectAsStateWithLifecycle()
    val categoryNames by viewModel.ingredientCategoryNames.collectAsStateWithLifecycle()
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val listCounts by viewModel.listCounts.collectAsStateWithLifecycle()
    val selectedListUid by viewModel.selectedListUid.collectAsStateWithLifecycle()
    val author by viewModel.author.collectAsStateWithLifecycle()
    val intentEvent by ShoppingIntents.event.collectAsStateWithLifecycle()
    val currentListName = lists.firstOrNull { it.uid == selectedListUid }?.name ?: L10n.str(R.string.shopping)

    var quickText by remember { mutableStateOf("") }
    var shopMode by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showStoreMenu by remember { mutableStateOf(false) }
    var addSheetTab by remember { mutableStateOf<AddTab?>(null) }
    var addSheetQuery by remember { mutableStateOf("") }
    var showLists by remember { mutableStateOf(false) }
    /** Whether the lists are on screen beside the items, so the title does not open them. */
    var listsPaneShown by remember { mutableStateOf(false) }
    var deletingCurrentList by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var showStores by remember { mutableStateOf(false) }
    var editingStore by remember { mutableStateOf<ShoppingStore?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ShoppingListItem?>(null) }
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
        if (addSheetTab != null || showTemplates || editingTemplateId != null || productDetail != null) Toast.makeText(context, message, Toast.LENGTH_SHORT).show() else showMessage(message)
    }

    fun deleteWithUndo(item: ShoppingListItem) {
        viewModel.deleteItem(item) {
            scope.launch {
                val result = snackbarHostState.showSnackbar(L10n.str(R.string.item_removed), actionLabel = L10n.str(R.string.undo))
                if (result == SnackbarResult.ActionPerformed) viewModel.restoreItem(item)
            }
        }
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

    AdaptiveScaffold(
        contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.navigationBars).exclude(WindowInsets.ime),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        maxContentWidth = GRID_CONTENT_WIDTH,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Row(
                        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(enabled = !listsPaneShown) { showLists = true }.padding(horizontal = 4.dp, vertical = 2.dp),
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
                        if (!listsPaneShown) Icon(Icons.Filled.ArrowDropDown, contentDescription = L10n.str(R.string.switch_list))
                    }
                },
                actions = {
                    IconButton(onClick = { showShare = true }) { Icon(Icons.Filled.Share, contentDescription = L10n.str(R.string.share_2)) }
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = L10n.str(R.string.more_options)) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(L10n.str(R.string.templates)) },
                            leadingIcon = { Icon(Icons.Filled.Bookmarks, null) },
                            onClick = { showMenu = false; showTemplates = true }
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
                        // The main list cannot be deleted, only emptied.
                        if (selectedListUid != DEFAULT_SHOPPING_LIST_UID) {
                            DropdownMenuItem(
                                text = { Text(L10n.str(R.string.delete_list), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { showMenu = false; deletingCurrentList = true }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            // Shop mode: bigger rows, swipe to tick, screen kept on.
            if (totalCount > 0) {
                ExtendedFloatingActionButton(
                    onClick = { shopMode = !shopMode },
                    icon = { Icon(if (shopMode) Icons.Filled.Check else Icons.Filled.ShoppingBag, contentDescription = null) },
                    text = { Text(if (shopMode) L10n.str(R.string.leave_shop_mode) else L10n.str(R.string.shop_mode)) },
                    containerColor = if (shopMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                )
            }
        }
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            // On large screens the lists stay visible on the left instead of in a sheet.
            val listsBeside = maxWidth >= LISTS_PANE_MIN_WIDTH
            listsPaneShown = listsBeside
            Row(modifier = Modifier.fillMaxSize()) {
                if (listsBeside) {
                    ShoppingListsPanel(
                        lists = lists,
                        listCounts = listCounts,
                        selectedListUid = selectedListUid,
                        author = author,
                        onSelect = { viewModel.selectList(it) },
                        onCreate = { viewModel.createList(it) },
                        onRename = { uid, name -> viewModel.renameList(uid, name) },
                        onDelete = { viewModel.deleteList(it) },
                        onAuthorChange = { viewModel.setAuthor(it) },
                        modifier = Modifier.width(LISTS_PANE_WIDTH).fillMaxHeight()
                    )
                    VerticalDivider()
                }
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
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
                                // Several items at once: separated by commas, or pasted one per line.
                                maxLines = 4,
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
                            onTemplates = { showTemplates = true },
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
                                onEdit = { editingItem = shoppingItem },
                                onCheckedChange = { viewModel.setChecked(shoppingItem.id, it) },
                                onDelete = { deleteWithUndo(shoppingItem) }
                            )
                        }
                        ShoppingItemsList(
                            pendingGroups = pendingGroups,
                            cartItems = cartItems,
                            collapsed = collapsed,
                            onToggleCollapsed = { toggleCollapsed(it) },
                            onClearCart = { viewModel.clearChecked() },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            row = { rowFor(it)() }
                        )
                    }
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
            onMessage = { announce(it) },
            onSaveToTemplate = { templateTarget = it },
            onDismiss = {
                addSheetTab = null
                viewModel.clearSearch()
            }
        )
    }

    if (showTemplates) {
        ShoppingTemplatesSheet(
            templates = templates,
            listIsEmpty = totalCount == 0,
            onApply = { template ->
                viewModel.applyTemplate(template)
                announce(L10n.str(R.string.added_x_items_x, template.items.size, template.name))
            },
            onEdit = { id -> showTemplates = false; editingTemplateId = id },
            onNew = { showNewTemplate = true },
            onSaveList = { showSaveTemplate = true },
            onDismiss = { showTemplates = false }
        )
    }

    editingItem?.let { item ->
        ShoppingItemEditorSheet(
            item = item,
            aisles = categoryNames,
            onSave = { name, quantity, unit, aisle ->
                viewModel.updateItem(item.id, name, quantity, unit, aisle)
                editingItem = null
            },
            onDelete = {
                editingItem = null
                deleteWithUndo(item)
            },
            onDismiss = { editingItem = null }
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
                    showTemplates = false
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
                announce(L10n.str(R.string.template_x_saved, name))
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
        ShareListDialog(
            onSendText = { viewModel.share(context) },
            onShowQr = {
                val pending = viewModel.pendingEntries()
                if (pending.isEmpty()) showMessage(L10n.str(R.string.there_no_items_share)) else qrShareEntries = pending
            },
            onScanQr = {
                listQrLauncher.launch(
                    ScanOptions().apply {
                        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        setPrompt(L10n.str(R.string.point_miga_list_qr_code))
                        setBeepEnabled(false)
                        setOrientationLocked(false)
                    }
                )
            },
            onDismiss = { showShare = false }
        )
    }

    if (showStores) {
        StoresDialog(
            stores = stores,
            onEdit = { editingStore = it },
            onDelete = { viewModel.deleteStore(it.id) },
            onDismiss = { showStores = false }
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

    if (deletingCurrentList) {
        AlertDialog(
            onDismissRequest = { deletingCurrentList = false },
            title = { Text(L10n.str(R.string.delete_list)) },
            text = { Text(L10n.str(R.string.delete_x_items, currentListName)) },
            confirmButton = {
                TextButton(onClick = {
                    deletingCurrentList = false
                    viewModel.deleteList(selectedListUid)
                }) { Text(L10n.str(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingCurrentList = false }) { Text(L10n.str(R.string.cancel)) } }
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

    qrShareEntries?.let { entries -> ListQrDialog(entries, onDismiss = { qrShareEntries = null }) }

    scannedEntries?.let { entries ->
        ReceivedListDialog(
            entries = entries,
            onAdd = {
                viewModel.addParsedEntries(entries)
                scannedEntries = null
            },
            onDismiss = { scannedEntries = null }
        )
    }
}

