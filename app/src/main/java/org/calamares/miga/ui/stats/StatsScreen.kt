package org.calamares.miga.ui.stats

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.stats.CountEntry
import org.calamares.miga.data.stats.LibraryStats
import org.calamares.miga.data.stats.LibraryStatsCalculator
import org.calamares.miga.data.stats.RankedRecipe
import org.calamares.miga.data.stats.StorageUsage
import java.util.Locale

private fun formatMinutes(minutes: Int): String = when {
    minutes < 60 -> L10n.str(R.string.stats_minutes_x, minutes)
    minutes % 60 == 0 -> L10n.str(R.string.stats_hours_x, minutes / 60)
    else -> L10n.str(R.string.stats_hours_minutes_x_y, minutes / 60, minutes % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: StatsViewModel, onBack: () -> Unit, onRecipeClick: (Long) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(L10n.str(R.string.statistics)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = L10n.str(R.string.back)) }
                }
            )
        }
    ) { padding ->
        val stats = uiState.stats
        if (uiState.isLoading || stats == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (stats.totalRecipes == 0) {
                    Text(
                        L10n.str(R.string.dont_have_recipes_yet_nadd),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                    )
                } else {
                    Overview(stats)
                    LibraryContent(stats, onRecipeClick)
                }
                StorageCard(
                    storage = uiState.storage,
                    busy = uiState.storageBusy,
                    onClearCache = { viewModel.clearCache(showMessage) },
                    onDeleteUnusedPhotos = { viewModel.deleteUnusedPhotos(showMessage) }
                )
            }
        }
    }
}

