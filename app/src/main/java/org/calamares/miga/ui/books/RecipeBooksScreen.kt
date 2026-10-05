package org.calamares.miga.ui.books

import org.calamares.miga.L10n
import org.calamares.miga.R
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import org.calamares.miga.ui.components.EmptyState
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeBooksScreen(
    viewModel: RecipeBooksViewModel,
    onBookClick: (Long) -> Unit,
    onAddBookClick: () -> Unit,
    onEditBookClick: (Long) -> Unit,
    onExplorePacks: () -> Unit = {}
) {
    val books by viewModel.books.collectAsState()
    val changelogAnnouncement by viewModel.changelogAnnouncement.collectAsState()
    val crashReport by viewModel.crashReport.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    var showViewModeMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(Unit) { viewModel.syncAllOnOpen(context) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.navigationBars),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Miga") },
                actions = {
                    Box {
                        IconButton(onClick = { showViewModeMenu = true }) {
                            Icon(bookViewModeIcon(viewMode), contentDescription = "Vista: ${viewMode.label}")
                        }
                        DropdownMenu(expanded = showViewModeMenu, onDismissRequest = { showViewModeMenu = false }) {
                            RecipeListViewMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label) },
                                    leadingIcon = { Icon(bookViewModeIcon(mode), contentDescription = null) },
                                    onClick = { showViewModeMenu = false; viewModel.setViewMode(mode) }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddBookClick,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(L10n.str(R.string.nuevo_libro)) }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (books.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.MenuBook,
                    title = L10n.str(R.string.empieza_recetario),
                    body = L10n.str(R.string.crea_libro_cada_persona_tema),
                    modifier = Modifier.fillMaxSize().weight(1f)
                ) {
                    Button(onClick = onAddBookClick) { Text(L10n.str(R.string.crear_mi_primer_libro)) }
                    OutlinedButton(onClick = onExplorePacks) { Text(L10n.str(R.string.explorar_packs_recetas)) }
                }
            } else if (viewMode == RecipeListViewMode.GRID) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    // Abajo deja hueco para que el botón "Nuevo libro" no tape la última fila.
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(books, key = { it.id }) { book ->
                        RecipeBookCard(book = book, onClick = { onBookClick(book.id) }, onEditClick = { onEditBookClick(book.id) })
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(books, key = { it.id }) { book ->
                        RecipeBookRow(
                            book = book,
                            compact = viewMode == RecipeListViewMode.COMPACT,
                            onClick = { onBookClick(book.id) },
                            onEditClick = { onEditBookClick(book.id) }
                        )
                    }
                }
            }
        }
    }

    changelogAnnouncement?.let { announcement ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissChangelogAnnouncement() },
            title = { Text(L10n.str(R.string.novedades_version_x, announcement.versionName)) },
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
                TextButton(onClick = { viewModel.dismissChangelogAnnouncement() }) { Text(L10n.str(R.string.entendido)) }
            }
        )
    }

    crashReport?.let { report ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissCrashReport() },
            title = { Text(L10n.str(R.string.app_cerro_forma_inesperada)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        L10n.str(R.string.esto_es_guardo_ultimo_fallo),
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
                        putExtra(Intent.EXTRA_SUBJECT, L10n.str(R.string.informe_fallo_miga))
                        putExtra(Intent.EXTRA_TEXT, report)
                    }
                    runCatching { context.startActivity(Intent.createChooser(intent, L10n.str(R.string.compartir_informe))) }
                }) { Text(L10n.str(R.string.compartir)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { clipboardManager.setText(AnnotatedString(report)) }) { Text(L10n.str(R.string.copiar)) }
                    TextButton(onClick = { viewModel.dismissCrashReport() }) { Text(L10n.str(R.string.descartar)) }
                }
            }
        )
    }
}

@Composable
private fun RecipeBookCard(book: RecipeBookSummary, onClick: () -> Unit, onEditClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp).align(Alignment.Center)
                )
            }
            if (book.isPack) {
                Card(
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(32.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.CloudDone,
                            contentDescription = L10n.str(R.string.pack_instalado),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                if (book.isSynced) {
                    Card(
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(32.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Sync,
                                contentDescription = L10n.str(R.string.sincronizado_servidor),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Card(
                    onClick = onEditClick,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(32.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Edit, contentDescription = L10n.str(R.string.editar_libro), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Text(
            text = book.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = if (book.recipeCount == 1) L10n.str(R.string.recipe_count_one) else L10n.str(R.string.recipe_count_many, book.recipeCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun bookViewModeIcon(mode: RecipeListViewMode): ImageVector = when (mode) {
    RecipeListViewMode.COMPACT -> Icons.Filled.ViewHeadline
    RecipeListViewMode.NORMAL -> Icons.Filled.ViewAgenda
    RecipeListViewMode.GRID -> Icons.Filled.GridView
}

/** Fila de libro para las vistas Normal y Compacta (paralelo a RecipeCard). */
@Composable
private fun RecipeBookRow(book: RecipeBookSummary, compact: Boolean, onClick: () -> Unit, onEditClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (book.isPack) {
                        Icon(
                            Icons.Filled.CloudDone,
                            contentDescription = L10n.str(R.string.pack_instalado),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    } else if (book.isSynced) {
                        Icon(
                            Icons.Filled.Sync,
                            contentDescription = L10n.str(R.string.sincronizado_servidor),
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
                    text = if (book.recipeCount == 1) L10n.str(R.string.recipe_count_one) else L10n.str(R.string.recipe_count_many, book.recipeCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!book.isPack) {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Filled.Edit, contentDescription = L10n.str(R.string.editar_libro), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
