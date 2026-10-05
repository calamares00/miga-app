package org.calamares.miga.ui.shoppinglist

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
 * Lista de la compra. Arriba solo lo que se usa a diario (escribir, dictar, escanear y el botón
 * "Explorar" que abre catálogo, Open Food Facts, plantillas y añadir varios en una sola hoja); el
 * título cambia de lista y el menú se queda en compartir, supermercados, fotos y vaciar.
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
    val currentListName = lists.firstOrNull { it.uid == selectedListUid }?.name ?: "Compra"

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

    // Con una hoja abierta la snackbar queda tapada: entonces se avisa con un toast.
    fun announce(message: String) {
        if (addSheetTab != null || editingTemplateId != null || productDetail != null) Toast.makeText(context, message, Toast.LENGTH_SHORT).show() else showMessage(message)
    }

    fun openAddSheet(tab: AddTab, query: String = "") {
        addSheetQuery = query
        addSheetTab = tab
    }

    DisposableEffect(Unit) { onDispose { recognizer?.destroy() } }

    // Mientras la pantalla está visible, trae los cambios de otras personas cada pocos segundos
    // (el sync periódico en segundo plano es de 15 minutos); sin conexión compartida no hace nada.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.syncSharedListsOnce(context)
                delay(SHARED_SYNC_INTERVAL_MILLIS)
            }
        }
    }

    // Avisos de lo que otras personas añaden a la lista compartida.
    LaunchedEffect(Unit) {
        viewModel.remoteAdditions.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    // Texto compartido hacia Miga o botón del widget (ver ShoppingIntents).
    LaunchedEffect(intentEvent) {
        when (val event = intentEvent) {
            is ShoppingIntentEvent.SharedText -> {
                val entries = ShoppingEntryParser.parse(event.text)
                if (entries.isEmpty()) showMessage("No se reconocieron artículos en el texto recibido") else scannedEntries = entries
                ShoppingIntents.consume()
            }
            ShoppingIntentEvent.QuickAdd -> {
                shopMode = false
                delay(200) // da tiempo a que el campo de añadir vuelva a componerse
                runCatching { quickAddFocus.requestFocus() }
                ShoppingIntents.consume() // al final: consumirlo antes cancelaría esta corrutina (cambia la clave)
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
            showMessage("El reconocimiento de voz no está disponible en este dispositivo")
            return
        }
        recognizer?.destroy()
        listening = true
        recognizer = SpeechDictation.startListening(context, dictationLanguage) { result ->
            listening = false
            when (result) {
                is DictationResult.Success -> {
                    val count = viewModel.addEntries(result.text, splitOnY = true)
                    showMessage(if (count == 1) "Añadido: ${result.text}" else "Añadidos $count artículos")
                }
                is DictationResult.Error -> showMessage(result.reason)
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginListening() else showMessage("Sin permiso de micrófono no se puede dictar")
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
            if (decoded == null) showMessage("Ese código QR no es una lista de Miga") else scannedEntries = decoded
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
                setPrompt("Apunta al código de barras del producto")
                setBeepEnabled(false)
                setOrientationLocked(false)
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.navigationBars),
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
                                if (selectedListUid == DEFAULT_SHOPPING_LIST_UID) "Lista de la compra" else currentListName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (lists.size > 1) {
                                Text(
                                    if (selectedListUid == DEFAULT_SHOPPING_LIST_UID) currentListName else "Lista de la compra",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Cambiar de lista")
                    }
                },
                actions = {
                    IconButton(onClick = { shopMode = !shopMode }) {
                        Icon(
                            Icons.Filled.ShoppingBag,
                            contentDescription = if (shopMode) "Salir del modo tienda" else "Modo tienda",
                            tint = if (shopMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Compartir…") },
                            leadingIcon = { Icon(Icons.Filled.Share, null) },
                            onClick = { showMenu = false; showShare = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Supermercados…") },
                            leadingIcon = { Icon(Icons.Filled.Store, null) },
                            onClick = { showMenu = false; showStores = true }
                        )
                        DropdownMenuItem(
                            text = { Text(if (imagesEnabled) "Ocultar fotos" else "Mostrar fotos") },
                            leadingIcon = { Icon(if (imagesEnabled) Icons.Filled.HideImage else Icons.Filled.Photo, null) },
                            onClick = { showMenu = false; viewModel.setImagesEnabled(!imagesEnabled) }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Vaciar lista", color = MaterialTheme.colorScheme.error) },
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
                        placeholder = { Text("Añadir: 2 kg tomates, leche…") },
                        shape = CircleShape,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitQuick() }),
                        trailingIcon = {
                            if (quickText.isBlank()) {
                                Row {
                                    IconButton(onClick = { onMicClick() }) {
                                        Icon(
                                            imageVector = if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                                            contentDescription = if (listening) "Dejar de escuchar" else "Dictar artículos",
                                            tint = if (listening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { launchProductScan() }) {
                                        Icon(Icons.Filled.QrCodeScanner, contentDescription = "Escanear código de barras de un producto")
                                    }
                                }
                            } else {
                                IconButton(onClick = { submitQuick() }) {
                                    Icon(Icons.Filled.AddCircle, contentDescription = "Añadir a la lista", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    FilledTonalIconButton(onClick = { openAddSheet(AddTab.CATALOG) }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Filled.GridView, contentDescription = "Explorar: catálogo, Open Food Facts, plantillas")
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
                            label = { Text("Buscar producto") },
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
                        text = if (shopMode) "Desliza → para marcar · ${cartItems.size}/$totalCount" else "$pendingCount por comprar · ${cartItems.size} en el carrito",
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
                            Text(selectedStore?.name ?: "Orden por tienda", style = MaterialTheme.typography.labelLarge)
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = showStoreMenu, onDismissRequest = { showStoreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Sin tienda (por categorías)") },
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
                                text = { Text("Gestionar supermercados…") },
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
                                    val result = snackbarHostState.showSnackbar("Artículo quitado", actionLabel = "Deshacer")
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
                                title = group.categoryName,
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
                                title = "En el carrito",
                                count = cartItems.size,
                                style = org.calamares.miga.data.model.CategoryStyle("🛒", 0xFF7B8794),
                                collapsed = CART_KEY in collapsed,
                                onToggle = { toggleCollapsed(CART_KEY) },
                                trailing = { TextButton(onClick = { viewModel.clearChecked() }) { Text("Quitar") } }
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
                announce("Guardado en \"${template.name}\"")
            },
            onCreate = { name ->
                viewModel.createTemplate(name, item)
                templateTarget = null
                announce("Plantilla \"$name\" creada con ${item.name}")
            },
            onDismiss = { templateTarget = null }
        )
    }

    if (showNewTemplate) {
        NameDialog(
            title = "Nueva plantilla",
            placeholder = "p. ej. Compra semanal",
            confirmLabel = "Crear",
            supporting = "Después podrás añadirle productos de Open Food Facts, escaneados o escritos.",
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
            title = "Guardar como plantilla",
            placeholder = "p. ej. Compra semanal",
            confirmLabel = "Guardar",
            supporting = "Se guardan los $totalCount artículos de la lista (con sus fotos y fichas) para volver a añadirlos con un toque.",
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
                        Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Guardar en plantilla")
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
            title = { Text("Compartir") },
            text = {
                Column {
                    ShareOption(Icons.Filled.Share, "Enviar como texto", "WhatsApp, correo, notas…") {
                        showShare = false
                        viewModel.share(context)
                    }
                    ShareOption(Icons.Filled.QrCode2, "Mostrar QR", "Para pasarla a otro móvil con Miga") {
                        showShare = false
                        val pending = viewModel.pendingEntries()
                        if (pending.isEmpty()) showMessage("No hay artículos pendientes que compartir") else qrShareEntries = pending
                    }
                    ShareOption(Icons.Filled.QrCodeScanner, "Recibir por QR", "Escanea el QR de otra lista de Miga") {
                        showShare = false
                        listQrLauncher.launch(
                            ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setPrompt("Apunta al QR de la lista de Miga")
                                setBeepEnabled(false)
                                setOrientationLocked(false)
                            }
                        )
                    }
                    Text(
                        "Para editar la misma lista entre varias personas, compártela desde Ajustes → Sincronización.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showShare = false }) { Text("Cerrar") } }
        )
    }

    if (showStores) {
        AlertDialog(
            onDismissRequest = { showStores = false },
            title = { Text("Supermercados") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Cada supermercado guarda su orden de pasillos: al elegirlo, la lista sale ordenada como lo recorres.",
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
                                Icon(Icons.Filled.Delete, contentDescription = "Borrar ${store.name}")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showStores = false
                    editingStore = ShoppingStore(0L, "", ShoppingAisleOrder.PALETTE[0], emptyList())
                }) { Text("Añadir supermercado") }
            },
            dismissButton = { TextButton(onClick = { showStores = false }) { Text("Cerrar") } }
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
            title = { Text("Vaciar lista") },
            text = { Text("¿Seguro que quieres borrar todos los artículos de \"$currentListName\"?") },
            confirmButton = {
                TextButton(onClick = {
                    showClearAllConfirm = false
                    viewModel.clearAll()
                }) { Text("Vaciar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showClearAllConfirm = false }) { Text("Cancelar") } }
        )
    }

    qrShareEntries?.let { entries ->
        val payload = remember(entries) { ShoppingListShareCodec.encode(entries) }
        val qrBitmap = remember(payload) { renderQrBitmap(payload, QR_SIZE_PX) }
        AlertDialog(
            onDismissRequest = { qrShareEntries = null },
            title = { Text("QR de la lista") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Código QR con ${entries.size} artículos",
                            modifier = Modifier.size(260.dp)
                        )
                        Text(
                            "${entries.size} artículos pendientes. En el otro móvil: menú → Compartir… → Recibir por QR.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text("La lista es demasiado grande para un QR. Usa \"Enviar como texto\".")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { qrShareEntries = null }) { Text("Cerrar") } }
        )
    }

    scannedEntries?.let { entries ->
        AlertDialog(
            onDismissRequest = { scannedEntries = null },
            title = { Text("Lista recibida") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Se van a añadir ${entries.size} artículos a tu lista (las cantidades se suman a lo que ya tengas):")
                    entries.take(8).forEach { entry ->
                        Text("• ${formatIngredientText(entry.name, entry.quantity, entry.unit)}", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (entries.size > 8) Text("… y ${entries.size - 8} más", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addParsedEntries(entries)
                    scannedEntries = null
                }) { Text("Añadir") }
            },
            dismissButton = { TextButton(onClick = { scannedEntries = null }) { Text("Cancelar") } }
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
            "Tu lista está vacía",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            "Escribe o dicta arriba lo que necesitas, o empieza desde aquí:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.widthIn(max = 320.dp)) {
            FilledTonalButton(onClick = onTemplates, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Bookmarks, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Usar una plantilla")
            }
            OutlinedButton(onClick = onCatalog, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.GridView, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Catálogo")
            }
            OutlinedButton(onClick = onSearch, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Buscar en Open Food Facts")
            }
            OutlinedButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Escanear un producto")
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

/** Cambiar de lista, crear, renombrar o borrar listas y poner el nombre con el que se firman los cambios. */
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
            item(key = "title") { Text("Mis listas", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp)) }
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
                            if (pending == 0) "Nada pendiente" else if (pending == 1) "1 por comprar" else "$pending por comprar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (list.uid != DEFAULT_SHOPPING_LIST_UID) {
                        IconButton(onClick = { renaming = list }) { Icon(Icons.Filled.Edit, contentDescription = "Renombrar ${list.name}") }
                        IconButton(onClick = { deleting = list }) { Icon(Icons.Filled.Delete, contentDescription = "Borrar ${list.name}") }
                    }
                }
            }
            item(key = "new") {
                TextButton(onClick = { creating = true }, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Nueva lista")
                }
            }
            item(key = "author") {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                OutlinedTextField(
                    value = authorName,
                    onValueChange = { authorName = it },
                    label = { Text("Mi nombre en listas compartidas") },
                    placeholder = { Text("p. ej. Ana (opcional)") },
                    supportingText = { Text("Así el resto sabe quién añadió o marcó cada artículo.") },
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
            title = "Nueva lista",
            placeholder = "p. ej. Fiesta, Viaje, Cena del sábado",
            confirmLabel = "Crear",
            onConfirm = { creating = false; saveAuthor(); onCreate(it) },
            onDismiss = { creating = false }
        )
    }
    renaming?.let { list ->
        NameDialog(
            title = "Renombrar lista",
            initial = list.name,
            placeholder = list.name,
            confirmLabel = "Guardar",
            onConfirm = { onRename(list.uid, it); renaming = null },
            onDismiss = { renaming = null }
        )
    }
    deleting?.let { list ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Borrar lista") },
            text = { Text("¿Borrar \"${list.name}\" y sus artículos?") },
            confirmButton = {
                TextButton(onClick = { onDelete(list.uid); deleting = null }) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }
        )
    }
}
