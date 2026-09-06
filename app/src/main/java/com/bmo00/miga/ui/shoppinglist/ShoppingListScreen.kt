package com.bmo00.miga.ui.shoppinglist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.bmo00.miga.data.model.ShoppingListItem
import com.bmo00.miga.data.model.formatIngredientText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(viewModel: ShoppingListViewModel, onBack: () -> Unit) {
    val groups by viewModel.groups.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Lista de la compra") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Volver") }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Compartir lista") },
                            leadingIcon = { Icon(Icons.Filled.Share, null) },
                            onClick = { showMenu = false; viewModel.share(context) }
                        )
                        DropdownMenuItem(
                            text = { Text("Borrar marcados") },
                            leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            onClick = { showMenu = false; viewModel.clearChecked() }
                        )
                        DropdownMenuItem(
                            text = { Text("Vaciar lista", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; showClearAllConfirm = true }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddItemDialog = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Añadir item") }
            )
        }
    ) { padding ->
        if (groups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "Tu lista de la compra está vacía.\nAñade recetas desde el buscador o un artículo suelto con \"Añadir item\".",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                groups.forEach { group ->
                    item(key = "header_${group.categoryName}") {
                        Text(
                            text = group.categoryName,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(group.items, key = { it.id }) { shoppingItem ->
                        ShoppingListRow(
                            item = shoppingItem,
                            onCheckedChange = { viewModel.setChecked(shoppingItem.id, it) },
                            onDelete = { viewModel.deleteItem(shoppingItem.id) }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.padding(40.dp)) }
            }
        }
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("Vaciar lista") },
            text = { Text("¿Seguro que quieres borrar todos los artículos de la lista de la compra?") },
            confirmButton = {
                TextButton(onClick = {
                    showClearAllConfirm = false
                    viewModel.clearAll()
                }) { Text("Vaciar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    if (showAddItemDialog) {
        var name by remember { mutableStateOf("") }
        var quantity by remember { mutableStateOf("") }
        var unit by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddItemDialog = false },
            title = { Text("Añadir item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = quantity,
                            onValueChange = { quantity = it },
                            label = { Text("Cant.") },
                            modifier = Modifier.weight(0.7f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = unit,
                            onValueChange = { unit = it },
                            label = { Text("Ud.") },
                            modifier = Modifier.weight(0.8f),
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Artículo") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addManualItem(
                            name,
                            quantity.trim().replace(',', '.').toDoubleOrNull(),
                            unit.trim().ifBlank { null }
                        )
                        showAddItemDialog = false
                    },
                    enabled = name.isNotBlank()
                ) { Text("Añadir") }
            },
            dismissButton = {
                TextButton(onClick = { showAddItemDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun ShoppingListRow(item: ShoppingListItem, onCheckedChange: (Boolean) -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = item.checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (item.checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = if (item.checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = formatIngredientText(item.name, item.quantity, item.unit),
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
            color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Quitar artículo", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
