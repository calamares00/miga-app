package org.calamares.miga.ui.ideas

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowDropDown
import org.calamares.miga.ui.theme.recipePhotoFrame
import androidx.compose.animation.AnimatedVisibility
import org.calamares.miga.ui.components.AdaptiveScaffold
import org.calamares.miga.ui.components.ButtonContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.ideas.IdeaRecipe
import org.calamares.miga.data.ideas.IdeasAnswer
import org.calamares.miga.data.ideas.IdeasDish
import org.calamares.miga.data.ideas.IdeasFilters
import org.calamares.miga.data.ideas.IdeasMeal
import org.calamares.miga.data.ideas.IdeasStyle
import org.calamares.miga.data.ideas.MAX_IDEAS_QUESTION_CHARS
import org.calamares.miga.data.ideas.NewDishIdea
import org.calamares.miga.data.ideas.RecipeReferences
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.ui.components.AiContentNotice
import org.calamares.miga.ui.components.AiProgressView
import org.calamares.miga.ui.components.ErrorMessage

/**
 * Ideas: AI recommendations based on the user's recipes. It opens on ready-made ideas asked with
 * one tap; the bar at the bottom combines options (meal, style, kind of dish, utensil, each a
 * dropdown) with any typed question, including cooking advice. Answers form a conversation;
 * recommended recipes open their detail and new dishes can be created with the AI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeasScreen(
    viewModel: IdeasViewModel,
    onBack: () -> Unit,
    onRecipeClick: (Long) -> Unit,
    onCreateDish: (bookId: Long, dish: NewDishIdea) -> Unit
) {
    val entries by viewModel.entries.collectAsState()
    val recipes by viewModel.recipes.collectAsState()
    val books by viewModel.targetBooks.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val utensils by viewModel.utensilOptions.collectAsState()
    val progress by viewModel.progress.collectAsState()
    var question by remember { mutableStateOf("") }
    var dishToCreate by remember { mutableStateOf<NewDishIdea?>(null) }
    var addingUtensil by remember { mutableStateOf(false) }
    var openGroup by remember { mutableStateOf<FilterGroup?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val busy = entries.any { it.state == IdeasEntryState.Loading }

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.size - 1)
    }

    fun send() {
        if (busy || (question.isBlank() && filters.isEmpty)) return
        viewModel.send(question)
        question = ""
    }

    AdaptiveScaffold(
        // The keyboard is handled by the NavHost; the navigation bar only by the input bar below,
        // so it does not stay padded while the keyboard covers it.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(L10n.str(R.string.ideas)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = L10n.str(R.string.back)) }
                },
                actions = {
                    if (entries.isNotEmpty() && !busy) {
                        IconButton(onClick = { viewModel.clear() }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = L10n.str(R.string.ideas_new_conversation))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (entries.isEmpty()) {
                    item(key = "welcome") {
                        IdeasWelcome(enabled = !busy, onPreset = viewModel::askPreset)
                    }
                }
                items(entries, key = { it.id }) { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuestionBubble(entry.label)
                        when (val state = entry.state) {
                            IdeasEntryState.Loading -> Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AiProgressView(
                                    progress = progress,
                                    fallback = L10n.str(R.string.ideas_thinking),
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                            is IdeasEntryState.Failed -> ErrorMessage(state.reason, onRetry = { viewModel.retry(entry.id) })
                            is IdeasEntryState.Answered -> AnswerCard(
                                answer = state.answer,
                                recipes = recipes,
                                onRecipeClick = onRecipeClick,
                                onCreateDish = { dishToCreate = it },
                                onAddToShopping = {
                                    viewModel.addToShoppingList(state.answer) { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
                                }
                            )
                        }
                    }
                }
            }
            // Everything to ask sits here, within reach of the thumb: the options as dropdowns and
            // the field. One send button takes the text, the options or both.
            Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 2.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                        .padding(vertical = 8.dp)
                ) {
                    FilterBar(filters = filters, onOpen = { openGroup = it }, onClear = viewModel::clearFilters)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = question,
                            onValueChange = { question = it.take(MAX_IDEAS_QUESTION_CHARS) },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(L10n.str(if (filters.isEmpty) R.string.ideas_ask_placeholder else R.string.ideas_details_placeholder))
                            },
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 4
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledIconButton(
                            onClick = ::send,
                            enabled = (question.isNotBlank() || !filters.isEmpty) && !busy,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = L10n.str(R.string.ideas_send))
                        }
                    }
                }
            }
        }
    }

    openGroup?.let { group ->
        FilterGroupSheet(
            group = group,
            filters = filters,
            utensils = utensils,
            onMeal = viewModel::setMeal,
            onStyle = viewModel::toggleStyle,
            onDish = viewModel::toggleDish,
            onUtensil = viewModel::toggleUtensil,
            onAddUtensil = { addingUtensil = true },
            onDismiss = { openGroup = null }
        )
    }

    if (addingUtensil) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingUtensil = false },
            title = { Text(L10n.str(R.string.ideas_other_utensil)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text(L10n.str(R.string.ideas_other_utensil_hint)) }
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.addCustomUtensil(name); addingUtensil = false }, enabled = name.isNotBlank()) {
                    Text(L10n.str(R.string.add))
                }
            },
            dismissButton = { TextButton(onClick = { addingUtensil = false }) { Text(L10n.str(R.string.cancel)) } }
        )
    }

    dishToCreate?.let { dish ->
        AlertDialog(
            onDismissRequest = { dishToCreate = null },
            title = { Text(L10n.str(R.string.ideas_choose_book)) },
            text = {
                if (books.isEmpty()) {
                    Text(L10n.str(R.string.ideas_no_books))
                } else {
                    Column {
                        books.forEach { book ->
                            Text(
                                book.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        dishToCreate = null
                                        onCreateDish(book.id, dish)
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { dishToCreate = null }) { Text(L10n.str(R.string.cancel)) } }
        )
    }
}

/** The groups of options, each a dropdown in the bar above the field. */
private enum class FilterGroup { MEAL, STYLE, DISH, UTENSIL }

