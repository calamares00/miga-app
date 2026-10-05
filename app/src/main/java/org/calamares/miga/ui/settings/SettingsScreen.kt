package org.calamares.miga.ui.settings

import org.calamares.miga.AppLanguage
import android.content.ContextWrapper
import android.content.Context
import android.app.Activity
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.remote.DEFAULT_PACKS_CATALOG
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import org.calamares.miga.BuildConfig
import org.calamares.miga.data.export.LibraryImportParseResult
import org.calamares.miga.data.export.RecipeExportDto
import org.calamares.miga.data.export.RecipeImportResult
import org.calamares.miga.data.voice.DictationLanguages
import org.calamares.miga.data.model.ColorTheme
import org.calamares.miga.data.model.RecipePhoto
import org.calamares.miga.data.model.ThemeMode
import org.calamares.miga.data.vision.ANTHROPIC_MODELS
import org.calamares.miga.data.vision.GEMINI_MODELS
import org.calamares.miga.data.vision.VisionProviderType
import org.calamares.miga.ui.common.BACKUP_MIME_TYPES
import org.calamares.miga.ui.security.BiometricAuthenticator
import org.calamares.miga.ui.theme.Blue
import org.calamares.miga.ui.theme.Green
import org.calamares.miga.ui.theme.Orange
import org.calamares.miga.ui.theme.Pink
import org.calamares.miga.ui.theme.Purple
import org.calamares.miga.ui.theme.Teal
import org.calamares.miga.ui.theme.Terracotta
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
            scope.launch { snackbarHostState.showSnackbar(L10n.str(R.string.copia_seguridad_exportada)) }
        }
    }
    var pendingLibraryImport by remember { mutableStateOf<LibraryImportParseResult.Success?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                when (val result = viewModel.validateLibraryImport(context, uri)) {
                    is LibraryImportParseResult.Error -> snackbarHostState.showSnackbar(L10n.str(R.string.no_pudo_importar_x, result.reason))
                    is LibraryImportParseResult.Success -> pendingLibraryImport = result
                }
            }
        }
    }
    val importRecipeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                when (val result = viewModel.parseRecipeJson(context, uri)) {
                    is RecipeImportResult.Error -> snackbarHostState.showSnackbar(L10n.str(R.string.no_pudo_importar_x, result.reason))
                    is RecipeImportResult.Success -> when {
                        books.isEmpty() -> snackbarHostState.showSnackbar(L10n.str(R.string.no_tienes_ningun_libro_crea))
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
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = L10n.str(R.string.volver)) }
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

                LanguagePicker()

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    L10n.str(R.string.color),
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
                        Text(L10n.str(R.string.bloqueo_biometrico), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            L10n.str(R.string.pide_huella_rostro_pin_dispositivo),
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
                                    snackbarHostState.showSnackbar(L10n.str(R.string.configura_huella_rostro_pin_dispositivo))
                                }
                            }
                        }
                    )
                }
            }
                }
                SettingsSection.CONTENT -> {
            SettingsCard(icon = Icons.Filled.Tune, title = "", contentSpacing = 0.dp) {
                ManageRow(icon = Icons.Filled.Category, label = L10n.str(R.string.categorias), onClick = onManageCategories)
                HorizontalDivider()
                ManageRow(icon = Icons.Filled.Kitchen, label = L10n.str(R.string.utensilios), onClick = onManageUtensils)
                HorizontalDivider()
                ManageRow(icon = Icons.Filled.RestaurantMenu, label = L10n.str(R.string.ingredientes), onClick = onManageIngredients)
                HorizontalDivider()
                ManageRow(icon = Icons.Filled.Sell, label = L10n.str(R.string.categorias_ingredientes), onClick = onManageIngredientCategories)
            }
                }
                SettingsSection.BACKUP -> {
            SettingsCard(icon = Icons.Filled.Backup, title = "", contentSpacing = 0.dp) {
                ManageRow(
                    icon = Icons.Filled.Backup,
                    label = L10n.str(R.string.exportar_toda_app),
                    summary = L10n.str(R.string.guarda_libros_recetas_fotos_archivo),
                    chevron = false,
                    onClick = { exportLauncher.launch("recetarios_backup.zip") }
                )
                HorizontalDivider()
                ManageRow(
                    icon = Icons.Filled.Restore,
                    label = L10n.str(R.string.importar_copia_seguridad),
                    summary = L10n.str(R.string.restaura_desde_zip_json_puedes),
                    chevron = false,
                    onClick = { importLauncher.launch(BACKUP_MIME_TYPES) }
                )
                HorizontalDivider()
                ManageRow(
                    icon = Icons.Filled.Add,
                    label = L10n.str(R.string.importar_receta),
                    summary = L10n.str(R.string.anade_receta_suelta_uno_libros),
                    chevron = false,
                    onClick = { importRecipeLauncher.launch(BACKUP_MIME_TYPES) }
                )
            }
                }
                SettingsSection.AI -> {
            SettingsCard(
                icon = Icons.Filled.AutoAwesome,
                title = "",
                description = L10n.str(R.string.reconoce_texto_foto_receta_libro)
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
                        label = { Text(L10n.str(R.string.api_key_gemini)) },
                        placeholder = { Text(L10n.str(R.string.consiguela_gratis_aistudio_google_com)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val isCustomModel = geminiModel !in GEMINI_MODELS
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = if (isCustomModel) L10n.str(R.string.personalizado_2) else geminiModel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(L10n.str(R.string.modelo_gemini)) },
                            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = L10n.str(R.string.abrir_selector_modelo)) },
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
                                text = { Text(L10n.str(R.string.personalizado)) },
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
                            label = { Text(L10n.str(R.string.id_modelo)) },
                            placeholder = { Text(L10n.str(R.string.p_ej_gemini_3_6)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = anthropicApiKey,
                        onValueChange = { viewModel.setAnthropicApiKey(it) },
                        label = { Text(L10n.str(R.string.api_key_anthropic)) },
                        placeholder = { Text(L10n.str(R.string.consiguela_console_anthropic_com)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val isCustomAnthropicModel = anthropicModel !in ANTHROPIC_MODELS
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = if (isCustomAnthropicModel) L10n.str(R.string.personalizado_2) else anthropicModel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(L10n.str(R.string.modelo_claude)) },
                            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = L10n.str(R.string.abrir_selector_modelo)) },
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
                                text = { Text(L10n.str(R.string.personalizado)) },
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
                            label = { Text(L10n.str(R.string.id_modelo)) },
                            placeholder = { Text(L10n.str(R.string.p_ej_claude_haiku_4)) },
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
                title = L10n.str(R.string.dictado_voz),
                description = L10n.str(R.string.idioma_reconoce_dictas_lista_compra)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = DictationLanguages.label(dictationLanguage),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(L10n.str(R.string.idioma_dictado)) },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = L10n.str(R.string.abrir_selector_idioma)) },
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
                title = L10n.str(R.string.modo_cocina),
                description = L10n.str(R.string.voz_usada_leer_pasos_voz)
            ) {
                val selectedVoiceLabel = availableVoices.firstOrNull { it.name == ttsVoiceName }
                    ?.let { "${it.locale.displayName} (${it.name})" }
                    ?: L10n.str(R.string.predeterminada_sistema)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedVoiceLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(L10n.str(R.string.voz)) },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = L10n.str(R.string.abrir_selector_voz)) },
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
                            text = { Text(L10n.str(R.string.predeterminada_sistema)) },
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
                    Text(L10n.str(R.string.probar_voz))
                }
            }
                }
                SettingsSection.PACKS -> {
            SettingsCard(
                icon = Icons.Filled.Storefront,
                title = "",
                description = L10n.str(R.string.instala_libros_recetas_listos_usar)
            ) {
                ManageRow(icon = Icons.Filled.Storefront, label = L10n.str(R.string.explorar_catalogo), onClick = onOpenPacksCatalog)
                var showCustomCatalog by remember { mutableStateOf(packsCatalogRepo != DEFAULT_PACKS_CATALOG) }
                if (showCustomCatalog) {
                    OutlinedTextField(
                        // El catálogo oficial no se muestra: el campo vacío equivale a usarlo.
                        value = if (packsCatalogRepo == DEFAULT_PACKS_CATALOG) "" else packsCatalogRepo,
                        onValueChange = { viewModel.setPacksCatalogRepo(it) },
                        label = { Text(L10n.str(R.string.catalogo_alternativo)) },
                        placeholder = { Text(L10n.str(R.string.url_usuario_repo_github_vacio)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    TextButton(onClick = { showCustomCatalog = true }) { Text(L10n.str(R.string.usar_otro_catalogo)) }
                }
            }
                }
                SettingsSection.SYNC -> {
            SettingsCard(
                icon = Icons.Filled.Sync,
                title = "",
                description = L10n.str(R.string.conecta_app_uno_varios_namespaces)
            ) {
                ManageRow(icon = Icons.Filled.Sync, label = L10n.str(R.string.gestionar_conexiones), summary = L10n.str(R.string.servidor_namespace_e_invitaciones_qr), onClick = onOpenSyncConnections)
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
                    Text(L10n.str(R.string.libro_quieres_anadir_esta_receta), modifier = Modifier.padding(bottom = 8.dp))
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
                                scope.launch { snackbarHostState.showSnackbar(L10n.str(R.string.receta_importada)) }
                            }
                        }
                        pendingRecipeImport = null
                    },
                    enabled = selectedBookId != null
                ) { Text(L10n.str(R.string.importar)) }
            },
            dismissButton = { TextButton(onClick = { pendingRecipeImport = null }) { Text(L10n.str(R.string.cancelar)) } }
        )
    }

    pendingLibraryImport?.let { parsed ->
        AlertDialog(
            onDismissRequest = { pendingLibraryImport = null },
            title = { Text(L10n.str(R.string.importar_copia_seguridad)) },
            text = {
                Text(
                    L10n.str(R.string.van_importar_x_receta_s, parsed.dto.recipes.size)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val toImport = parsed
                    pendingLibraryImport = null
                    viewModel.confirmLibraryImport(context, toImport, wipeFirst = true) { message ->
                        scope.launch { snackbarHostState.showSnackbar(message) }
                    }
                }) { Text(L10n.str(R.string.borrar_todo_e_importar), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { pendingLibraryImport = null }) { Text(L10n.str(R.string.cancelar)) }
                    TextButton(onClick = {
                        val toImport = parsed
                        pendingLibraryImport = null
                        viewModel.confirmLibraryImport(context, toImport, wipeFirst = false) { message ->
                            scope.launch { snackbarHostState.showSnackbar(message) }
                        }
                    }) { Text(L10n.str(R.string.anadir_sin_borrar)) }
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
            Icon(Icons.Filled.Check, contentDescription = L10n.str(R.string.seleccionado), tint = Color.White)
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

/** Idioma de la interfaz: el del sistema, español o inglés. Cambiarlo reinicia la app. */
@Composable
private fun LanguagePicker() {
    val context = LocalContext.current
    val current = remember { L10n.language(context) }
    var pending by remember { mutableStateOf<AppLanguage?>(null) }
    Text(
        L10n.str(R.string.language_title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    AppLanguage.entries.forEach { language ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { if (language != current) pending = language }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = current == language, onClick = { if (language != current) pending = language })
            Text(languageLabel(language), modifier = Modifier.padding(start = 8.dp))
        }
    }
    pending?.let { language ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(L10n.str(R.string.language_restart_title)) },
            text = { Text(L10n.str(R.string.language_restart_body)) },
            confirmButton = {
                TextButton(onClick = {
                    val activity = context.findActivity()
                    if (activity != null) L10n.setLanguage(activity, language) else pending = null
                }) { Text(L10n.str(R.string.language_restart_confirm)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(L10n.str(R.string.cancelar)) } }
        )
    }
}

// Los nombres de idioma se muestran en su propio idioma, como en los ajustes de Android.
private fun languageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.SYSTEM -> L10n.str(R.string.language_system)
    AppLanguage.SPANISH -> "Español"
    AppLanguage.ENGLISH -> "English"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
