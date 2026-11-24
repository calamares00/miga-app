package org.calamares.miga.ui.sync

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.model.BookColor
import org.calamares.miga.ui.books.BookSymbol

/**
 * Which books of a sync server this device syncs (miga-server 1.3.0): each with its size, who
 * created it and whether it is here. Unticking a book that is here asks first; it only leaves this
 * device. See [ServerBooksViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerBooksScreen(viewModel: ServerBooksViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    fun size(bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

    BackHandler(onBack = onBack)
    LaunchedEffect(message) {
        message?.let { (text, done) ->
            viewModel.consumeMessage()
            if (done) onBack()
            snackbarHostState.showSnackbar(text)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(L10n.str(if (viewModel.firstTime) R.string.server_books_choose else R.string.server_books))
                        Text(
                            listOf(state.label, state.server).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = L10n.str(R.string.back)) } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!state.loading && !state.unsupported && state.error == null && state.books.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                        Text(
                            L10n.str(R.string.server_books_total, state.chosen.size, state.books.size, size(state.chosenBytes)),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.save(context) },
                            enabled = !state.saving && (state.hasChanges || viewModel.firstTime || state.chosen.isNotEmpty()),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.saving) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.CloudDownload, contentDescription = null)
                            }
                            Spacer(Modifier.width(8.dp))
                            val download = state.toDownload
                            Text(
                                when {
                                    viewModel.firstTime -> L10n.str(R.string.server_books_download_n, download.size)
                                    download.isNotEmpty() -> L10n.str(R.string.server_books_save_download, download.size, size(download.sumOf { it.dto.bytes }))
                                    state.toRemove.isNotEmpty() -> L10n.str(R.string.server_books_save_remove, state.toRemove.size)
                                    else -> L10n.str(R.string.save)
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.unsupported -> Notice(L10n.str(R.string.server_books_unsupported), Modifier.padding(padding))
            state.error != null -> Column(Modifier.padding(padding)) {
                Notice(L10n.str(R.string.server_books_load_failed, state.error.orEmpty()), Modifier)
                TextButton(onClick = { viewModel.load() }, modifier = Modifier.padding(horizontal = 16.dp)) { Text(L10n.str(R.string.retry)) }
            }
            state.books.isEmpty() -> Notice(L10n.str(R.string.server_books_empty), Modifier.padding(padding))
            else -> LazyColumn(Modifier.padding(padding)) {
                item {
                    Notice(
                        if (viewModel.firstTime) L10n.str(R.string.server_books_first_time, state.books.size)
                        else L10n.str(R.string.server_books_intro),
                        Modifier
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(L10n.str(R.string.server_books_follow_new)) },
                        supportingContent = { Text(L10n.str(R.string.server_books_follow_new_desc)) },
                        trailingContent = { Switch(checked = state.followNewBooks, onCheckedChange = viewModel::setFollowNewBooks) }
                    )
                    HorizontalDivider()
                }
                items(state.books, key = { it.dto.uid }) { book ->
                    ServerBookRow(book, chosen = book.dto.uid in state.chosen, size = size(book.dto.bytes)) { viewModel.toggle(book.dto.uid) }
                }
            }
        }
    }

    state.askRemove?.let { book ->
        var keepCopy by rememberSaveable(book.dto.uid) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = viewModel::cancelRemove,
            icon = { Icon(Icons.Filled.PhoneAndroid, contentDescription = null) },
            title = { Text(L10n.str(R.string.server_books_remove_title, book.dto.name)) },
            text = {
                Column {
                    Text(L10n.str(R.string.server_books_remove_body, recipesText(book.dto.recipeCount), size(book.dto.bytes)))
                    Spacer(Modifier.height(8.dp))
                    Text(L10n.str(R.string.server_books_remove_stays))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).clickable { keepCopy = !keepCopy }
                    ) {
                        Checkbox(checked = keepCopy, onCheckedChange = { keepCopy = it })
                        Text(L10n.str(R.string.server_books_keep_copy), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.confirmRemove(keepCopy) }) { Text(L10n.str(R.string.server_books_remove)) } },
            dismissButton = { TextButton(onClick = viewModel::cancelRemove) { Text(L10n.str(R.string.cancel)) } }
        )
    }
}

private fun recipesText(count: Int): String =
    if (count == 1) L10n.str(R.string.recipe_count_one) else L10n.str(R.string.x_recipes_count, count)

@Composable
private fun Notice(text: String, modifier: Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = modifier.fillMaxWidth().padding(16.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
    }
}

@Composable
private fun ServerBookRow(book: ServerBook, chosen: Boolean, size: String, onToggle: () -> Unit) {
    val dto = book.dto
    ListItem(
        modifier = Modifier.clickable(onClick = onToggle),
        leadingContent = {
            Box(
                Modifier.size(width = 44.dp, height = 58.dp).clip(RoundedCornerShape(6.dp)).background(Color(BookColor.of(dto.coverColor, dto.name).argb)),
                contentAlignment = Alignment.Center
            ) { BookSymbol(dto.coverEmoji, 26.dp) }
        },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dto.name, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (book.isNew) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        L10n.str(R.string.server_books_new),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        },
        supportingContent = {
            Column {
                Text(L10n.str(R.string.server_books_size, recipesText(dto.recipeCount), size))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (book.onDevice) Icon(Icons.Filled.CloudDone, contentDescription = null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        listOfNotNull(
                            L10n.str(R.string.server_books_on_device).takeIf { book.onDevice },
                            dto.createdBy?.let { L10n.str(R.string.server_books_created_by, it) }
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        trailingContent = { Checkbox(checked = chosen, onCheckedChange = { onToggle() }) }
    )
}
