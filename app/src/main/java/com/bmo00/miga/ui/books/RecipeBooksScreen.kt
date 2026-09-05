package com.bmo00.miga.ui.books

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bmo00.miga.data.model.RecipeBookSummary
import com.bmo00.miga.data.model.RecipeListViewMode
import com.bmo00.miga.data.remote.UpdateInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeBooksScreen(
    viewModel: RecipeBooksViewModel,
    onBookClick: (Long) -> Unit,
    onAddBookClick: () -> Unit,
    onEditBookClick: (Long) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val books by viewModel.books.collectAsState()
    val updateAvailable by viewModel.updateAvailable.collectAsState()
    val changelogAnnouncement by viewModel.changelogAnnouncement.collectAsState()
    val crashReport by viewModel.crashReport.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    var showViewModeMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
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
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Filled.Search, contentDescription = "Buscar recetas")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddBookClick,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Nuevo libro") }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            updateAvailable?.let { info ->
                UpdateBanner(
                    info = info,
                    onDismiss = { viewModel.dismissUpdateBanner() },
                    onOpen = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.apkDownloadUrl ?: info.releaseUrl))
                        runCatching { context.startActivity(intent) }
                    }
                )
            }

            if (books.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        "Aún no tienes libros de recetas.\nPulsa \"Nuevo libro\" para crear el primero.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (viewMode == RecipeListViewMode.GRID) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
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
                    contentPadding = PaddingValues(16.dp),
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
            title = { Text("Novedades de la versión ${announcement.versionName}") },
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
                TextButton(onClick = { viewModel.dismissChangelogAnnouncement() }) { Text("Entendido") }
            }
        )
    }

    crashReport?.let { report ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissCrashReport() },
            title = { Text("La app se cerró de forma inesperada") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Esto es lo que se guardó del último fallo, solo en este dispositivo. " +
                            "Puedes copiarlo o compartirlo para reportarlo, o simplemente descartarlo.",
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
                        putExtra(Intent.EXTRA_SUBJECT, "Informe de fallo - Miga")
                        putExtra(Intent.EXTRA_TEXT, report)
                    }
                    runCatching { context.startActivity(Intent.createChooser(intent, "Compartir informe")) }
                }) { Text("Compartir") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { clipboardManager.setText(AnnotatedString(report)) }) { Text("Copiar") }
                    TextButton(onClick = { viewModel.dismissCrashReport() }) { Text("Descartar") }
                }
            }
        )
    }
}

@Composable
private fun UpdateBanner(info: UpdateInfo, onDismiss: () -> Unit, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    "Nueva versión disponible",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "Miga ${info.latestVersion}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            TextButton(onClick = onOpen) { Text("Descargar") }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Descartar", tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
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
                            contentDescription = "Pack instalado",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                Card(
                    onClick = onEditClick,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(32.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Edit, contentDescription = "Editar libro", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Text(
            text = book.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = "${book.recipeCount} ${if (book.recipeCount == 1) "receta" else "recetas"}",
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
                            contentDescription = "Pack instalado",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(text = book.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                }
                Text(
                    text = "${book.recipeCount} ${if (book.recipeCount == 1) "receta" else "recetas"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!book.isPack) {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Filled.Edit, contentDescription = "Editar libro", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
