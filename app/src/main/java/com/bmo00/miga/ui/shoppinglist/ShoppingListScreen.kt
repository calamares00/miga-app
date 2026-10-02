package com.bmo00.miga.ui.shoppinglist

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.bmo00.miga.data.model.DEFAULT_SHOPPING_LIST_UID
import com.bmo00.miga.data.model.ShoppingListInfo
import coil.compose.AsyncImage
import com.bmo00.miga.data.model.IngredientCatalogItem
import com.bmo00.miga.data.model.PredefinedShoppingLists
import com.bmo00.miga.data.model.ProductInfo
import com.bmo00.miga.data.remote.ScannedProduct
import androidx.compose.material3.CircularProgressIndicator
import com.bmo00.miga.data.model.ProductLabels
import com.bmo00.miga.data.model.ShoppingAisleOrder
import com.bmo00.miga.data.model.ShoppingStore
import com.bmo00.miga.data.model.ShoppingTemplate
import com.bmo00.miga.data.model.UNCATEGORIZED_INGREDIENT_LABEL
import com.bmo00.miga.data.share.ShoppingIntentEvent
import com.bmo00.miga.data.share.ShoppingIntents
import com.bmo00.miga.data.model.ParsedShoppingEntry
import com.bmo00.miga.data.model.ShoppingEntryParser
import com.bmo00.miga.data.model.ShoppingListGroup
import com.bmo00.miga.data.model.ShoppingListItem
import com.bmo00.miga.data.model.ShoppingVisuals
import com.bmo00.miga.data.model.ShoppingSuggestions
import com.bmo00.miga.data.model.formatIngredientText
import com.bmo00.miga.data.share.ShoppingListShareCodec
import com.bmo00.miga.data.voice.DictationResult
import com.bmo00.miga.data.voice.SpeechDictation
import com.bmo00.miga.ui.components.rememberDictationLanguage
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val QR_SIZE_PX = 720
private const val CART_KEY = "__cart__"
private const val SHARED_SYNC_INTERVAL_MILLIS = 20_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(viewModel: ShoppingListViewModel) {
    val groups by viewModel.groups.collectAsState()
    val history by viewModel.history.collectAsState()
    val catalogNames by viewModel.catalogNames.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var showBulkDialog by remember { mutableStateOf(false) }
    var qrShareEntries by remember { mutableStateOf<List<ParsedShoppingEntry>?>(null) }
    var scannedEntries by remember { mutableStateOf<List<ParsedShoppingEntry>?>(null) }
    var quickText by remember { mutableStateOf("") }
    var shopMode by remember { mutableStateOf(false) }
    val dictationLanguage = rememberDictationLanguage()
    var showCatalog by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var showSaveTemplate by remember { mutableStateOf(false) }
    val catalog by viewModel.catalog.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val imagesEnabled by viewModel.imagesEnabled.collectAsState()
    val stores by viewModel.stores.collectAsState()
    val selectedStore by viewModel.selectedStore.collectAsState()
    val categoryNames by viewModel.ingredientCategoryNames.collectAsState()
    val lists by viewModel.lists.collectAsState()
    val selectedListUid by viewModel.selectedListUid.collectAsState()
    val author by viewModel.author.collectAsState()
    var showLists by remember { mutableStateOf(false) }
    var productDetail by remember { mutableStateOf<ShoppingListItem?>(null) }
    var showProductSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showNewList by remember { mutableStateOf(false) }
    val listCounts by viewModel.listCounts.collectAsState()
    val currentListName = lists.firstOrNull { it.uid == selectedListUid }?.name
    var showStores by remember { mutableStateOf(false) }
    var editingStore by remember { mutableStateOf<ShoppingStore?>(null) }
    val quickAddFocus = remember { FocusRequester() }
    val intentEvent by ShoppingIntents.event.collectAsState()
    var collapsed by remember { mutableStateOf(setOf<String>()) }
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
    val totalCount = cartItems.size + pendingGroups.sumOf { it.items.size }
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
        when {
            quickText.isBlank() -> emptyList()
            typedEntry == null -> emptyList()
            else -> ShoppingSuggestions.rank(typedEntry.name, history, catalogNames)
        }
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

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
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
                title = { Text(if (selectedListUid == DEFAULT_SHOPPING_LIST_UID) "Lista de la compra" else currentListName ?: "Lista de la compra") },
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
                            text = { Text("Añadir varios…") },
                            leadingIcon = { Icon(Icons.Filled.Add, null) },
                            onClick = { showMenu = false; showBulkDialog = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Listas y mi nombre…") },
                            leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = { showMenu = false; showLists = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Escanear producto (código de barras)") },
                            leadingIcon = { Icon(Icons.Filled.QrCodeScanner, null) },
                            onClick = { showMenu = false; launchProductScan() }
                        )
                        DropdownMenuItem(
                            text = { Text("Buscar producto en Open Food Facts…") },
                            leadingIcon = { Icon(Icons.Filled.GridView, null) },
                            onClick = { showMenu = false; searchQuery = quickText.trim(); showProductSearch = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Listas predefinidas y plantillas…") },
                            leadingIcon = { Icon(Icons.Filled.GridView, null) },
                            onClick = { showMenu = false; showTemplates = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Guardar lista como plantilla…") },
                            leadingIcon = { Icon(Icons.Filled.Add, null) },
                            onClick = {
                                showMenu = false
                                if (totalCount == 0) showMessage("La lista está vacía") else showSaveTemplate = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (imagesEnabled) "Ocultar fotos de productos" else "Mostrar fotos de productos") },
                            leadingIcon = { Icon(Icons.Filled.ShoppingBag, null) },
                            onClick = { showMenu = false; viewModel.setImagesEnabled(!imagesEnabled) }
                        )
                        DropdownMenuItem(
                            text = { Text("Compartir lista") },
                            leadingIcon = { Icon(Icons.Filled.Share, null) },
                            onClick = { showMenu = false; viewModel.share(context) }
                        )
                        DropdownMenuItem(
                            text = { Text("Mostrar QR de la lista") },
                            leadingIcon = { Icon(Icons.Filled.QrCode2, null) },
                            onClick = {
                                showMenu = false
                                val pending = viewModel.pendingEntries()
                                if (pending.isEmpty()) showMessage("No hay artículos pendientes que compartir") else qrShareEntries = pending
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Escanear QR de otra lista") },
                            leadingIcon = { Icon(Icons.Filled.QrCodeScanner, null) },
                            onClick = {
                                showMenu = false
                                scanLauncher.launch(
                                    ScanOptions().apply {
                                        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                        setPrompt("Apunta al QR de la lista de Miga")
                                        setBeepEnabled(false)
                                        setOrientationLocked(false)
                                    }
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Borrar marcados") },
                            leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            onClick = { showMenu = false; viewModel.clearChecked() }
                        )
                        DropdownMenuItem(
                            text = { Text("Vaciar lista", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; showClearAllConfirm = true }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!shopMode) Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = quickText,
                    onValueChange = { quickText = it },
                    modifier = Modifier.weight(1f).focusRequester(quickAddFocus),
                    placeholder = { Text("Añadir: 2 kg tomates, leche…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitQuick() })
                )
                IconButton(onClick = { launchProductScan() }) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = "Escanear código de barras de un producto")
                }
                IconButton(onClick = { onMicClick() }) {
                    Icon(
                        imageVector = if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (listening) "Dejar de escuchar" else "Dictar artículos"
                    )
                }
                if (quickText.isBlank()) {
                    IconButton(onClick = { showCatalog = true }) {
                        Icon(Icons.Filled.GridView, contentDescription = "Abrir el catálogo para tocar")
                    }
                } else {
                    IconButton(onClick = { submitQuick() }) {
                        Icon(Icons.Filled.Add, contentDescription = "Añadir a la lista")
                    }
                }
            }

            if (!shopMode && quickText.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { searchQuery = typedEntry?.name ?: quickText.trim(); showProductSearch = true },
                        label = { Text("🔍 Buscar en Open Food Facts") }
                    )
                    suggestions.forEach { suggestion ->
                        SuggestionChip(
                            onClick = {
                                viewModel.addSuggestion(suggestion, typedEntry)
                                quickText = ""
                            },
                            label = { Text(suggestion.name) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                lists.forEach { list ->
                    val pending = listCounts[list.uid] ?: 0
                    FilterChip(
                        selected = list.uid == selectedListUid,
                        onClick = { viewModel.selectList(list.uid) },
                        label = { Text(if (pending > 0) "${list.name} · $pending" else list.name) }
                    )
                }
                AssistChip(
                    onClick = { showNewList = true },
                    label = { Text("Nueva lista") },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (stores.isNotEmpty()) {
                    FilterChip(
                        selected = selectedStore == null,
                        onClick = { viewModel.selectStore(0L) },
                        label = { Text("Sin tienda") }
                    )
                    stores.forEach { store ->
                        FilterChip(
                            selected = store.id == selectedStore?.id,
                            onClick = { viewModel.selectStore(store.id) },
                            label = { Text(store.name) },
                            leadingIcon = {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(store.argb)))
                            }
                        )
                    }
                }
                AssistChip(
                    onClick = { showStores = true },
                    label = { Text(if (stores.isEmpty()) "Ordenar por supermercado" else "Supermercados…") },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }

            if (totalCount > 0) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(
                        text = if (shopMode) "Modo tienda · desliza → para marcar, ← para quitar · ${cartItems.size} de $totalCount en el carrito"
                        else "${cartItems.size} de $totalCount en el carrito",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(
                        progress = { cartItems.size.toFloat() / totalCount },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
            }

            if (totalCount == 0) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        "Tu lista de la compra está vacía.\nEscribe arriba lo que necesitas, díctalo o añade recetas desde el buscador.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
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
                                style = com.bmo00.miga.data.model.CategoryStyle("🛒", 0xFF7B8794),
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

    if (showCatalog) {
        val pendingNames = remember(groups) {
            groups.flatMap { it.items }.filter { !it.checked }.map { it.name.trim().lowercase() }.toSet()
        }
        CatalogSheet(
            catalog = catalog,
            pendingNames = pendingNames,
            onToggle = { viewModel.toggleCatalogItem(it) },
            onDismiss = { showCatalog = false }
        )
    }

    if (showSaveTemplate) {
        var templateName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveTemplate = false },
            title = { Text("Guardar como plantilla") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Se guardan los $totalCount artículos de la lista para volver a añadirlos con un toque.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = templateName,
                        onValueChange = { templateName = it },
                        label = { Text("Nombre") },
                        placeholder = { Text("p. ej. Compra semanal") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveTemplate(templateName)
                        showSaveTemplate = false
                        showMessage("Plantilla \"${templateName.trim()}\" guardada")
                    },
                    enabled = templateName.isNotBlank()
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showSaveTemplate = false }) { Text("Cancelar") } }
        )
    }

    if (showTemplates) {
        fun applyAndClose(template: ShoppingTemplate) {
            viewModel.applyTemplate(template)
            showTemplates = false
            showMessage("Añadidos ${template.entries.size} artículos de \"${template.name}\"")
        }
        AlertDialog(
            onDismissRequest = { showTemplates = false },
            title = { Text("Listas") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    item(key = "predefined_header") {
                        Text("Predefinidas", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    items(PredefinedShoppingLists.ALL, key = { "p${it.id}" }) { template ->
                        TemplateRow(template = template, onClick = { applyAndClose(template) }, onDelete = null)
                    }
                    item(key = "mine_header") {
                        Text(
                            "Mis plantillas",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                    if (templates.isEmpty()) {
                        item(key = "mine_empty") {
                            Text(
                                "Rellena la lista y usa \"Guardar lista como plantilla…\" para crear una.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                    items(templates, key = { "m${it.id}" }) { template ->
                        TemplateRow(template = template, onClick = { applyAndClose(template) }, onDelete = { viewModel.deleteTemplate(template.id) })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTemplates = false }) { Text("Cerrar") } }
        )
    }

    if (showNewList) {
        var newListName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewList = false },
            title = { Text("Nueva lista") },
            text = {
                OutlinedTextField(
                    value = newListName,
                    onValueChange = { newListName = it },
                    label = { Text("Nombre") },
                    placeholder = { Text("p. ej. Fiesta, Viaje, Cena del sábado") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.createList(newListName); showNewList = false },
                    enabled = newListName.isNotBlank()
                ) { Text("Crear") }
            },
            dismissButton = { TextButton(onClick = { showNewList = false }) { Text("Cancelar") } }
        )
    }

    if (showProductSearch) {
        ProductSearchSheet(
            viewModel = viewModel,
            initialQuery = searchQuery,
            onAdded = { name -> showMessage("Añadido: $name") },
            onDismiss = { showProductSearch = false; viewModel.clearSearch() }
        )
    }

    productDetail?.let { item ->
        ProductDetailSheet(item = item, onDismiss = { productDetail = null })
    }

    if (showLists) {
        var newListName by remember { mutableStateOf("") }
        var authorName by remember(author) { mutableStateOf(author) }
        var renaming by remember { mutableStateOf<ShoppingListInfo?>(null) }
        AlertDialog(
            onDismissRequest = {
                viewModel.setAuthor(authorName)
                showLists = false
            },
            title = { Text("Listas y mi nombre") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item(key = "author") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(
                                value = authorName,
                                onValueChange = { authorName = it },
                                label = { Text("Mi nombre en las listas compartidas") },
                                placeholder = { Text("p. ej. Ana (opcional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Se muestra al resto de personas para saber quién añadió o marcó cada artículo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    item(key = "lists_header") {
                        Text("Listas", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(lists, key = { "l${it.uid}" }) { list ->
                        val isDefault = list.uid == DEFAULT_SHOPPING_LIST_UID
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectList(list.uid)
                                    viewModel.setAuthor(authorName)
                                    showLists = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = (if (list.uid == selectedListUid) "✓ " else "") + list.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            if (!isDefault) {
                                IconButton(onClick = { renaming = list }) { Icon(Icons.Filled.Edit, contentDescription = "Renombrar ${list.name}") }
                                IconButton(onClick = { viewModel.deleteList(list.uid) }) { Icon(Icons.Filled.Delete, contentDescription = "Borrar ${list.name}") }
                            }
                        }
                    }
                    item(key = "new_list") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newListName,
                                onValueChange = { newListName = it },
                                label = { Text("Nueva lista") },
                                placeholder = { Text("p. ej. Fiesta") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    viewModel.createList(newListName)
                                    viewModel.setAuthor(authorName)
                                    showLists = false
                                },
                                enabled = newListName.isNotBlank()
                            ) { Icon(Icons.Filled.Add, contentDescription = "Crear lista") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setAuthor(authorName)
                    showLists = false
                }) { Text("Listo") }
            }
        )
        renaming?.let { list ->
            var newName by remember(list.uid) { mutableStateOf(list.name) }
            AlertDialog(
                onDismissRequest = { renaming = null },
                title = { Text("Renombrar lista") },
                text = {
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.renameList(list.uid, newName); renaming = null }, enabled = newName.isNotBlank()) { Text("Guardar") }
                },
                dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancelar") } }
            )
        }
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
            text = { Text("¿Seguro que quieres borrar todos los artículos de la lista de la compra?") },
            confirmButton = {
                TextButton(onClick = {
                    showClearAllConfirm = false
                    viewModel.clearAll()
                }) { Text("Vaciar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    if (showBulkDialog) {
        var bulkText by remember { mutableStateOf("") }
        val parsedCount = ShoppingEntryParser.parse(bulkText).size
        AlertDialog(
            onDismissRequest = { showBulkDialog = false },
            title = { Text("Añadir varios") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Escribe o pega un artículo por línea, o separados por comas. Puedes indicar cantidad y unidad: \"2 kg tomates\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = bulkText,
                        onValueChange = { bulkText = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addEntries(bulkText)
                        showBulkDialog = false
                    },
                    enabled = parsedCount > 0
                ) { Text(if (parsedCount > 0) "Añadir $parsedCount" else "Añadir") }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDialog = false }) { Text("Cancelar") }
            }
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
                            "${entries.size} artículos pendientes. En el otro móvil: Lista de la compra → menú → Escanear QR de otra lista.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text("La lista es demasiado grande para un QR. Usa \"Compartir lista\" para enviarla como texto.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { qrShareEntries = null }) { Text("Cerrar") }
            }
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
            dismissButton = {
                TextButton(onClick = { scannedEntries = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun CategoryHeader(
    title: String,
    count: Int,
    style: com.bmo00.miga.data.model.CategoryStyle,
    collapsed: Boolean,
    onToggle: () -> Unit,
    trailing: @Composable () -> Unit = {}
) {
    val accent = Color(style.argb)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EmojiBadge(emoji = style.emoji, accent = accent, size = 32)
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = accent)
        Text(
            text = "  $count",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        trailing()
        Icon(
            imageVector = if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
            contentDescription = if (collapsed) "Desplegar $title" else "Plegar $title",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmojiBadge(emoji: String, accent: Color, size: Int) {
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = (size * 0.55f).sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShoppingListRow(item: ShoppingListItem, shopMode: Boolean, showImage: Boolean, note: String?, onOpenProduct: (() -> Unit)?, onCheckedChange: (Boolean) -> Unit, onDelete: () -> Unit) {
    val style = ShoppingVisuals.categoryStyle(item.categoryName)
    val emoji = ShoppingVisuals.itemEmoji(item.name, item.categoryName)
    val currentChecked = rememberUpdatedState(item.checked)
    val currentOnCheckedChange = rememberUpdatedState(onCheckedChange)
    val currentOnDelete = rememberUpdatedState(onDelete)

    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .toggleable(value = item.checked, role = Role.Checkbox, onValueChange = onCheckedChange)
                .padding(vertical = if (shopMode) 12.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                contentDescription = null,
                tint = if (item.checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(if (shopMode) 26.dp else 20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .alpha(if (item.checked) 0.5f else 1f)
                    .then(if (onOpenProduct != null) Modifier.clip(CircleShape).clickable(onClickLabel = "Ver detalles del producto", onClick = onOpenProduct) else Modifier)
            ) {
                val badgeSize = if (shopMode) 44 else 34
                if (showImage && item.imageUrl != null) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(badgeSize.dp).clip(CircleShape)
                    )
                } else {
                    EmojiBadge(emoji = emoji, accent = Color(style.argb), size = badgeSize)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatIngredientText(item.name, item.quantity, item.unit),
                    style = if (shopMode) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                if (note != null) {
                    Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val nutriLetter = ProductLabels.gradeLetter(item.productInfo?.nutriScore)
            if (nutriLetter != null) {
                NutriScoreBadge(letter = nutriLetter, large = false)
                Spacer(modifier = Modifier.width(8.dp))
            }
            if (!shopMode) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Quitar artículo", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (!shopMode) {
        rowContent()
        return
    }

    val swipeState = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        when (value) {
            SwipeToDismissBoxValue.StartToEnd -> currentOnCheckedChange.value(!currentChecked.value)
            SwipeToDismissBoxValue.EndToStart -> currentOnDelete.value()
            SwipeToDismissBoxValue.Settled -> Unit
        }
        false // la fila vuelve a su sitio; si cambia de sección, Compose la recoloca sola
    })
    SwipeToDismissBox(
        state = swipeState,
        backgroundContent = {
            val direction = swipeState.dismissDirection
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        when (direction) {
                            SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                            SwipeToDismissBoxValue.Settled -> Color.Transparent
                        }
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = if (direction == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Icon(Icons.Filled.CheckBox, contentDescription = "Marcar")
                    SwipeToDismissBoxValue.EndToStart -> Icon(Icons.Filled.Delete, contentDescription = "Quitar")
                    SwipeToDismissBoxValue.Settled -> Unit
                }
            }
        },
        content = { rowContent() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogSheet(
    catalog: List<IngredientCatalogItem>,
    pendingNames: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val byCategory = remember(catalog) {
        catalog.groupBy { it.categoryName ?: UNCATEGORIZED_INGREDIENT_LABEL }
            .mapValues { (_, items) -> items.map { it.name }.sortedBy { it.lowercase() } }
    }
    val categories = remember(byCategory) {
        byCategory.keys.sortedWith(compareBy({ it == UNCATEGORIZED_INGREDIENT_LABEL || it == "Otros" }, { it.lowercase() }))
    }
    var selected by remember { mutableStateOf<String?>(null) }
    val current = selected?.takeIf { it in byCategory } ?: categories.firstOrNull()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxHeight(0.8f)) {
            Text(
                "Toca para añadir o quitar de la lista",
                style = MaterialTheme.typography.titleMedium,
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
                        label = { Text("${ShoppingVisuals.categoryStyle(category).emoji} $category") }
                    )
                }
            }
            if (current == null) {
                Text(
                    "El catálogo de ingredientes está vacío.",
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
}

@Composable
private fun TemplateRow(template: ShoppingTemplate, onClick: () -> Unit, onDelete: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(template.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${template.entries.size} artículos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Borrar plantilla \"${template.name}\"")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoreEditorSheet(
    store: ShoppingStore,
    categoryNames: List<String>,
    onSave: (ShoppingStore) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(store.name) }
    var argb by remember { mutableStateOf(store.argb) }
    var order by remember {
        mutableStateOf(ShoppingAisleOrder.complete(store.aisleOrder.ifEmpty { ShoppingAisleOrder.TYPICAL_ORDER }, categoryNames))
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxHeight(0.85f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (store.id == 0L) "Nuevo supermercado" else "Editar supermercado", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShoppingAisleOrder.SUGGESTED_NAMES.forEach { suggestion ->
                    SuggestionChip(onClick = { name = suggestion }, label = { Text(suggestion) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ShoppingAisleOrder.PALETTE.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .then(if (color == argb) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { argb = color }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Orden de pasillos", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { order = ShoppingAisleOrder.complete(ShoppingAisleOrder.TYPICAL_ORDER, categoryNames) }) {
                    Text("Recorrido típico")
                }
            }
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(order, key = { _, category -> category }) { index, category ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        EmojiBadge(
                            emoji = ShoppingVisuals.categoryStyle(category).emoji,
                            accent = Color(ShoppingVisuals.categoryStyle(category).argb),
                            size = 30
                        )
                        Text("${index + 1}. $category", modifier = Modifier.weight(1f).padding(start = 10.dp))
                        IconButton(onClick = { order = ShoppingAisleOrder.move(order, index, -1) }, enabled = index > 0) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Subir $category")
                        }
                        IconButton(onClick = { order = ShoppingAisleOrder.move(order, index, 1) }, enabled = index < order.lastIndex) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = "Bajar $category")
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                TextButton(onClick = { onSave(store.copy(name = name.trim(), argb = argb, aisleOrder = order)) }, enabled = name.isNotBlank()) {
                    Text("Guardar")
                }
            }
        }
    }
}

/** "Añadido por Ana" / "Marcado por Luis" si lo hizo otra persona (no uno mismo) y consta quién fue. */
private fun authorNote(item: ShoppingListItem, me: String): String? {
    val by = (if (item.checked) item.updatedBy else item.addedBy)?.takeIf { it.isNotBlank() } ?: return null
    if (by.equals(me.trim(), ignoreCase = true)) return null
    return if (item.checked) "Marcado por $by" else "Añadido por $by"
}

/** Insignia del Nutri-Score: la letra sobre su color oficial (texto oscuro sobre el amarillo y el verde claro). */
@Composable
private fun NutriScoreBadge(letter: String, large: Boolean) {
    val argb = ProductLabels.nutriScoreArgb(letter) ?: return
    val darkText = letter == "C" || letter == "B"
    Box(
        modifier = Modifier
            .size(if (large) 40.dp else 24.dp)
            .clip(MaterialTheme.shapes.small)
            .background(Color(argb)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            fontWeight = FontWeight.Bold,
            fontSize = if (large) 22.sp else 14.sp,
            color = if (darkText) Color(0xFF1B1B1B) else Color.White
        )
    }
}

/** Ficha de un producto escaneado: foto grande, puntuaciones, alérgenos, nutrición por 100 g e ingredientes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductDetailSheet(item: ShoppingListItem, onDismiss: () -> Unit) {
    val info = item.productInfo ?: return
    val context = LocalContext.current
    val photos = listOfNotNull(info.imageUrl ?: item.imageUrl, info.ingredientsImageUrl, info.nutritionImageUrl).distinct()
    val photoLabels = mapOf(info.imageUrl to "Frontal", info.ingredientsImageUrl to "Ingredientes", info.nutritionImageUrl to "Nutrición")
    var selectedPhoto by remember(item.uid) { mutableStateOf(photos.firstOrNull()) }
    val nutriLetter = ProductLabels.gradeLetter(info.nutriScore)
    val ecoLetter = ProductLabels.gradeLetter(info.ecoScore)
    val badges = ProductLabels.badges(info)
    val nutrition = ProductLabels.nutritionRows(info)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.9f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (selectedPhoto != null) {
                AsyncImage(
                    model = selectedPhoto,
                    contentDescription = "Foto de ${item.name}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(260.dp)
                )
                if (photos.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        photos.forEach { url ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clip(MaterialTheme.shapes.small).clickable { selectedPhoto = url }.padding(4.dp)
                            ) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .then(if (url == selectedPhoto) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small) else Modifier)
                                )
                                Text(photoLabels[url] ?: "Foto", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            Text(item.name, style = MaterialTheme.typography.headlineSmall)
            val subtitle = listOfNotNull(info.brand, info.quantity).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (nutriLetter != null || info.nova != null || ecoLetter != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (nutriLetter != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NutriScoreBadge(letter = nutriLetter, large = true)
                            Text("  Nutri-Score", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    val novaColor = ProductLabels.novaArgb(info.nova)
                    if (info.nova != null && novaColor != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(novaColor)),
                                contentAlignment = Alignment.Center
                            ) { Text("${info.nova}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White) }
                            Text("  NOVA", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (ecoLetter != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NutriScoreBadge(letter = ecoLetter, large = true)
                            Text("  Eco-Score", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                ProductLabels.novaDescription(info.nova)?.let {
                    Text("NOVA ${info.nova}: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (badges.isNotEmpty()) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    badges.forEach { SuggestionChip(onClick = {}, label = { Text(it) }) }
                }
            }

            if (info.allergens.isNotEmpty() || info.traces.isNotEmpty()) {
                HorizontalDivider()
                Text("Alérgenos", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                if (info.allergens.isNotEmpty()) {
                    Text("Contiene: " + info.allergens.joinToString(", ") { ProductLabels.allergenName(it) }, style = MaterialTheme.typography.bodyMedium)
                }
                if (info.traces.isNotEmpty()) {
                    Text("Puede contener trazas de: " + info.traces.joinToString(", ") { ProductLabels.allergenName(it) }, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "Datos colaborativos de Open Food Facts: comprueba siempre la etiqueta del envase.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (nutrition.isNotEmpty()) {
                HorizontalDivider()
                Text("Nutrición por 100 g", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                nutrition.forEach { (label, value) ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }

            if (!info.ingredients.isNullOrBlank()) {
                HorizontalDivider()
                Text("Ingredientes", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(info.ingredients, style = MaterialTheme.typography.bodyMedium)
            }

            HorizontalDivider()
            TextButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://world.openfoodfacts.org/product/${info.barcode}")))
                }
            }) { Text("Ver en Open Food Facts") }
            Text(
                "Datos de Open Food Facts (licencia ODbL), una base de datos abierta y colaborativa.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}

/** Búsqueda de productos por nombre en Open Food Facts; tocar un resultado lo añade a la lista (con su foto y su ficha). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductSearchSheet(
    viewModel: ShoppingListViewModel,
    initialQuery: String,
    onAdded: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val state by viewModel.searchState.collectAsState()
    var query by remember { mutableStateOf(initialQuery) }
    var spainOnly by remember { mutableStateOf(true) }
    var addedBarcodes by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(Unit) {
        if (initialQuery.trim().length >= 2) viewModel.searchProducts(initialQuery, spainOnly)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxHeight(0.9f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Buscar en Open Food Facts", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("p. ej. leche, galletas digestive…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.searchProducts(query, spainOnly) }),
                trailingIcon = {
                    IconButton(onClick = { viewModel.searchProducts(query, spainOnly) }, enabled = query.trim().length >= 2) {
                        Icon(Icons.Filled.Search, contentDescription = "Buscar")
                    }
                }
            )
            FilterChip(
                selected = spainOnly,
                onClick = { spainOnly = !spainOnly; if (query.trim().length >= 2) viewModel.searchProducts(query, spainOnly) },
                label = { Text("Solo productos de España") }
            )
            when (val current = state) {
                ProductSearchState.Idle -> Text(
                    "Escribe el nombre de un producto y pulsa buscar. Salen primero los más escaneados.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ProductSearchState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text("  Buscando…")
                }
                is ProductSearchState.Error -> Text(current.reason, color = MaterialTheme.colorScheme.error)
                is ProductSearchState.Results -> if (current.products.isEmpty()) {
                    Text(
                        "Sin resultados. Prueba con otro nombre" + if (spainOnly) " o quita el filtro de España." else ".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(current.products, key = { it.barcode }) { product ->
                            val added = product.barcode in addedBarcodes
                            SearchResultRow(
                                product = product,
                                added = added,
                                onAdd = {
                                    viewModel.addSearchedProduct(product)
                                    addedBarcodes = addedBarcodes + product.barcode
                                    onAdded(product.name)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(product: ScannedProduct, added: Boolean, onAdd: () -> Unit) {
    val info = product.info
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !added, onClick = onAdd).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (product.imageUrl != null) {
            AsyncImage(
                model = product.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).clip(MaterialTheme.shapes.small)
            )
        } else {
            Box(
                modifier = Modifier.size(52.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) { Text("🛒", fontSize = 22.sp) }
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(product.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val subtitle = listOfNotNull(info.brand, info.quantity).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        ProductLabels.gradeLetter(info.nutriScore)?.let {
            NutriScoreBadge(letter = it, large = false)
            Spacer(modifier = Modifier.width(8.dp))
        }
        IconButton(onClick = onAdd, enabled = !added) {
            Icon(
                imageVector = if (added) Icons.Filled.CheckBox else Icons.Filled.Add,
                contentDescription = if (added) "Añadido" else "Añadir a la lista",
                tint = if (added) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
