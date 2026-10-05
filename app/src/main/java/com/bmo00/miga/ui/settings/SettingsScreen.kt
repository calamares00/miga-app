package com.bmo00.miga.ui.settings

import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedScroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.bmo00.miga.BuildConfig
import com.bmo00.miga.data.export.LibraryImportParseResult
import com.bmo00.miga.data.export.RecipeExportDto
import com.bmo00.miga.data.export.RecipeImportResult
import com.bmo00.miga.data.voice.DictationLanguages
import com.bmo00.miga.data.model.ColorTheme
import com.bmo00.miga.data.model.RecipePhoto
import com.bmo00.miga.data.model.ThemeMode
import com.bmo00.miga.data.model.UpdateChannel
import com.bmo00.miga.data.vision.ANTHROPIC_MODELS
import com.bmo00.miga.data.vision.GEMINI_MODELS
import com.bmo00.miga.data.vision.VisionProviderType
import com.bmo00.miga.ui.common.BACKUP_MIME_TYPES
import com.bmo00.miga.ui.security.BiometricAuthenticator
import com.bmo00.miga.ui.theme.Blue
import com.bmo00.miga.ui.theme.Green
import com.bmo00.miga.ui.theme.Orange
import com.bmo00.miga.ui.theme.Pink
import com.bmo00.miga.ui.theme.Purple
import com.bmo00.miga.ui.theme.Teal
import com.bmo00.miga.ui.theme.Terracotta
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Pantalla de una categoría de Ajustes (ver [SettingsSection]); la pantalla principal es [SettingsHomeScreen].
 * Cada categoría reúne las opciones que antes estaban apiladas en tarjetas dentro de una sola pantalla.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSectionScreen(
    viewModel: SettingsViewModel,
    section: SettingsSection,
    onBack: () -> Unit,
    onManageCategories: () -> Unit,
    onManageUtensils: () -> Unit,
    onManageIngredients: () -> Unit,
    onManageIngredientCategories: () -> Unit,
    onOpenPacksCatalog: () -> Unit,
    onOpenSyncConnections: () -> Unit
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val colorTheme by viewModel.colorTheme.collectAsState()
    val biometricLockEnabled by viewModel.biometricLockEnabled.collectAsState()
    val autoCheckUpdatesEnabled by viewModel.autoCheckUpdatesEnabled.collectAsState()
    val updateChannel by viewModel.updateChannel.collectAsState()
    val updateCheckState by viewModel.updateCheckState.collectAsState()
    val books by viewModel.books.collectAsState()
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()
    val packsCatalogRepo by viewModel.packsCatalogRepo.collectAsState()
    val geminiModel by viewModel.geminiModel.collectAsState()
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var dictationMenuExpanded by remember { mutableStateOf(false) }
    val dictationLanguage by viewModel.dictationLanguage.collectAsState()
    val visionProvider by viewModel.visionProvider.collectAsState()
    val anthropicApiKey by viewModel.anthropicApiKey.collectAsState()
    val anthropicModel by viewModel.anthropicModel.collectAsState()
    var anthropicModelMenuExpanded by remember { mutableStateOf(false) }
    val ttsVoiceName by viewModel.ttsVoiceName.collectAsState()
    var voiceMenuExpanded by remember { mutableStateOf(false) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var availableVoices by remember { mutableStateOf<List<Voice>>(emptyList()) }
    val context = LocalContext.current

    DisposableEffect(section) {
        if (section != SettingsSection.VOICE) return@DisposableEffect onDispose { }
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                availableVoices = engine?.voices
                    ?.filter { it.locale.language == "es" }
                    ?.sortedBy { "${it.locale} ${it.name}" }
                    .orEmpty()
            }
        }
        tts = engine
        onDispose { engine?.stop(); engine?.shutdown() }
    }
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
    var pendingLibraryImport by remember { mutableStateOf<LibraryImportParseResult.Success?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                when (val result = viewModel.validateLibraryImport(context, uri)) {
                    is LibraryImportParseResult.Error -> snackbarHostState.showSnackbar("No se pudo importar: ${result.reason}")
                    is LibraryImportParseResult.Success -> pendingLibraryImport = result
                }
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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text(section.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Volver") }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (section) {
                SettingsSection.APPEARANCE -> {
            SettingsCard(icon = Icons.Filled.Palette, title = "") {
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    "Color",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ColorTheme.entries.forEach { theme ->
                        ColorThemeSwatch(
                            color = colorForTheme(theme),
                            selected = colorTheme == theme,
                            contentDescription = theme.label,
                            onClick = { viewModel.setColorTheme(theme) }
                        )
                    }
                }
            }
                }
                SettingsSection.SECURITY -> {
            SettingsCard(icon = Icons.Filled.Fingerprint, title = "") {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bloqueo biométrico", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Pide huella, rostro o PIN del dispositivo al abrir la app",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                }
                SettingsSection.CONTENT -> {
            SettingsCard(icon = Icons.Filled.Tune, title = "", contentSpacing = 0.dp) {
                ManageRow(icon = Icons.Filled.Category, label = "Categorías", onClick = onManageCategories)
                HorizontalDivider()
                ManageRow(icon = Icons.Filled.Kitchen, label = "Utensilios", onClick = onManageUtensils)
                HorizontalDivider()
                ManageRow(icon = Icons.Filled.RestaurantMenu, label = "Ingredientes", onClick = onManageIngredients)
                HorizontalDivider()
                ManageRow(icon = Icons.Filled.Sell, label = "Categorías de ingredientes", onClick = onManageIngredientCategories)
            }
                }
                SettingsSection.BACKUP -> {
            SettingsCard(icon = Icons.Filled.Backup, title = "", contentSpacing = 0.dp) {
                ManageRow(
                    icon = Icons.Filled.Backup,
                    label = "Exportar toda la app",
                    summary = "Guarda libros, recetas y fotos en un archivo ZIP",
                    chevron = false,
                    onClick = { exportLauncher.launch("recetarios_backup.zip") }
                )
                HorizontalDivider()
                ManageRow(
                    icon = Icons.Filled.Restore,
                    label = "Importar copia de seguridad",
                    summary = "Restaura desde un ZIP o JSON; puedes borrar antes lo actual",
                    chevron = false,
                    onClick = { importLauncher.launch(BACKUP_MIME_TYPES) }
                )
                HorizontalDivider()
                ManageRow(
                    icon = Icons.Filled.Add,
                    label = "Importar receta",
                    summary = "Añade una receta suelta a uno de tus libros",
                    chevron = false,
                    onClick = { importRecipeLauncher.launch(BACKUP_MIME_TYPES) }
                )
            }
                }
                SettingsSection.AI -> {
            SettingsCard(
                icon = Icons.Filled.AutoAwesome,
                title = "",
                description = "Reconoce el texto de una foto de una receta (libro, revista, escrita a mano) " +
                    "y valora lo saludable que es, usando el proveedor de IA que elijas. La foto o el " +
                    "texto se envían a ese proveedor para procesarlos; no se guarda ninguna copia salvo " +
                    "la que decidas añadir tú a la receta."
            ) {
                VisionProviderType.entries.forEach { provider ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setVisionProvider(provider) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = visionProvider == provider, onClick = { viewModel.setVisionProvider(provider) })
                        Text(provider.label, modifier = Modifier.padding(start = 8.dp))
                    }
                }

                if (visionProvider == VisionProviderType.GEMINI) {
                    OutlinedTextField(
                        value = geminiApiKey,
                        onValueChange = { viewModel.setGeminiApiKey(it) },
                        label = { Text("API key de Gemini") },
                        placeholder = { Text("Consíguela gratis en aistudio.google.com") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val isCustomModel = geminiModel !in GEMINI_MODELS
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = if (isCustomModel) "Personalizado" else geminiModel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Modelo de Gemini") },
                            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = "Abrir selector de modelo") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        // Capa transparente encima del campo para abrir el menú al tocar, sin que el
                        // propio TextField (de solo lectura) capture el toque y muestre el cursor.
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { modelMenuExpanded = true }
                        )
                        DropdownMenu(
                            expanded = modelMenuExpanded,
                            onDismissRequest = { modelMenuExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            GEMINI_MODELS.forEach { modelId ->
                                DropdownMenuItem(
                                    text = { Text(modelId) },
                                    onClick = {
                                        viewModel.setGeminiModel(modelId)
                                        modelMenuExpanded = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Personalizado…") },
                                onClick = {
                                    viewModel.setGeminiModel("")
                                    modelMenuExpanded = false
                                }
                            )
                        }
                    }
                    if (isCustomModel) {
                        OutlinedTextField(
                            value = geminiModel,
                            onValueChange = { viewModel.setGeminiModel(it) },
                            label = { Text("Id del modelo") },
                            placeholder = { Text("p. ej. gemini-3.6-flash") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = anthropicApiKey,
                        onValueChange = { viewModel.setAnthropicApiKey(it) },
                        label = { Text("API key de Anthropic") },
                        placeholder = { Text("Consíguela en console.anthropic.com") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val isCustomAnthropicModel = anthropicModel !in ANTHROPIC_MODELS
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = if (isCustomAnthropicModel) "Personalizado" else anthropicModel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Modelo de Claude") },
                            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = "Abrir selector de modelo") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { anthropicModelMenuExpanded = true }
                        )
                        DropdownMenu(
                            expanded = anthropicModelMenuExpanded,
                            onDismissRequest = { anthropicModelMenuExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ANTHROPIC_MODELS.forEach { modelId ->
                                DropdownMenuItem(
                                    text = { Text(modelId) },
                                    onClick = {
                                        viewModel.setAnthropicModel(modelId)
                                        anthropicModelMenuExpanded = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Personalizado…") },
                                onClick = {
                                    viewModel.setAnthropicModel("")
                                    anthropicModelMenuExpanded = false
                                }
                            )
                        }
                    }
                    if (isCustomAnthropicModel) {
                        OutlinedTextField(
                            value = anthropicModel,
                            onValueChange = { viewModel.setAnthropicModel(it) },
                            label = { Text("Id del modelo") },
                            placeholder = { Text("p. ej. claude-haiku-4-5") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
                }
                SettingsSection.VOICE -> {
            SettingsCard(
                icon = Icons.Filled.Mic,
                title = "Dictado por voz",
                description = "Idioma en el que se reconoce lo que dictas (lista de la compra, pasos de receta " +
                    "y modo cocina). Si tu móvil está en otro idioma, elige aquí español."
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = DictationLanguages.label(dictationLanguage),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Idioma del dictado") },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = "Abrir selector de idioma") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(modifier = Modifier.matchParentSize().clickable { dictationMenuExpanded = true })
                    DropdownMenu(
                        expanded = dictationMenuExpanded,
                        onDismissRequest = { dictationMenuExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        DictationLanguages.ALL.forEach { (tag, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { viewModel.setDictationLanguage(tag); dictationMenuExpanded = false }
                            )
                        }
                    }
                }
            }

            SettingsCard(
                icon = Icons.Filled.RecordVoiceOver,
                title = "Modo cocina",
                description = "Voz usada para leer los pasos en voz alta en el modo cocina."
            ) {
                val selectedVoiceLabel = availableVoices.firstOrNull { it.name == ttsVoiceName }
                    ?.let { "${it.locale.displayName} (${it.name})" }
                    ?: "Predeterminada del sistema"
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedVoiceLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Voz") },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = "Abrir selector de voz") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { voiceMenuExpanded = true }
                    )
                    DropdownMenu(
                        expanded = voiceMenuExpanded,
                        onDismissRequest = { voiceMenuExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        DropdownMenuItem(
                            text = { Text("Predeterminada del sistema") },
                            onClick = { viewModel.setTtsVoiceName(null); voiceMenuExpanded = false }
                        )
                        availableVoices.forEach { voice ->
                            DropdownMenuItem(
                                text = { Text("${voice.locale.displayName} (${voice.name})") },
                                onClick = { viewModel.setTtsVoiceName(voice.name); voiceMenuExpanded = false }
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        val engine = tts ?: return@OutlinedButton
                        availableVoices.firstOrNull { it.name == ttsVoiceName }?.let { engine.setVoice(it) }
                        engine.setLanguage(Locale("es", "ES"))
                        engine.speak("Añade dos cucharadas de aceite de oliva.", TextToSpeech.QUEUE_FLUSH, null, "voice_preview")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Probar voz")
                }
            }
                }
                SettingsSection.PACKS -> {
            SettingsCard(
                icon = Icons.Filled.Storefront,
                title = "",
                description = "Instala libros de recetas publicados por otros usuarios. Son de solo " +
                    "lectura: no se pueden editar, solo consultar, cocinar y desinstalar."
            ) {
                OutlinedTextField(
                    value = packsCatalogRepo,
                    onValueChange = { viewModel.setPacksCatalogRepo(it) },
                    label = { Text("Repositorio del catálogo") },
                    placeholder = { Text("usuario/repositorio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ManageRow(icon = Icons.Filled.Storefront, label = "Explorar catálogo", onClick = onOpenPacksCatalog)
            }
                }
                SettingsSection.SYNC -> {
            SettingsCard(
                icon = Icons.Filled.Sync,
                title = "",
                description = "Conecta la app a uno o varios namespaces de un servidor self-hosted " +
                    "para compartir y sincronizar libros y recetas de lectura-escritura con otras " +
                    "apps Miga."
            ) {
                ManageRow(icon = Icons.Filled.Sync, label = "Gestionar conexiones", summary = "Servidor, namespace e invitaciones por QR", onClick = onOpenSyncConnections)
            }
                }
                SettingsSection.UPDATES -> {
            SettingsCard(icon = Icons.Filled.SystemUpdate, title = "") {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Notificar si hay una versión nueva", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Se comprueba al abrir la app",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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

                ManageRow(
                    icon = Icons.Filled.SystemUpdate,
                    label = "Buscar actualizaciones",
                    summary = "Versión instalada: ${BuildConfig.VERSION_NAME}",
                    chevron = false,
                    onClick = { if (updateCheckState !is UpdateCheckState.Checking) viewModel.checkForUpdatesNow() }
                )

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
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.info.apkDownloadUrl ?: state.info.releaseUrl))
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
                }
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

    pendingLibraryImport?.let { parsed ->
        AlertDialog(
            onDismissRequest = { pendingLibraryImport = null },
            title = { Text("Importar copia de seguridad") },
            text = {
                Text(
                    "Se van a importar ${parsed.dto.recipes.size} receta(s). ¿Quieres borrar antes tus " +
                        "libros y recetas actuales (con sus fotos) para dejarlo limpio, o añadir el " +
                        "contenido importado a lo que ya tienes? Los packs instalados no se ven afectados."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val toImport = parsed
                    pendingLibraryImport = null
                    viewModel.confirmLibraryImport(context, toImport, wipeFirst = true) { message ->
                        scope.launch { snackbarHostState.showSnackbar(message) }
                    }
                }) { Text("Borrar todo e importar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { pendingLibraryImport = null }) { Text("Cancelar") }
                    TextButton(onClick = {
                        val toImport = parsed
                        pendingLibraryImport = null
                        viewModel.confirmLibraryImport(context, toImport, wipeFirst = false) { message ->
                            scope.launch { snackbarHostState.showSnackbar(message) }
                        }
                    }) { Text("Añadir sin borrar") }
                }
            }
        )
    }
}

/**
 * Grupo de opciones dentro de la pantalla de una categoría: sin tarjeta ni icono (el icono y el título
 * de la categoría ya están en la barra superior), con un encabezado opcional en el color primario
 * (se omite si [title] está vacío) y una descripción opcional. [icon] ya no se dibuja; se conserva
 * para no tocar las llamadas existentes.
 */
@Composable
private fun SettingsCard(
    icon: ImageVector,
    title: String,
    description: String? = null,
    contentSpacing: Dp = 12.dp,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title.isNotBlank()) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        if (description != null) {
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(contentSpacing)) {
            content()
        }
    }
}

private fun colorForTheme(colorTheme: ColorTheme) = when (colorTheme) {
    ColorTheme.TERRACOTTA -> Terracotta
    ColorTheme.BLUE -> Blue
    ColorTheme.GREEN -> Green
    ColorTheme.PURPLE -> Purple
    ColorTheme.PINK -> Pink
    ColorTheme.ORANGE -> Orange
    ColorTheme.TEAL -> Teal
}

@Composable
private fun ColorThemeSwatch(color: Color, selected: Boolean, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape
            )
            .clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "Seleccionado", tint = Color.White)
        }
    }
}

@Composable
private fun ManageRow(icon: ImageVector, label: String, onClick: () -> Unit, summary: String? = null, chevron: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (chevron) {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
