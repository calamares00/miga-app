package org.calamares.miga.ui.shoppinglist

import org.calamares.miga.data.model.displayCategoryName
import org.calamares.miga.L10n
import org.calamares.miga.R
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
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

@Composable
internal fun CategoryHeader(
    title: String,
    count: Int,
    style: org.calamares.miga.data.model.CategoryStyle,
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
            contentDescription = if (collapsed) L10n.str(R.string.desplegar_x, title) else L10n.str(R.string.plegar_x, title),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun EmojiBadge(emoji: String, accent: Color, size: Int) {
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = (size * 0.55f).sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShoppingListRow(item: ShoppingListItem, shopMode: Boolean, showImage: Boolean, note: String?, onOpenProduct: (() -> Unit)?, onCheckedChange: (Boolean) -> Unit, onDelete: () -> Unit) {
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
                // En modo tienda quitar es un gesto (deslizar): se ofrece también como acción accesible.
                .semantics { customActions = listOf(CustomAccessibilityAction(L10n.str(R.string.quitar_x, item.name)) { onDelete(); true }) }
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
                    .then(if (onOpenProduct != null) Modifier.clip(CircleShape).clickable(onClickLabel = L10n.str(R.string.ver_detalles_producto), onClick = onOpenProduct) else Modifier)
            ) {
                val badgeSize = if (shopMode) 44 else 34
                if (showImage && item.imageUrl != null) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = if (onOpenProduct != null) L10n.str(R.string.ficha_x, item.name) else null,
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
                Spacer(modifier = Modifier.width(6.dp))
            }
            val rowScore = remember(item.productInfo) { item.productInfo?.let { ProductScoring.compute(it) } }
            if (rowScore != null) {
                ScoreChip(score = rowScore, large = false)
                Spacer(modifier = Modifier.width(8.dp))
            }
            if (!shopMode) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = L10n.str(R.string.quitar_x, item.name), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    SwipeToDismissBoxValue.StartToEnd -> Icon(Icons.Filled.CheckBox, contentDescription = L10n.str(R.string.marcar))
                    SwipeToDismissBoxValue.EndToStart -> Icon(Icons.Filled.Delete, contentDescription = L10n.str(R.string.quitar))
                    SwipeToDismissBoxValue.Settled -> Unit
                }
            }
        },
        content = { rowContent() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StoreEditorSheet(
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
            Text(if (store.id == 0L) L10n.str(R.string.nuevo_supermercado) else L10n.str(R.string.editar_supermercado), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(L10n.str(R.string.nombre)) },
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
                Text(L10n.str(R.string.orden_pasillos), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { order = ShoppingAisleOrder.complete(ShoppingAisleOrder.TYPICAL_ORDER, categoryNames) }) {
                    Text(L10n.str(R.string.recorrido_tipico))
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
                        Text("${index + 1}. ${displayCategoryName(category)}", modifier = Modifier.weight(1f).padding(start = 10.dp))
                        IconButton(onClick = { order = ShoppingAisleOrder.move(order, index, -1) }, enabled = index > 0) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = L10n.str(R.string.subir_x, category))
                        }
                        IconButton(onClick = { order = ShoppingAisleOrder.move(order, index, 1) }, enabled = index < order.lastIndex) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = L10n.str(R.string.bajar_x, category))
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(L10n.str(R.string.cancelar)) }
                TextButton(onClick = { onSave(store.copy(name = name.trim(), argb = argb, aisleOrder = order)) }, enabled = name.isNotBlank()) {
                    Text(L10n.str(R.string.guardar))
                }
            }
        }
    }
}

/** "Añadido por Ana" / "Marcado por Luis" si lo hizo otra persona (no uno mismo) y consta quién fue. */
internal fun authorNote(item: ShoppingListItem, me: String): String? {
    val by = (if (item.checked) item.updatedBy else item.addedBy)?.takeIf { it.isNotBlank() } ?: return null
    if (by.equals(me.trim(), ignoreCase = true)) return null
    return if (item.checked) L10n.str(R.string.marcado_x, by) else L10n.str(R.string.anadido_x_2, by)
}
