package org.calamares.miga.ui.shoppinglist

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import org.calamares.miga.ui.components.rememberSettingText
import org.calamares.miga.ui.components.ButtonContent
import org.calamares.miga.L10n
import org.calamares.miga.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.calamares.miga.data.model.DEFAULT_SHOPPING_LIST_UID
import org.calamares.miga.data.model.ParsedShoppingEntry
import org.calamares.miga.data.model.ShoppingAisleOrder
import org.calamares.miga.data.model.ShoppingListInfo
import org.calamares.miga.data.model.ShoppingStore
import org.calamares.miga.data.model.formatIngredientText
import org.calamares.miga.data.share.ShoppingListShareCodec

@Composable
internal fun EmptyShoppingList(
    modifier: Modifier,
    onCatalog: () -> Unit,
    onTemplates: () -> Unit,
    onSearch: () -> Unit,
    onScan: () -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) { Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🛒", fontSize = 56.sp)
        Text(
            L10n.str(R.string.list_empty),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            L10n.str(R.string.type_dictate_what_need_above),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.widthIn(max = 320.dp)) {
            FilledTonalButton(onClick = onTemplates, modifier = Modifier.fillMaxWidth()) {
                ButtonContent(Icons.Filled.Bookmarks, L10n.str(R.string.use_template))
            }
            OutlinedButton(onClick = onCatalog, modifier = Modifier.fillMaxWidth()) {
                ButtonContent(Icons.Filled.GridView, L10n.str(R.string.catalogue_2))
            }
            OutlinedButton(onClick = onSearch, modifier = Modifier.fillMaxWidth()) {
                ButtonContent(Icons.Filled.Search, L10n.str(R.string.search_open_food_facts))
            }
            OutlinedButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                ButtonContent(Icons.Filled.QrCodeScanner, L10n.str(R.string.scan_product))
            }
        }
    } }
}

@Composable
internal fun ShareOption(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The lists in a bottom sheet, on screens too narrow to show them beside the list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShoppingListsSheet(
    lists: List<ShoppingListInfo>,
    listCounts: Map<String, Int>,
    selectedListUid: String,
    author: String,
    onSelect: (String) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ShoppingListsPanel(
            lists = lists,
            listCounts = listCounts,
            selectedListUid = selectedListUid,
            author = author,
            onSelect = onSelect,
            onCreate = onCreate,
            onRename = onRename,
            onDelete = onDelete,
            onAuthorChange = onAuthorChange,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)
        )
    }
}

/**
 * Switch, create, rename or delete lists, and set the name used to sign changes. In a sheet on
 * phones and always visible beside the list on large screens.
 */
