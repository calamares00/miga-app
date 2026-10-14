package org.calamares.miga.ui.detail

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.polish.BulkPolish
import org.calamares.miga.data.polish.BulkPolishItem
import org.calamares.miga.data.polish.BulkPolishState
import org.calamares.miga.ui.components.AiContentNotice
import org.calamares.miga.ui.components.ButtonContent
import org.calamares.miga.ui.components.ErrorMessage

/**
 * The recipes being improved with AI together (see BulkPolish), like the import of several
 * photos: each with its state, tapped to review it (before / after) and apply or discard it, or
 * all the reviewed ones applied at once. Leaving does not stop it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkPolishScreen(onBack: () -> Unit, onOpenRecipe: (Long) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by BulkPolish.items.collectAsState()
    val running = items.any { it.state == BulkPolishState.Working || it.state == BulkPolishState.Queued } && BulkPolish.isRunning
    val total = items.size
    val finished = items.count { it.state !is BulkPolishState.Queued && it.state != BulkPolishState.Working }
    val readyCount = items.count { it.state is BulkPolishState.Ready }
    val failedCount = items.count { it.state is BulkPolishState.Failed }
    var reviewing by remember { mutableStateOf<Long?>(null) }
    var confirmApplyAll by remember { mutableStateOf(false) }

    // While the list is on screen, its end needs no notification.
    DisposableEffect(Unit) {
        BulkPolish.watched = true
        onDispose { BulkPolish.watched = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(L10n.str(R.string.bulk_polish_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = L10n.str(R.string.back)) } },
                actions = {
                    when {
                        running -> TextButton(onClick = { BulkPolish.cancel() }) { Text(L10n.str(R.string.cancel)) }
                        items.any { it.state == BulkPolishState.Queued } ->
                            TextButton(onClick = { BulkPolish.resume() }) { Text(L10n.str(R.string.bulk_polish_resume)) }
                        failedCount > 0 -> TextButton(onClick = { BulkPolish.retryFailed() }) { Text(L10n.str(R.string.bulk_polish_retry_failed)) }
                        items.isNotEmpty() && readyCount == 0 -> TextButton(onClick = { BulkPolish.clear(); onBack() }) { Text(L10n.str(R.string.bulk_polish_finish)) }
                    }
                }
            )
        },
        bottomBar = {
            if (!running && readyCount > 0) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { confirmApplyAll = true },
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)
                    ) { ButtonContent(Icons.Filled.DoneAll, L10n.str(R.string.bulk_polish_apply_all_n, readyCount)) }
                }
            }
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(L10n.str(R.string.bulk_polish_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item(key = "summary") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        L10n.str(R.string.bulk_polish_summary, finished, total, readyCount),
                        style = MaterialTheme.typography.titleSmall
                    )
                    LinearProgressIndicator(progress = { if (total == 0) 0f else finished.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                    Text(
                        L10n.str(if (running) R.string.ai_background_hint else R.string.bulk_polish_review_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AiContentNotice(
                        feature = L10n.str(R.string.bulk_polish_title),
                        content = { L10n.str(R.string.bulk_polish_summary, finished, total, readyCount) }
                    )
                }
                HorizontalDivider()
            }
            items(items, key = { it.recipe.id }) { item ->
                BulkPolishRow(
                    item = item,
                    onReview = { reviewing = item.recipe.id },
                    onOpenRecipe = { onOpenRecipe(item.recipe.id) },
                    onRetry = { BulkPolish.retryFailed() }.takeIf { !running }
                )
                HorizontalDivider()
            }
        }
    }

    // Review of one recipe: Apply or Discard decide; back only closes it, to review it later.
    val reviewed = items.firstOrNull { it.recipe.id == reviewing }
    val ready = reviewed?.state as? BulkPolishState.Ready
    if (reviewed != null && ready != null) {
        val position = items.filter { it.state is BulkPolishState.Ready }.indexOfFirst { it.recipe.id == reviewed.recipe.id } + 1
        PolishReview(
            polished = ready.polished,
            recipe = reviewed.recipe,
            subtitle = L10n.str(R.string.bulk_polish_reviewing_x_of_y, position, readyCount),
            onApply = {
                scope.launch { BulkPolish.apply(reviewed.recipe.id) }
                reviewing = nextReady(items, reviewed.recipe.id)
            },
            onDiscard = {
                BulkPolish.discard(reviewed.recipe.id)
                reviewing = nextReady(items, reviewed.recipe.id)
            },
            onDismiss = { reviewing = null }
        )
    }

    if (confirmApplyAll) {
        AlertDialog(
            onDismissRequest = { confirmApplyAll = false },
            icon = { Icon(Icons.Filled.DoneAll, contentDescription = null) },
            title = { Text(L10n.str(R.string.bulk_polish_apply_all_n, readyCount)) },
            text = { Text(L10n.str(R.string.bulk_polish_apply_all_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmApplyAll = false
                    scope.launch {
                        val applied = BulkPolish.applyAllReady()
                        Toast.makeText(context, L10n.str(R.string.bulk_polish_applied_n, applied), Toast.LENGTH_SHORT).show()
                    }
                }) { Text(L10n.str(R.string.polish_apply)) }
            },
            dismissButton = { TextButton(onClick = { confirmApplyAll = false }) { Text(L10n.str(R.string.cancel)) } }
        )
    }
}

/** The next recipe waiting for review after [currentId], to go through the list in one go. */
private fun nextReady(items: List<BulkPolishItem>, currentId: Long): Long? {
    val index = items.indexOfFirst { it.recipe.id == currentId }
    return (items.drop(index + 1) + items.take(index))
        .firstOrNull { it.state is BulkPolishState.Ready && it.recipe.id != currentId }?.recipe?.id
}

@Composable
private fun BulkPolishRow(item: BulkPolishItem, onReview: () -> Unit, onOpenRecipe: () -> Unit, onRetry: (() -> Unit)?) {
    val state = item.state
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = if (state is BulkPolishState.Ready) onReview else onOpenRecipe)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val photo = item.recipe.coverPhotoUri
            if (photo != null) {
                AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Outlined.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.recipe.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            when (state) {
                BulkPolishState.Queued -> StatusLine(Icons.Filled.HourglassEmpty, L10n.str(R.string.queued))
                BulkPolishState.Working -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(L10n.str(R.string.bulk_polish_working), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                is BulkPolishState.Ready -> StatusLine(
                    Icons.Filled.RateReview,
                    if (state.polished.changes.isEmpty()) L10n.str(R.string.bulk_polish_ready) else L10n.str(R.string.bulk_polish_ready_n_changes, state.polished.changes.size),
                    highlighted = true
                )
                is BulkPolishState.Failed -> ErrorMessage(state.reason, onRetry = onRetry)
                BulkPolishState.Applied -> StatusLine(Icons.Filled.CheckCircle, L10n.str(R.string.bulk_polish_applied), highlighted = true)
                BulkPolishState.Discarded -> StatusLine(Icons.Filled.RemoveCircleOutline, L10n.str(R.string.bulk_polish_discarded))
            }
        }
        if (state is BulkPolishState.Ready) {
            FilledTonalButton(onClick = onReview) { Text(L10n.str(R.string.bulk_polish_review)) }
        }
    }
}

@Composable
private fun StatusLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, highlighted: Boolean = false) {
    val color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}
