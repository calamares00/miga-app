package com.bmo00.miga.ui.shoppinglist

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
import com.bmo00.miga.data.model.AdditiveRisk
import com.bmo00.miga.data.model.DEFAULT_SHOPPING_LIST_UID
import com.bmo00.miga.data.model.IngredientCatalogItem
import com.bmo00.miga.data.model.ParsedShoppingEntry
import com.bmo00.miga.data.model.PredefinedShoppingLists
import com.bmo00.miga.data.model.ProductInfo
import com.bmo00.miga.data.model.ProductLabels
import com.bmo00.miga.data.model.ProductScore
import com.bmo00.miga.data.model.ProductScoring
import com.bmo00.miga.data.model.ShoppingAisleOrder
import com.bmo00.miga.data.model.ShoppingEntryParser
import com.bmo00.miga.data.model.ShoppingListGroup
import com.bmo00.miga.data.model.ShoppingListInfo
import com.bmo00.miga.data.model.ShoppingListItem
import com.bmo00.miga.data.model.ShoppingStore
import com.bmo00.miga.data.model.ShoppingSuggestions
import com.bmo00.miga.data.model.ShoppingTemplate
import com.bmo00.miga.data.model.ShoppingVisuals
import com.bmo00.miga.data.model.TemplateItem
import com.bmo00.miga.data.model.UNCATEGORIZED_INGREDIENT_LABEL
import com.bmo00.miga.data.model.formatIngredientText
import com.bmo00.miga.data.remote.ScannedProduct
import com.bmo00.miga.data.share.ShoppingIntentEvent
import com.bmo00.miga.data.share.ShoppingIntents
import com.bmo00.miga.data.share.ShoppingListShareCodec
import com.bmo00.miga.data.voice.DictationResult
import com.bmo00.miga.data.voice.SpeechDictation
import com.bmo00.miga.ui.components.rememberDictationLanguage
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Fila de plantilla: miniaturas, nombre, resumen y botón para volcarla a la lista. [onClick] null = no editable. */
@Composable
internal fun TemplateRow(template: ShoppingTemplate, onApply: () -> Unit, onClick: (() -> Unit)?) {
    val products = template.items.count { it.isProduct }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TemplateThumb(template)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(template.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val count = if (template.items.size == 1) "1 artículo" else "${template.items.size} artículos"
            Text(
                if (products > 0) "$count · $products de Open Food Facts" else count,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                template.items.take(6).joinToString(", ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        FilledTonalButton(onClick = onApply, enabled = template.items.isNotEmpty(), contentPadding = PaddingValues(horizontal = 12.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(" Añadir")
        }
    }
}

@Composable
private fun TemplateThumb(template: ShoppingTemplate) {
    val image = template.items.firstNotNullOfOrNull { it.imageUrl }
    val emoji = template.name.takeWhile { !it.isLetterOrDigit() && !it.isWhitespace() }.ifBlank { "📋" }
    Box(
        modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(emoji, fontSize = 24.sp)
        }
    }
}

