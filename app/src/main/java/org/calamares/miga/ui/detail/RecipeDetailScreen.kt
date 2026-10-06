package org.calamares.miga.ui.detail

import org.calamares.miga.ui.components.ErrorMessage
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.ui.components.AiContentNotice
import org.calamares.miga.ui.components.rememberAiEnabled
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Autorenew
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
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.StarBorder
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import org.calamares.miga.data.export.RecipeExporter
import org.calamares.miga.data.model.HealthColorLevel
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.formatQuantity
import org.calamares.miga.ui.theme.HealthAmberContainer
import org.calamares.miga.ui.theme.HealthAmberOn
import org.calamares.miga.ui.theme.HealthGreenContainer
import org.calamares.miga.ui.theme.HealthGreenOn
import org.calamares.miga.ui.theme.HealthRedContainer
import org.calamares.miga.ui.theme.HealthRedOn
import kotlinx.coroutines.launch
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import org.calamares.miga.data.model.displayCategoryName

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
    val nutritionState by viewModel.nutritionState.collectAsState()
    val substitutionDialogState by viewModel.substitutionDialogState.collectAsState()
    val aiEnabled = rememberAiEnabled()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCookMode by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    // La barra superior pasa de transparente (sobre la foto) a sólida al desplazarse bajo la foto.
    val headerScrolled by remember { derivedStateOf { scrollState.value > with(density) { (HEADER_HEIGHT - 96.dp).toPx() } } }

    LaunchedEffect(Unit) { viewModel.fetchHealthinessIfNeeded() }
    LaunchedEffect(Unit) { viewModel.fetchNutritionIfNeeded() }

    fun addToShoppingList() {
        viewModel.addIngredientsToShoppingList()
        Toast.makeText(context, L10n.str(R.string.added_shopping_list), Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        floatingActionButton = {
            if (recipe?.stepGroups?.any { it.instructions.isNotEmpty() } == true) {
                ExtendedFloatingActionButton(
                    onClick = { showCookMode = true },
                    icon = { Icon(Icons.Filled.PlayArrow, null) },
                    text = { Text(L10n.str(R.string.cooking_mode)) },
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        }
    ) { padding ->
        val current = recipe
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (current == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                RecipeDetailContent(
                    recipe = current,
                    scrollState = scrollState,
                    healthState = healthState,
                    onRetryHealth = { viewModel.retryHealthCheck() },
                    nutritionState = nutritionState,
                    onRetryNutrition = { viewModel.retryNutritionCheck() },
                    onSubstituteIngredient = { name: String -> viewModel.findSubstitutesFor(name) }.takeIf { aiEnabled },
                    onRatingChange = { stars -> viewModel.setRating(stars) },
                    onAddToShoppingList = { addToShoppingList() }
                )
            }

            // Barra superior superpuesta a la foto.
            val barColor by animateColorAsState(
                if (headerScrolled || current?.coverPhotoUri == null) MaterialTheme.colorScheme.surface else Color.Transparent,
                label = "detailBar"
            )
            val currentBookIsPack = recipeBooks.firstOrNull { it.id == current?.recipeBookId }?.isPack == true
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(barColor)
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val onPhoto = !headerScrolled && current?.coverPhotoUri != null
                HeaderIconButton(onPhoto = onPhoto, onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = L10n.str(R.string.back))
                }
                Text(
                    text = if (headerScrolled) current?.name.orEmpty() else "",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                HeaderIconButton(onPhoto = onPhoto, onClick = { viewModel.toggleFavorite() }) {
                    Icon(
                        imageVector = if (current?.isFavorite == true) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = L10n.str(R.string.favourite),
                        tint = if (current?.isFavorite == true) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
                Box {
                    HeaderIconButton(onPhoto = onPhoto, onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = L10n.str(R.string.more_options))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (!currentBookIsPack) {
                            DropdownMenuItem(text = { Text(L10n.str(R.string.edit)) }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { showMenu = false; onEdit() })
                        }
                        DropdownMenuItem(
                            text = { Text(L10n.str(R.string.share_text)) },
                            leadingIcon = { Icon(Icons.Filled.Share, null) },
                            onClick = { showMenu = false; recipe?.let { RecipeExporter.shareAsText(context, it) } }
                        )
                        DropdownMenuItem(
                            text = { Text(L10n.str(R.string.export_pdf)) },
                            leadingIcon = { Icon(Icons.Filled.PictureAsPdf, null) },
                            onClick = { showMenu = false; recipe?.let { scope.launch { RecipeExporter.shareAsPdf(context, it) } } }
                        )
                        DropdownMenuItem(
                            text = { Text(L10n.str(R.string.export_backup)) },
                            leadingIcon = { Icon(Icons.Filled.Archive, null) },
                            onClick = { showMenu = false; recipe?.let { RecipeExporter.shareRecipe(context, it) } }
                        )
                        DropdownMenuItem(
                            text = { Text(L10n.str(R.string.move_another_book)) },
                            leadingIcon = { Icon(Icons.Filled.SwapHoriz, null) },
                            onClick = { showMenu = false; showMoveDialog = true }
                        )
                        if (!currentBookIsPack) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(L10n.str(R.string.delete_2), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { showMenu = false; showDeleteConfirm = true }
                            )
                        }
                    }
                }
            }
        }
    }

    val recipeForCookMode = recipe
    if (showCookMode && recipeForCookMode != null) {
        CookModeOverlay(recipe = recipeForCookMode, ttsVoiceName = ttsVoiceName, onClose = { showCookMode = false })
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(L10n.str(R.string.delete_recipe)) },
            text = { Text(L10n.str(R.string.sure_want_delete_x_cant, recipe?.name)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteRecipe(onDeleted = onBack)
                }) { Text(L10n.str(R.string.delete_2), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(L10n.str(R.string.cancel)) }
            }
        )
    }

    val recipeForMove = recipe
    if (showMoveDialog && recipeForMove != null) {
        val otherBooks = recipeBooks.filter { it.id != recipeForMove.recipeBookId && !it.isPack }
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text(L10n.str(R.string.move_another_book)) },
            text = {
                if (otherBooks.isEmpty()) {
                    Text(L10n.str(R.string.dont_have_other_recipe_books))
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
                TextButton(onClick = { showMoveDialog = false }) { Text(L10n.str(R.string.close)) }
            }
        )
    }

    if (substitutionDialogState != SubstitutionDialogState.Hidden) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSubstitutionDialog() },
            title = { Text(L10n.str(R.string.substitute_x, substitutionDialogState.ingredientNameOrNull().orEmpty())) },
            text = {
                when (val state = substitutionDialogState) {
                    is SubstitutionDialogState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            L10n.str(R.string.finding_substitutes_ai),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                    is SubstitutionDialogState.NotConfigured -> Text(L10n.str(R.string.set_up_ai_provider_settings))
                    is SubstitutionDialogState.Error -> ErrorMessage(state.reason, onRetry = { viewModel.findSubstitutesFor(state.ingredientName) })
                    is SubstitutionDialogState.Loaded -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AiContentNotice(
                            feature = L10n.str(R.string.ingredient_substitution),
                            content = { "${state.ingredientName}: " + state.substitutions.joinToString("; ") { "${it.substitute} (${it.notes})" } }
                        )
                        state.substitutions.forEach { substitution ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(substitution.substitute, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    substitution.notes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    SubstitutionDialogState.Hidden -> Unit
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissSubstitutionDialog() }) { Text(L10n.str(R.string.close)) }
            }
        )
    }
}

