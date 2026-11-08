package org.calamares.miga.ui.books

import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.clickable
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import kotlinx.coroutines.flow.filterNotNull
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Link
import org.calamares.miga.data.share.BookFileIntents
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import org.calamares.miga.L10n
import org.calamares.miga.R
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import org.calamares.miga.ui.components.AdaptiveScaffold
import org.calamares.miga.ui.components.GRID_CONTENT_WIDTH
import org.calamares.miga.ui.components.EmptyState
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material.icons.outlined.MenuBook
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import org.calamares.miga.data.remote.AppInstallSource
import org.calamares.miga.BuildConfig
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.calamares.miga.data.model.RecipeBookSummary
import org.calamares.miga.data.model.RecipeListViewMode
import org.calamares.miga.ui.StartupGate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecipeBooksScreen(
    viewModel: RecipeBooksViewModel,
    onBookClick: (Long) -> Unit,
    onAddBookClick: () -> Unit,
    onEditBookClick: (Long) -> Unit,
    onExplorePacks: () -> Unit = {},
    onOpenIdeas: () -> Unit = {},
    onOpenSync: () -> Unit = {}
) {
    val loadedBooks by viewModel.books.collectAsState()
    val books = loadedBooks.orEmpty()
    LaunchedEffect(loadedBooks != null) {
        if (loadedBooks != null) StartupGate.contentReady()
    }
    val aiReady by viewModel.aiReady.collectAsState()
    val changelogAnnouncement by viewModel.changelogAnnouncement.collectAsState()
    val crashReport by viewModel.crashReport.collectAsState()
    val availableUpdate by viewModel.availableUpdate.collectAsState()
    val packUpdates by viewModel.packUpdates.collectAsState()
    val updatingPacks by viewModel.updatingPacks.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val grouped by viewModel.grouped.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val connectionNames by viewModel.connectionNames.collectAsState()
    var showViewModeMenu by remember { mutableStateOf(false) }
    var packToUninstall by remember { mutableStateOf<RecipeBookSummary?>(null) }
    val labels = BookSectionLabels(
        myBooks = L10n.str(R.string.books_group_mine),
        server = L10n.str(R.string.books_group_server),
        packs = L10n.str(R.string.books_group_packs),
        otherPacks = L10n.str(R.string.books_group_other_packs)
    )
    val sections = remember(books, filter, grouped, connectionNames, labels) {
        bookSections(books, filter, grouped, connectionNames, labels)
    }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        viewModel.syncAllOnOpen(context)
        viewModel.checkForUpdate(context)
        viewModel.checkPackUpdates()
    }

    // The server filter is always offered: without a connection it explains what a server is for.
    val hasServer = connectionNames.isNotEmpty()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val syncing by viewModel.syncing.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val incomingBook by viewModel.incomingBook.collectAsState()
    val incomingRecipe by viewModel.incomingRecipe.collectAsState()
    val importingBook by viewModel.importingBook.collectAsState()
    val downloadingBook by viewModel.downloadingBook.collectAsState()
    // Only the visible Miga takes the file: opening it from WhatsApp starts a second Miga in
    // WhatsApp's task, and the one left in the background must not take it and ask there, unseen.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            BookFileIntents.event.filterNotNull().collect { viewModel.onIncomingFile(context, it) }
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(syncMessage) {
        syncMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSyncMessage()
        }
    }

    // Lazy lists keep their position by item key: after "Packs", going back to "All" would stay on
    // the packs header and hide the books above it. A new filter always starts from the top.
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    LaunchedEffect(filter) {
        gridState.scrollToItem(0)
        listState.scrollToItem(0)
    }

    AdaptiveScaffold(
        maxContentWidth = GRID_CONTENT_WIDTH,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.navigationBars).exclude(WindowInsets.ime),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                scrollBehavior = scrollBehavior,
                title = { Text(L10n.str(R.string.my_books)) },
                actions = {
                    if (aiReady) {
                        IconButton(onClick = onOpenIdeas) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = L10n.str(R.string.ideas), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Box {
                        IconButton(onClick = { showViewModeMenu = true }) {
                            Icon(bookViewModeIcon(viewMode), contentDescription = L10n.str(R.string.view_x, viewMode.label))
                        }
                        DropdownMenu(expanded = showViewModeMenu, onDismissRequest = { showViewModeMenu = false }) {
                            RecipeListViewMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label) },
                                    leadingIcon = { Icon(bookViewModeIcon(mode), contentDescription = null) },
                                    onClick = { showViewModeMenu = false; viewModel.setViewMode(mode) }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(L10n.str(R.string.group_books)) },
                                leadingIcon = { Icon(Icons.Filled.Category, contentDescription = null) },
                                trailingIcon = { Checkbox(checked = grouped, onCheckedChange = null) },
                                onClick = { showViewModeMenu = false; viewModel.setGrouped(!grouped) }
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // The button follows the filter: packs come from the catalogue and server books from a sync.
            // An empty filter already shows its own button in the message; a second one would repeat it.
            val filterEmpty = loadedBooks != null && books.isNotEmpty() && sections.isEmpty()
            when {
                filterEmpty && filter != BookFilter.SERVER -> Unit
                filter == BookFilter.SERVER && !hasServer -> Unit
                filter == BookFilter.PACKS -> ExtendedFloatingActionButton(
                    onClick = onExplorePacks,
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text(L10n.str(R.string.add_pack)) }
                )
                filter == BookFilter.SERVER -> ExtendedFloatingActionButton(
                    onClick = { viewModel.syncNow(context) },
                    icon = {
                        if (syncing) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Sync, null)
                    },
                    text = { Text(L10n.str(if (syncing) R.string.syncing_now else R.string.sync_books)) }
                )
                else -> ExtendedFloatingActionButton(
                    onClick = onAddBookClick,
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text(L10n.str(R.string.new_book)) }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (loadedBooks == null) {
                // Still loading (usually behind the splash screen): nothing rather than "no books".
                Spacer(modifier = Modifier.weight(1f))
            } else if (books.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.MenuBook,
                    title = L10n.str(R.string.start_recipe_book),
                    body = L10n.str(R.string.create_book_each_person_theme),
                    modifier = Modifier.fillMaxSize().weight(1f)
                ) {
                    Button(onClick = onAddBookClick) { Text(L10n.str(R.string.create_my_first_book)) }
                    OutlinedButton(onClick = onExplorePacks) { Text(L10n.str(R.string.explore_recipe_packs)) }
                }
            } else {
                BookFilterChips(filter, books, onSelect = viewModel::setFilter)
                if (updatingPacks) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
                if (sections.isEmpty()) {
                    FilterEmptyState(
                        filter,
                        hasServer = hasServer,
                        onExplorePacks = onExplorePacks,
                        onAddBookClick = onAddBookClick,
                        onOpenSync = onOpenSync,
                        modifier = Modifier.fillMaxSize().weight(1f)
                    )
                } else if (viewMode == RecipeListViewMode.GRID) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(150.dp),
                        // Bottom padding so the "New book" button does not cover the last row.
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        sections.forEach { section ->
                            section.title?.let { title ->
                                item(key = "header/${section.key}", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                                    SectionHeader(title, section.level)
                                }
                            }
                            items(section.books, key = { it.id }) { book ->
                                BookMenuHost(
                                    book,
                                    onEdit = { onEditBookClick(book.id) },
                                    onTogglePin = { viewModel.togglePinned(book) },
                                    onUninstall = { packToUninstall = book },
                                    onShare = { viewModel.shareBook(context, book) },
                                    onShareLink = { viewModel.shareBookLink(context, book) },
                                    // Pinning moves the book to the top; animate it instead of jumping.
                                    modifier = Modifier.animateItemPlacement()
                                ) { openMenu ->
                                    RecipeBookCard(
                                        book = book,
                                        onClick = { onBookClick(book.id) },
                                        onLongClick = openMenu,
                                        onEditClick = { onEditBookClick(book.id) }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        sections.forEach { section ->
                            section.title?.let { title ->
                                item(key = "header/${section.key}", contentType = "header") { SectionHeader(title, section.level) }
                            }
                            items(section.books, key = { it.id }) { book ->
                                BookMenuHost(
                                    book,
                                    onEdit = { onEditBookClick(book.id) },
                                    onTogglePin = { viewModel.togglePinned(book) },
                                    onUninstall = { packToUninstall = book },
                                    onShare = { viewModel.shareBook(context, book) },
                                    onShareLink = { viewModel.shareBookLink(context, book) },
                                    // Pinning moves the book to the top; animate it instead of jumping.
                                    modifier = Modifier.animateItemPlacement()
                                ) { openMenu ->
                                    RecipeBookRow(
                                        book = book,
                                        compact = viewMode == RecipeListViewMode.COMPACT,
                                        onClick = { onBookClick(book.id) },
                                        onLongClick = openMenu,
                                        onEditClick = { onEditBookClick(book.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    incomingBook?.let { prompt ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissIncomingBook() },
            icon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
            title = { Text(L10n.str(R.string.import_book_x, prompt.bookName)) },
            text = {
                Text(
                    listOfNotNull(
                        sharedByLine(prompt.sharedBy, prompt.sharedAt),
                        L10n.str(R.string.x_recipes_count, prompt.recipeCount)
                    ).joinToString(" · ") + "\n\n" +
                        L10n.str(if (prompt.alreadyHere) R.string.shared_book_update_explanation else R.string.shared_book_new_explanation)
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.importIncomingBook(context) }) { Text(L10n.str(R.string.import_action)) } },
            dismissButton = { TextButton(onClick = { viewModel.dismissIncomingBook() }) { Text(L10n.str(R.string.cancel)) } }
        )
    }
    incomingRecipe?.let { prompt ->
        SharedRecipeDialog(
            prompt = prompt,
            books = books.filter { !it.isPack },
            onImport = viewModel::importIncomingRecipe,
            onDismiss = viewModel::dismissIncomingRecipe
        )
    }
    if (importingBook || downloadingBook) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(L10n.str(if (downloadingBook) R.string.downloading_book else R.string.importing_book)) },
            text = { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) },
            confirmButton = {}
        )
    }

    packToUninstall?.let { book ->
        AlertDialog(
            onDismissRequest = { packToUninstall = null },
            title = { Text(L10n.str(R.string.uninstall_pack)) },
            text = { Text(L10n.str(R.string.sure_want_uninstall_x_recipes, book.name)) },
            confirmButton = {
                TextButton(onClick = { viewModel.uninstallPack(book); packToUninstall = null }) {
                    Text(L10n.str(R.string.uninstall), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { packToUninstall = null }) { Text(L10n.str(R.string.cancel)) }
            }
        )
    }

    changelogAnnouncement?.let { announcement ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissChangelogAnnouncement() },
            title = { Text(L10n.str(R.string.whats_new_version_x, announcement.versionName)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    announcement.entries.forEach { entry ->
                        Text(
                            "• $entry",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissChangelogAnnouncement() }) { Text(L10n.str(R.string.got)) }
            }
        )
    }

    // Pack updates: after the other start-up notices, never on top of them.
    packUpdates.takeIf { it.isNotEmpty() && changelogAnnouncement == null && crashReport == null && availableUpdate == null }?.let { updates ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissPackUpdates() },
            icon = { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
            title = { Text(L10n.str(R.string.pack_updates_found_title)) },
            text = { Text(L10n.str(R.string.pack_updates_found_x, updates.size, updates.joinToString(", ") { it.name })) },
            confirmButton = {
                TextButton(onClick = {
                    Toast.makeText(context, L10n.str(R.string.updating_packs_x, updates.size), Toast.LENGTH_SHORT).show()
                    viewModel.installPackUpdates(context) { message -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
                }) { Text(L10n.str(R.string.update)) }
            },
            dismissButton = { TextButton(onClick = { viewModel.dismissPackUpdates() }) { Text(L10n.str(R.string.not_now)) } }
        )
    }

    // After "What's new" and the crash report, never on top of them.
    availableUpdate?.takeIf { changelogAnnouncement == null && crashReport == null }?.let { release ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdate(forever = false) },
            icon = { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
            title = { Text(L10n.str(R.string.new_version_available)) },
            text = { Text(L10n.str(R.string.miga_x_available_play, release.versionName, BuildConfig.VERSION_NAME)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissUpdate(forever = false)
                    AppInstallSource.openStorePage(context)
                }) { Text(L10n.str(R.string.update)) }
            },
            dismissButton = { TextButton(onClick = { viewModel.dismissUpdate(forever = true) }) { Text(L10n.str(R.string.not_now)) } }
        )
    }

    crashReport?.let { report ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissCrashReport() },
            title = { Text(L10n.str(R.string.app_closed_unexpectedly)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        L10n.str(R.string.what_was_saved_last_crash),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    SelectionContainer {
                        Text(report, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, L10n.str(R.string.crash_report_miga))
                        putExtra(Intent.EXTRA_TEXT, report)
                    }
                    runCatching { context.startActivity(Intent.createChooser(intent, L10n.str(R.string.share_report))) }
                }) { Text(L10n.str(R.string.share)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { clipboardManager.setText(AnnotatedString(report)) }) { Text(L10n.str(R.string.copy)) }
                    TextButton(onClick = { viewModel.dismissCrashReport() }) { Text(L10n.str(R.string.discard)) }
                }
            }
        )
    }
}