/** Elegir en qué plantilla guardar [item] (o crear una nueva con él). */
@Composable
internal fun TemplatePickerDialog(
    item: TemplateItem,
    templates: List<ShoppingTemplate>,
    onPick: (ShoppingTemplate) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.BookmarkAdd, contentDescription = null) },
        title = { Text("Guardar en plantilla") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                item(key = "what") {
                    Text(
                        "\"${item.name}\" se guardará con su foto y su ficha.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(templates, key = { it.id }) { template ->
                    val already = template.items.any { it.name.trim().equals(item.name.trim(), ignoreCase = true) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onPick(template) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TemplateThumb(template)
                        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(template.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (already) "Ya está: se actualizará" else "${template.items.size} artículos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                item(key = "new") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text(if (templates.isEmpty()) "Nombre de la plantilla" else "O crea una nueva") },
                            placeholder = { Text("p. ej. Compra semanal") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (newName.isNotBlank()) onCreate(newName) }),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onCreate(newName) }, enabled = newName.isNotBlank()) {
                            Icon(Icons.Filled.Add, contentDescription = "Crear plantilla")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Diálogo de un campo para nombrar algo (plantilla, lista…). */
@Composable
internal fun NameDialog(
    title: String,
    initial: String = "",
    placeholder: String,
    confirmLabel: String,
    supporting: String? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (supporting != null) {
                    Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm(name.trim()) }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/**
 * Editor de una plantilla propia: renombrar, quitar artículos y añadir escribiendo, buscando en
 * Open Food Facts o escaneando el código de barras.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TemplateEditorSheet(
    viewModel: ShoppingListViewModel,
    template: ShoppingTemplate,
    onMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searching by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<TemplateItem?>(null) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents != null) viewModel.addScannedProductToTemplate(template.id, contents.trim()) { onMessage(it) }
    }

    fun close() {
        viewModel.clearSearch()
        onDismiss()
    }

    fun submitText() {
        if (viewModel.addTextToTemplate(template.id, text) > 0) text = ""
    }

    ModalBottomSheet(onDismissRequest = { close() }) {
        Column(modifier = Modifier.fillMaxHeight(0.92f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(template.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (template.items.size == 1) "1 artículo" else "${template.items.size} artículos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { renaming = true }) { Icon(Icons.Filled.Edit, contentDescription = "Renombrar plantilla") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Borrar plantilla") }
            }

            if (searching) {
                TextButton(onClick = { searching = false; viewModel.clearSearch() }) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Volver a la plantilla")
                }
                Box(modifier = Modifier.weight(1f)) {
                    ProductSearchContent(
                        viewModel = viewModel,
                        initialQuery = text.trim(),
                        addDescription = "Añadir a la plantilla",
                        onAdd = { product ->
                            viewModel.addToTemplate(template.id, product.toTemplateItem())
                            onMessage("Añadido a \"${template.name}\": ${product.name}")
                        },
                        onSaveToTemplate = null
                    )
                }
            } else {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Añadir: 2 kg tomates, leche…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitText() }),
                    trailingIcon = {
                        if (text.isNotBlank()) {
                            IconButton(onClick = { submitText() }) { Icon(Icons.Filled.Add, contentDescription = "Añadir a la plantilla") }
                        }
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { searching = true },
                        label = { Text("Buscar en Open Food Facts") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    AssistChip(
                        onClick = {
                            scanLauncher.launch(
                                ScanOptions().apply {
                                    setDesiredBarcodeFormats(ScanOptions.PRODUCT_CODE_TYPES)
                                    setPrompt("Apunta al código de barras del producto")
                                    setBeepEnabled(false)
                                    setOrientationLocked(false)
                                }
                            )
                        },
                        label = { Text("Escanear") },
                        leadingIcon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
                if (template.items.isEmpty()) {
                    Text(
                        "Plantilla vacía. Escribe artículos, busca productos en Open Food Facts o escanea su código de barras.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(template.items, key = { index, item -> "$index-${item.name}" }) { index, item ->
                        TemplateItemRow(
                            item = item,
                            onOpen = if (item.info != null) ({ detail = item }) else null,
                            onRemove = { viewModel.removeFromTemplate(template.id, index) }
                        )
                    }
                }
                Button(
                    onClick = {
                        viewModel.applyTemplate(template)
                        onMessage("Añadidos ${template.items.size} artículos de \"${template.name}\"")
                        close()
                    },
                    enabled = template.items.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) { Text("Añadir todo a la lista") }
            }
        }
    }

    detail?.let { item ->
        val info = item.info
        if (info != null) {
            ProductDetailSheet(name = item.name, imageUrl = item.imageUrl, info = info, onDismiss = { detail = null })
        }
    }

    if (renaming) {
        NameDialog(
            title = "Renombrar plantilla",
            initial = template.name,
            placeholder = "p. ej. Compra semanal",
            confirmLabel = "Guardar",
            onConfirm = { viewModel.renameTemplate(template.id, it); renaming = false },
            onDismiss = { renaming = false }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Borrar plantilla") },
            text = { Text("¿Borrar \"${template.name}\"? Los artículos de tu lista no cambian.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteTemplate(template.id)
                    close()
                }) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun TemplateItemRow(item: TemplateItem, onOpen: (() -> Unit)?, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.imageUrl != null) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small)
            )
        } else {
            val emoji = ShoppingVisuals.itemEmoji(item.name, "")
            EmojiBadge(emoji = emoji, accent = MaterialTheme.colorScheme.secondary, size = 40)
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                formatIngredientText(item.name, item.quantity, item.unit),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val info = item.info
            if (info != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    info.brand?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    ProductLabels.gradeLetter(info.nutriScore)?.let {
                        NutriScoreBadge(letter = it, large = false)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    remember(info) { ProductScoring.compute(info) }?.let { ScoreChip(score = it, large = false) }
                }
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "Quitar ${item.name} de la plantilla", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
