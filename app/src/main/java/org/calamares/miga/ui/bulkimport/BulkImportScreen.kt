package org.calamares.miga.ui.bulkimport

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkImportScreen(
    viewModel: BulkImportViewModel,
    onBack: () -> Unit,
    onOpenRecipe: (Long) -> Unit
) {
    val context = LocalContext.current
    val rows by viewModel.rows.collectAsState()
    val progress by viewModel.aiProgress.collectAsState()
    val isProcessing = rows.any { it.state is BulkImportRowState.Pending || it.state is BulkImportRowState.Processing }
    var showExitConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.start(context) }
    BackHandler(enabled = isProcessing) { showExitConfirm = true }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(L10n.str(R.string.several_recipes_images)) },
                navigationIcon = {
                    IconButton(onClick = { if (isProcessing) showExitConfirm = true else onBack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = L10n.str(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                AiContentNotice(
                    feature = L10n.str(R.string.several_recipes_images),
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
        AsyncImage(
            model = row.photoUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
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
                spinnerSize = 20.dp
            )
            is BulkImportRowState.Success -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(state.name, style = MaterialTheme.typography.bodyLarge, maxLines = 3)
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
