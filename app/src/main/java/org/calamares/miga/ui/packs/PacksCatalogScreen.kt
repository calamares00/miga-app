package org.calamares.miga.ui.packs

import org.calamares.miga.ui.components.AdaptiveScaffold
import org.calamares.miga.L10n
import org.calamares.miga.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Packs of the catalogue grouped by series: the largest series first, packs without one last, by
 * name inside each series.
 */
private fun groupBySeries(items: List<PackListItem>): List<Pair<String?, List<PackListItem>>> =
    items.groupBy { it.entry.series?.takeIf { series -> series.isNotBlank() } }
        .entries
        .sortedWith(compareBy({ it.key == null }, { -it.value.size }, { it.key?.lowercase() }))
        .map { (series, group) -> series to group.sortedBy { it.entry.name.lowercase() } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacksCatalogScreen(
    viewModel: PacksCatalogViewModel,
    onBack: () -> Unit,
    onPackClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val selecting by viewModel.selecting.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val message by viewModel.message.collectAsState()
    var query by remember { mutableStateOf("") }
    var confirmUninstall by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val items = (uiState as? CatalogUiState.Loaded)?.items.orEmpty()
    val filtered = items.filter {
        query.isBlank() ||
            it.entry.name.contains(query, ignoreCase = true) ||
            it.entry.description.contains(query, ignoreCase = true) ||
            it.entry.author.contains(query, ignoreCase = true) ||
            it.entry.series.orEmpty().contains(query, ignoreCase = true)
    }
    val selectedItems = items.filter { it.entry.id in selected }
    val toInstall = selectedItems.count { it.canInstall }
    val toUninstall = selectedItems.count { it.isInstalled }
    val busy = progress != null

    BackHandler(enabled = selecting && !busy) { viewModel.clearSelection() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    AdaptiveScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selecting) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    title = { Text(L10n.str(R.string.n_selected, selected.size)) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }, enabled = !busy) {
                            Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.cancel))
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.selectAll(filtered.map { it.entry.id }) }, enabled = !busy) {
                            Icon(Icons.Filled.SelectAll, contentDescription = L10n.str(R.string.select_all))
                        }
                    }
                )
            } else {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    title = { Text(L10n.str(R.string.pack_catalogue)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = L10n.str(R.string.back)) }
                    },
                    actions = {
                        if (items.isNotEmpty()) {
                            IconButton(onClick = { viewModel.startSelection() }) {
                                Icon(Icons.Filled.Checklist, contentDescription = L10n.str(R.string.select_packs))
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (selecting) {
                Surface(tonalElevation = 3.dp) {
                    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                        progress?.let { batch ->
                            Text(
                                L10n.str(
                                    if (batch.uninstalling) R.string.uninstalling_x_of_x else R.string.installing_x_of_x,
                                    batch.done + 1, batch.total
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            LinearProgressIndicator(
                                progress = { batch.done.toFloat() / batch.total },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { confirmUninstall = true },
                                enabled = toUninstall > 0 && !busy,
                                modifier = Modifier.weight(1f)
                            ) { Text(L10n.str(R.string.uninstall_x, toUninstall)) }
                            Button(
                                onClick = { viewModel.installSelected(context) },
                                enabled = toInstall > 0 && !busy,
                                modifier = Modifier.weight(1f)
                            ) { Text(L10n.str(R.string.install_x, toInstall)) }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(L10n.str(R.string.search_packs)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true
            )

            when (val state = uiState) {
                is CatalogUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is CatalogUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                state.reason,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            OutlinedButton(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 16.dp)) {
                                Text(L10n.str(R.string.retry))
                            }
                        }
                    }
                }
                is CatalogUiState.Loaded -> {
                    if (filtered.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (state.items.isEmpty()) L10n.str(R.string.no_packs_have_been_published) else L10n.str(R.string.no_packs_match_search),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            groupBySeries(filtered).forEach { (series, group) ->
                                item(key = "series/${series.orEmpty()}", contentType = "header") {
                                    Text(
                                        series ?: L10n.str(R.string.books_group_other_packs),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                                items(group, key = { it.entry.id }) { item ->
                                    PackListCard(
                                        item = item,
                                        selecting = selecting,
                                        selected = item.entry.id in selected,
                                        onClick = {
                                            when {
                                                busy -> Unit
                                                selecting -> viewModel.toggleSelection(item.entry.id)
                                                else -> onPackClick(item.entry.id)
                                            }
                                        },
                                        onLongClick = { if (!busy) viewModel.startSelection(item.entry.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmUninstall) {
        AlertDialog(
            onDismissRequest = { confirmUninstall = false },
            title = { Text(L10n.str(R.string.uninstall_x_packs, toUninstall)) },
            text = { Text(L10n.str(R.string.uninstall_packs_explanation)) },
            confirmButton = {
                TextButton(onClick = { confirmUninstall = false; viewModel.uninstallSelected() }) {
                    Text(L10n.str(R.string.uninstall), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUninstall = false }) { Text(L10n.str(R.string.cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PackListCard(
    item: PackListItem,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = if (selecting) Alignment.CenterVertically else Alignment.Top
        ) {
            if (selecting) {
                // The whole card toggles the selection; the checkbox only shows it.
                Checkbox(checked = selected, onCheckedChange = null)
            }
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (item.entry.coverImageUrl != null) {
                    AsyncImage(
                        model = item.entry.coverImageUrl,
                        contentDescription = item.entry.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = item.entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    text = L10n.str(R.string.x_x_recipes, item.entry.author, item.entry.recipeCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.entry.description.isNotBlank()) {
                    Text(
                        text = item.entry.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                if (item.hasUpdate) {
                    AssistChip(
                        onClick = onClick,
                        label = { Text(L10n.str(R.string.update_available)) },
                        leadingIcon = { Icon(Icons.Filled.SystemUpdate, null, modifier = Modifier.size(16.dp)) }
                    )
                } else if (item.isInstalled) {
                    AssistChip(
                        onClick = onClick,
                        label = { Text(L10n.str(R.string.installed)) },
                        leadingIcon = { Icon(Icons.Filled.CloudDone, null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        }
    }
}