/**
 * Wraps a book with its long-press menu (pin to the top, edit). [content] gets the function that
 * opens the menu, to call on long press.
 */
@Composable
private fun BookMenuHost(
    book: RecipeBookSummary,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onUninstall: () -> Unit,
    onShare: () -> Unit,
    onShareLink: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (openMenu: () -> Unit) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box(modifier = modifier) {
        content {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            showMenu = true
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text(L10n.str(if (book.isPinned) R.string.unpin_book else R.string.pin_book)) },
                leadingIcon = { Icon(if (book.isPinned) Icons.Outlined.PushPin else Icons.Filled.PushPin, contentDescription = null) },
                onClick = { showMenu = false; onTogglePin() }
            )
            if (book.isPack) {
                DropdownMenuItem(
                    text = { Text(L10n.str(R.string.uninstall_pack)) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = { showMenu = false; onUninstall() }
                )
            } else {
                DropdownMenuItem(
                    text = { Text(L10n.str(R.string.edit_book)) },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = { showMenu = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text(L10n.str(R.string.share_book)) },
                    leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                    onClick = { showMenu = false; onShare() }
                )
                DropdownMenuItem(
                    text = { Text(L10n.str(R.string.share_link)) },
                    leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                    onClick = { showMenu = false; onShareLink() }
                )
            }
        }
    }
}

