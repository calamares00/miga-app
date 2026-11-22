package org.calamares.miga.ui.favorites

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.follow.FavoritePost
import org.calamares.miga.data.follow.FollowedSource
import org.calamares.miga.data.follow.PostKind
import org.calamares.miga.data.follow.SocialNetwork
import org.calamares.miga.data.model.RecipeBookSummary

private val YOUTUBE_RED = Color(0xFFE53935)
private val INSTAGRAM_BRUSH = Brush.linearGradient(listOf(Color(0xFFF58529), Color(0xFFDD2A7B), Color(0xFF8134AF)))

/** The small round badge of a network: ▶ for YouTube, ◎ for Instagram. */
@Composable
internal fun NetworkBadge(network: SocialNetwork, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(if (network == SocialNetwork.YOUTUBE) Modifier.background(YOUTUBE_RED) else Modifier.background(INSTAGRAM_BRUSH)),
        contentAlignment = Alignment.Center
    ) {
        if (network == SocialNetwork.YOUTUBE) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "YouTube", tint = Color.White, modifier = Modifier.size(size * 0.7f))
        } else {
            Box(Modifier.size(size * 0.5f).border(1.5.dp, Color.White, RoundedCornerShape(size * 0.15f)))
        }
    }
}

/** "1 nuevo" or "3 nuevos". */
internal fun newCountText(count: Int) = if (count == 1) L10n.str(R.string.favorites_new_one) else L10n.str(R.string.favorites_new_n, count)

internal fun networkLabel(network: SocialNetwork) = if (network == SocialNetwork.YOUTUBE) "YouTube" else "Instagram"

/** A channel's or profile's picture with its network badge and, when it has some, the new posts count. */
@Composable
internal fun SourceAvatar(source: FollowedSource, size: Dp, showCount: Boolean = true) {
    Box(modifier = Modifier.size(size + 6.dp)) {
        AsyncImage(
            model = source.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).align(Alignment.Center).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
        )
        NetworkBadge(source.network, size = size * 0.32f, modifier = Modifier.align(Alignment.BottomEnd))
        if (showCount && source.newCount > 0) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.tertiary).padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(source.newCount.toString(), color = MaterialTheme.colorScheme.onTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** "Hace 2 h", "ayer"…; empty when unknown. */
internal fun whenPublished(millis: Long): String =
    if (millis <= 0L) "" else DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

/** A post's picture with what it is (reel, Short, photo) in a corner. */
@Composable
internal fun PostThumbnail(post: FavoritePost, modifier: Modifier, showNetwork: Boolean = false) {
    Box(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        AsyncImage(model = post.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        val label = when (post.kind) {
            PostKind.REEL -> L10n.str(R.string.post_kind_reel)
            PostKind.SHORT -> "Short"
            PostKind.PHOTO -> L10n.str(R.string.post_kind_photo)
            PostKind.VIDEO -> null
        }
        label?.let {
            Text(
                it,
                color = Color.White,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
        if (post.imported != null) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = L10n.str(R.string.favorites_imported),
                tint = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(5.dp).size(18.dp)
            )
        }
        if (showNetwork) NetworkBadge(post.network, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
    }
}

/** What can be done with a post: import it (or go to its recipe), open it and keep it. */
@Composable
internal fun PostActions(post: FavoritePost, onImport: () -> Unit, onOpenRecipe: (Long) -> Unit, onOpen: () -> Unit, onToggleSaved: (() -> Unit)?) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        val imported = post.imported
        if (imported != null) {
            TextButton(onClick = { onOpenRecipe(imported.recipeId) }) {
                Text(L10n.str(R.string.favorites_in_book, imported.bookName), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            FilledTonalButton(onClick = onImport) {
                if (!post.readsWithoutAssistant) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(L10n.str(R.string.favorites_import))
            }
        }
        IconButton(onClick = onOpen) { Icon(Icons.Filled.OpenInNew, contentDescription = L10n.str(R.string.favorites_open_in, networkLabel(post.network))) }
        if (onToggleSaved != null) {
            IconButton(onClick = onToggleSaved) {
                Icon(
                    if (post.isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = L10n.str(if (post.isSaved) R.string.favorites_unsave else R.string.favorites_save),
                    tint = if (post.isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** A post as a row: picture on the left, title, who and when, and its actions. */
@Composable
internal fun PostRow(
    post: FavoritePost,
    showAuthor: Boolean,
    onImport: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onOpen: () -> Unit,
    onToggleSaved: (() -> Unit)?
) {
    val vertical = post.kind == PostKind.REEL || post.kind == PostKind.SHORT
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PostThumbnail(post, Modifier.width(if (vertical) 84.dp else 132.dp).aspectRatio(if (vertical) 9f / 13f else 16f / 9f).clickable(onClick = onOpen))
        Column(modifier = Modifier.weight(1f)) {
            Text(post.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                if (showAuthor) {
                    NetworkBadge(post.network, size = 14.dp)
                    Spacer(Modifier.width(5.dp))
                }
                val meta = listOfNotNull(
                    post.author.takeIf { showAuthor },
                    whenPublished(post.publishedAt).ifEmpty { null },
                    L10n.str(R.string.favorites_new).takeIf { post.isNew }
                ).joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (post.description.isNotBlank()) {
                Text(
                    post.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            PostActions(post, onImport, onOpenRecipe, onOpen, onToggleSaved)
        }
    }
}

/** A post as a grid cell: tall picture, title and author, and its main action. */
@Composable
internal fun PostCell(post: FavoritePost, onImport: () -> Unit, onOpenRecipe: (Long) -> Unit, onOpen: () -> Unit, showNetwork: Boolean) {
    val vertical = post.kind != PostKind.VIDEO
    Column {
        PostThumbnail(post, Modifier.fillMaxWidth().aspectRatio(if (vertical) 9f / 13f else 16f / 10f).clickable(onClick = onOpen), showNetwork)
        Text(post.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        if (showNetwork) post.author?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        val imported = post.imported
        if (imported != null) {
            TextButton(onClick = { onOpenRecipe(imported.recipeId) }, modifier = Modifier.padding(top = 2.dp)) {
                Text(L10n.str(R.string.favorites_in_book, imported.bookName), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            AssistChip(
                onClick = onImport,
                label = { Text(L10n.str(R.string.favorites_import)) },
                leadingIcon = if (post.readsWithoutAssistant) null else ({ Icon(Icons.Filled.AutoAwesome, null, Modifier.size(16.dp)) })
            )
        }
    }
}

/** Asks in which book to import a post's recipe. */
@Composable
internal fun ImportBookDialog(books: List<RecipeBookSummary>, onBook: (Long) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.str(R.string.ideas_choose_book)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (books.isEmpty()) Text(L10n.str(R.string.ideas_no_books))
                books.forEach { book ->
                    Text(
                        book.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onBook(book.id) }.padding(vertical = 12.dp, horizontal = 8.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.str(R.string.cancel)) } }
    )
}

@Composable
internal fun SectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}
