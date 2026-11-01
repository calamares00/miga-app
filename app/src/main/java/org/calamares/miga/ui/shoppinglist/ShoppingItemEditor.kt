package org.calamares.miga.ui.shoppinglist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.model.ShoppingListItem
import org.calamares.miga.data.model.ShoppingVisuals
import org.calamares.miga.data.model.UNCATEGORIZED_INGREDIENT_LABEL
import org.calamares.miga.data.model.displayCategoryName
import org.calamares.miga.data.model.formatQuantity

/**
 * Edits an item of the list: name, quantity, unit and aisle. The aisle is remembered for that name
 * (see RecipeRepository.updateShoppingListItem). Also the place to delete it outside shop mode.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ShoppingItemEditorSheet(
    item: ShoppingListItem,
    aisles: List<String>,
    onSave: (name: String, quantity: Double?, unit: String?, aisle: String?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var quantity by remember { mutableStateOf(item.quantity?.let { formatQuantity(it) }.orEmpty()) }
    var unit by remember { mutableStateOf(item.unit.orEmpty()) }
    var aisle by remember { mutableStateOf(item.categoryName.takeIf { it != UNCATEGORIZED_INGREDIENT_LABEL }) }
    val parsedQuantity = quantity.trim().replace(',', '.').toDoubleOrNull()
    val quantityError = quantity.isNotBlank() && (parsedQuantity == null || parsedQuantity <= 0)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(L10n.str(R.string.edit_item), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(L10n.str(R.string.name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text(L10n.str(R.string.quantity)) },
                    singleLine = true,
                    isError = quantityError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text(L10n.str(R.string.unit_of_measure)) },
                    placeholder = { Text(L10n.str(R.string.e_g_kg_packs)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                L10n.str(R.string.aisle),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                L10n.str(R.string.aisle_remembered_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                aisles.forEach { category ->
                    FilterChip(
                        selected = aisle == category,
                        onClick = { aisle = if (aisle == category) null else category },
                        label = { Text("${ShoppingVisuals.categoryStyle(category).emoji} ${displayCategoryName(category)}") }
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)) {
                TextButton(onClick = onDelete) { Text(L10n.str(R.string.remove), color = MaterialTheme.colorScheme.error) }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(L10n.str(R.string.cancel)) }
                TextButton(
                    onClick = { onSave(name.trim(), parsedQuantity, unit.trim().ifEmpty { null }, aisle) },
                    enabled = name.isNotBlank() && !quantityError
                ) { Text(L10n.str(R.string.save)) }
            }
        }
    }
}
