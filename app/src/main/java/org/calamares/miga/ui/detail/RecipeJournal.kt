package org.calamares.miga.ui.detail

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.model.RecipeNote

/** What the journal section can do; see [RecipeJournalSection]. */
class JournalActions(
    val onAdd: (String) -> Unit,
    val onEdit: (id: Long, text: String) -> Unit,
    val onDelete: (id: Long) -> Unit
)

/** How many notes show before "See all": a recipe cooked for years can collect many. */
private const val COLLAPSED_NOTES = 3

/**
 * "My notes": dated personal notes on the recipe ("next time, less salt"), newest first, as a
 * timeline. Unlike the recipe's notes they are not part of the recipe itself, so adding one does
 * not open the editor.
 */
@Composable
internal fun RecipeJournalSection(notes: List<RecipeNote>, actions: JournalActions) {
    /** The note being written: null closed, id 0 for a new one. */
    var editing by remember { mutableStateOf<RecipeNote?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shown = if (expanded) notes else notes.take(COLLAPSED_NOTES)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(L10n.str(R.string.journal_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { editing = RecipeNote(0, "", 0) }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(L10n.str(R.string.journal_add))
            }
        }
        if (notes.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .clickable { editing = RecipeNote(0, "", 0) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    L10n.str(R.string.journal_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column {
                shown.forEachIndexed { index, note ->
                    JournalEntry(
                        note = note,
                        isLast = index == shown.lastIndex,
                        onEdit = { editing = note },
                        onDelete = { actions.onDelete(note.id) }
                    )
                }
            }
            if (notes.size > COLLAPSED_NOTES) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) L10n.str(R.string.see_less) else L10n.str(R.string.see_all_x, notes.size))
                }
            }
        }
    }

    editing?.let { note ->
        JournalNoteDialog(
            initial = note.text,
            isNew = note.id == 0L,
            onSave = { text ->
                if (note.id == 0L) actions.onAdd(text) else actions.onEdit(note.id, text)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

/** One note of the timeline: a dot and a line on the left, the date and the text on the right. */
@Composable
private fun JournalEntry(note: RecipeNote, isLast: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onEdit)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 6.dp, end = 12.dp)) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .width(2.dp)
                        .heightIn(min = 40.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
        Column(modifier = Modifier.weight(1f).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                DateUtils.getRelativeTimeSpanString(note.createdAt, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString() +
                    " · " + DateUtils.formatDateTime(context, note.createdAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(note.text, style = MaterialTheme.typography.bodyMedium)
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = L10n.str(R.string.more_options), modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(L10n.str(R.string.edit)) },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = { menu = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text(L10n.str(R.string.delete_2), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun JournalNoteDialog(initial: String, isNew: Boolean, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.str(if (isNew) R.string.journal_new_title else R.string.journal_edit_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(L10n.str(R.string.journal_placeholder)) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank() || !isNew) { Text(L10n.str(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.str(R.string.cancel)) } }
    )
}
