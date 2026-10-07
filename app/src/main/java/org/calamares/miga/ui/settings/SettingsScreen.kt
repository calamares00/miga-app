package org.calamares.miga.ui.settings

import org.calamares.miga.ui.theme.accentColorFor
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
import org.calamares.miga.data.ai.ANTHROPIC_MODELS
import org.calamares.miga.data.ai.GEMINI_MODELS
import org.calamares.miga.data.ai.AiProvider
import org.calamares.miga.ui.common.BACKUP_MIME_TYPES
import org.calamares.miga.ui.security.BiometricAuthenticator
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Screen of one Settings category (see [SettingsSection]); the main screen is [SettingsHomeScreen].
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
    val packsCatalogRepo by viewModel.packsCatalogRepo.collectAsState()
    var dictationMenuExpanded by remember { mutableStateOf(false) }
    val dictationLanguage by viewModel.dictationLanguage.collectAsState()
    val aiEnabled by viewModel.aiEnabled.collectAsState()
    val aiHealthEnabled by viewModel.aiHealthEnabled.collectAsState()
    val aiNutritionEnabled by viewModel.aiNutritionEnabled.collectAsState()
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
                    ?.filter { it.locale.language == "es" || it.locale.language == "en" }
                    ?.sortedWith(compareBy({ it.locale.language != L10n.locale().language }, { it.locale.toString() }, { it.isNetworkConnectionRequired }, { it.name }))
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

    // Backup: the password chosen in ExportBackupDialog waits here while the user picks where to
    // save the file (plain backups are .zip, encrypted ones a .migabackup only Miga can open).
    var showExportDialog by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf<CharArray?>(null) }
    val onExported: (String) -> Unit = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.exportLibrary(context, uri, null, onExported)
    }
    val encryptedExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val password = exportPassword
        exportPassword = null
        if (uri != null && password != null) viewModel.exportLibrary(context, uri, password, onExported) else password?.fill('\u0000')
    }
    var pendingLibraryImport by remember { mutableStateOf<LibraryImportParseResult.Success?>(null) }
    var passwordPrompt by remember { mutableStateOf<LibraryImportParseResult.NeedsPassword?>(null) }
    fun handleImportResult(result: LibraryImportParseResult) {
        when (result) {
            is LibraryImportParseResult.Error -> scope.launch { snackbarHostState.showSnackbar(L10n.str(R.string.couldnt_import_x, result.reason)) }
            is LibraryImportParseResult.Success -> pendingLibraryImport = result
            is LibraryImportParseResult.NeedsPassword -> passwordPrompt = result
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch { handleImportResult(viewModel.validateLibraryImport(context, uri)) }
        }
    }
    val importRecipeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                when (val result = viewModel.parseRecipeJson(context, uri)) {
                    is RecipeImportResult.Error -> snackbarHostState.showSnackbar(L10n.str(R.string.couldnt_import_x, result.reason))
                    is RecipeImportResult.Success -> when {
                        books.isEmpty() -> snackbarHostState.showSnackbar(L10n.str(R.string.dont_have_books_create_one))
                        else -> {
                            selectedBookId = books.first().id
                            pendingRecipeImport = result.recipe to result.photos
                        }
                    }
                }
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(section.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = L10n.str(R.string.back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
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
            SettingsCard(title = "") {
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
                    L10n.str(R.string.colour),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ColorTheme.entries.forEach { theme ->
                        ColorThemeSwatch(
                            color = accentColorFor(theme),
                            selected = colorTheme == theme,
                            contentDescription = theme.label,
                            onClick = { viewModel.setColorTheme(theme) }
                        )
                    }
                }
            }
                }
                SettingsSection.SECURITY -> {
            SettingsCard(title = "") {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(L10n.str(R.string.biometric_lock), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            L10n.str(R.string.asks_devices_fingerprint_face_pin),
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
                                    snackbarHostState.showSnackbar(L10n.str(R.string.set_up_fingerprint_face_pin))
                                }
                            }
                        }
                    )
                }
            }
                }
                SettingsSection.CONTENT -> {
            SettingsCard(title = "", contentSpacing = 0.dp) {
                ManageRow(icon = Icons.Filled.Category, label = L10n.str(R.string.categories), onClick = onManageCategories)
                HorizontalDivider()
                ManageRow(icon = Icons.Filled.Kitchen, label = L10n.str(R.string.utensils), onClick = onManageUtensils)
                HorizontalDivider()
                ManageRow(icon = Icons.Filled.RestaurantMenu, label = L10n.str(R.string.ingredients), onClick = onManageIngredients)
                HorizontalDivider()
                ManageRow(icon = Icons.Filled.Sell, label = L10n.str(R.string.ingredient_categories), onClick = onManageIngredientCategories)
            }
                }
                SettingsSection.BACKUP -> {
            SettingsCard(title = "", contentSpacing = 0.dp) {
                ManageRow(
                    icon = Icons.Filled.Backup,
                    label = L10n.str(R.string.export_whole_app),
                    summary = L10n.str(R.string.saves_books_recipes_photos_zip),
                    chevron = false,
                    onClick = { showExportDialog = true }
                )
                HorizontalDivider()
                ManageRow(
                    icon = Icons.Filled.Restore,
                    label = L10n.str(R.string.import_backup),
                    summary = L10n.str(R.string.restore_zip_json_delete_current),
                    chevron = false,
                    onClick = { importLauncher.launch(BACKUP_MIME_TYPES) }
                )
                HorizontalDivider()
                ManageRow(
                    icon = Icons.Filled.Add,
                    label = L10n.str(R.string.import_recipe),
                    summary = L10n.str(R.string.add_single_recipe_one_books),
                    chevron = false,
                    onClick = { importRecipeLauncher.launch(BACKUP_MIME_TYPES) }
                )
            }
                }
                SettingsSection.AI -> {
            SettingsCard(title = "") {
                AiSwitchRow(
                    title = L10n.str(R.string.ai_enabled_title),
                    subtitle = L10n.str(R.string.ai_enabled_desc),
                    checked = aiEnabled,
                    onCheckedChange = { viewModel.setAiEnabled(it) }
                )
                if (aiEnabled) {
                    AiSwitchRow(
                        title = L10n.str(R.string.ai_health_title),
                        subtitle = L10n.str(R.string.ai_health_desc),
                        checked = aiHealthEnabled,
                        onCheckedChange = { viewModel.setAiHealthEnabled(it) }
                    )
                    AiSwitchRow(
                        title = L10n.str(R.string.ai_nutrition_title),
                        subtitle = L10n.str(R.string.ai_nutrition_desc),
                        checked = aiNutritionEnabled,
                        onCheckedChange = { viewModel.setAiNutritionEnabled(it) }
                    )
                }
            }
            if (aiEnabled) {
            SettingsCard(
                title = "",
                description = L10n.str(R.string.reads_text_photo_recipe_book)
            ) {
                AiProvidersSettings(viewModel)
            }
            }
                }
                SettingsSection.VOICE -> {
            SettingsCard(
                title = L10n.str(R.string.voice_dictation),
                description = L10n.str(R.string.language_used_recognise_what_dictate)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = DictationLanguages.label(dictationLanguage),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(L10n.str(R.string.dictation_language)) },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = L10n.str(R.string.open_language_picker)) },
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
                title = L10n.str(R.string.cooking_mode),
                description = L10n.str(R.string.voice_used_read_steps_aloud)
            ) {
                val voiceLabels = remember(availableVoices) { simpleVoiceLabels(availableVoices) }
                val selectedVoiceLabel = availableVoices.firstOrNull { it.name == ttsVoiceName }
                    ?.let { voiceLabels[it.name] }
                    ?: L10n.str(R.string.system_default_2)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedVoiceLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(L10n.str(R.string.voice)) },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = L10n.str(R.string.open_voice_picker)) },
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
                            text = { Text(L10n.str(R.string.system_default_2)) },
                            onClick = { viewModel.setTtsVoiceName(null); voiceMenuExpanded = false }
                        )
                        availableVoices.forEach { voice ->
                            DropdownMenuItem(
                                text = { Text(voiceLabels[voice.name] ?: voice.name) },
                                onClick = { viewModel.setTtsVoiceName(voice.name); voiceMenuExpanded = false }
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        val engine = tts ?: return@OutlinedButton
                        val chosen = availableVoices.firstOrNull { it.name == ttsVoiceName }
                        if (chosen != null) engine.setVoice(chosen) else engine.setLanguage(L10n.locale())
                        // The sample sentence uses the chosen voice's language, or the app language
                        // when none is chosen.
                        val language = (chosen?.locale ?: L10n.locale()).language
                        val sample = if (language == "es") "Añade dos cucharadas de aceite de oliva." else "Add two tablespoons of olive oil."
                        engine.speak(sample, TextToSpeech.QUEUE_FLUSH, null, "voice_preview")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(L10n.str(R.string.test_voice))
                }
            }
                }
                SettingsSection.PACKS -> {
            SettingsCard(
                title = "",
                description = L10n.str(R.string.install_ready_use_recipe_books)
            ) {
                ManageRow(icon = Icons.Filled.Storefront, label = L10n.str(R.string.explore_catalogue), onClick = onOpenPacksCatalog)
                var showCustomCatalog by remember { mutableStateOf(packsCatalogRepo != DEFAULT_PACKS_CATALOG) }
                if (showCustomCatalog) {
                    OutlinedTextField(
                        // The official catalogue is not shown: an empty field means using it.
                        value = if (packsCatalogRepo == DEFAULT_PACKS_CATALOG) "" else packsCatalogRepo,
                        onValueChange = { viewModel.setPacksCatalogRepo(it) },
                        label = { Text(L10n.str(R.string.alternative_catalogue)) },
                        placeholder = { Text(L10n.str(R.string.url_github_user_repo_empty)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    TextButton(onClick = { showCustomCatalog = true }) { Text(L10n.str(R.string.use_another_catalogue)) }
                }
            }
                }
                SettingsSection.SYNC -> {
            SettingsCard(
                title = "",
                description = L10n.str(R.string.connect_app_one_more_namespaces)
            ) {
                ManageRow(icon = Icons.Filled.Sync, label = L10n.str(R.string.manage_connections), summary = L10n.str(R.string.server_namespace_qr_invitations), onClick = onOpenSyncConnections)
            }
                }
            }
        }
    }

    pendingRecipeImport?.let { (dto, photos) ->
        AlertDialog(
            onDismissRequest = { pendingRecipeImport = null },
            title = { Text(L10n.str(R.string.import_quoted_x, dto.name)) },
            text = {
                Column {
                    Text(L10n.str(R.string.which_book_want_add_recipe), modifier = Modifier.padding(bottom = 8.dp))
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
                                scope.launch { snackbarHostState.showSnackbar(L10n.str(R.string.recipe_imported)) }
                            }
                        }
                        pendingRecipeImport = null
                    },
                    enabled = selectedBookId != null
                ) { Text(L10n.str(R.string.import_action)) }
            },
            dismissButton = { TextButton(onClick = { pendingRecipeImport = null }) { Text(L10n.str(R.string.cancel)) } }
        )
    }

    if (showExportDialog) {
        ExportBackupDialog(
            onConfirm = { password ->
                showExportDialog = false
                val baseName = L10n.str(R.string.file_name_backup_x, java.time.LocalDate.now().toString())
                if (password == null) {
                    exportLauncher.launch("$baseName.zip")
                } else {
                    exportPassword = password
                    encryptedExportLauncher.launch("$baseName.migabackup")
                }
            },
            onDismiss = { showExportDialog = false }
        )
    }

    passwordPrompt?.let { prompt ->
        BackupPasswordPromptDialog(
            wrongPassword = prompt.wrongPassword,
            onSubmit = { password ->
                passwordPrompt = null
                scope.launch { handleImportResult(viewModel.validateLibraryImport(context, prompt.source, password)) }
            },
            onDismiss = { passwordPrompt = null }
        )
    }

    pendingLibraryImport?.let { parsed ->
        AlertDialog(
            onDismissRequest = { viewModel.discardLibraryImport(parsed); pendingLibraryImport = null },
            title = { Text(L10n.str(R.string.import_backup)) },
            text = {
                Text(
                    L10n.str(R.string.x_recipe_imported_want_delete, parsed.dto.recipes.size)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val toImport = parsed
                    pendingLibraryImport = null
                    viewModel.confirmLibraryImport(context, toImport, wipeFirst = true) { message ->
                        scope.launch { snackbarHostState.showSnackbar(message) }
                    }
                }) { Text(L10n.str(R.string.delete_everything_import), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { viewModel.discardLibraryImport(parsed); pendingLibraryImport = null }) { Text(L10n.str(R.string.cancel)) }
                    TextButton(onClick = {
                        val toImport = parsed
                        pendingLibraryImport = null
                        viewModel.confirmLibraryImport(context, toImport, wipeFirst = false) { message ->
                            scope.launch { snackbarHostState.showSnackbar(message) }
                        }
                    }) { Text(L10n.str(R.string.add_without_deleting)) }
                }
            }
        )
    }
}

