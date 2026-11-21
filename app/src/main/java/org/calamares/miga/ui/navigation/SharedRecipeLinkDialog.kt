package org.calamares.miga.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.model.RecipeBookSummary
import org.calamares.miga.data.search.SocialPostRecipe
import org.calamares.miga.data.share.SharedRecipeLink

/**
 * A link shared to Miga (see RecipeLinkIntents): asks in which book to import the recipe, or adds
 * the text to the shopping list as before.
 */
@Composable
internal fun SharedRecipeLinkDialog(
    link: SharedRecipeLink,
    books: List<RecipeBookSummary>,
    onImport: (bookId: Long) -> Unit,
    onShoppingList: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(L10n.str(if (SocialPostRecipe.isInstagram(link.url)) R.string.shared_link_title_instagram else R.string.shared_link_title))
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(link.url, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    L10n.str(if (books.isEmpty()) R.string.ideas_no_books else R.string.ideas_choose_book),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
                books.forEach { book ->
                    Text(
                        book.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onImport(book.id) }
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onShoppingList) { Text(L10n.str(R.string.shared_link_to_shopping)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.str(R.string.cancel)) } }
    )
}
