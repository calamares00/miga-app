package com.bmo00.miga.ui.books

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bmo00.miga.data.model.RecipeBookSummary
import com.bmo00.miga.data.remote.UpdateInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeBooksScreen(
    viewModel: RecipeBooksViewModel,
    onBookClick: (Long) -> Unit,
    onAddBookClick: () -> Unit,
    onEditBookClick: (Long) -> Unit,
    onSettingsClick: () -> Unit
) {
    val books by viewModel.books.collectAsState()
    val updateAvailable by viewModel.updateAvailable.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Miga") },
                actions = {
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
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl))
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
            } else {
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
            }
        }
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
            TextButton(onClick = onOpen) { Text("Ver") }
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
            Card(
                onClick = onEditClick,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(32.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Edit, contentDescription = "Editar libro", modifier = Modifier.size(16.dp))
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
