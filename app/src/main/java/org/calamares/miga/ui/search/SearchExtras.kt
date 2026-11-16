package org.calamares.miga.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.model.PantryMatch
import org.calamares.miga.data.model.SavedFilter
import org.calamares.miga.ui.components.EmptyState

/**
 * What the search tab shows over the recipes before anything is typed: "What can I cook?", the
 * saved searches and the latest ones, so a search is one tap away.
 */
internal fun LazyListScope.searchStartItems(
    recent: List<String>,
    saved: List<SavedFilter>,
    onPantry: () -> Unit,
    onRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    onSaved: (SavedFilter) -> Unit,
    onDeleteSaved: (SavedFilter) -> Unit
) {
    item(key = "pantry") {
        Card(
            onClick = onPantry,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Kitchen, contentDescription = null, modifier = Modifier.size(32.dp))
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(L10n.str(R.string.pantry_title), style = MaterialTheme.typography.titleMedium)
                    Text(L10n.str(R.string.pantry_card_body), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    if (saved.isNotEmpty()) {
        item(key = "saved") {
            SectionLabel(L10n.str(R.string.saved_searches))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                saved.forEach { filter ->
                    InputChip(
                        selected = false,
                        onClick = { onSaved(filter) },
                        label = { Text(filter.name) },
                        leadingIcon = { Icon(Icons.Filled.Bookmark, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            IconButton(onClick = { onDeleteSaved(filter) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.delete_x, filter.name), modifier = Modifier.size(18.dp))
                            }
                        }
                    )
                }
            }
        }
    }
    if (recent.isNotEmpty()) {
        item(key = "recent") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(L10n.str(R.string.recent_searches), Modifier.weight(1f))
                TextButton(onClick = onClearRecent) { Text(L10n.str(R.string.clear_2)) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                recent.forEach { query ->
                    AssistChip(
                        onClick = { onRecent(query) },
                        label = { Text(query, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
        }
    }
}

/** Asks before deleting a saved search. */
@Composable
internal fun DeleteSavedSearchDialog(filter: SavedFilter, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.str(R.string.delete_saved_search)) },
        text = { Text(filter.name) },
        confirmButton = {
            TextButton(onClick = onDelete) { Text(L10n.str(R.string.delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.str(R.string.cancel)) } }
    )
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(bottom = 4.dp)
    )
}

/**
 * "What can I cook?": the ingredients the user has (remembered for next time) and the recipes
 * that use them, those missing fewest things first.
 */
@Composable
internal fun PantryContent(
    items: List<String>,
    matches: List<PantryMatch>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onRecipeClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    fun add() {
        onAdd(text)
        text = ""
    }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(L10n.str(R.string.pantry_what_you_have)) },
                placeholder = { Text(L10n.str(R.string.pantry_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                trailingIcon = {
                    if (text.isNotBlank()) IconButton(onClick = { add() }) {
                        Icon(Icons.Filled.AddCircle, contentDescription = L10n.str(R.string.add), tint = MaterialTheme.colorScheme.primary)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (items.isNotEmpty()) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEach { item ->
                        InputChip(
                            selected = true,
                            onClick = { onRemove(item) },
                            label = { Text(item) },
                            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.remove_x, item), modifier = Modifier.size(18.dp)) }
                        )
                    }
                    TextButton(onClick = onClear) { Text(L10n.str(R.string.clear_2)) }
                }
            }
        }
        when {
            items.isEmpty() -> item {
                EmptyState(
                    icon = Icons.Filled.Kitchen,
                    title = L10n.str(R.string.pantry_title),
                    body = L10n.str(R.string.pantry_empty_body),
                    scrollable = false
                )
            }
            matches.isEmpty() -> item {
                EmptyState(
                    icon = Icons.Filled.Kitchen,
                    title = L10n.str(R.string.no_results),
                    body = L10n.str(R.string.pantry_no_matches),
                    scrollable = false
                )
            }
            else -> items(matches, key = { it.recipe.id }) { match -> PantryMatchCard(match) { onRecipeClick(match.recipe.id) } }
        }
    }
}

@Composable
private fun PantryMatchCard(match: PantryMatch, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(match.recipe.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(match.recipe.recipeBookName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (match.missing.isEmpty()) L10n.str(R.string.pantry_have_everything)
                else L10n.str(R.string.pantry_missing_x, match.missing.joinToString(", ")),
                style = MaterialTheme.typography.bodyMedium,
                color = if (match.missing.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Name for the current search, to find it again among the saved ones. */
@Composable
internal fun SaveSearchDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.str(R.string.save_search)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(L10n.str(R.string.cook_timer_name)) },
                placeholder = { Text(L10n.str(R.string.save_search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name) }) { Text(L10n.str(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.str(R.string.cancel)) } }
    )
}