/** "Shared by Ana · 8 Nov" for a shared book or recipe; null when nobody signed it. */
private fun sharedByLine(sharedBy: String?, sharedAt: Long?): String? {
    val name = sharedBy?.takeIf { it.isNotBlank() } ?: return null
    val date = sharedAt?.let { java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, L10n.locale()).format(java.util.Date(it)) }
    return listOfNotNull(L10n.str(R.string.shared_by_x, name), date).joinToString(" · ")
}

/**
 * Where to save a recipe shared alone: in one of the user's books or in a new one. When this phone
 * already has it, it is just updated where it is.
 */
@Composable
private fun SharedRecipeDialog(
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
private fun BookFilterChips(selected: BookFilter, books: List<RecipeBookSummary>, onSelect: (BookFilter) -> Unit) {
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
private fun SectionHeader(title: String, level: Int) {
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
private fun FilterEmptyState(
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
            icon = Icons.Outlined.MenuBook,
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
private fun PinnedBadge() {
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
private fun RecipeBookCard(
    book: RecipeBookSummary,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(20.dp))
            .background(bookPlaceholderBrush(book.name))
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
            Icon(
                imageVector = Icons.Outlined.MenuBook,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(56.dp).align(Alignment.Center).padding(bottom = 24.dp)
            )
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
            if (!book.isPack) {
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.35f), contentColor = Color.White)
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = L10n.str(R.string.edit_book), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun BookBadge(icon: ImageVector, description: String) {
    Box(
        modifier = Modifier.size(30.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

/** Background for books without a cover: a gradient with a stable colour derived from the name. */
private fun bookPlaceholderBrush(name: String): Brush {
    val palette = listOf(0xFFC1633D, 0xFF3E8EB5, 0xFF5C9E4B, 0xFF8E6BB5, 0xFFD1709F, 0xFFD9962B, 0xFF3FA39B)
    val base = Color(palette[Math.floorMod(name.hashCode(), palette.size)])
    return Brush.linearGradient(listOf(base, base.copy(red = base.red * 0.7f, green = base.green * 0.7f, blue = base.blue * 0.7f)))
}

private fun bookViewModeIcon(mode: RecipeListViewMode): ImageVector = when (mode) {
    RecipeListViewMode.COMPACT -> Icons.Filled.ViewHeadline
    RecipeListViewMode.NORMAL -> Icons.Filled.ViewAgenda
    RecipeListViewMode.GRID -> Icons.Filled.GridView
}

/** Book row for the Normal and Compact views (counterpart of RecipeCard). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecipeBookRow(
    book: RecipeBookSummary,
    compact: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEditClick: () -> Unit,
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
            if (!compact) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(bookPlaceholderBrush(book.name))
                ) {
                    if (book.coverPhotoUri != null) {
                        AsyncImage(
                            model = book.coverPhotoUri,
                            contentDescription = book.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.MenuBook,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(14.dp)
                        )
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

            if (!book.isPack) {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Filled.Edit, contentDescription = L10n.str(R.string.edit_book), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
