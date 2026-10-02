package com.bmo00.miga.ui.shoppinglist

import android.Manifest
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

private const val FREQUENT_CHIPS = 10
private const val QR_SIZE_PX = 720
private const val CART_KEY = "__cart__"

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
    var collapsed by remember { mutableStateOf(setOf<String>()) }
    val view = LocalView.current
    DisposableEffect(shopMode) {
        view.keepScreenOn = shopMode
        onDispose { view.keepScreenOn = false }
    }
    val pendingGroups = remember(groups) {
        groups.map { group -> ShoppingListGroup(group.categoryName, group.items.filter { !it.checked }) }.filter { it.items.isNotEmpty() }
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

    val typedEntry = remember(quickText) {
        if (quickText.isBlank() || quickText.any { it == ',' || it == ';' || it == '\n' }) null
        else ShoppingEntryParser.parse(quickText).firstOrNull()
    }
    val suggestions = remember(quickText, typedEntry, history, catalogNames) {
        when {
            quickText.isBlank() -> history.take(FREQUENT_CHIPS)
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
        recognizer = SpeechDictation.startListening(context) { result ->
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

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.navigationBars),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Lista de la compra") },
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
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Añadir: 2 kg tomates, leche…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitQuick() })
                )
                IconButton(onClick = { onMicClick() }) {
                    Icon(
                        imageVector = if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (listening) "Dejar de escuchar" else "Dictar artículos"
                    )
                }
                IconButton(onClick = { submitQuick() }, enabled = quickText.isNotBlank()) {
                    Icon(Icons.Filled.Add, contentDescription = "Añadir a la lista")
                }
            }

            if (!shopMode && suggestions.isNotEmpty()) {
                if (quickText.isBlank()) {
                    Text(
                        "Frecuentes",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
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
                }
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
private fun ShoppingListRow(item: ShoppingListItem, shopMode: Boolean, onCheckedChange: (Boolean) -> Unit, onDelete: () -> Unit) {
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
            Box(modifier = Modifier.alpha(if (item.checked) 0.5f else 1f)) {
                EmojiBadge(emoji = emoji, accent = Color(style.argb), size = if (shopMode) 44 else 34)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = formatIngredientText(item.name, item.quantity, item.unit),
                style = if (shopMode) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
                color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
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
