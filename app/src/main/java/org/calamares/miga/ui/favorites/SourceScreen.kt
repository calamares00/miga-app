package org.calamares.miga.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.follow.FollowedSource
import org.calamares.miga.data.follow.SocialNetwork
import org.calamares.miga.ui.components.AdaptiveScaffold
import org.calamares.miga.ui.components.openWebLink

/** A followed channel or profile: its latest videos or posts, as a list or a grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceScreen(
    viewModel: SourceViewModel,
    onBack: () -> Unit,
    onRecipeClick: (Long) -> Unit,
    onImport: (bookId: Long, url: String) -> Unit
) {
    val context = LocalContext.current
    val source by viewModel.source.collectAsStateWithLifecycle()
    val posts by viewModel.posts.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val books by viewModel.books.collectAsStateWithLifecycle()
    val grid by viewModel.grid.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var confirmUnfollow by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf<String?>(null) }

    // Its posts are "new" while the user looks at them, and stop being new on leaving.
    DisposableEffect(Unit) { onDispose { viewModel.markOpened() } }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() } }

    AdaptiveScaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(source?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = L10n.str(R.string.back)) } },
                actions = {
                    if (refreshing) {
                        CircularProgressIndicator(modifier = Modifier.padding(horizontal = 12.dp).size(22.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = viewModel::refresh) { Icon(Icons.Filled.Refresh, contentDescription = L10n.str(R.string.favorites_refresh)) }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = L10n.str(R.string.more_options)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            source?.let { current ->
                                DropdownMenuItem(
                                    text = { Text(L10n.str(R.string.favorites_open_in, networkLabel(current.network))) },
                                    onClick = { menu = false; openWebLink(context, current.url) }
                                )
                            }
                            DropdownMenuItem(text = { Text(L10n.str(R.string.favorites_unfollow)) }, onClick = { menu = false; confirmUnfollow = true })
                        }
                    }
                }
            )
        }
    ) { padding ->
        val current = source ?: return@AdaptiveScaffold
        val header: @Composable () -> Unit = {
            Column {
                SourceHeader(current, posts.size)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SegmentedButton(selected = !grid, onClick = { viewModel.grid.value = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(L10n.str(R.string.favorites_list)) }
                    SegmentedButton(selected = grid, onClick = { viewModel.grid.value = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(L10n.str(R.string.favorites_grid)) }
                }
                if (posts.isEmpty() && !refreshing) {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(L10n.str(R.string.favorites_no_source_posts), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = viewModel::refresh, modifier = Modifier.padding(top = 12.dp)) { Text(L10n.str(R.string.favorites_refresh)) }
                    }
                }
            }
        }
        if (grid) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { header() }
                items(posts, key = { it.id }) { post ->
                    PostCell(post, { importing = post.url }, onRecipeClick, { openWebLink(context, post.url) }, showNetwork = false)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { header() }
                items(posts, key = { it.id }) { post ->
                    PostRow(post, showAuthor = false, { importing = post.url }, onRecipeClick, { openWebLink(context, post.url) }, { viewModel.toggleSaved(post) })
                }
            }
        }
    }

    importing?.let { url -> ImportBookDialog(books, onBook = { bookId -> importing = null; onImport(bookId, url) }, onDismiss = { importing = null }) }
    if (confirmUnfollow) {
        AlertDialog(
            onDismissRequest = { confirmUnfollow = false },
            title = { Text(L10n.str(R.string.favorites_unfollow)) },
            text = { Text(L10n.str(R.string.favorites_unfollow_confirm, source?.name.orEmpty())) },
            confirmButton = { TextButton(onClick = { confirmUnfollow = false; viewModel.unfollow(onBack) }) { Text(L10n.str(R.string.favorites_unfollow)) } },
            dismissButton = { TextButton(onClick = { confirmUnfollow = false }) { Text(L10n.str(R.string.cancel)) } }
        )
    }
}

@Composable
private fun SourceHeader(source: FollowedSource, count: Int) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        SourceAvatar(source, 72.dp, showCount = false)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(source.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(source.handle, networkLabel(source.network)).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val recent = when {
                source.network == SocialNetwork.YOUTUBE && count == 1 -> L10n.str(R.string.favorites_recent_videos_one)
                source.network == SocialNetwork.YOUTUBE -> L10n.str(R.string.favorites_recent_videos_n, count)
                count == 1 -> L10n.str(R.string.favorites_recent_posts_one)
                else -> L10n.str(R.string.favorites_recent_posts_n, count)
            }
            val news = if (source.newCount > 0) " · " + newCountText(source.newCount) else ""
            Text(recent + news, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
