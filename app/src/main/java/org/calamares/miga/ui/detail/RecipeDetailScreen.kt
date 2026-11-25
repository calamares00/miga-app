package org.calamares.miga.ui.detail

import org.calamares.miga.data.carbs.FoodGroups
import org.calamares.miga.data.health.PortionPlan
import androidx.compose.ui.res.painterResource
import org.calamares.miga.data.health.HealthPrefs
import org.calamares.miga.data.health.AllergenDisplay
import org.calamares.miga.ui.components.keepScrollInSheet
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import org.calamares.miga.data.allergens.AllergenAnalysis
import org.calamares.miga.data.carbs.CarbAnalysis
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.filled.AutoAwesome
import kotlinx.coroutines.launch
import org.calamares.miga.ui.components.AdaptiveScaffold
import org.calamares.miga.ui.components.FormattedText
import org.calamares.miga.ui.components.ButtonContent
import org.calamares.miga.ui.components.ErrorMessage
import org.calamares.miga.L10n
import org.calamares.miga.R
import androidx.compose.foundation.layout.heightIn
import org.calamares.miga.ui.components.sheetContentHeight
import androidx.compose.material3.ModalBottomSheet
import org.calamares.miga.data.tips.CookingTips
import org.calamares.miga.data.tips.tipsForRecipe
import org.calamares.miga.ui.tips.RecipeTipsContent
import org.calamares.miga.ui.tips.RecipeTipsRow
import org.calamares.miga.ui.components.AiContentNotice
import org.calamares.miga.ui.components.rememberAiReady
import androidx.compose.material.icons.filled.Refresh
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.calamares.miga.data.export.FileExports
import org.calamares.miga.ui.components.ShareSheet
import org.calamares.miga.data.export.ShareAction
import org.calamares.miga.data.model.formatDuration
import org.calamares.miga.data.model.Recipe
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.calamares.miga.data.model.RecipeOrigin
import org.calamares.miga.data.model.displayCategoryName

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeDetailScreen(
    viewModel: RecipeDetailViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    active: Boolean = true,
    onOpenCookMode: (recipe: Recipe, ttsVoiceName: String?) -> Unit,
    onOpenPhoto: (recipe: Recipe, index: Int) -> Unit,
    onOpenTips: () -> Unit = {}
) {
    val recipe by viewModel.recipe.collectAsStateWithLifecycle()
    val recipeBooks by viewModel.recipeBooks.collectAsStateWithLifecycle()
    val ttsVoiceName by viewModel.ttsVoiceName.collectAsStateWithLifecycle()
    val healthState by viewModel.healthState.collectAsStateWithLifecycle()
    val canRecalculateAnalysis by viewModel.canRecalculateAnalysis.collectAsStateWithLifecycle()
    val nutritionState by viewModel.nutritionState.collectAsStateWithLifecycle()
    val carbPortionGrams by viewModel.carbPortionGrams.collectAsStateWithLifecycle()
    val healthPrefs by viewModel.healthPrefs.collectAsStateWithLifecycle()
    val portionPlan by viewModel.portionPlan.collectAsStateWithLifecycle()
    var showAllergensFromMenu by remember { mutableStateOf(false) }
    val substitutionDialogState by viewModel.substitutionDialogState.collectAsStateWithLifecycle()
    val polishState by viewModel.polishState.collectAsStateWithLifecycle()
    val polishInBackground by viewModel.polishInBackground.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val aiEnabled = rememberAiReady()
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    /** Choosing a book to move (false) or copy (true) the recipe to; null when not asked. */
    var bookDialogCopies by remember { mutableStateOf<Boolean?>(null) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showTipsSheet by remember { mutableStateOf(false) }
    val savedTips by viewModel.savedTips.collectAsStateWithLifecycle()
    val recipeTips = remember(recipe) { recipe?.let { tipsForRecipe(it, CookingTips.all(context)) }.orEmpty() }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    /**
     * The top bar turns from transparent (over the photo) to solid once the content scrolls under
     * the photo.
     */
    val headerScrolled by remember { derivedStateOf { scrollState.value > with(density) { (HEADER_HEIGHT - 96.dp).toPx() } } }

    // Only for the recipe on screen, not for the neighbours a swipe shows for a moment.
    LaunchedEffect(active) {
        if (active) {
            viewModel.fetchHealthinessIfNeeded()
            viewModel.fetchNutritionIfNeeded()
        }
    }

    fun addToShoppingList() {
        viewModel.addIngredientsToShoppingList()
        Toast.makeText(context, L10n.str(R.string.added_shopping_list), Toast.LENGTH_SHORT).show()
    }

    AdaptiveScaffold(
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.navigationBarsPadding()) },
        floatingActionButton = {
            if (recipe?.stepGroups?.any { it.instructions.isNotEmpty() } == true) {
                // Full at the top and while scrolling up; only the icon while reading down, so it
                // covers less of the text.
                var lastScroll by remember { mutableIntStateOf(0) }
                var fabExpanded by remember { mutableStateOf(true) }
                LaunchedEffect(scrollState) {
                    snapshotFlow { scrollState.value }.collect { value ->
                        fabExpanded = value < 48 || value < lastScroll
                        lastScroll = value
                    }
                }
                ExtendedFloatingActionButton(
                    onClick = { recipe?.let { onOpenCookMode(it, ttsVoiceName) } },
                    expanded = fabExpanded,
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = L10n.str(R.string.cooking_mode).takeIf { !fabExpanded }) },
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
                    carbPortionGrams = carbPortionGrams,
                    health = healthPrefs,
                    portionPlan = portionPlan,
                    onRecalculateAnalysis = { viewModel.recalculateAnalysis() }.takeIf { canRecalculateAnalysis },
                    onSubstituteIngredient = { name: String -> viewModel.findSubstitutesFor(name) }.takeIf { aiEnabled },
                    onRatingChange = { stars -> viewModel.setRating(stars) },
                    onAddToShoppingList = { addToShoppingList() },
                    onPhotoClick = { index -> onOpenPhoto(current, index) },
                    tipCount = recipeTips.size,
                    onShowTips = { showTipsSheet = true },
                    journalActions = remember(viewModel) {
                        JournalActions(
                            onAdd = viewModel::addJournalNote,
                            onEdit = viewModel::editJournalNote,
                            onDelete = viewModel::deleteJournalNote
                        )
                    }
                )
            }

            // Top bar drawn over the photo.
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = L10n.str(R.string.back))
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
                HeaderIconButton(onPhoto = onPhoto, onClick = { showShareSheet = true }) {
                    Icon(Icons.Filled.Share, contentDescription = L10n.str(R.string.share))
                }
                // The same as "More actions" of one selected recipe; a pack recipe is only copied.
                Box {
                    HeaderIconButton(onPhoto = onPhoto, onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = L10n.str(R.string.more_options))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (!currentBookIsPack) {
                            DropdownMenuItem(text = { Text(L10n.str(R.string.edit)) }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { showMenu = false; onEdit() })
                            if (aiEnabled) {
                                DropdownMenuItem(
                                    // While it runs in the background, the same item shows its progress again.
                                    text = { Text(L10n.str(if (polishInBackground) R.string.polish_show_progress else R.string.polish_menu)) },
                                    leadingIcon = { Icon(Icons.Filled.AutoAwesome, null) },
                                    onClick = {
                                        showMenu = false
                                        if (polishInBackground) viewModel.showPolishProgress() else viewModel.polishRecipe()
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(L10n.str(R.string.move_another_book)) },
                                leadingIcon = { Icon(Icons.Filled.Folder, null) },
                                onClick = { showMenu = false; bookDialogCopies = false }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(L10n.str(R.string.bulk_copy)) },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                            onClick = { showMenu = false; bookDialogCopies = true }
                        )
                        DropdownMenuItem(
                            text = { Text(L10n.str(R.string.bulk_add_to_shopping)) },
                            leadingIcon = { Icon(Icons.Filled.ShoppingCart, null) },
                            onClick = { showMenu = false; addToShoppingList() }
                        )
                        DropdownMenuItem(
                            text = { Text(L10n.str(R.string.allergens_menu)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_allergen_gluten), null) },
                            onClick = { showMenu = false; showAllergensFromMenu = true }
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

    recipe?.let { current ->
        RecipePolishDialog(
            state = polishState,
            recipe = current,
            onApply = {
                viewModel.applyPolish {
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = L10n.str(R.string.polish_applied),
                            actionLabel = L10n.str(R.string.undo),
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.undoPolish()
                    }
                }
            },
            onRetry = { viewModel.polishRecipe() },
            onDismiss = { viewModel.dismissPolish() },
            onHide = { viewModel.hidePolishProgress() },
            onCancel = { viewModel.cancelPolish() }
        )
    }

    if (showAllergensFromMenu) {
        recipe?.let { current -> AllergenSheet(remember(current.ingredientGroups) { AllergenAnalysis.ofRecipe(current) }) { showAllergensFromMenu = false } }
    }

    if (showTipsSheet && recipeTips.isNotEmpty()) {
        ModalBottomSheet(onDismissRequest = { showTipsSheet = false }, sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))) {
            Column(modifier = Modifier.keepScrollInSheet()) {
                // Capped by the window, not the sheet, so the sheet doesn't bounce (see sheetContentHeight).
                Column(modifier = Modifier.heightIn(max = sheetContentHeight(0.9f)).verticalScroll(rememberScrollState())) {
                    RecipeTipsContent(
                        tips = recipeTips,
                        saved = savedTips,
                        onSavedChange = viewModel::setTipSaved,
                        onSeeAll = { showTipsSheet = false; onOpenTips() }
                    )
                }
            }
        }
    }

    if (showShareSheet) {
        recipe?.let { current ->
            val accent = MaterialTheme.colorScheme.primary.toArgb()
            ShareSheet(
                title = current.name,
                actions = ShareAction.entries.toList(),
                onAction = { action ->
                    if (!FileExports.shareRecipeAs(context, current, action, accent)) {
                        Toast.makeText(context, L10n.str(R.string.pdf_already_running), Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { showShareSheet = false }
            )
        }
    }

    val recipeForMove = recipe
    val copying = bookDialogCopies
    if (copying != null && recipeForMove != null) {
        val otherBooks = recipeBooks.filter { it.id != recipeForMove.recipeBookId && !it.isPack }
        val dialogTitle = L10n.str(if (copying) R.string.bulk_copy else R.string.move_another_book)
        AlertDialog(
            onDismissRequest = { bookDialogCopies = null },
            title = { Text(dialogTitle) },
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
                                        bookDialogCopies = null
                                        if (copying) {
                                            viewModel.copyToBook(context, book.id)
                                            Toast.makeText(context, L10n.str(R.string.recipe_copied_to_x, book.name), Toast.LENGTH_SHORT).show()
                                        } else {
                                            viewModel.moveToBook(book.id)
                                        }
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
                TextButton(onClick = { bookDialogCopies = null }) { Text(L10n.str(R.string.close)) }
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

internal val HEADER_HEIGHT = 300.dp

/** Top bar button: over the photo it gets a translucent circle so it reads on any image. */
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
    /** Grams in a carbohydrate portion when the user counts them; null hides them. */
    carbPortionGrams: Int?,
    /** Which allergens to show (Settings > Health). */
    health: HealthPrefs = HealthPrefs(),
    /** The user's portion plan, to fit the recipe to a meal; null hides it. */
    portionPlan: PortionPlan? = null,
    onRecalculateAnalysis: (() -> Unit)?,
    onSubstituteIngredient: ((String) -> Unit)?,
    onRatingChange: (Int) -> Unit,
    onAddToShoppingList: () -> Unit,
    onPhotoClick: (Int) -> Unit,
    tipCount: Int,
    onShowTips: () -> Unit,
    journalActions: JournalActions
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
            PhotoHeader(recipe, onPhotoClick)
        } else {
            Spacer(modifier = Modifier.statusBarsPadding().height(64.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // Title: category, origin and rating in one line, then the name.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val originLabel = RecipeOrigin.label(recipe.origin, recipe.originCountry)
                FlowRow(verticalArrangement = Arrangement.Center, itemVerticalAlignment = Alignment.CenterVertically) {
                    recipe.categoryName?.let {
                        Text(displayCategoryName(it).uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Dot()
                    }
                    originLabel?.let {
                        Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Dot()
                    }
                    StarRatingRow(rating = recipe.rating, onRatingChange = onRatingChange, small = true)
                    if (recipe.timesCooked > 0) {
                        Dot()
                        Text(
                            L10n.str(R.string.cooked_x_x, recipe.timesCooked),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(recipe.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                recipe.sharedBy?.let {
                    Text(L10n.str(R.string.shared_by_x, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Key facts in one strip: total time, servings and difficulty.
            val totalMinutes = listOfNotNull(recipe.prepTimeMinutes, recipe.cookTimeMinutes).sum().takeIf { it > 0 }
            FactsStrip(
                listOfNotNull(
                    totalMinutes?.let { minutes ->
                        Fact(
                            Icons.Outlined.Timer,
                            formatDuration(minutes),
                            // Prep and cooking times stay available to screen readers.
                            listOfNotNull(
                                recipe.prepTimeMinutes?.let { "${L10n.str(R.string.prep_short)} ${formatDuration(it)}" },
                                recipe.cookTimeMinutes?.let { "${L10n.str(R.string.cook_short)} ${formatDuration(it)}" }
                            ).joinToString(", ")
                        )
                    },
                    Fact(Icons.Outlined.People, L10n.plural(R.plurals.servings_count_x, servings), null),
                    Fact(Icons.Outlined.BarChart, recipe.difficulty.label, L10n.str(R.string.difficulty) + ": " + recipe.difficulty.label)
                )
            )

            // Allergens in one row, with what coeliacs need to know under them; the details
            // open in a sheet.
            val allergens = remember(recipe.ingredientGroups) { AllergenAnalysis.ofRecipe(recipe) }
            var showAllergens by rememberSaveable { mutableStateOf(false) }
            if (recipe.ingredientGroups.any { group -> group.ingredients.any { it.name.isNotBlank() } }) {
                when (health.allergenDisplay) {
                    AllergenDisplay.ALL -> AllergenLine(allergens) { showAllergens = true }
                    AllergenDisplay.MINE -> MyAllergenLine(allergens, health.myAllergens, health.coeliac) { showAllergens = true }
                    AllergenDisplay.NONE -> MyAllergenLine(allergens, null, health.coeliac) { showAllergens = true }
                }
            }
            if (showAllergens) AllergenSheet(allergens) { showAllergens = false }

            // Carbohydrates per serving, for those who count them.
            if (carbPortionGrams != null && recipe.ingredientGroups.any { group -> group.ingredients.any { it.name.isNotBlank() } }) {
                val carbs = remember(recipe.ingredientGroups, recipe.servings) { CarbAnalysis.ofRecipe(recipe) }
                var showCarbs by rememberSaveable { mutableStateOf(false) }
                CarbLine(carbs, carbPortionGrams) { showCarbs = true }
                if (showCarbs) CarbSheet(carbs, carbPortionGrams) { showCarbs = false }
                if (portionPlan != null) {
                    val perServing = remember(recipe.ingredientGroups, recipe.servings) { FoodGroups.ofRecipe(recipe) }
                    var showPlan by rememberSaveable { mutableStateOf(false) }
                    PlanLine(perServing) { showPlan = true }
                    if (showPlan) PlanSheet(perServing, portionPlan, recipe.servings) { showPlan = false }
                }
            }

            // Ingredients
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(L10n.str(R.string.ingredients), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    // The new value is computed from the state when the tap happens, not from the
                    // last composition, so quick taps are never lost.
                    ServingsStepper(servings = servings, onStep = { delta -> servings = (servings + delta).coerceIn(MIN_SERVINGS, MAX_SERVINGS) })
                }
                // Each ingredient as written ("50 g de panceta"), the amount in the theme colour;
                // compact rows without per-row buttons.
                recipe.ingredientGroups.filter { it.ingredients.isNotEmpty() }.forEach { group ->
                    if (group.name != null) GroupTitle(group.name)
                    RoundedGroup(contentPadding = 6.dp) {
                        Column {
                            group.ingredients.forEachIndexed { index, ingredient ->
                                val key = "${group.name}_$index"
                                IngredientLine(
                                    ingredient = ingredient,
                                    scale = scale,
                                    checked = checkedIngredients[key] ?: false,
                                    onCheckedChange = { checkedIngredients[key] = it },
                                    onSubstitute = onSubstituteIngredient?.let { substitute -> { substitute(ingredient.name) } },
                                    watchedAllergens = health.ingredientWarnings
                                )
                            }
                        }
                    }
                }
                if (onSubstituteIngredient != null) {
                    Text(
                        L10n.str(R.string.long_press_to_substitute),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(onClick = onAddToShoppingList, modifier = Modifier.fillMaxWidth()) {
                    ButtonContent(Icons.Filled.ShoppingCart, L10n.str(R.string.add_shopping_list))
                }
            }

            // Bundled cooking tips that help with this recipe (its ingredients, equipment, steps).
            if (tipCount > 0) RecipeTipsRow(count = tipCount, onClick = onShowTips)

            // Steps
            if (recipe.stepGroups.any { it.instructions.isNotEmpty() }) {
                val stepIngredients = remember(recipe.ingredientGroups) { recipe.ingredientGroups.flatMap { it.ingredients } }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(L10n.str(R.string.method), style = MaterialTheme.typography.titleLarge)
                    recipe.stepGroups.filter { it.instructions.isNotEmpty() }.forEach { group ->
                        if (group.name != null) GroupTitle(group.name)
                        group.instructions.forEachIndexed { index, instruction ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                                Box(
                                    modifier = Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${index + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                FormattedText(
                                    instruction,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f).padding(top = 2.dp),
                                    highlightTimes = true,
                                    ingredients = stepIngredients
                                )
                            }
                        }
                    }
                }
            }

            // Health and nutrition: saved values always show; the assistant only creates them.
            if (healthState != HealthState.Idle || nutritionState != NutritionState.Idle) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(L10n.str(R.string.health_and_nutrition), style = MaterialTheme.typography.titleLarge)
                    RoundedGroup(contentPadding = 16.dp) {
                        val sameNotice = when {
                            healthState == HealthState.NotConfigured && nutritionState == NutritionState.NotConfigured -> R.string.health_needs_assistant
                            healthState == HealthState.PackPending && nutritionState == NutritionState.PackPending -> R.string.health_pack_pending
                            else -> null
                        }
                        if (sameNotice != null) {
                            NoticeText(L10n.str(sameNotice))
                        } else {
                            HealthBlock(recipe, healthState, onRetryHealth)
                            if (healthState != HealthState.Idle && nutritionState != NutritionState.Idle) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            }
                            NutritionBlock(recipe, nutritionState, onRetryNutrition)
                        }
                        val analysing = healthState == HealthState.Loading || nutritionState == NutritionState.Loading
                        val hasValues = recipe.healthRating != null || recipe.nutritionInfo != null
                        // One notice for both: what they are, that they are not medical advice, and a way to report them.
                        val shownHealth = recipe.healthRating.takeIf { healthState == HealthState.Loaded }
                        val shownNutrition = recipe.nutritionInfo.takeIf { nutritionState == NutritionState.Loaded }
                        if (shownHealth != null || shownNutrition != null) {
                            AiContentNotice(
                                feature = L10n.str(R.string.health_and_nutrition),
                                text = L10n.str(R.string.ai_health_disclaimer),
                                content = {
                                    listOfNotNull(
                                        shownHealth?.let { "${recipe.name}: ${it.description}" },
                                        shownNutrition?.let {
                                            L10n.str(R.string.x_x_kcal_x_g, recipe.name, it.caloriesPerServing, it.proteinGrams, it.carbsGrams, it.fatGrams)
                                        }
                                    ).joinToString("\n")
                                },
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                        if (onRecalculateAnalysis != null && hasValues && !analysing) {
                            TextButton(onClick = onRecalculateAnalysis, modifier = Modifier.padding(top = 4.dp)) {
                                ButtonContent(Icons.Filled.Refresh, L10n.str(R.string.recalculate_with_assistant))
                            }
                        }
                    }
                }
            }

            RecipeJournalSection(notes = recipe.journal, actions = journalActions)

            // More information
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
                        Section(title = L10n.str(R.string.notes)) { FormattedText(recipe.notes, style = MaterialTheme.typography.bodyLarge) }
                    }
                    if (recipe.source.isNotBlank()) {
                        Section(title = L10n.str(R.string.source)) { SourceLine(recipe.source) }
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

