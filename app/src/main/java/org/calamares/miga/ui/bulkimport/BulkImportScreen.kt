package org.calamares.miga.ui.bulkimport

import org.calamares.miga.ui.components.AdaptiveScaffold
import org.calamares.miga.ui.components.AiProgressView
import org.calamares.miga.data.ai.AiProgress
import org.calamares.miga.ui.components.ErrorMessage
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.ui.components.AiContentNotice
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkImportScreen(
    viewModel: BulkImportViewModel,
    onBack: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
    /** Every photo turned out to be the same recipe: it is reviewed in the editor instead. */
    onSingleRecipe: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val singleRecipe by viewModel.singleRecipe.collectAsStateWithLifecycle()
    val progress by viewModel.aiProgress.collectAsStateWithLifecycle()
    val isProcessing = phase == BulkImportPhase.Grouping ||
        rows.any { it.state is BulkImportRowState.Pending || it.state is BulkImportRowState.Processing }
    var showExitConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.start(context) }
    LaunchedEffect(singleRecipe) { singleRecipe?.let(onSingleRecipe) }
    BackHandler(enabled = isProcessing) { showExitConfirm = true }

    AdaptiveScaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(L10n.str(R.string.recipes_from_photos)) },
                navigationIcon = {
                    IconButton(onClick = { if (isProcessing) showExitConfirm = true else onBack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = L10n.str(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        when (val current = phase) {
            // Sorting the photos into recipes, before there is a list to show.
            BulkImportPhase.Grouping -> Box(modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                AiProgressView(progress = progress, fallback = L10n.str(R.string.ai_step_sorting_photos), centered = true)
            }
            is BulkImportPhase.GroupingFailed -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(L10n.str(R.string.sorting_photos_failed), style = MaterialTheme.typography.titleMedium)
                ErrorMessage(current.reason, onRetry = { viewModel.retryGrouping(context) })
                OutlinedButton(onClick = { viewModel.importOnePerPhoto(context) }) { Text(L10n.str(R.string.one_recipe_per_photo)) }
            }
            BulkImportPhase.Importing -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                AiContentNotice(
                    feature = L10n.str(R.string.recipes_from_photos),
                    content = { L10n.str(R.string.recipes_imported_ai_x_photos, rows.size) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            items(rows) { row ->
                BulkImportRowItem(
                    row = row,
                    progress = progress,
                    onClick = { (row.state as? BulkImportRowState.Success)?.let { onOpenRecipe(it.recipeId) } },
                    onRetry = { viewModel.retry(context, rows.indexOf(row)) }
                )
            }
            }
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text(L10n.str(R.string.leave_2)) },
            text = { Text(L10n.str(R.string.photos_still_being_processed_havent)) },
            confirmButton = {
                TextButton(onClick = { showExitConfirm = false; onBack() }) { Text(L10n.str(R.string.leave)) }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text(L10n.str(R.string.wait)) }
            }
        )
    }
}

@Composable
private fun BulkImportRowItem(row: BulkImportRow, progress: AiProgress?, onClick: () -> Unit, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (row.state is BulkImportRowState.Success) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            AsyncImage(
                model = row.photoUris.first(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            // A recipe read from several photos (its pages, or the dish) says how many.
            if (row.photoUris.size > 1) {
                Text(
                    "${row.photoUris.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
        when (val state = row.state) {
            BulkImportRowState.Pending -> Text(
                L10n.str(R.string.queued),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            BulkImportRowState.Processing -> AiProgressView(
                progress = progress,
                fallback = L10n.str(R.string.processing),
                modifier = Modifier.weight(1f),
                spinnerSize = 20.dp,
                showDone = false
            )
            is BulkImportRowState.Success -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(state.name, style = MaterialTheme.typography.bodyLarge, maxLines = 3)
                    state.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            is BulkImportRowState.Failed -> Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ErrorMessage(state.reason, modifier = Modifier.weight(1f), onRetry = onRetry)
            }
        }
    }
}
