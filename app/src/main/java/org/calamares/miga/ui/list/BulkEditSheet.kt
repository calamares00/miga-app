package org.calamares.miga.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.model.Difficulty
import org.calamares.miga.data.model.RecipeBookSummary

private enum class BulkDialog { CATEGORY, DIFFICULTY, SERVINGS, SOURCE, MOVE, COPY }

/**
 * Acciones en bloque sobre las recetas seleccionadas de un libro. Las que necesitan un valor
 * (categoría, dificultad, raciones, fuente, libro destino) lo piden en un diálogo; al aplicar se
 * cierra todo y la pantalla muestra el resultado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkEditSheet(
    count: Int,
    categories: List<String>,
    books: List<RecipeBookSummary>,
    aiAvailable: Boolean,
    onDismiss: () -> Unit,
    onCategory: (String?) -> Unit,
    onDifficulty: (Difficulty) -> Unit,
    onServings: (servings: Int, scaleIngredients: Boolean) -> Unit,
    onSource: (String) -> Unit,
    onFavorite: (Boolean) -> Unit,
    onMove: (Long) -> Unit,
    onCopy: (Long) -> Unit,
    onShopping: () -> Unit,
    onRecalculateAi: () -> Unit
) {
    var dialog by remember { mutableStateOf<BulkDialog?>(null) }

    fun done(action: () -> Unit) {
        action()
        dialog = null
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 24.dp)) {
            Text(
                L10n.str(R.string.bulk_edit_title, count),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            BulkRow(Icons.Filled.Category, L10n.str(R.string.bulk_change_category)) { dialog = BulkDialog.CATEGORY }
            BulkRow(Icons.Filled.SignalCellularAlt, L10n.str(R.string.bulk_change_difficulty)) { dialog = BulkDialog.DIFFICULTY }
            BulkRow(Icons.Filled.People, L10n.str(R.string.bulk_change_servings)) { dialog = BulkDialog.SERVINGS }
            BulkRow(Icons.Filled.Link, L10n.str(R.string.bulk_change_source)) { dialog = BulkDialog.SOURCE }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            BulkRow(Icons.Filled.Favorite, L10n.str(R.string.bulk_favorite_on)) { done { onFavorite(true) } }
            BulkRow(Icons.Filled.FavoriteBorder, L10n.str(R.string.bulk_favorite_off)) { done { onFavorite(false) } }
            BulkRow(Icons.Filled.Folder, L10n.str(R.string.move_another_book)) { dialog = BulkDialog.MOVE }
            BulkRow(Icons.Filled.ContentCopy, L10n.str(R.string.bulk_copy)) { dialog = BulkDialog.COPY }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            BulkRow(Icons.Filled.ShoppingCart, L10n.str(R.string.bulk_add_to_shopping)) { done(onShopping) }
            if (aiAvailable) {
                BulkRow(Icons.Filled.AutoAwesome, L10n.str(R.string.bulk_recalculate_ai)) { done(onRecalculateAi) }
            }
        }
    }

    when (dialog) {
        BulkDialog.CATEGORY -> CategoryDialog(
            categories = categories,
            onPick = { name -> done { onCategory(name) } },
            onDismiss = { dialog = null }
        )
        BulkDialog.DIFFICULTY -> ChoiceDialog(
            title = L10n.str(R.string.bulk_change_difficulty),
            options = Difficulty.entries.map { it.label },
            onPick = { index -> done { onDifficulty(Difficulty.entries[index]) } },
            onDismiss = { dialog = null }
        )
        BulkDialog.SERVINGS -> ServingsDialog(
            onApply = { servings, scale -> done { onServings(servings, scale) } },
            onDismiss = { dialog = null }
        )
        BulkDialog.SOURCE -> SourceDialog(
            onApply = { source -> done { onSource(source) } },
            onDismiss = { dialog = null }
        )
        BulkDialog.MOVE, BulkDialog.COPY -> {
            val moving = dialog == BulkDialog.MOVE
            if (books.isEmpty()) {
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text(L10n.str(if (moving) R.string.move_another_book else R.string.bulk_copy)) },
                    text = { Text(L10n.str(R.string.bulk_no_other_books)) },
                    confirmButton = { TextButton(onClick = { dialog = null }) { Text(L10n.str(R.string.close)) } }
                )
            } else {
                ChoiceDialog(
                    title = L10n.str(if (moving) R.string.move_another_book else R.string.bulk_copy),
                    options = books.map { it.name },
                    onPick = { index -> done { if (moving) onMove(books[index].id) else onCopy(books[index].id) } },
                    onDismiss = { dialog = null }
                )
            }
        }
        null -> Unit
    }
}

@Composable
private fun BulkRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
    }
}

/** Lista de opciones; tocar una la aplica. */
@Composable
private fun ChoiceDialog(title: String, options: List<String>, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                options.forEachIndexed { index, option ->
                    Text(
                        option,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(index) }.padding(vertical = 12.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.str(R.string.cancel)) } }
    )
}

@Composable
private fun CategoryDialog(categories: List<String>, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.str(R.string.bulk_change_category)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text(
                    L10n.str(R.string.uncategorized),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().clickable { onPick(null) }.padding(vertical = 12.dp)
                )
                categories.forEach { category ->
                    Text(
                        category,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(category) }.padding(vertical = 12.dp)
                    )
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(L10n.str(R.string.bulk_new_category)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(newName.trim()) }, enabled = newName.isNotBlank()) { Text(L10n.str(R.string.bulk_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.str(R.string.cancel)) } }
    )
}

@Composable
private fun ServingsDialog(onApply: (Int, Boolean) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var scale by remember { mutableStateOf(true) }
    val servings = text.toIntOrNull()?.takeIf { it in 1..99 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.str(R.string.bulk_change_servings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { value -> text = value.filter { it.isDigit() }.take(2) },
                    label = { Text(L10n.str(R.string.servings_2)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { scale = !scale },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = scale, onCheckedChange = { scale = it })
                    Text(L10n.str(R.string.bulk_scale_ingredients), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { servings?.let { onApply(it, scale) } }, enabled = servings != null) { Text(L10n.str(R.string.bulk_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.str(R.string.cancel)) } }
    )
}

@Composable
private fun SourceDialog(onApply: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.str(R.string.bulk_change_source)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(L10n.str(R.string.bulk_source_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    L10n.str(R.string.bulk_source_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = { onApply(text) }) { Text(L10n.str(R.string.bulk_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.str(R.string.cancel)) } }
    )
}
