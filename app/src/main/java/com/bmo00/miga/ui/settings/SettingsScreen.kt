package com.bmo00.miga.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.bmo00.miga.BuildConfig
import com.bmo00.miga.data.export.RecipeExportDto
import com.bmo00.miga.data.export.RecipeImportResult
import com.bmo00.miga.data.model.RecipePhoto
import com.bmo00.miga.data.model.ThemeMode
import com.bmo00.miga.data.model.UpdateChannel
import com.bmo00.miga.ui.common.BACKUP_MIME_TYPES
import com.bmo00.miga.ui.security.BiometricAuthenticator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onManageCategories: () -> Unit,
    onManageUtensils: () -> Unit,
    onManageIngredients: () -> Unit,
    onManageIngredientCategories: () -> Unit,
    onHelp: () -> Unit
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val biometricLockEnabled by viewModel.biometricLockEnabled.collectAsState()
    val autoCheckUpdatesEnabled by viewModel.autoCheckUpdatesEnabled.collectAsState()
    val updateChannel by viewModel.updateChannel.collectAsState()
    val updateCheckState by viewModel.updateCheckState.collectAsState()
    val books by viewModel.books.collectAsState()
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingRecipeImport by remember { mutableStateOf<Pair<RecipeExportDto, List<RecipePhoto>>?>(null) }
    var selectedBookId by remember { mutableStateOf<Long?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            viewModel.exportLibrary(context, uri)
            scope.launch { snackbarHostState.showSnackbar("Copia de seguridad exportada") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.importLibrary(context, uri) { message ->
                scope.launch { snackbarHostState.showSnackbar(message) }
            }
        }
    }
    val importRecipeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                when (val result = viewModel.parseRecipeJson(context, uri)) {
                    is RecipeImportResult.Error -> snackbarHostState.showSnackbar("No se pudo importar: ${result.reason}")
                    is RecipeImportResult.Success -> when {
                        books.isEmpty() -> snackbarHostState.showSnackbar("No tienes ningún libro. Crea uno primero.")
                        else -> {
                            selectedBookId = books.first().id
                            pendingRecipeImport = result.recipe to result.photos
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Volver") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SettingsSection(title = "Tema") {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setThemeMode(mode) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = themeMode == mode, onClick = { viewModel.setThemeMode(mode) })
                        Text(mode.label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            HorizontalDivider()

            SettingsSection(title = "Seguridad") {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Bloqueo biométrico", modifier = Modifier.padding(start = 16.dp).weight(1f))
                    Switch(
                        checked = biometricLockEnabled,
                        onCheckedChange = { checked ->
                            if (!checked) {
                                viewModel.setBiometricLockEnabled(false)
                            } else if (activity != null && BiometricAuthenticator.canAuthenticate(activity)) {
                                viewModel.setBiometricLockEnabled(true)
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Configura una huella, rostro o PIN en el dispositivo para activar el bloqueo")
                                }
                            }
                        }
                    )
                }
            }

            HorizontalDivider()

            SettingsSection(title = "Copia de seguridad") {
                OutlinedButton(onClick = { exportLauncher.launch("recetarios_backup.zip") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Exportar toda la app")
                }
                OutlinedButton(onClick = { importLauncher.launch(BACKUP_MIME_TYPES) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Importar copia de seguridad")
                }
                OutlinedButton(onClick = { importRecipeLauncher.launch(BACKUP_MIME_TYPES) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Importar receta")
                }
            }

            HorizontalDivider()

            SettingsSection(title = "Importar con IA (beta)") {
                Text(
                    "Reconoce el texto de una foto de una receta (libro, revista, escrita a mano) " +
                        "usando Google Gemini. La foto se envía a Google para procesarla; no se " +
                        "guarda ninguna copia salvo la que decidas añadir tú a la receta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = geminiApiKey,
                    onValueChange = { viewModel.setGeminiApiKey(it) },
                    label = { Text("API key de Gemini") },
                    placeholder = { Text("Consíguela gratis en aistudio.google.com") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider()

            SettingsSection(title = "Actualizaciones") {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Notificar si hay una versión nueva", modifier = Modifier.padding(start = 16.dp).weight(1f))
                    Switch(checked = autoCheckUpdatesEnabled, onCheckedChange = { viewModel.setAutoCheckUpdatesEnabled(it) })
                }

                Text(
                    "Canal",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                UpdateChannel.entries.forEach { channel ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setUpdateChannel(channel) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = updateChannel == channel, onClick = { viewModel.setUpdateChannel(channel) })
                        Text(channel.label, modifier = Modifier.padding(start = 8.dp))
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.checkForUpdatesNow() },
                    enabled = updateCheckState !is UpdateCheckState.Checking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Buscar actualizaciones")
                }

                when (val state = updateCheckState) {
                    is UpdateCheckState.Checking -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Text(
                                "Buscando...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                    is UpdateCheckState.UpToDate -> {
                        Text(
                            "Ya tienes la última versión (${BuildConfig.VERSION_NAME})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is UpdateCheckState.Available -> {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Hay una versión nueva: ${state.info.latestVersion}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.info.releaseUrl))
                                runCatching { context.startActivity(intent) }
                            }) { Text("Descargar") }
                        }
                    }
                    is UpdateCheckState.Error -> {
                        Text(
                            "No se pudo comprobar: ${state.reason}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    is UpdateCheckState.Idle -> Unit
                }
            }

            HorizontalDivider()

            SettingsSection(title = "Gestionar") {
                ManageRow(icon = Icons.Filled.Category, label = "Categorías", onClick = onManageCategories)
                ManageRow(icon = Icons.Filled.Kitchen, label = "Utensilios", onClick = onManageUtensils)
                ManageRow(icon = Icons.Filled.RestaurantMenu, label = "Ingredientes", onClick = onManageIngredients)
                ManageRow(icon = Icons.Filled.Sell, label = "Categorías de ingredientes", onClick = onManageIngredientCategories)
            }

            HorizontalDivider()

            SettingsSection(title = "Ayuda") {
                ManageRow(icon = Icons.Filled.HelpOutline, label = "Ayuda y soporte", onClick = onHelp)
            }
        }
    }

    pendingRecipeImport?.let { (dto, photos) ->
        AlertDialog(
            onDismissRequest = { pendingRecipeImport = null },
            title = { Text("Importar \"${dto.name}\"") },
            text = {
                Column {
                    Text("¿A qué libro quieres añadir esta receta?", modifier = Modifier.padding(bottom = 8.dp))
                    books.forEach { book ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedBookId = book.id }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedBookId == book.id, onClick = { selectedBookId = book.id })
                            Text(book.name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val bookId = selectedBookId
                        if (bookId != null) {
                            viewModel.importRecipeIntoBook(dto, photos, bookId) {
                                scope.launch { snackbarHostState.showSnackbar("Receta importada") }
                            }
                        }
                        pendingRecipeImport = null
                    },
                    enabled = selectedBookId != null
                ) { Text("Importar") }
            },
            dismissButton = { TextButton(onClick = { pendingRecipeImport = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun ManageRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, modifier = Modifier.padding(start = 16.dp).weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
