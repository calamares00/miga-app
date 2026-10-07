package org.calamares.miga.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.model.KitchenEquipment
import org.calamares.miga.data.model.RecipeOrigin

/** Searchable list of countries with their flags; [onPick] gets the code, or null for none. */
@Composable
fun CountryPickerDialog(selected: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val countries = remember { RecipeOrigin.countries() }
    var query by remember { mutableStateOf("") }
    val key = KitchenEquipment.key(query)
    val shown = if (key.isEmpty()) countries else countries.filter { KitchenEquipment.key(it.second).contains(key) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.str(R.string.origin_country)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(L10n.str(R.string.search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                    if (key.isEmpty()) {
                        item {
                            Text(
                                L10n.str(R.string.origin_no_country),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().clickable { onPick(null) }.padding(vertical = 12.dp, horizontal = 4.dp)
                            )
                            HorizontalDivider()
                        }
                    }
                    items(shown, key = { it.first }) { (code, name) ->
                        Text(
                            "${RecipeOrigin.flag(code).orEmpty()}  $name",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (code == selected) FontWeight.Bold else null,
                            color = if (code == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(code) }.padding(vertical = 12.dp, horizontal = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.str(R.string.cancel)) } }
    )
}
