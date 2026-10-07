package org.calamares.miga.ui.ideas

import androidx.compose.foundation.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import org.calamares.miga.data.ideas.IdeasPreset
import org.calamares.miga.data.ideas.NewDishIdea
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.ui.components.AiContentNotice
import org.calamares.miga.ui.components.ErrorMessage
import org.calamares.miga.ui.components.FormattedText

/**
 * Ideas: AI recommendations based on the user's recipes. Ready-made requests (weekly menu,
 * breakfasts, seasonal...) as cards, and a question field for anything else, including cooking
 * advice. Recommended recipes open their detail; new dishes can be created with the AI.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var question by remember { mutableStateOf("") }
    var dishToCreate by remember { mutableStateOf<NewDishIdea?>(null) }
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
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.navigationBars),
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
                        IconButton(onClick = viewModel::clear) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = L10n.str(R.string.ideas_new_conversation))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "intro") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (entries.isEmpty()) {
                            Text(
                                L10n.str(R.string.ideas_intro),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            IdeasPreset.entries.forEach { preset ->
                                PresetCard(preset, enabled = !busy) { viewModel.askPreset(preset) }
                            }
                        }
                    }
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(L10n.str(R.string.ideas_ask_placeholder)) },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp)
                )
                IconButton(onClick = ::send, enabled = question.isNotBlank() && !busy) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = L10n.str(R.string.ideas_send))
                }
            }
        }
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

@Composable
private fun PresetCard(preset: IdeasPreset, enabled: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = { Text(presetLabel(preset)) },
        leadingIcon = { Text(presetEmoji(preset)) }
    )
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
