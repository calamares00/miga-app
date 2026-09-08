package com.bmo00.miga.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bmo00.miga.data.model.SyncConnection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncConnectionsScreen(viewModel: SyncConnectionsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val connections by viewModel.connections.collectAsState()
    val syncingConnectionIds by viewModel.syncingConnectionIds.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var connectionToRemove by remember { mutableStateOf<SyncConnection?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Servidor de sincronización") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Volver") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.resetTestState(); showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Añadir conexión") }
            )
        }
    ) { padding ->
        if (connections.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "Todavía no tienes ninguna conexión.\nAñade una para compartir y sincronizar " +
                        "libros con otras apps Miga a través de tu propio servidor.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), modifier = Modifier.padding(padding)) {
                items(connections, key = { it.id }) { connection ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(connection.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${connection.serverUrl} · ${connection.namespaceId}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = connection.lastSyncError?.let { "Error: $it" }
                                    ?: if (connection.lastSyncedAt == null) "Todavía no sincronizada" else "Sincronizada",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (connection.lastSyncError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (connection.id in syncingConnectionIds) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 8.dp))
                        } else {
                            IconButton(onClick = { viewModel.syncNow(context, connection.id) }) {
                                Icon(Icons.Filled.Sync, contentDescription = "Sincronizar ahora")
                            }
                        }
                        IconButton(onClick = { connectionToRemove = connection }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Quitar conexión")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAddDialog) {
        AddConnectionDialog(
            viewModel = viewModel,
            onDismiss = { showAddDialog = false },
            onSave = { label, serverUrl, namespaceId, accessToken ->
                viewModel.addConnection(label, serverUrl, namespaceId, accessToken)
                showAddDialog = false
            }
        )
    }

    connectionToRemove?.let { connection ->
        AlertDialog(
            onDismissRequest = { connectionToRemove = null },
            title = { Text("Quitar \"${connection.label}\"") },
            text = {
                Text(
                    "Los libros que sincronizaba pasarán a ser locales; no se borra nada de su " +
                        "contenido, solo dejan de sincronizarse."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.removeConnection(connection.id); connectionToRemove = null }) {
                    Text("Quitar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { connectionToRemove = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun AddConnectionDialog(
    viewModel: SyncConnectionsViewModel,
    onDismiss: () -> Unit,
    onSave: (label: String, serverUrl: String, namespaceId: String, accessToken: String) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("") }
    var namespaceId by remember { mutableStateOf("") }
    var accessToken by remember { mutableStateOf("") }
    val testState by viewModel.testState.collectAsState()
    val canSave = label.isNotBlank() && serverUrl.isNotBlank() && namespaceId.isNotBlank() && accessToken.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir conexión") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Nombre") },
                    placeholder = { Text("p. ej. Casa") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("URL del servidor") },
                    placeholder = { Text("http://192.168.1.10:8080") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = namespaceId,
                    onValueChange = { namespaceId = it },
                    label = { Text("Namespace") },
                    placeholder = { Text("casa") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = accessToken,
                    onValueChange = { accessToken = it },
                    label = { Text("Token de acceso") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { viewModel.testConnection(serverUrl, namespaceId, accessToken) },
                    enabled = serverUrl.isNotBlank() && namespaceId.isNotBlank() && accessToken.isNotBlank() && testState != TestConnectionState.Testing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Probar conexión")
                }
                when (val state = testState) {
                    TestConnectionState.Testing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Text(" Comprobando...", modifier = Modifier.padding(start = 8.dp))
                    }
                    TestConnectionState.Success -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(" Conexión correcta", modifier = Modifier.padding(start = 8.dp))
                    }
                    is TestConnectionState.Error -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(state.reason, modifier = Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.error)
                    }
                    TestConnectionState.Idle -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(label, serverUrl, namespaceId, accessToken) }, enabled = canSave) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
