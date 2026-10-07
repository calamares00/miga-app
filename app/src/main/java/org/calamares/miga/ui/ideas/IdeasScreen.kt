package org.calamares.miga.ui.ideas

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Scaffold
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
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.ui.components.AiContentNotice
import org.calamares.miga.ui.components.ErrorMessage
import org.calamares.miga.ui.components.FormattedText

/**
 * Ideas: AI recommendations based on the user's recipes. A "What do you fancy?" panel combines
 * options (meal, style, kind of dish, utensil) into one request, and the field at the bottom takes
 * any question, including cooking advice. Answers form a conversation; recommended recipes open
 * their detail and new dishes can be created with the AI.
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
    var question by remember { mutableStateOf("") }
    var dishToCreate by remember { mutableStateOf<NewDishIdea?>(null) }
    var addingUtensil by remember { mutableStateOf(false) }
    // The options panel folds away once there is a conversation, to leave room for the answers.
    var optionsExpanded by rememberSaveable { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val busy = entries.any { it.state == IdeasEntryState.Loading }

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.size)
    }

    fun send() {
        if (question.isBlank() || busy) return
        viewModel.askQuestion(question)
        question = ""
        optionsExpanded = false
    }

    Scaffold(
        // Not a bottom-bar tab: this screen keeps clear of the system navigation bar and keyboard.
        contentWindowInsets = WindowInsets.safeDrawing,
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
                        IconButton(onClick = { viewModel.clear(); optionsExpanded = true }) {
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
                item(key = "options") {
                    OptionsPanel(
                        filters = filters,
                        utensils = utensils,
                        expanded = optionsExpanded || entries.isEmpty(),
                        collapsible = entries.isNotEmpty(),
                        showIntro = entries.isEmpty(),
                        busy = busy,
                        onToggleExpanded = { optionsExpanded = !optionsExpanded },
                        onMeal = viewModel::setMeal,
                        onStyle = viewModel::toggleStyle,
                        onDish = viewModel::toggleDish,
                        onUtensil = viewModel::toggleUtensil,
                        onAddUtensil = { addingUtensil = true },
                        onClear = viewModel::clearFilters,
                        onAsk = {
                            viewModel.askWithFilters()
                            optionsExpanded = false
                        }
                    )
                }
                items(entries, key = { it.id }) { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuestionBubble(entry.label)
                        when (val state = entry.state) {
                            IdeasEntryState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(L10n.str(R.string.ideas_thinking), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it.take(MAX_IDEAS_QUESTION_CHARS) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(L10n.str(R.string.ideas_ask_placeholder)) },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledIconButton(onClick = ::send, enabled = question.isNotBlank() && !busy) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = L10n.str(R.string.ideas_send))
                    }
                }
            }
        }
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

/**
 * "What do you fancy?" panel: groups of options that combine into one request. It folds into its
 * header when [collapsible] (there is a conversation) and the user closes it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionsPanel(
    filters: IdeasFilters,
    utensils: List<String>,
    expanded: Boolean,
    collapsible: Boolean,
    showIntro: Boolean,
    busy: Boolean,
    onToggleExpanded: () -> Unit,
    onMeal: (IdeasMeal) -> Unit,
    onStyle: (IdeasStyle) -> Unit,
    onDish: (IdeasDish) -> Unit,
    onUtensil: (String) -> Unit,
    onAddUtensil: () -> Unit,
    onClear: () -> Unit,
    onAsk: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = collapsible, onClick = onToggleExpanded),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(L10n.str(R.string.ideas_what_do_you_fancy), style = MaterialTheme.typography.titleMedium)
                    if (!expanded && !filters.isEmpty) {
                        Text(
                            filtersLabel(filters),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (collapsible) {
                    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "optionsArrow")
                    Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.rotate(rotation))
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 12.dp)) {
                    if (showIntro) {
                        Text(
                            L10n.str(R.string.ideas_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OptionGroup(L10n.str(R.string.ideas_group_meal)) {
                        IdeasMeal.entries.forEach { meal ->
                            OptionChip("${mealEmoji(meal)}  ${mealLabel(meal)}", filters.meal == meal) { onMeal(meal) }
                        }
                    }
                    OptionGroup(L10n.str(R.string.ideas_group_style)) {
                        IdeasStyle.entries.forEach { style ->
                            val label = if (style == IdeasStyle.SEASONAL) "${seasonEmoji()}  ${styleLabel(style)}" else styleLabel(style)
                            OptionChip(label, style in filters.styles) { onStyle(style) }
                        }
                    }
                    OptionGroup(L10n.str(R.string.ideas_group_dish)) {
                        IdeasDish.entries.forEach { dish ->
                            OptionChip(dishLabel(dish), dish in filters.dishes) { onDish(dish) }
                        }
                    }
                    OptionGroup(L10n.str(R.string.ideas_group_utensil)) {
                        utensils.forEach { name ->
                            OptionChip(name, filters.utensils.any { it.equals(name, ignoreCase = true) }) { onUtensil(name) }
                        }
                        FilterChip(
                            selected = false,
                            onClick = onAddUtensil,
                            label = { Text(L10n.str(R.string.ideas_other)) },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (filters.isEmpty) L10n.str(R.string.ideas_combine_hint) else filtersLabel(filters),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!filters.isEmpty) {
                            TextButton(onClick = onClear) { Text(L10n.str(R.string.ideas_clear)) }
                        }
                    }
                    Button(onClick = onAsk, enabled = !filters.isEmpty && !busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(L10n.str(R.string.ideas_ask_button))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            content()
        }
    }
}

@Composable
private fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
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
            if (answer.text.isNotBlank()) FormattedText(answer.text, style = MaterialTheme.typography.bodyLarge)

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
                        FormattedText(tip, style = MaterialTheme.typography.bodyMedium)
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
                    Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(L10n.str(R.string.ideas_add_to_shopping))
                }
            }

            AiContentNotice(
                feature = L10n.str(R.string.ideas),
                content = {
                    listOf(answer.title, answer.text).plus(answer.tips).plus(answer.newDishes.map { "${it.name}: ${it.description}" })
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
                AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
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