@Composable
private fun Overview(stats: LibraryStats) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(Icons.AutoMirrored.Filled.MenuBook, stats.totalRecipes, L10n.str(R.string.recipes), Modifier.weight(1f))
            StatTile(Icons.Filled.LibraryBooks, stats.totalBooks, L10n.str(R.string.books), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(Icons.Filled.Favorite, stats.favorites, L10n.str(R.string.favourites), Modifier.weight(1f))
            StatTile(Icons.Filled.Restaurant, stats.timesCookedTotal, L10n.str(R.string.stats_times_cooked), Modifier.weight(1f))
        }
        val highlights = listOfNotNull(
            stats.addedThisMonth.takeIf { it > 0 }?.let { L10n.str(R.string.stats_added_this_month_x, it) },
            stats.photos.takeIf { it > 0 }?.let { L10n.str(R.string.stats_photos_x, it) }
        )
        if (highlights.isNotEmpty()) {
            Text(
                highlights.joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryContent(stats: LibraryStats, onRecipeClick: (Long) -> Unit) {
    val averageMinutes = stats.averageMinutes
    if (averageMinutes != null) {
        StatsCard(Icons.Filled.Schedule, L10n.str(R.string.stats_time)) {
            KeyValueRow(L10n.str(R.string.stats_average_time), formatMinutes(averageMinutes))
            KeyValueRow(
                L10n.str(R.string.stats_quick_recipes_x, LibraryStatsCalculator.QUICK_RECIPE_MINUTES),
                "${stats.quickRecipes} (${stats.quickRecipes * 100 / stats.totalRecipes} %)"
            )
            stats.longestRecipe?.let { longest ->
                KeyValueRow(
                    L10n.str(R.string.stats_longest),
                    "${longest.name} · ${formatMinutes(longest.value)}",
                    onClick = { onRecipeClick(longest.recipeId) }
                )
            }
        }
    }

    if (stats.mostCooked.isNotEmpty()) {
        StatsCard(Icons.Filled.EmojiEvents, L10n.str(R.string.most_cooked_2)) {
            stats.mostCooked.forEachIndexed { index, entry ->
                RankedRow(index + 1, entry, L10n.str(R.string.stats_times_x, entry.value)) { onRecipeClick(entry.recipeId) }
            }
        }
    }

    if (stats.topRated.isNotEmpty()) {
        StatsCard(Icons.Filled.Star, L10n.str(R.string.stats_top_rated)) {
            stats.averageRating?.let { average ->
                Text(
                    L10n.str(R.string.stats_average_rating_x_y, String.format(Locale.getDefault(), "%.1f", average), stats.ratedRecipes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            stats.topRated.forEachIndexed { index, entry ->
                RankedRow(index + 1, entry, "★".repeat(entry.value)) { onRecipeClick(entry.recipeId) }
            }
        }
    }

    if (stats.byBook.size > 1) {
        StatsCard(Icons.Filled.LibraryBooks, L10n.str(R.string.stats_by_book)) { BarList(stats.byBook) }
    }
    if (stats.byCategory.isNotEmpty()) {
        StatsCard(Icons.Filled.Category, L10n.str(R.string.category_2)) { BarList(stats.byCategory) }
    }
    if (stats.byDifficulty.isNotEmpty()) {
        StatsCard(Icons.Filled.BarChart, L10n.str(R.string.difficulty_2)) { BarList(stats.byDifficulty) }
    }
    if (stats.topIngredients.isNotEmpty()) {
        StatsCard(Icons.Filled.Kitchen, L10n.str(R.string.stats_top_ingredients)) { BarList(stats.topIngredients) }
    }
    if (stats.topTags.isNotEmpty()) {
        StatsCard(Icons.Filled.LocalOffer, L10n.str(R.string.stats_top_tags)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                stats.topTags.forEach { tag ->
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(10.dp)) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(tag.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "${tag.count}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }

    StatsCard(Icons.Filled.TaskAlt, L10n.str(R.string.stats_to_complete)) {
        if (!stats.incomplete) {
            Text(L10n.str(R.string.stats_all_complete), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            MissingRow(Icons.Filled.PhotoCamera, L10n.str(R.string.stats_without_photo), stats.withoutPhoto, stats.totalRecipes)
            MissingRow(Icons.Filled.Category, L10n.str(R.string.stats_without_category), stats.withoutCategory, stats.totalRecipes)
            MissingRow(Icons.Filled.Kitchen, L10n.str(R.string.stats_without_ingredients), stats.withoutIngredients, stats.totalRecipes)
            MissingRow(Icons.Filled.FormatListNumbered, L10n.str(R.string.stats_without_steps), stats.withoutSteps, stats.totalRecipes)
            MissingRow(Icons.Filled.Schedule, L10n.str(R.string.stats_without_time), stats.withoutTime, stats.totalRecipes)
        }
    }
}

@Composable
private fun StatTile(icon: ImageVector, value: Int, label: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("$value", style = MaterialTheme.typography.titleLarge)
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StatsCard(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
private fun KeyValueRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.4f, fill = false)
        )
    }
}

@Composable
private fun RankedRow(position: Int, entry: RankedRecipe, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (position == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$position",
                style = MaterialTheme.typography.labelLarge,
                color = if (position == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
    }
}

/** Horizontal bars, each with its label and count above, scaled to the largest count. */
@Composable
private fun BarList(entries: List<CountEntry>) {
    val maxCount = entries.maxOf { it.count }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        entries.forEach { entry ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${entry.count}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(entry.count.toFloat() / maxCount)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

@Composable
private fun MissingRow(icon: ImageVector, label: String, count: Int, total: Int) {
    if (count > 0) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("$count / $total", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

private data class StoragePart(val label: String, val bytes: Long, val color: Color)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StorageCard(storage: StorageUsage?, busy: Boolean, onClearCache: () -> Unit, onDeleteUnusedPhotos: () -> Unit) {
    val context = LocalContext.current
    fun size(bytes: Long): String = Formatter.formatShortFileSize(context, bytes)
    var confirmDelete by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    StatsCard(Icons.Filled.Storage, L10n.str(R.string.stats_storage)) {
        if (storage == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(L10n.str(R.string.stats_storage_calculating), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            val parts = listOf(
                StoragePart(L10n.str(R.string.stats_storage_photos_x, storage.photosCount), storage.photosBytes, colors.primary),
                StoragePart(L10n.str(R.string.stats_storage_database), storage.databaseBytes, colors.tertiary),
                StoragePart(L10n.str(R.string.stats_storage_cache), storage.cacheBytes, colors.secondary),
                StoragePart(L10n.str(R.string.stats_storage_other), storage.otherBytes, colors.outline)
            )
            Text(L10n.str(R.string.stats_storage_total_x, size(storage.totalBytes)), style = MaterialTheme.typography.headlineSmall)
            // One bar split by kind; empty kinds take no room.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(colors.surfaceVariant)
            ) {
                parts.filter { it.bytes > 0 }.forEach { part ->
                    Box(modifier = Modifier.weight(part.bytes.toFloat()).fillMaxHeight().background(part.color))
                }
            }
            parts.forEach { part ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(part.color))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(part.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(size(part.bytes), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                }
            }

            if (storage.unusedPhotosCount > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    L10n.str(R.string.stats_unused_photos_x_y, storage.unusedPhotosCount, size(storage.unusedPhotosBytes)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }
            // Wraps on narrow screens instead of squeezing the buttons.
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp).align(Alignment.CenterVertically), strokeWidth = 2.dp)
                OutlinedButton(onClick = onClearCache, enabled = !busy && storage.cacheBytes > 0) {
                    Text(L10n.str(R.string.stats_clear_cache))
                }
                if (storage.unusedPhotosCount > 0) {
                    FilledTonalButton(onClick = { confirmDelete = true }, enabled = !busy) {
                        Text(L10n.str(R.string.stats_delete_unused_photos))
                    }
                }
            }

            if (confirmDelete) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    title = { Text(L10n.str(R.string.stats_delete_unused_photos)) },
                    text = { Text(L10n.str(R.string.stats_delete_unused_photos_confirm_x_y, storage.unusedPhotosCount, size(storage.unusedPhotosBytes))) },
                    confirmButton = {
                        TextButton(onClick = { confirmDelete = false; onDeleteUnusedPhotos() }) {
                            Text(L10n.str(R.string.delete_2), color = colors.error)
                        }
                    },
                    dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(L10n.str(R.string.cancel)) } }
                )
            }
        }
    }
}