/** A ready-made idea of the start screen: asked at once with a tap. */
private data class IdeaPreset(val emoji: String, val title: String, val subtitle: String, val filters: IdeasFilters)

/** Ready-made ideas; the first follows the time of day (breakfast, lunch or dinner). */
private fun ideaPresets(): List<IdeaPreset> {
    val hour = java.time.LocalTime.now().hour
    val (nowTitle, nowMeal) = when (hour) {
        in 5..11 -> R.string.ideas_preset_breakfast to IdeasMeal.BREAKFAST
        in 12..16 -> R.string.ideas_preset_lunch to IdeasMeal.LUNCH
        else -> R.string.ideas_preset_dinner to IdeasMeal.DINNER
    }
    return listOf(
        IdeaPreset(mealEmoji(nowMeal), L10n.str(nowTitle), L10n.str(R.string.ideas_preset_now_desc), IdeasFilters(meal = nowMeal)),
        IdeaPreset(mealEmoji(IdeasMeal.WEEKLY_MENU), L10n.str(R.string.ideas_weekly_menu), L10n.str(R.string.ideas_preset_week_desc), IdeasFilters(meal = IdeasMeal.WEEKLY_MENU)),
        IdeaPreset("⚡", L10n.str(R.string.ideas_preset_quick), L10n.str(R.string.ideas_preset_quick_desc), IdeasFilters(styles = setOf(IdeasStyle.QUICK))),
        IdeaPreset("🥗", L10n.str(R.string.ideas_preset_healthy), L10n.str(R.string.ideas_preset_healthy_desc), IdeasFilters(styles = setOf(IdeasStyle.HEALTHY))),
        IdeaPreset(seasonEmoji(), L10n.str(R.string.ideas_seasonal), L10n.str(R.string.ideas_preset_season_desc), IdeasFilters(styles = setOf(IdeasStyle.SEASONAL))),
        IdeaPreset(mealEmoji(IdeasMeal.DESSERT), L10n.str(R.string.ideas_preset_dessert), L10n.str(R.string.ideas_preset_dessert_desc), IdeasFilters(meal = IdeasMeal.DESSERT))
    )
}