@Composable
private fun AiSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Group of options inside a category screen: no card or icon (the category icon and title are
 * already in the top bar), with an optional header in the primary colour (omitted when [title] is
 * empty) and an optional description.
 */
@Composable
private fun SettingsCard(
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
            Icon(Icons.Filled.Check, contentDescription = L10n.str(R.string.selected_2), tint = Color.White)
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

/** UI language: system default, Spanish or English. Changing it restarts the app. */
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
            dismissButton = { TextButton(onClick = { pending = null }) { Text(L10n.str(R.string.cancel)) } }
        )
    }
}

/** Language names are shown in their own language, as in Android's settings. */
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

/**
 * Short voice names ("Español (España) · Voice 1"), adding "offline"/"online" only when needed to
 * tell them apart.
 */
private fun simpleVoiceLabels(voices: List<Voice>): Map<String, String> {
    val labels = mutableMapOf<String, String>()
    voices.groupBy { it.locale.toString() }.forEach { (_, group) ->
        group.forEachIndexed { index, voice ->
            val name = voice.locale.getDisplayName(L10n.locale()).replaceFirstChar { it.uppercase() }
            val online = if (voice.isNetworkConnectionRequired) " · ${L10n.str(R.string.voice_online)}" else ""
            labels[voice.name] = "$name · ${L10n.str(R.string.voice_n, index + 1)}$online"
        }
    }
    return labels
}
