package org.calamares.miga.ui.favorites

import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.follow.FavoritePost
import org.calamares.miga.data.follow.SocialLink
import org.calamares.miga.data.follow.SocialNetwork
import org.calamares.miga.ui.components.AdaptiveScaffold
import org.calamares.miga.ui.components.openWebLink

/**
 * The Favourites tab. "Recipes" is the favourite recipes with their search and filters
 * ([recipes], which shows [FavoritesFilterChips] as its header); the other views are the followed
 * channels and profiles and the saved videos and reels.
 */
@Composable
fun FavoritesTab(
    viewModel: FavoritesViewModel,
    recipes: @Composable (header: @Composable () -> Unit) -> Unit,
    onRecipeClick: (Long) -> Unit,
    onOpenSource: (Long) -> Unit,
    onImport: (bookId: Long, url: String) -> Unit
) {
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val chips: @Composable () -> Unit = { FavoritesFilterChips(viewModel) }
    if (filter == FavoritesFilter.RECIPES) recipes(chips) else FavoritesHub(viewModel, chips, onRecipeClick, onOpenSource, onImport)
}

/** Todo · Recetas · Canales · Vídeos y reels, with how many there are. */
@Composable
fun FavoritesFilterChips(viewModel: FavoritesViewModel) {
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val recipes by viewModel.favoriteRecipes.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            FavoritesFilter.ALL to L10n.str(R.string.favorites_all),
            FavoritesFilter.RECIPES to L10n.str(R.string.favorites_recipes_n, recipes.size),
            FavoritesFilter.CHANNELS to L10n.str(R.string.favorites_channels_n, sources.size),
            FavoritesFilter.POSTS to L10n.str(R.string.favorites_posts_n, saved.size)
        ).forEach { (value, label) ->
            FilterChip(selected = filter == value, onClick = { viewModel.setFilter(value) }, label = { Text(label) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesHub(
    viewModel: FavoritesViewModel,
    chips: @Composable () -> Unit,
    onRecipeClick: (Long) -> Unit,
    onOpenSource: (Long) -> Unit,
    onImport: (bookId: Long, url: String) -> Unit
) {
    val context = LocalContext.current
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val openSource by viewModel.openSource.collectAsStateWithLifecycle()
    val books by viewModel.books.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var importing by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() } }
    LaunchedEffect(openSource) {
        openSource?.let { showAdd = false; viewModel.consumeOpenSource(); onOpenSource(it) }
    }

    val actions = PostCallbacks(
        onImport = { post -> importing = post.url },
        onOpenRecipe = onRecipeClick,
        onOpen = { post -> openWebLink(context, post.url) },
        onToggleSaved = viewModel::toggleSaved
    )

    AdaptiveScaffold(
        contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.navigationBars),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(L10n.str(R.string.favourites_2)) },
                actions = {
                    if (refreshing) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 16.dp).size(22.dp), strokeWidth = 2.dp)
                    } else if (filter != FavoritesFilter.POSTS) {
                        IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Filled.Refresh, contentDescription = L10n.str(R.string.favorites_refresh)) }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showAdd = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(L10n.str(R.string.favorites_add)) })
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            chips()
            when (filter) {
                FavoritesFilter.ALL -> AllView(viewModel, actions, onRecipeClick, onOpenSource) { showAdd = true }
                FavoritesFilter.CHANNELS -> ChannelsView(viewModel, onOpenSource) { showAdd = true }
                FavoritesFilter.POSTS -> PostsView(viewModel, actions) { showAdd = true }
                FavoritesFilter.RECIPES -> Unit
            }
        }
    }

    if (showAdd) AddSheet(viewModel, onDismiss = { showAdd = false; viewModel.resetAdd() }, onImport = { url -> showAdd = false; importing = url })
    importing?.let { url ->
        ImportBookDialog(books, onBook = { bookId -> importing = null; onImport(bookId, url) }, onDismiss = { importing = null })
    }
}

/** What a post's buttons do, the same everywhere. */
internal class PostCallbacks(
    val onImport: (FavoritePost) -> Unit,
    val onOpenRecipe: (Long) -> Unit,
    val onOpen: (FavoritePost) -> Unit,
    val onToggleSaved: (FavoritePost) -> Unit
)

