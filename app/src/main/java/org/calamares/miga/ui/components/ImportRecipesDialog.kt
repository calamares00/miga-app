package org.calamares.miga.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.export.RecipeImportResult

/**
 * Asked when a Miga file with several recipes (an exported book or selection) is imported: as a
 * new book, named after the one they came from, or into one of [books] (id and name).
 * [onConfirm] gets the chosen book id, or null for a new book.
 */
@Composable
fun ImportRecipesDialog(
    collection: RecipeImportResult.Collection,
    books: List<Pair<Long, String>>,
    initialTarget: Long?,
    onConfirm: (targetBookId: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val count = collection.parsed.dto.recipes.size
    val sourceBook = collection.bookName
    var target by remember { mutableStateOf(initialTarget) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.str(R.string.import_n_recipes_title, count)) },
        text = {
            Column {
                Text(
                    if (sourceBook != null) L10n.str(R.string.import_recipes_from_book_x, count, sourceBook)
                    else L10n.str(R.string.import_recipes_n, count),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    TargetRow(
                        label = if (sourceBook != null) L10n.str(R.string.import_as_new_book_x, sourceBook) else L10n.str(R.string.import_as_new_book),
                        selected = target == null,
                        emphasized = true
                    ) { target = null }
                    if (books.isNotEmpty()) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    books.forEach { (id, name) ->
                        TargetRow(label = name, selected = target == id) { target = id }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(target) }) { Text(L10n.str(R.string.import_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.str(R.string.cancel)) } }
    )
}

@Composable
private fun TargetRow(label: String, selected: Boolean, emphasized: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (emphasized) FontWeight.SemiBold else null,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
