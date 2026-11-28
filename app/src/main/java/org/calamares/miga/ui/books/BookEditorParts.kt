package org.calamares.miga.ui.books

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.model.BOOK_EMOJIS
import org.calamares.miga.data.model.BookColor

/**
 * The cover as the books screen will show it: photo or colour with its emoji, the name and the
 * author over a dark gradient. [onClick] (null for packs) changes the photo.
 */
@Composable
internal fun CoverPreview(
    name: String,
    author: String,
    photoUri: String?,
    coverColor: String?,
    coverEmoji: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .width(184.dp)
            .aspectRatio(0.72f)
            .shadow(10.dp, shape)
            .clip(shape)
            .background(bookPlaceholderBrush(name.ifBlank { L10n.str(R.string.new_book) }, coverColor))
            .then(if (onClick != null) Modifier.clickable(onClickLabel = L10n.str(R.string.change_cover), onClick = onClick) else Modifier)
    ) {
        if (photoUri != null) {
            AsyncImage(model = photoUri, contentDescription = L10n.str(R.string.cover), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            BookSymbol(coverEmoji, 64.dp, Modifier.align(Alignment.Center).padding(bottom = 28.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(
                name.ifBlank { L10n.str(R.string.book_name) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = if (name.isBlank()) 0.6f else 1f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (author.isNotBlank()) {
                Text(author, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = Color.White.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onClick != null) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (photoUri != null) Icons.Filled.Edit else Icons.Filled.AddAPhoto, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Names to start from when creating a book. */
internal fun bookNameSuggestions(): List<String> = L10n.str(R.string.book_name_suggestions).split('|').map { it.trim() }.filter { it.isNotEmpty() }

/**
 * Colour and emoji of a cover without a photo. "Automatic" (null) keeps the colour picked from
 * the name and the book icon.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CoverStylePicker(
    name: String,
    coverColor: String?,
    coverEmoji: String?,
    onColor: (String?) -> Unit,
    onEmoji: (String?) -> Unit
) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(L10n.str(R.string.book_cover_style), style = MaterialTheme.typography.titleSmall)
            Text(L10n.str(R.string.book_cover_color), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ColorDot(
                    color = Color(BookColor.of(null, name.ifBlank { L10n.str(R.string.new_book) }).argb),
                    selected = coverColor == null,
                    label = L10n.str(R.string.book_cover_automatic),
                    automatic = true
                ) { onColor(null) }
                BookColor.entries.forEach { color ->
                    ColorDot(Color(color.argb), selected = coverColor == color.name, label = color.label) { onColor(color.name) }
                }
            }
            Text(L10n.str(R.string.book_cover_emoji), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EmojiCell(null, selected = coverEmoji == null) { onEmoji(null) }
                BOOK_EMOJIS.forEach { emoji -> EmojiCell(emoji, selected = coverEmoji == emoji) { onEmoji(emoji) } }
            }
        }
    }
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, label: String, automatic: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label; this.selected = selected; role = Role.RadioButton },
        contentAlignment = Alignment.Center
    ) {
        when {
            selected -> Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            automatic -> Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun EmojiCell(emoji: String?, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .border(if (selected) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = emoji ?: L10n.str(R.string.book_cover_no_emoji)
                this.selected = selected
                role = Role.RadioButton
            },
        contentAlignment = Alignment.Center
    ) {
        if (emoji == null) {
            Text("—", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(emoji, fontSize = 22.sp)
        }
    }
}

/** Photos of the book's recipes, to take the cover from one of them. */
@Composable
internal fun RecipePhotoPicker(photos: List<String>, onPick: (String) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
        Text(L10n.str(R.string.book_cover_from_recipes_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(420.dp)
        ) {
            items(photos) { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).clickable { onPick(uri) }
                )
            }
        }
    }
}