@Composable
private fun AllView(viewModel: FavoritesViewModel, actions: PostCallbacks, onRecipeClick: (Long) -> Unit, onOpenSource: (Long) -> Unit, onAdd: () -> Unit) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val feed by viewModel.feed.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val recipes by viewModel.favoriteRecipes.collectAsStateWithLifecycle()
    if (sources.isEmpty() && saved.isEmpty() && recipes.isEmpty()) {
        EmptyFavorites(onAdd)
        return
    }
    val news = feed.filter { it.isNew }.take(MAX_NEWS)
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        if (sources.isNotEmpty()) {
            item { SectionTitle(L10n.str(R.string.favorites_your_channels), L10n.str(R.string.favorites_see_all)) { viewModel.setFilter(FavoritesFilter.CHANNELS) } }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(sources, key = { it.id }) { source ->
                        Column(
                            modifier = Modifier.width(76.dp).clip(RoundedCornerShape(12.dp)).clickable { onOpenSource(source.id) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            SourceAvatar(source, 64.dp)
                            Text(source.handle ?: source.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            val shown = news.ifEmpty { feed.take(MAX_LATEST) }
            if (shown.isNotEmpty()) {
                item { SectionTitle(L10n.str(if (news.isEmpty()) R.string.favorites_latest else R.string.favorites_news)) }
                items(shown, key = { "feed_" + it.id }) { post ->
                    PostRow(post, showAuthor = true, { actions.onImport(post) }, actions.onOpenRecipe, { actions.onOpen(post) }, { actions.onToggleSaved(post) })
                }
            }
        }
        if (saved.isNotEmpty()) {
            item { SectionTitle(L10n.str(R.string.favorites_saved_posts), L10n.str(R.string.favorites_see_all)) { viewModel.setFilter(FavoritesFilter.POSTS) } }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(saved, key = { "saved_" + it.id }) { post ->
                        Column(modifier = Modifier.width(112.dp).clickable { actions.onOpen(post) }) {
                            PostThumbnail(post, Modifier.fillMaxWidth().aspectRatio(9f / 13f), showNetwork = true)
                            Text(post.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
        if (recipes.isNotEmpty()) {
            item { SectionTitle(L10n.str(R.string.favorites_saved_recipes), L10n.str(R.string.favorites_see_all)) { viewModel.setFilter(FavoritesFilter.RECIPES) } }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recipes.take(MAX_RECIPES), key = { "recipe_" + it.id }) { recipe ->
                        Column(modifier = Modifier.width(128.dp).clip(RoundedCornerShape(14.dp)).clickable { onRecipeClick(recipe.id) }) {
                            AsyncImage(
                                model = recipe.coverPhotoUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            Text(recipe.name, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkChips(viewModel: FavoritesViewModel, extra: @Composable () -> Unit = {}) {
    val network by viewModel.network.collectAsStateWithLifecycle()
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(selected = network == null, onClick = { viewModel.setNetwork(null) }, label = { Text(L10n.str(R.string.favorites_all)) })
        SocialNetwork.entries.forEach { value ->
            FilterChip(
                selected = network == value,
                onClick = { viewModel.setNetwork(value) },
                label = { Text(if (value == SocialNetwork.WEB) L10n.str(R.string.favorites_network_webs) else networkLabel(value)) },
                leadingIcon = { NetworkBadge(value, size = 16.dp) }
            )
        }
        extra()
    }
}

@Composable
private fun ChannelsView(viewModel: FavoritesViewModel, onOpenSource: (Long) -> Unit, onAdd: () -> Unit) {
    val all by viewModel.sources.collectAsStateWithLifecycle()
    val sources by viewModel.visibleSources.collectAsStateWithLifecycle()
    if (all.isEmpty()) {
        EmptyFavorites(onAdd, L10n.str(R.string.favorites_no_channels))
        return
    }
    NetworkChips(viewModel)
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        items(sources, key = { it.id }) { source ->
            ListItem(
                modifier = Modifier.clickable { onOpenSource(source.id) },
                leadingContent = { SourceAvatar(source, 48.dp, showCount = false) },
                headlineContent = { Text(source.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(listOfNotNull(source.handle, networkLabel(source.network)).joinToString(" · "), maxLines = 1) },
                trailingContent = {
                    if (source.newCount > 0) Text(newCountText(source.newCount), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge)
                }
            )
        }
    }
}

@Composable
private fun PostsView(viewModel: FavoritesViewModel, actions: PostCallbacks, onAdd: () -> Unit) {
    val all by viewModel.saved.collectAsStateWithLifecycle()
    val posts by viewModel.visibleSaved.collectAsStateWithLifecycle()
    val onlyNotImported by viewModel.onlyNotImported.collectAsStateWithLifecycle()
    if (all.isEmpty()) {
        EmptyFavorites(onAdd, L10n.str(R.string.favorites_no_posts))
        return
    }
    NetworkChips(viewModel) {
        FilterChip(selected = onlyNotImported, onClick = { viewModel.setOnlyNotImported(!onlyNotImported) }, label = { Text(L10n.str(R.string.favorites_not_imported)) })
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(posts, key = { it.id }) { post ->
            Box {
                PostCell(post, { actions.onImport(post) }, actions.onOpenRecipe, { actions.onOpen(post) }, showNetwork = true)
                IconButton(onClick = { actions.onToggleSaved(post) }, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Filled.Favorite, contentDescription = L10n.str(R.string.favorites_unsave), tint = Color.White)
                }
            }
        }
    }
}

/** Nothing saved yet: what Favourites holds and how to add to it. */
@Composable
private fun EmptyFavorites(onAdd: () -> Unit, title: String = L10n.str(R.string.favorites_empty_title)) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.FavoriteBorder, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
        Text(
            L10n.str(R.string.favorites_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        FilledTonalButton(onClick = onAdd, modifier = Modifier.padding(top = 20.dp)) {
            Icon(Icons.Filled.Add, null)
            Spacer(Modifier.width(8.dp))
            Text(L10n.str(R.string.favorites_add))
        }
    }
}

/** Paste a link (or share it from YouTube or Instagram) and follow, save or import it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSheet(viewModel: FavoritesViewModel, onDismiss: () -> Unit, onImport: (String) -> Unit) {
    val context = LocalContext.current
    val state by viewModel.addState.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding()) {
            Text(L10n.str(R.string.favorites_add_title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; viewModel.inspect(it) },
                label = { Text(L10n.str(R.string.favorites_add_link)) },
                placeholder = { Text("https://…") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        val pasted = context.getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        if (pasted.isNotBlank()) { text = pasted; viewModel.inspect(pasted) }
                    }) { Icon(Icons.Filled.ContentPaste, contentDescription = L10n.str(R.string.favorites_paste)) }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
            Text(
                L10n.str(R.string.favorites_add_share_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            when (val current = state) {
                AddState.Idle -> Unit
                AddState.Working -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is AddState.NotSupported -> Text(current.text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                is AddState.Found -> {
                    val link = current.link
                    val network = when (link) {
                        is SocialLink.YouTubeChannel, is SocialLink.YouTubeVideo -> SocialNetwork.YOUTUBE
                        is SocialLink.InstagramProfile, is SocialLink.InstagramPost -> SocialNetwork.INSTAGRAM
                        is SocialLink.Website -> SocialNetwork.WEB
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                        NetworkBadge(network, size = 28.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(L10n.str(linkKind(link)), style = MaterialTheme.typography.titleSmall)
                    }
                    if (link is SocialLink.Website) {
                        // The page may be the site (or a section) or one of its recipes.
                        AddOption(Icons.Filled.PersonAdd, L10n.str(R.string.favorites_follow_web), L10n.str(R.string.favorites_follow_web_desc)) { viewModel.follow(link) }
                        AddOption(Icons.Filled.FavoriteBorder, L10n.str(R.string.favorites_save_recipe), L10n.str(R.string.favorites_save_post_desc)) { viewModel.saveLink(link) }
                        AddOption(Icons.Filled.Download, L10n.str(R.string.favorites_import_recipe), L10n.str(R.string.favorites_import_recipe_desc)) { onImport(link.url) }
                    } else if (link.isSource) {
                        AddOption(Icons.Filled.PersonAdd, L10n.str(R.string.favorites_follow), L10n.str(R.string.favorites_follow_desc)) { viewModel.follow(link) }
                    } else {
                        AddOption(Icons.Filled.FavoriteBorder, L10n.str(R.string.favorites_save_post), L10n.str(R.string.favorites_save_post_desc)) { viewModel.saveLink(link) }
                        AddOption(Icons.Filled.Download, L10n.str(R.string.favorites_import_recipe), L10n.str(R.string.favorites_import_recipe_desc)) { onImport(link.url) }
                    }
                }
            }
        }
    }
}

internal fun linkKind(link: SocialLink): Int = when (link) {
    is SocialLink.YouTubeChannel -> R.string.favorites_kind_channel
    is SocialLink.YouTubeVideo -> R.string.favorites_kind_video
    is SocialLink.InstagramProfile -> R.string.favorites_kind_profile
    is SocialLink.InstagramPost -> R.string.favorites_kind_post
    is SocialLink.Website -> R.string.favorites_kind_website
}

@Composable
private fun AddOption(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(title) },
        supportingContent = { Text(description) }
    )
}

private const val MAX_NEWS = 6
private const val MAX_LATEST = 4
private const val MAX_RECIPES = 12
