package com.bmo00.miga.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageCatalogScreen(
    title: String,
    items: List<CatalogItem>,
    onBack: () -> Unit,
    onAdd: (String) -> Unit,
    onRename: (CatalogItem, String) -> Unit,
    onDelete: (CatalogItem) -> Unit,
    usageLabel: (Int) -> String = { count -> if (count == 1) "1 receta" else "$count recetas" }
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var itemToRename by remember { mutableStateOf<CatalogItem?>(null) }
    var itemToDelete by remember { mutableStateOf<CatalogItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Volver") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showAddDialog = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Añadir") })
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Todavía no hay elementos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), modifier = Modifier.padding(padding)) {
                items(items, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.bodyLarge)
                            if (item.usageCount != null) {
                                Text(
                                    text = usageLabel(item.usageCount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { itemToRename = item }) { Icon(Icons.Filled.Edit, contentDescription = "Renombrar") }
                        IconButton(onClick = { itemToDelete = item }) { Icon(Icons.Filled.Delete, contentDescription = "Borrar") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAddDialog) {
        NameDialog(
            title = "Añadir",
            initialValue = "",
            onConfirm = { onAdd(it); showAddDialog = false },
            onDismiss = { showAddDialog = false }
        )
    }

    itemToRename?.let { item ->
        NameDialog(
            title = "Renombrar",
            initialValue = item.name,
            onConfirm = { onRename(item, it); itemToRename = null },
            onDismiss = { itemToRename = null }
        )
    }

    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Borrar \"${item.name}\"") },
            text = { Text("¿Seguro que quieres borrarlo? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { onDelete(item); itemToDelete = null }) {
                    Text("Borrar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { itemToDelete = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun NameDialog(title: String, initialValue: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(onClick = { if (value.isNotBlank()) onConfirm(value) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