private fun SubstitutionDialogState.ingredientNameOrNull(): String? = when (this) {
    is SubstitutionDialogState.Loading -> ingredientName
    is SubstitutionDialogState.Loaded -> ingredientName
    is SubstitutionDialogState.NotConfigured -> ingredientName
    is SubstitutionDialogState.Error -> ingredientName
    SubstitutionDialogState.Hidden -> null
}

private val HEADER_HEIGHT = 300.dp

/** Botón de la barra superior: sobre la foto lleva un círculo translúcido para leerse sobre cualquier imagen. */
@Composable
private fun HeaderIconButton(onPhoto: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val container = if (onPhoto) Color.Black.copy(alpha = 0.35f) else Color.Transparent
    val tint = if (onPhoto) Color.White else MaterialTheme.colorScheme.onSurface
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(containerColor = container, contentColor = tint)
    ) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeDetailContent(
    recipe: Recipe,
    scrollState: ScrollState,
    healthState: HealthState,
    onRetryHealth: () -> Unit,
    nutritionState: NutritionState,
    onRetryNutrition: () -> Unit,
    onSubstituteIngredient: ((String) -> Unit)?,
    onRatingChange: (Int) -> Unit,
    onAddToShoppingList: () -> Unit
) {
    var servings by remember(recipe.id) { mutableIntStateOf(recipe.servings) }
    val checkedIngredients = remember(recipe.id) { mutableStateMapOf<String, Boolean>() }
    val scale = if (recipe.servings > 0) servings.toDouble() / recipe.servings else 1.0
    val hasPhoto = recipe.coverPhotoUri != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .navigationBarsPadding()
            .padding(bottom = 96.dp)
    ) {
        if (hasPhoto) {
            PhotoHeader(recipe)
        } else {
            Spacer(modifier = Modifier.statusBarsPadding().height(64.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // Título
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                recipe.categoryName?.let {
                    Text(displayCategoryName(it).uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Text(recipe.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StarRatingRow(rating = recipe.rating, onRatingChange = onRatingChange)
                    if (recipe.timesCooked > 0) {
                        Text(
                            L10n.str(R.string.cooked_x_x, recipe.timesCooked),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            // Datos clave
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                recipe.prepTimeMinutes?.let { FactTile(Icons.Outlined.Timer, L10n.str(R.string.prep_short), L10n.str(R.string.minutes_short, it), Modifier.weight(1f)) }
                recipe.cookTimeMinutes?.let { FactTile(Icons.Outlined.LocalFireDepartment, L10n.str(R.string.cook_short), L10n.str(R.string.minutes_short, it), Modifier.weight(1f)) }
                FactTile(Icons.Outlined.People, L10n.str(R.string.servings_2), "${recipe.servings}", Modifier.weight(1f))
                FactTile(Icons.Outlined.BarChart, L10n.str(R.string.difficulty), recipe.difficulty.label, Modifier.weight(1f))
            }

            // Ingredientes
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(L10n.str(R.string.ingredients), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    ServingsStepper(servings = servings, onChange = { servings = it.coerceIn(1, 99) })
                }
                recipe.ingredientGroups.filter { it.ingredients.isNotEmpty() }.forEach { group ->
                    if (group.name != null) GroupTitle(group.name)
                    RoundedGroup {
                        group.ingredients.forEachIndexed { index, ingredient ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            val key = "${group.name}_$index"
                            val checked = checkedIngredients[key] ?: false
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(value = checked, role = Role.Checkbox, onValueChange = { checkedIngredients[key] = it })
                                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (checked) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                    contentDescription = null,
                                    tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = formatIngredient(ingredient, scale),
                                    style = MaterialTheme.typography.bodyLarge,
                                    textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
                                    color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                                if (onSubstituteIngredient != null) {
                                    IconButton(onClick = { onSubstituteIngredient(ingredient.name) }) {
                                        Icon(
                                            Icons.Filled.Autorenew,
                                            contentDescription = L10n.str(R.string.substitute_x, ingredient.name),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                FilledTonalButton(onClick = onAddToShoppingList, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  " + L10n.str(R.string.add_shopping_list))
                }
            }

            // Preparación
            if (recipe.stepGroups.any { it.instructions.isNotEmpty() }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(L10n.str(R.string.method), style = MaterialTheme.typography.titleLarge)
                    recipe.stepGroups.filter { it.instructions.isNotEmpty() }.forEach { group ->
                        if (group.name != null) GroupTitle(group.name)
                        group.instructions.forEachIndexed { index, instruction ->
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Box(
                                    modifier = Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${index + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Text(instruction, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(top = 4.dp))
                            }
                        }
                    }
                }
            }

            // Salud y nutrición (IA)
            if (healthState != HealthState.Idle || nutritionState != NutritionState.Idle) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(L10n.str(R.string.health_and_nutrition), style = MaterialTheme.typography.titleLarge)
                    RoundedGroup(contentPadding = 16.dp) {
                        HealthBlock(recipe, healthState, onRetryHealth)
                        if (healthState != HealthState.Idle && nutritionState != NutritionState.Idle) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        }
                        NutritionBlock(recipe, nutritionState, onRetryNutrition)
                    }
                }
            }

            // Más información
            if (recipe.utensils.isNotEmpty() || recipe.tags.isNotEmpty() || recipe.notes.isNotBlank() || recipe.source.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (recipe.utensils.isNotEmpty()) {
                        Section(title = L10n.str(R.string.utensils)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                recipe.utensils.forEach { SuggestionChip(onClick = {}, label = { Text(it) }) }
                            }
                        }
                    }
                    if (recipe.notes.isNotBlank()) {
                        Section(title = L10n.str(R.string.notes)) { Text(recipe.notes, style = MaterialTheme.typography.bodyLarge) }
                    }
                    if (recipe.source.isNotBlank()) {
                        Section(title = L10n.str(R.string.source)) {
                            Text(recipe.source, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (recipe.tags.isNotEmpty()) {
                        Section(title = L10n.str(R.string.tags)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                recipe.tags.forEach { SuggestionChip(onClick = {}, label = { Text("#$it") }) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthBlock(recipe: Recipe, healthState: HealthState, onRetry: () -> Unit) {
    when (healthState) {
        HealthState.Loading -> LoadingRow()
        HealthState.NotConfigured -> Text(
            L10n.str(R.string.set_up_ai_provider_settings_3),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        is HealthState.Error -> ErrorMessage(healthState.reason, onRetry = onRetry)
        HealthState.Loaded -> recipe.healthRating?.let { rating ->
            val (containerColor, contentColor, label) = when (rating.color) {
                HealthColorLevel.GREEN -> Triple(HealthGreenContainer, HealthGreenOn, L10n.str(R.string.healthy))
                HealthColorLevel.YELLOW -> Triple(HealthAmberContainer, HealthAmberOn, L10n.str(R.string.moderate))
                HealthColorLevel.RED -> Triple(HealthRedContainer, HealthRedOn, L10n.str(R.string.not_very_healthy))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    label = { Text(label) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = containerColor, labelColor = contentColor)
                )
                Text(rating.description, style = MaterialTheme.typography.bodyMedium)
                AiContentNotice(feature = L10n.str(R.string.health_rating), content = { "${recipe.name}: $label. ${rating.description}" })
            }
        }
        HealthState.Idle -> Unit
    }
}

@Composable
private fun NutritionBlock(recipe: Recipe, nutritionState: NutritionState, onRetry: () -> Unit) {
    when (nutritionState) {
        NutritionState.Loading -> LoadingRow()
        NutritionState.NotConfigured -> Text(
            L10n.str(R.string.set_up_ai_provider_settings_3),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        is NutritionState.Error -> ErrorMessage(nutritionState.reason, onRetry = onRetry)
        NutritionState.Loaded -> recipe.nutritionInfo?.let { nutrition ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(L10n.str(R.string.nutrition_per_serving), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    NutritionStat(value = "${nutrition.caloriesPerServing}", label = "kcal")
                    NutritionStat(value = formatQuantity(nutrition.proteinGrams), label = L10n.str(R.string.protein_g))
                    NutritionStat(value = formatQuantity(nutrition.carbsGrams), label = L10n.str(R.string.carbs_g))
                    NutritionStat(value = formatQuantity(nutrition.fatGrams), label = L10n.str(R.string.fat_g))
                }
                AiContentNotice(
                    feature = L10n.str(R.string.estimated_nutrition),
                    content = {
                        L10n.str(R.string.x_x_kcal_x_g, recipe.name, nutrition.caloriesPerServing, nutrition.proteinGrams, nutrition.carbsGrams, nutrition.fatGrams)
                    }
                )
            }
        }
        NutritionState.Idle -> Unit
    }
}

@Composable
private fun LoadingRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            L10n.str(R.string.analysing_ai),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

/** Foto(s) de cabecera a sangre, deslizables si hay varias, con degradado inferior. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoHeader(recipe: Recipe) {
    val photos = remember(recipe.photos) {
        recipe.photos.sortedByDescending { it.isCover }.map { it.uri }.ifEmpty { listOfNotNull(recipe.coverPhotoUri) }
    }
    val pagerState = rememberPagerState(pageCount = { photos.size })
    Box(modifier = Modifier.fillMaxWidth().height(HEADER_HEIGHT).background(MaterialTheme.colorScheme.surfaceVariant)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            AsyncImage(
                model = photos[page],
                contentDescription = recipe.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)))
        )
        if (photos.size > 1) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                photos.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (index == pagerState.currentPage) 1f else 0.6f))
                    )
                }
            }
        }
    }
}

@Composable
private fun FactTile(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GroupTitle(name: String) {
    Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
}

/** Contenedor redondeado como los grupos de Ajustes. */
@Composable
private fun RoundedGroup(contentPadding: androidx.compose.ui.unit.Dp = 0.dp, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(contentPadding)
    ) { content() }
}

private const val MAX_RATING_STARS = 5

@Composable
private fun StarRatingRow(rating: Int?, onRatingChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (star in 1..MAX_RATING_STARS) {
            val filled = rating != null && star <= rating
            IconButton(onClick = { onRatingChange(star) }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = L10n.str(R.string.stars_x, star),
                    tint = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ServingsStepper(servings: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onChange(servings - 1) }) { Icon(Icons.Filled.Remove, contentDescription = L10n.str(R.string.fewer_servings)) }
        Text("$servings", style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { onChange(servings + 1) }) { Icon(Icons.Filled.Add, contentDescription = L10n.str(R.string.more_servings)) }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun NutritionStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
