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

/** Insignia del Nutri-Score: la letra sobre su color oficial (texto oscuro sobre el amarillo y el verde claro). */
@Composable
internal fun NutriScoreBadge(letter: String, large: Boolean) {
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
internal fun ProductDetailSheet(
    name: String,
    imageUrl: String?,
    info: ProductInfo,
    onDismiss: () -> Unit,
    actions: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val photos = listOfNotNull(info.imageUrl ?: imageUrl, info.ingredientsImageUrl, info.nutritionImageUrl).distinct()
    val photoLabels = mapOf(info.imageUrl to "Frontal", info.ingredientsImageUrl to "Ingredientes", info.nutritionImageUrl to "Nutrición")
    var selectedPhoto by remember(info.barcode) { mutableStateOf(photos.firstOrNull()) }
    val nutriLetter = ProductLabels.gradeLetter(info.nutriScore)
    val ecoLetter = ProductLabels.gradeLetter(info.ecoScore)
    val badges = ProductLabels.badges(info)
    val nutrition = ProductLabels.nutritionRows(info)
    val score = remember(info) { ProductScoring.compute(info) }

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
                    contentDescription = "Foto de $name",
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

            Text(name, style = MaterialTheme.typography.headlineSmall)
            val subtitle = listOfNotNull(info.brand, info.quantity).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            actions()

            if (nutriLetter != null || info.nova != null || ecoLetter != null || score != null) {
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
                    if (score != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ScoreChip(score = score, large = true)
                            Text("  Puntuación", style = MaterialTheme.typography.labelLarge)
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

            if (score != null) {
                HorizontalDivider()
                Text(
                    "Puntuación ${score.value}/100 · ${score.tier.label}",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(score.tier.argb)
                )
                Text(
                    "Nutrición ${score.nutrition}/100 (60 %) · Aditivos ${score.additives}/100 (30 %)" +
                        if (score.organicBonus > 0) " · Ecológico +${score.organicBonus}" else "",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (score.cappedByRiskyAdditive) {
                    Text(
                        "Limitada a 49 por contener un aditivo de riesgo alto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    "Estimación propia de Miga con los datos de Open Food Facts y el reparto 60 % nutrición, 30 % aditivos y 10 % ecológico " +
                        "que usan apps como Yuka. No es la puntuación oficial de Yuka ni un consejo médico.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val additiveCodes = info.additives.orEmpty()
            if (additiveCodes.isNotEmpty()) {
                HorizontalDivider()
                Text("Aditivos (${additiveCodes.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    additiveCodes.joinToString(", ") { code ->
                        val risk = ProductScoring.additiveRisk(code)
                        code.uppercase() + if (risk == AdditiveRisk.NONE) "" else " (${risk.label})"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
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

@Composable
internal fun SearchResultRow(
    product: ScannedProduct,
    added: Boolean,
    addDescription: String,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    onSaveToTemplate: (() -> Unit)?
) {
    val info = product.info
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 8.dp),
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
            Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                ProductLabels.gradeLetter(info.nutriScore)?.let {
                    NutriScoreBadge(letter = it, large = false)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                remember(info) { ProductScoring.compute(info) }?.let { ScoreChip(score = it, large = false) }
            }
        }
        if (onSaveToTemplate != null) {
            IconButton(onClick = onSaveToTemplate) {
                Icon(Icons.Filled.BookmarkAdd, contentDescription = "Guardar en una plantilla", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onAdd, enabled = !added) {
            Icon(
                imageVector = if (added) Icons.Filled.CheckCircle else Icons.Filled.AddCircle,
                contentDescription = if (added) "Añadido" else addDescription,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Nota de 0 a 100 sobre el color de su tramo (excelente, bueno, mediocre, malo). */
@Composable
internal fun ScoreChip(score: ProductScore, large: Boolean) {
    val darkText = score.tier == com.bmo00.miga.data.model.ScoreTier.GOOD || score.tier == com.bmo00.miga.data.model.ScoreTier.MEDIOCRE
    Box(
        modifier = Modifier
            .height(if (large) 40.dp else 24.dp)
            .width(if (large) 54.dp else 32.dp)
            .clip(MaterialTheme.shapes.small)
            .background(Color(score.tier.argb)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = score.value.toString(),
            fontWeight = FontWeight.Bold,
            fontSize = if (large) 20.sp else 13.sp,
            color = if (darkText) Color(0xFF1B1B1B) else Color.White
        )
    }
}
