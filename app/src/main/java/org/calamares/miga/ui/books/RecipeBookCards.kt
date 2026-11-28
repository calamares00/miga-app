package org.calamares.miga.ui.books

import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.FilterChip
import org.calamares.miga.L10n
import org.calamares.miga.R
import androidx.compose.material3.Button
import org.calamares.miga.ui.components.EmptyState
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.calamares.miga.data.model.RecipeBookSummary
import org.calamares.miga.data.model.BookColor
import org.calamares.miga.data.model.RecipeListViewMode

/** "Shared by Ana · 8 Nov" for a shared book or recipe; null when nobody signed it. */
internal fun sharedByLine(sharedBy: String?, sharedAt: Long?): String? {
    val name = sharedBy?.takeIf { it.isNotBlank() } ?: return null
    val date = sharedAt?.let { java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, L10n.locale()).format(java.util.Date(it)) }
    return listOfNotNull(L10n.str(R.string.shared_by_x, name), date).joinToString(" · ")
}

/**
 * Where to save a recipe shared alone: in one of the user's books or in a new one. When this phone
 * already has it, it is just updated where it is.
 */
@Composable
internal fun SharedRecipeDialog(
    prompt: RecipeBooksViewModel.IncomingRecipePrompt,
    books: List<RecipeBookSummary>,
    onImport: (targetBookId: Long?, newBookName: String?) -> Unit,
    onDismiss: () -> Unit
) {
    // null = a new book.
    var selected by remember { mutableStateOf<Long?>(null) }
    var newName by remember { mutableStateOf(L10n.str(R.string.shared_recipes_book)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
        title = { Text(L10n.str(R.string.import_recipe_x, prompt.recipe.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                sharedByLine(prompt.recipe.sharedBy, null)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (prompt.existingBookName != null) {
                    Text(L10n.str(R.string.shared_recipe_already_here_x, prompt.existingBookName), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(L10n.str(R.string.shared_recipe_choose_book), style = MaterialTheme.typography.bodyMedium)
                    books.forEach { book ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { selected = book.id }
                        ) {
                            RadioButton(selected = selected == book.id, onClick = { selected = book.id })
                            Text(book.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { selected = null }
                    ) {
                        RadioButton(selected = selected == null, onClick = { selected = null })
                        Text(L10n.str(R.string.new_book), style = MaterialTheme.typography.bodyLarge)
                    }
                    if (selected == null) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text(L10n.str(R.string.book_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImport(selected, newName) },
                enabled = prompt.existingBookName != null || selected != null || newName.isNotBlank()
            ) { Text(L10n.str(R.string.import_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.str(R.string.cancel)) } }
    )
}

/** All, my books, packs, server: which kind of book the list shows, each with how many books it has. */
@Composable
internal fun BookFilterChips(selected: BookFilter, books: List<RecipeBookSummary>, onSelect: (BookFilter) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BookFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = {
                    val name = L10n.str(
                        when (filter) {
                            BookFilter.ALL -> R.string.books_filter_all
                            BookFilter.PACKS -> R.string.books_filter_packs
                            BookFilter.LOCAL -> R.string.books_filter_mine
                            BookFilter.SERVER -> R.string.books_filter_server
                        }
                    )
                    Text("$name (${books.count { it.matches(filter) }})")
                }
            )
        }
    }
}

@Composable
internal fun SectionHeader(title: String, level: Int) {
    Text(
        text = title,
        style = if (level == 0) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
        fontWeight = if (level == 0) FontWeight.SemiBold else FontWeight.Medium,
        color = if (level == 0) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(top = if (level == 0) 8.dp else 0.dp)
    )
}

/**
 * Shown when a filter leaves no books: says why and offers the way to get some (the pack
 * catalogue, a new book or the sync settings).
 */
@Composable
internal fun FilterEmptyState(
    filter: BookFilter,
    hasServer: Boolean,
    onExplorePacks: () -> Unit,
    onAddBookClick: () -> Unit,
    onOpenSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (filter) {
        BookFilter.PACKS -> EmptyState(
            icon = Icons.Filled.CloudDone,
            title = L10n.str(R.string.no_packs_installed),
            body = L10n.str(R.string.no_packs_installed_body),
            modifier = modifier
        ) {
            Button(onClick = onExplorePacks) { Text(L10n.str(R.string.open_pack_catalogue)) }
        }
        BookFilter.LOCAL -> EmptyState(
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            title = L10n.str(R.string.no_own_books),
            body = L10n.str(R.string.no_own_books_body),
            modifier = modifier
        ) {
            Button(onClick = onAddBookClick) { Text(L10n.str(R.string.new_book)) }
        }
        // With a server but no books yet, the "Sync" button below is the action.
        BookFilter.SERVER -> if (hasServer) EmptyState(
            icon = Icons.Filled.Sync,
            title = L10n.str(R.string.no_server_books),
            body = L10n.str(R.string.no_server_books_body),
            modifier = modifier
        ) else EmptyState(
            icon = Icons.Filled.Sync,
            title = L10n.str(R.string.no_server_yet),
            body = L10n.str(R.string.no_server_yet_body),
            modifier = modifier
        ) {
            Button(onClick = onOpenSync) { Text(L10n.str(R.string.set_up_server)) }
        }
        BookFilter.ALL -> Unit
    }
}

/** Pin shown on the cover of a pinned book. */
@Composable
internal fun PinnedBadge() {
    Box(
        modifier = Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.PushPin,
            contentDescription = L10n.str(R.string.pinned_book),
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RecipeBookCard(
    book: RecipeBookSummary,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(20.dp))
            .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)) else Modifier)
            .background(bookPlaceholderBrush(book.name, book.coverColor))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        if (book.coverPhotoUri != null) {
            AsyncImage(
                model = book.coverPhotoUri,
                contentDescription = book.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            BookSymbol(book.coverEmoji, 56.dp, Modifier.align(Alignment.Center).padding(bottom = 24.dp))
        }
        // Bottom gradient so the title reads over any cover.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(
                text = book.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (book.author.isNotBlank()) {
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = if (book.recipeCount == 1) L10n.str(R.string.recipe_count_one) else L10n.str(R.string.recipe_count_many, book.recipeCount),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f)
            )
            book.sharedBy?.let {
                Text(
                    text = L10n.str(R.string.shared_by_x, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (book.isPinned) PinnedBadge()
            when {
                book.isPack -> BookBadge(Icons.Filled.CloudDone, L10n.str(R.string.installed_pack))
                book.isSynced -> BookBadge(Icons.Filled.Sync, L10n.str(R.string.synced_server))
            }
            Spacer(modifier = Modifier.weight(1f))
            if (selectionMode) SelectionMark(isSelected, onCover = true)
        }
    }
}

@Composable
internal fun BookBadge(icon: ImageVector, description: String) {
    Box(
        modifier = Modifier.size(30.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

/**
 * Background for books without a cover: a gradient of the chosen colour or, when there is none, a
 * stable one derived from the name (see BookColor).
 */
internal fun bookPlaceholderBrush(name: String, chosen: String? = null): Brush {
    val base = Color(BookColor.of(chosen, name).argb)
    return Brush.linearGradient(listOf(base, base.copy(red = base.red * 0.7f, green = base.green * 0.7f, blue = base.blue * 0.7f)))
}

internal fun bookViewModeIcon(mode: RecipeListViewMode): ImageVector = when (mode) {
    RecipeListViewMode.COMPACT -> Icons.Filled.ViewHeadline
    RecipeListViewMode.NORMAL -> Icons.Filled.ViewAgenda
    RecipeListViewMode.GRID -> Icons.Filled.GridView
}

/** Book row for the Normal and Compact views (counterpart of RecipeCard). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RecipeBookRow(
    book: RecipeBookSummary,
    compact: Boolean,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // As on recipe cards, the cover gives way to the selection mark.
            if (selectionMode) {
                SelectionMark(isSelected, onCover = false)
            } else if (!compact) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(bookPlaceholderBrush(book.name, book.coverColor)),
                    contentAlignment = Alignment.Center
                ) {
                    if (book.coverPhotoUri != null) {
                        AsyncImage(
                            model = book.coverPhotoUri,
                            contentDescription = book.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        BookSymbol(book.coverEmoji, 28.dp)
                    }
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (book.isPinned) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = L10n.str(R.string.pinned_book),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (book.isPack) {
                        Icon(
                            Icons.Filled.CloudDone,
                            contentDescription = L10n.str(R.string.installed_pack),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    } else if (book.isSynced) {
                        Icon(
                            Icons.Filled.Sync,
                            contentDescription = L10n.str(R.string.synced_server),
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = book.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    // "12 recipes · Shared by Ana": who sent a shared book, read-only.
                    text = listOfNotNull(
                        if (book.recipeCount == 1) L10n.str(R.string.recipe_count_one) else L10n.str(R.string.recipe_count_many, book.recipeCount),
                        book.sharedBy?.let { L10n.str(R.string.shared_by_x, it) }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Selected or not, while selecting books: on a cover ([onCover]) it gets a dark backing to stand out. */
@Composable
internal fun SelectionMark(selected: Boolean, onCover: Boolean) {
    val icon = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked
    val description = L10n.str(if (selected) R.string.item_selected else R.string.item_not_selected)
    if (onCover) {
        Box(
            modifier = Modifier.size(30.dp).clip(CircleShape).background(if (selected) Color.White else Color.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = description, tint = if (selected) MaterialTheme.colorScheme.primary else Color.White)
        }
    } else {
        Icon(icon, contentDescription = description, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "1 selected" / "3 selected", for the selection bar. */
internal fun selectedBooksText(count: Int): String =
    if (count == 1) L10n.str(R.string.one_selected_book) else L10n.plural(R.plurals.n_selected_books, count)