/** Start screen: a short header and ready-made ideas in two columns, without scrolling to reach anything. */
@Composable
private fun IdeasWelcome(enabled: Boolean, onPreset: (IdeasFilters) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(L10n.str(R.string.ideas_what_do_you_fancy), style = MaterialTheme.typography.titleLarge)
                Text(
                    L10n.str(R.string.ideas_intro_short),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        ideaPresets().chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                row.forEach { preset ->
                    PresetCard(preset, enabled, Modifier.weight(1f).fillMaxHeight()) { onPreset(preset.filters) }
                }
            }
        }
        Text(
            L10n.str(R.string.ideas_bar_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun PresetCard(preset: IdeaPreset, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(preset.emoji, style = MaterialTheme.typography.headlineSmall)
            Text(preset.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(preset.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The options as four dropdowns in one row (scrolls sideways on narrow screens). Each shows what is
 * chosen in it; a last chip clears them all.
 */
@Composable
private fun FilterBar(filters: IdeasFilters, onOpen: (FilterGroup) -> Unit, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterGroup.entries.forEach { group ->
            val chosen = when (group) {
                FilterGroup.MEAL -> listOfNotNull(filters.meal?.let { mealLabel(it) })
                FilterGroup.STYLE -> filters.styles.map { styleLabel(it) }
                FilterGroup.DISH -> filters.dishes.map { dishLabel(it) }
                FilterGroup.UTENSIL -> filters.utensils
            }
            val label = when (chosen.size) {
                0 -> groupTitle(group)
                1 -> chosen.single()
                else -> "${groupTitle(group)} · ${chosen.size}"
            }
            FilterChip(
                selected = chosen.isNotEmpty(),
                onClick = { onOpen(group) },
                label = { Text(label, maxLines = 1) },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
        if (!filters.isEmpty) {
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.ideas_clear))
            }
        }
    }
}

private fun groupTitle(group: FilterGroup): String = L10n.str(
    when (group) {
        FilterGroup.MEAL -> R.string.ideas_filter_meal
        FilterGroup.STYLE -> R.string.ideas_group_style
        FilterGroup.DISH -> R.string.ideas_filter_dish
        FilterGroup.UTENSIL -> R.string.ideas_filter_utensil
    }
)

/** The options of one group; changes apply at once and "Done" closes it. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterGroupSheet(
    group: FilterGroup,
    filters: IdeasFilters,
    utensils: List<String>,
    onMeal: (IdeasMeal) -> Unit,
    onStyle: (IdeasStyle) -> Unit,
    onDish: (IdeasDish) -> Unit,
    onUtensil: (String) -> Unit,
    onAddUtensil: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(groupTitle(group), style = MaterialTheme.typography.titleLarge)
            if (group == FilterGroup.MEAL || group == FilterGroup.UTENSIL) {
                Text(
                    L10n.str(if (group == FilterGroup.MEAL) R.string.ideas_filter_meal_hint else R.string.ideas_filter_utensil_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (group) {
                    FilterGroup.MEAL -> IdeasMeal.entries.forEach { meal ->
                        FilterChip(selected = filters.meal == meal, onClick = { onMeal(meal) }, label = { Text("${mealEmoji(meal)}  ${mealLabel(meal)}") })
                    }
                    FilterGroup.STYLE -> IdeasStyle.entries.forEach { style ->
                        val label = if (style == IdeasStyle.SEASONAL) "${seasonEmoji()}  ${styleLabel(style)}" else styleLabel(style)
                        FilterChip(selected = style in filters.styles, onClick = { onStyle(style) }, label = { Text(label) })
                    }
                    FilterGroup.DISH -> IdeasDish.entries.forEach { dish ->
                        FilterChip(selected = dish in filters.dishes, onClick = { onDish(dish) }, label = { Text(dishLabel(dish)) })
                    }
                    FilterGroup.UTENSIL -> {
                        utensils.forEach { name ->
                            FilterChip(selected = filters.utensils.any { it.equals(name, ignoreCase = true) }, onClick = { onUtensil(name) }, label = { Text(name) })
                        }
                        FilterChip(
                            selected = false,
                            onClick = onAddUtensil,
                            label = { Text(L10n.str(R.string.ideas_other)) },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(L10n.str(R.string.ideas_done))
            }
        }
    }
}

@Composable
private fun QuestionBubble(text: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
            modifier = Modifier.padding(start = 48.dp)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun AnswerCard(
    answer: IdeasAnswer,
    recipes: Map<Long, Recipe>,
    onRecipeClick: (Long) -> Unit,
    onCreateDish: (NewDishIdea) -> Unit,
    onAddToShopping: () -> Unit
) {
    val names = remember(recipes) { recipes.mapValues { it.value.name } }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (answer.title.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(answer.title, style = MaterialTheme.typography.titleMedium)
                }
            }
            if (answer.text.isNotBlank()) RecipeLinkText(answer.text, names, MaterialTheme.typography.bodyLarge, onRecipeClick)

            answer.sections.forEach { section ->
                if (section.title.isNotBlank()) {
                    Text(
                        section.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                section.recipes.forEach { idea ->
                    recipes[idea.recipeId]?.let { recipe -> RecipeIdeaRow(recipe, idea) { onRecipeClick(recipe.id) } }
                }
            }

            if (answer.tips.isNotEmpty()) {
                HorizontalDivider()
                answer.tips.forEach { tip ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        RecipeLinkText(tip, names, MaterialTheme.typography.bodyMedium, onRecipeClick)
                    }
                }
            }

            if (answer.newDishes.isNotEmpty()) {
                HorizontalDivider()
                Text(L10n.str(R.string.ideas_new_dishes), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                answer.newDishes.forEach { dish ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(dish.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            if (dish.description.isNotBlank()) {
                                Text(dish.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        TextButton(onClick = { onCreateDish(dish) }) { Text(L10n.str(R.string.ideas_create)) }
                    }
                }
            }

            if (answer.recipeIds.size > 1) {
                OutlinedButton(onClick = onAddToShopping) {
                    ButtonContent(Icons.Filled.ShoppingCart, L10n.str(R.string.ideas_add_to_shopping))
                }
            }

            AiContentNotice(
                feature = L10n.str(R.string.ideas),
                content = {
                    listOf(answer.title, answer.text).plus(answer.tips).map { RecipeReferences.toPlainText(it, names) }.plus(answer.newDishes.map { "${it.name}: ${it.description}" })
                        .filter { it.isNotBlank() }.joinToString("\n")
                }
            )
        }
    }
}

@Composable
private fun RecipeIdeaRow(recipe: Recipe, idea: IdeaRecipe, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val photo = recipe.coverPhotoUri
        Box(
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (photo != null) {
                AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().recipePhotoFrame())
            } else {
                Text("🍽️")
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            idea.label?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(recipe.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            idea.reason?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
