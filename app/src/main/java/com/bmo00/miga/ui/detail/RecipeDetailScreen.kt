package com.bmo00.miga.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bmo00.miga.data.export.RecipeExporter
import com.bmo00.miga.data.model.HealthColorLevel
import com.bmo00.miga.data.model.Recipe
import com.bmo00.miga.ui.theme.HealthAmberContainer
import com.bmo00.miga.ui.theme.HealthAmberOn
import com.bmo00.miga.ui.theme.HealthGreenContainer
import com.bmo00.miga.ui.theme.HealthGreenOn
import com.bmo00.miga.ui.theme.HealthRedContainer
import com.bmo00.miga.ui.theme.HealthRedOn

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeDetailScreen(
    viewModel: RecipeDetailViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    val recipe by viewModel.recipe.collectAsState()
    val recipeBooks by viewModel.recipeBooks.collectAsState()
    val ttsVoiceName by viewModel.ttsVoiceName.collectAsState()
    val healthState by viewModel.healthState.collectAsState()
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCookMode by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.fetchHealthinessIfNeeded() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recipe?.name.orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Volver") }
                },
                actions = {
                    val current = recipe
                    IconButton(onClick = { viewModel.toggleFavorite() }, enabled = current != null) {
                        Icon(
                            imageVector = if (current?.isFavorite == true) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorita",
                            tint = if (current?.isFavorite == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    val currentBookIsPack = recipeBooks.firstOrNull { it.id == current?.recipeBookId }?.isPack == true
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (!currentBookIsPack) {
                            DropdownMenuItem(text = { Text("Editar") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { showMenu = false; onEdit() })
                        }
                        DropdownMenuItem(
                            text = { Text("Compartir como texto") },
                            leadingIcon = { Icon(Icons.Filled.Share, null) },
                            onClick = {
                                showMenu = false
                                recipe?.let { RecipeExporter.shareAsText(context, it) }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Exportar como PDF") },
                            leadingIcon = { Icon(Icons.Filled.Share, null) },
                            onClick = {
                                showMenu = false
                                recipe?.let { RecipeExporter.shareAsPdf(context, it) }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Exportar copia de seguridad") },
                            leadingIcon = { Icon(Icons.Filled.Share, null) },
                            onClick = {
                                showMenu = false
                                recipe?.let { RecipeExporter.shareRecipe(context, it) }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Mover a otro libro") },
                            leadingIcon = { Icon(Icons.Filled.SwapHoriz, null) },
                            onClick = { showMenu = false; showMoveDialog = true }
                        )
                        if (!currentBookIsPack) {
                            DropdownMenuItem(
                                text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { showMenu = false; showDeleteConfirm = true }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (recipe?.stepGroups?.any { it.instructions.isNotEmpty() } == true) {
                ExtendedFloatingActionButton(
                    onClick = { showCookMode = true },
                    icon = { Icon(Icons.Filled.PlayArrow, null) },
                    text = { Text("Modo cocina") }
                )
            }
        }
    ) { padding ->
        val current = recipe
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Cargando...")
            }
        } else {
            RecipeDetailContent(
                recipe = current,
                healthState = healthState,
                onRetryHealth = { viewModel.retryHealthCheck() },
                modifier = Modifier.padding(padding)
            )
        }
    }

    val recipeForCookMode = recipe
    if (showCookMode && recipeForCookMode != null) {
        CookModeOverlay(recipe = recipeForCookMode, ttsVoiceName = ttsVoiceName, onClose = { showCookMode = false })
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar receta") },
            text = { Text("¿Seguro que quieres eliminar \"${recipe?.name}\"? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteRecipe(onDeleted = onBack)
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    val recipeForMove = recipe
    if (showMoveDialog && recipeForMove != null) {
        val otherBooks = recipeBooks.filter { it.id != recipeForMove.recipeBookId && !it.isPack }
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text("Mover a otro libro") },
            text = {
                if (otherBooks.isEmpty()) {
                    Text("No tienes más libros de recetas todavía.")
                } else {
                    Column {
                        otherBooks.forEach { book ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showMoveDialog = false
                                        viewModel.moveToBook(book.id)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = false, onClick = null)
                                Text(book.name, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMoveDialog = false }) { Text("Cerrar") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeDetailContent(
    recipe: Recipe,
    healthState: HealthState,
    onRetryHealth: () -> Unit,
    modifier: Modifier = Modifier
) {
    var servings by remember(recipe.id) { mutableIntStateOf(recipe.servings) }
    val checkedIngredients = remember(recipe.id) { mutableStateMapOf<String, Boolean>() }
    val scale = if (recipe.servings > 0) servings.toDouble() / recipe.servings else 1.0

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 96.dp)
    ) {
        CoverPhoto(recipe)

        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                recipe.categoryName?.let {
                    Text(it.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Text(recipe.name, style = MaterialTheme.typography.headlineMedium)
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SuggestionChip(onClick = {}, label = { Text(recipe.difficulty.label) })
                if (recipe.prepTimeMinutes != null) SuggestionChip(onClick = {}, label = { Text("Prep: ${recipe.prepTimeMinutes} min") })
                if (recipe.cookTimeMinutes != null) SuggestionChip(onClick = {}, label = { Text("Cocción: ${recipe.cookTimeMinutes} min") })
                if (recipe.timesCooked > 0) SuggestionChip(onClick = {}, label = { Text("Cocinada ${recipe.timesCooked}x") })
            }

            if (recipe.utensils.isNotEmpty()) {
                Section(title = "Utensilios") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        recipe.utensils.forEach { SuggestionChip(onClick = {}, label = { Text(it) }) }
                    }
                }
            }

            if (recipe.tags.isNotEmpty()) {
                Section(title = "Etiquetas") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        recipe.tags.forEach { SuggestionChip(onClick = {}, label = { Text("#$it") }) }
                    }
                }
            }

            if (healthState != HealthState.Idle) {
                Section(title = "Salud") {
                    when (healthState) {
                        HealthState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                "Analizando con IA...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                        HealthState.NotConfigured -> Text(
                            "Configura una API key de Gemini en Ajustes para ver esto.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        is HealthState.Error -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(healthState.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = onRetryHealth) { Text("Reintentar") }
                        }
                        HealthState.Loaded -> recipe.healthRating?.let { rating ->
                            val (containerColor, contentColor, label) = when (rating.color) {
                                HealthColorLevel.GREEN -> Triple(HealthGreenContainer, HealthGreenOn, "Saludable")
                                HealthColorLevel.YELLOW -> Triple(HealthAmberContainer, HealthAmberOn, "Moderada")
                                HealthColorLevel.RED -> Triple(HealthRedContainer, HealthRedOn, "Poco saludable")
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text(label) },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = containerColor, labelColor = contentColor)
                                )
                                Text(rating.description, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        HealthState.Idle -> Unit
                    }
                }
            }

            HorizontalDivider()

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Ingredientes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                ServingsStepper(servings = servings, onChange = { servings = it.coerceIn(1, 99) })
            }

            recipe.ingredientGroups.forEach { group ->
                if (group.ingredients.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (group.name != null) {
                            Text(
                                group.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        group.ingredients.forEachIndexed { index, ingredient ->
                            val key = "${group.name}_$index"
                            val checked = checkedIngredients[key] ?: false
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = checked,
                                        role = Role.Checkbox,
                                        onValueChange = { checkedIngredients[key] = it }
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                    contentDescription = null,
                                    tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = formatIngredient(ingredient, scale),
                                    style = MaterialTheme.typography.bodyLarge,
                                    textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
                                    color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            Text("Preparación", style = MaterialTheme.typography.titleLarge)
            recipe.stepGroups.forEach { group ->
                if (group.instructions.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (group.name != null) {
                            Text(
                                group.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                        group.instructions.forEachIndexed { index, instruction ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(28.dp)
                                )
                                Text(instruction, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }

            if (recipe.notes.isNotBlank()) {
                Section(title = "Notas") { Text(recipe.notes, style = MaterialTheme.typography.bodyLarge) }
            }
            if (recipe.source.isNotBlank()) {
                Section(title = "Origen") { Text(recipe.source, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
private fun CoverPhoto(recipe: Recipe) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (recipe.coverPhotoUri != null) {
            AsyncImage(
                model = recipe.coverPhotoUri,
                contentDescription = recipe.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Restaurant,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp).align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun ServingsStepper(servings: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(servings - 1) }) { Icon(Icons.Filled.Remove, contentDescription = "Menos raciones") }
        Text("$servings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 4.dp))
        IconButton(onClick = { onChange(servings + 1) }) { Icon(Icons.Filled.Add, contentDescription = "Más raciones") }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
