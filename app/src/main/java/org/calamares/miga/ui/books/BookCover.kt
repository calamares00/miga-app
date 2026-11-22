package org.calamares.miga.ui.books

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * What a cover without a photo shows over its colour: the chosen emoji or, when there is none,
 * the book icon. [size] is the height of either.
 */
@Composable
internal fun BookSymbol(emoji: String?, size: Dp, modifier: Modifier = Modifier) {
    if (emoji.isNullOrBlank()) {
        Icon(Icons.Outlined.MenuBook, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = modifier.size(size))
    } else {
        // Font size in sp from the wanted height, so emojis do not grow with the system font scale.
        val fontSize = with(LocalDensity.current) { (size * 0.82f).toSp() / fontScale }
        Text(emoji, fontSize = fontSize, modifier = modifier)
    }
}

/**
 * The few words about the book ("Our everyday family recipes") and its author above its recipes, in the
 * style of the first page of a printed recipe book.
 */
@Composable
internal fun BookAbout(description: String, author: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (description.isNotBlank()) {
            Text(
                "“${description.trim()}”",
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (author.isNotBlank()) {
            Text(
                "— ${author.trim()}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