@Composable
internal fun ShoppingListsPanel(
    lists: List<ShoppingListInfo>,
    listCounts: Map<String, Int>,
    selectedListUid: String,
    author: String,
    onSelect: (String) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    val (authorName, onAuthorNameChange) = rememberSettingText(saved = author, save = onAuthorChange)
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ShoppingListInfo?>(null) }
    var deleting by remember { mutableStateOf<ShoppingListInfo?>(null) }

    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        item(key = "title") { Text(L10n.str(R.string.my_lists), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp)) }
        items(lists, key = { "l${it.uid}" }) { list ->
            val selected = list.uid == selectedListUid
            val pending = listCounts[list.uid] ?: 0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .selectable(selected = selected, role = Role.RadioButton) { onSelect(list.uid) }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (selected) Icons.Filled.CheckCircle else Icons.Filled.ShoppingBag,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(list.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (pending == 0) L10n.str(R.string.nothing_left) else if (pending == 1) L10n.str(R.string.n_1_buy) else L10n.str(R.string.x_buy, pending),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (list.uid != DEFAULT_SHOPPING_LIST_UID) {
                    IconButton(onClick = { renaming = list }) { Icon(Icons.Filled.Edit, contentDescription = L10n.str(R.string.rename_x, list.name)) }
                    IconButton(onClick = { deleting = list }) { Icon(Icons.Filled.Delete, contentDescription = L10n.str(R.string.delete_x, list.name)) }
                }
            }
        }
        item(key = "new") {
            TextButton(onClick = { creating = true }, modifier = Modifier.padding(top = 4.dp)) {
                ButtonContent(Icons.Filled.Add, L10n.str(R.string.new_list_2))
            }
        }
        item(key = "author") {
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            OutlinedTextField(
                value = authorName,
                onValueChange = onAuthorNameChange,
                label = { Text(L10n.str(R.string.my_name_shared_lists)) },
                placeholder = { Text(L10n.str(R.string.e_g_ana_optional)) },
                supportingText = { Text(L10n.str(R.string.everyone_knows_who_added_ticked)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (creating) {
        NameDialog(
            title = L10n.str(R.string.new_list),
            placeholder = L10n.str(R.string.e_g_party_trip_saturday),
            confirmLabel = L10n.str(R.string.create),
            onConfirm = { creating = false; onCreate(it) },
            onDismiss = { creating = false }
        )
    }
    renaming?.let { list ->
        NameDialog(
            title = L10n.str(R.string.rename_list),
            initial = list.name,
            placeholder = list.name,
            confirmLabel = L10n.str(R.string.save),
            onConfirm = { onRename(list.uid, it); renaming = null },
            onDismiss = { renaming = null }
        )
    }
    deleting?.let { list ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(L10n.str(R.string.delete_list)) },
            text = { Text(L10n.str(R.string.delete_x_items, list.name)) },
            confirmButton = {
                TextButton(onClick = { onDelete(list.uid); deleting = null }) { Text(L10n.str(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(L10n.str(R.string.cancel)) } }
        )
    }
}

private const val QR_SIZE_PX = 720

/** Ways to share the list: as text, as a QR code for another phone, or receiving one. */
@Composable
internal fun ShareListDialog(onSendText: () -> Unit, onShowQr: () -> Unit, onScanQr: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Share, contentDescription = null) },
        title = { Text(L10n.str(R.string.share)) },
        text = {
            Column {
                ShareOption(Icons.Filled.Share, L10n.str(R.string.send_text), L10n.str(R.string.whatsapp_email_notes)) {
                    onDismiss()
                    onSendText()
                }
                ShareOption(Icons.Filled.QrCode2, L10n.str(R.string.show_qr), L10n.str(R.string.send_another_phone_miga)) {
                    onDismiss()
                    onShowQr()
                }
                ShareOption(Icons.Filled.QrCodeScanner, L10n.str(R.string.receive_via_qr), L10n.str(R.string.scan_qr_code_another_miga)) {
                    onDismiss()
                    onScanQr()
                }
                Text(
                    L10n.str(R.string.edit_same_list_other_people),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(L10n.str(R.string.close)) } }
    )
}

/** The user's supermarkets, each with its own aisle order: tap one to edit it. */
@Composable
internal fun StoresDialog(
    stores: List<ShoppingStore>,
    onEdit: (ShoppingStore) -> Unit,
    onDelete: (ShoppingStore) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.str(R.string.supermarkets)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    L10n.str(R.string.each_supermarket_keeps_own_aisle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                stores.forEach { store ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDismiss(); onEdit(store) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(Color(store.argb)))
                        Text(store.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        IconButton(onClick = { onDelete(store) }) {
                            Icon(Icons.Filled.Delete, contentDescription = L10n.str(R.string.delete_x, store.name))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onEdit(ShoppingStore(0L, "", ShoppingAisleOrder.PALETTE[0], emptyList()))
            }) { Text(L10n.str(R.string.add_supermarket)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.str(R.string.close)) } }
    )
}

/** The pending items as a QR code another phone with Miga can scan. */
@Composable
internal fun ListQrDialog(entries: List<ParsedShoppingEntry>, onDismiss: () -> Unit) {
    val payload = remember(entries) { ShoppingListShareCodec.encode(entries) }
    val qrBitmap = remember(payload) { renderQrBitmap(payload, QR_SIZE_PX) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.str(R.string.list_qr_code)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = L10n.str(R.string.qr_code_x_items, entries.size),
                        modifier = Modifier.size(260.dp)
                    )
                    Text(
                        L10n.str(R.string.x_items_buy_other_phone, entries.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(L10n.str(R.string.list_too_big_qr_code))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(L10n.str(R.string.close)) } }
    )
}

/** Items read from another phone's QR code, added when confirmed. */
@Composable
internal fun ReceivedListDialog(entries: List<ParsedShoppingEntry>, onAdd: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.str(R.string.list_received)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(L10n.str(R.string.x_items_added_list_quantities, entries.size))
                entries.take(8).forEach { entry ->
                    Text("• ${formatIngredientText(entry.name, entry.quantity, entry.unit)}", style = MaterialTheme.typography.bodyMedium)
                }
                if (entries.size > 8) Text(L10n.str(R.string.x_more, entries.size - 8), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = onAdd) { Text(L10n.str(R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.str(R.string.cancel)) } }
    )
}
