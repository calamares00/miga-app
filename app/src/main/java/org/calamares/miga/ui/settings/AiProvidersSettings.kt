package org.calamares.miga.ui.settings

import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.OutlinedButton
import org.calamares.miga.ui.components.ErrorMessage
import org.calamares.miga.data.ai.ProviderTestResult
import org.calamares.miga.data.ai.ProviderTest
import org.calamares.miga.data.ai.AiCandidate
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import org.calamares.miga.data.ai.OpenRouterConnect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Switch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.calamares.miga.ui.components.rememberSettingText
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.ai.OpenRouterModel
import org.calamares.miga.data.ai.OpenRouterModels
import org.calamares.miga.data.ai.imageSupportVaries
import org.calamares.miga.ui.components.rememberSettingText
import org.calamares.miga.data.ai.ProviderModels
import kotlinx.coroutines.delay
import org.calamares.miga.data.ai.AiProvider
import org.calamares.miga.ui.components.ReorderableColumn
import org.calamares.miga.ui.components.moved

/**
 * AI providers sorted by priority. Drag the handle to reorder; tap a provider to expand its key and
 * model.
 */
@Composable
fun AiProvidersSettings(viewModel: SettingsViewModel) {
    val order by viewModel.providerOrder.collectAsStateWithLifecycle()
    val apiKeys = viewModel.apiKeys.mapValues { (_, flow) -> flow.collectAsStateWithLifecycle().value }
    val models = viewModel.models.mapValues { (_, flow) -> flow.collectAsStateWithLifecycle().value }
    val modelImages = viewModel.modelImages.mapValues { (_, flow) -> flow.collectAsStateWithLifecycle().value }
    val customBaseUrl by viewModel.customBaseUrl.collectAsStateWithLifecycle()
    val disabledProviders by viewModel.disabledProviders.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf<AiProvider?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            L10n.str(R.string.ai_providers_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            L10n.str(R.string.ai_providers_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        ReorderableColumn(items = order, onReorder = { viewModel.setProviderOrder(it) }) { provider, index, dragHandle, isDragging ->
            val apiKey = apiKeys[provider].orEmpty()
            val model = models[provider].orEmpty()
            val readsImages = modelImages[provider] != false
            val status = when {
                provider == AiProvider.CUSTOM && customBaseUrl.isBlank() -> L10n.str(R.string.ai_provider_no_address)
                provider != AiProvider.CUSTOM && apiKey.isBlank() -> L10n.str(R.string.ai_provider_no_key)
                model.isBlank() -> L10n.str(R.string.ai_provider_no_model)
                else -> buildList {
                    add(model)
                    if (provider == AiProvider.OPENROUTER && isFreeModelId(model)) add(L10n.str(R.string.ai_free))
                    if (!readsImages) add(L10n.str(R.string.ai_no_images))
                }.joinToString(" · ")
            }
            val configured = when (provider) {
                AiProvider.CUSTOM -> customBaseUrl.isNotBlank() && model.isNotBlank()
                else -> apiKey.isNotBlank() && model.isNotBlank()
            }
            val switchedOn = provider !in disabledProviders
            ProviderRow(
                provider = provider,
                position = index + 1,
                status = if (configured && !switchedOn) L10n.str(R.string.ai_provider_off_x, status) else status,
                active = configured && switchedOn,
                // Only a provider with a key can be switched on or off; the key and model are kept.
                switchedOn = switchedOn.takeIf { configured },
                onSwitch = { viewModel.setProviderEnabled(provider, it) },
                expanded = expanded == provider,
                isDragging = isDragging,
                dragHandle = dragHandle,
                onToggle = { expanded = if (expanded == provider) null else provider },
                onMoveUp = if (index > 0) ({ viewModel.setProviderOrder(order.moved(index, index - 1)) }) else null,
                onMoveDown = if (index < order.lastIndex) ({ viewModel.setProviderOrder(order.moved(index, index + 1)) }) else null
            ) {
                ProviderFields(
                    provider = provider,
                    apiKey = apiKey,
                    model = model,
                    readsImages = readsImages,
                    baseUrl = customBaseUrl,
                    viewModel = viewModel
                )
            }
        }
    }
}

/** Texts of a provider's settings: key label and hint, model example and a note below, if any. */
private class ProviderTexts(val keyLabel: Int, val keyHint: Int, val modelExample: Int, val note: Int?)

private fun textsOf(provider: AiProvider): ProviderTexts = when (provider) {
    AiProvider.GEMINI -> ProviderTexts(R.string.api_key_gemini, R.string.get_one_free_aistudio_google, R.string.e_g_gemini_3_6, null)
    AiProvider.ANTHROPIC -> ProviderTexts(R.string.api_key_anthropic, R.string.get_one_console_anthropic_com, R.string.e_g_claude_haiku_4, null)
    AiProvider.OPENROUTER -> ProviderTexts(R.string.api_key_openrouter, R.string.openrouter_key_hint, R.string.e_g_custom_model, R.string.openrouter_free_note)
    AiProvider.OPENAI -> ProviderTexts(R.string.api_key_openai, R.string.get_one_platform_openai_com, R.string.e_g_gpt_5_mini, null)
    AiProvider.DEEPSEEK -> ProviderTexts(R.string.api_key_deepseek, R.string.get_one_platform_deepseek_com, R.string.e_g_deepseek_flash, R.string.deepseek_note)
    AiProvider.MISTRAL -> ProviderTexts(R.string.api_key_mistral, R.string.get_one_console_mistral_ai, R.string.e_g_mistral_groq_model, R.string.mistral_note)
    AiProvider.GROQ -> ProviderTexts(R.string.api_key_groq, R.string.get_one_console_groq_com, R.string.e_g_mistral_groq_model, R.string.groq_note)
    AiProvider.CUSTOM -> ProviderTexts(R.string.api_key_custom, R.string.custom_ai_key_hint, R.string.e_g_custom_model, R.string.custom_ai_note)
}

/**
 * Settings of a provider, the same for all: the address (custom server only), the key (optional
 * for a custom server), the model from the account's list (OpenRouter's catalogue for OpenRouter)
 * and, where models differ, whether it reads images, filled in when a model is chosen and open to
 * correction.
 */
@Composable
private fun ProviderFields(
    provider: AiProvider,
    apiKey: String,
    model: String,
    readsImages: Boolean,
    baseUrl: String,
    viewModel: SettingsViewModel
) {
    if (provider == AiProvider.CUSTOM) {
        val (url, onUrlChange) = rememberSettingText(saved = baseUrl, save = viewModel::setCustomBaseUrl)
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text(L10n.str(R.string.custom_ai_base_url)) },
            placeholder = { Text("http://192.168.1.10:11434/v1") },
            supportingText = { Text(L10n.str(R.string.custom_ai_base_url_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
    val texts = textsOf(provider)
    if (provider == AiProvider.OPENROUTER) OpenRouterConnectBlock()
    ApiKeyField(
        value = apiKey,
        onValueChange = { viewModel.setApiKey(provider, it) },
        label = L10n.str(texts.keyLabel),
        placeholder = L10n.str(texts.keyHint)
    )
    if (provider == AiProvider.OPENROUTER) {
        OpenRouterModelField(
            current = model,
            supportsImages = readsImages,
            onSelect = { id, images -> viewModel.setModel(provider, id, images) }
        )
    } else {
        AccountModelField(
            provider = provider,
            apiKey = apiKey,
            baseUrl = baseUrl.takeIf { provider == AiProvider.CUSTOM },
            label = L10n.str(R.string.ai_model),
            current = model,
            customPlaceholder = L10n.str(texts.modelExample),
            onSelect = { viewModel.setModel(provider, it) }
        )
    }
    if (provider.imageSupportVaries()) ReadsImagesSwitch(readsImages) { viewModel.setModelImages(provider, it) }
    texts.note?.let { note ->
        Text(L10n.str(note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    ProviderTestBlock(
        candidate = AiCandidate(provider, apiKey, model, baseUrl.takeIf { provider == AiProvider.CUSTOM }),
        ready = model.isNotBlank() && (if (provider == AiProvider.CUSTOM) baseUrl.isNotBlank() else apiKey.isNotBlank()),
        checkImage = readsImages,
        onImagesOff = if (provider.imageSupportVaries()) ({ viewModel.setModelImages(provider, false) }) else null
    )
}

/**
 * "Test connection": one small request with this key and model (and a photo, if the model is set
 * as reading them), to know right away whether they work. Testing again after a change starts
 * over; the result is forgotten when the key or model changes.
 */
@Composable
private fun ProviderTestBlock(candidate: AiCandidate, ready: Boolean, checkImage: Boolean, onImagesOff: (() -> Unit)?) {
    val scope = rememberCoroutineScope()
    var testing by remember(candidate) { mutableStateOf(false) }
    var result by remember(candidate, checkImage) { mutableStateOf<ProviderTestResult?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(
            onClick = {
                testing = true
                result = null
                scope.launch {
                    result = ProviderTest.run(candidate, checkImage)
                    testing = false
                }
            },
            enabled = ready && !testing
        ) {
            if (testing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(L10n.str(R.string.ai_test_running))
            } else {
                Text(L10n.str(R.string.ai_test_connection))
            }
        }
        result?.let { tested ->
            if (!tested.ok) {
                ErrorMessage(tested.error.orEmpty())
            } else {
                val seconds = "%.1f".format(L10n.locale(), tested.millis / 1000.0)
                Text(
                    L10n.str(R.string.ai_test_ok_x_x, candidate.model, seconds),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                when (tested.readsImage) {
                    true -> Text(L10n.str(R.string.ai_test_reads_photo), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    false -> {
                        Text(L10n.str(R.string.ai_test_photo_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        ErrorMessage(tested.imageError.orEmpty())
                        if (onImagesOff != null) TextButton(onClick = onImagesOff) { Text(L10n.str(R.string.ai_test_turn_photos_off)) }
                    }
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun ReadsImagesSwitch(readsImages: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!readsImages) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(L10n.str(R.string.ai_model_reads_images), style = MaterialTheme.typography.bodyLarge)
            Text(
                L10n.str(R.string.ai_model_reads_images_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = readsImages, onCheckedChange = onChange)
    }
}

@Composable
private fun ProviderRow(
    provider: AiProvider,
    position: Int,
    status: String,
    active: Boolean,
    /** Null when the provider has no key yet, so there is nothing to switch. */
    switchedOn: Boolean?,
    onSwitch: (Boolean) -> Unit,
    expanded: Boolean,
    isDragging: Boolean,
    dragHandle: Modifier,
    onToggle: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    content: @Composable () -> Unit
) {
    val moveUpLabel = L10n.str(R.string.ai_move_up)
    val moveDownLabel = L10n.str(R.string.ai_move_down)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDragging) 0.9f else 0.45f),
        shadowElevation = if (isDragging) 8.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics {
                customActions = listOfNotNull(
                    onMoveUp?.let { action -> CustomAccessibilityAction(moveUpLabel) { action(); true } },
                    onMoveDown?.let { action -> CustomAccessibilityAction(moveDownLabel) { action(); true } }
                )
            }
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = dragHandle.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.DragIndicator,
                        contentDescription = L10n.str(R.string.ai_drag_to_reorder),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        position.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(provider.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (switchedOn != null) {
                    Switch(
                        checked = switchedOn,
                        onCheckedChange = onSwitch,
                        modifier = Modifier.padding(horizontal = 8.dp).semantics { contentDescription = L10n.str(R.string.ai_provider_use_x, provider.label) }
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun ApiKeyField(value: String, onValueChange: (String) -> Unit, label: String, placeholder: String) {
    val (text, onTextChange) = rememberSettingText(saved = value, save = onValueChange)
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
    )
}

/** A model in a picker: its id, whether it reads images and, on OpenRouter, whether it is free. */
private data class ModelOption(val id: String, val readsImages: Boolean, val free: Boolean = false)

/**
 * Model picker of a provider that lists the models of the user's account (see [ProviderModels]).
 * The list always comes from the provider: it is downloaded once a key (or, for a custom server,
 * its address) is entered, and again when the menu is opened after a failed download.
 */
@Composable
private fun AccountModelField(
    provider: AiProvider,
    apiKey: String,
    baseUrl: String? = null,
    label: String,
    current: String,
    customPlaceholder: String,
    onSelect: (String) -> Unit
) {
    var accountModels by remember(provider) { mutableStateOf<List<String>?>(null) }
    var loading by remember(provider) { mutableStateOf(false) }
    var attempt by remember(provider) { mutableIntStateOf(0) }
    // A custom server is asked with its address, with or without a key.
    val ready = if (baseUrl != null) baseUrl.isNotBlank() else apiKey.isNotBlank()
    LaunchedEffect(provider, apiKey, baseUrl, attempt) {
        if (attempt == 0) accountModels = null
        if (!ready) return@LaunchedEffect
        // Wait for the user to finish typing or pasting the key.
        if (attempt == 0) delay(700)
        loading = true
        accountModels = ProviderModels.fetch(provider, apiKey, baseUrl)
        loading = false
    }
    ModelDropdownField(
        label = label,
        options = accountModels.orEmpty().map { ModelOption(it, ProviderModels.readsImages(provider, it)) },
        current = current,
        currentReadsImages = current.isNotBlank() && ProviderModels.readsImages(provider, current),
        customPlaceholder = customPlaceholder,
        supportingText = when {
            !ready -> L10n.str(if (baseUrl != null) R.string.ai_models_need_address else R.string.ai_models_need_key)
            loading -> L10n.str(R.string.ai_models_loading)
            accountModels != null -> L10n.str(R.string.ai_models_from_account_n, accountModels!!.size)
            else -> L10n.str(R.string.ai_models_load_failed)
        },
        // After a failed download, opening the menu tries again.
        onOpen = { if (ready && accountModels == null && !loading) attempt++ },
        onSelect = onSelect
    )
}

/** OpenRouter's model picker over its public catalogue (no key needed): free models first. */
@Composable
private fun OpenRouterModelField(current: String, supportsImages: Boolean, onSelect: (String, Boolean) -> Unit) {
    var catalog by remember { mutableStateOf(OpenRouterModels.cached()) }
    var loading by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        if (catalog != null && attempt == 0) return@LaunchedEffect
        loading = true
        OpenRouterModels.fetch(forceRefresh = attempt > 0)?.let { catalog = it }
        loading = false
    }
    val models = catalog.orEmpty().sortedByDescending { it.isFree }
    ModelDropdownField(
        label = L10n.str(R.string.openrouter_model),
        options = models.map { ModelOption(it.id, it.supportsImages, it.isFree) },
        current = current,
        currentReadsImages = supportsImages,
        currentFree = isFreeModelId(current),
        customPlaceholder = "vendor/model:free",
        supportingText = when {
            loading -> L10n.str(R.string.ai_models_loading)
            catalog != null -> L10n.str(R.string.ai_models_catalog_n, models.size)
            else -> L10n.str(R.string.ai_models_load_failed)
        },
        onOpen = { if (catalog == null && !loading) attempt++ },
        // An id typed by hand is assumed to read images; a failure falls back to the next provider.
        onSelect = { id -> onSelect(id, models.firstOrNull { it.id == id }?.supportsImages ?: true) }
    )
}

/**
 * Dropdown with a provider's models, marking those that read images ("Images") and free ones,
 * with a search box when the list is long and a "Custom" option to type an id by hand. A dropdown
 * rather than a bottom sheet, the same for every provider.
 */
@Composable
private fun ModelDropdownField(
    label: String,
    options: List<ModelOption>,
    current: String,
    currentReadsImages: Boolean,
    customPlaceholder: String,
    onSelect: (String) -> Unit,
    supportingText: String? = null,
    currentFree: Boolean = false,
    onOpen: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // Typing an id by hand: chosen in the menu, or a saved model the provider no longer lists.
    var typing by remember { mutableStateOf(false) }
    val listed = options.any { it.id == current }
    val isCustom = typing || (current.isNotBlank() && options.isNotEmpty() && !listed)
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = if (isCustom) L10n.str(R.string.custom_2) else current,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(L10n.str(R.string.ai_provider_no_model)) },
            supportingText = {
                Text(
                    listOfNotNull(
                        L10n.str(R.string.ai_free).takeIf { current.isNotBlank() && currentFree },
                        if (current.isBlank()) null
                        else L10n.str(if (currentReadsImages) R.string.ai_reads_images else R.string.ai_no_images),
                        supportingText
                    ).joinToString(" · ")
                )
            },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = L10n.str(R.string.open_model_picker)) },
            modifier = Modifier.fillMaxWidth()
        )
        // Transparent layer over the field that opens the menu on tap, so the read-only TextField
        // does not take the tap and show a cursor.
        Box(modifier = Modifier.matchParentSize().clickable { onOpen(); query = ""; menuExpanded = true })
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            // A provider may list dozens of models (OpenRouter, hundreds).
            modifier = Modifier.heightIn(max = 460.dp)
        ) {
            if (options.size > SEARCH_FROM_MODELS) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text(L10n.str(R.string.openrouter_search_models)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            options.filter { query.isBlank() || it.id.contains(query.trim(), ignoreCase = true) }.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.id, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = if (option.free || option.readsImages) {
                        {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (option.free) ModelBadge(L10n.str(R.string.ai_free), highlighted = true)
                                if (option.readsImages) ModelBadge(L10n.str(R.string.ai_images_badge), highlighted = false)
                            }
                        }
                    } else null,
                    onClick = { typing = false; onSelect(option.id); menuExpanded = false }
                )
            }
            DropdownMenuItem(
                text = { Text(L10n.str(R.string.custom)) },
                onClick = { typing = true; menuExpanded = false }
            )
        }
    }
    if (isCustom) {
        val (text, onTextChange) = rememberSettingText(saved = current, save = onSelect)
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            label = { Text(L10n.str(R.string.model_id)) },
            placeholder = { Text(customPlaceholder) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// Above this many models the menu starts with a search box.
private const val SEARCH_FROM_MODELS = 12

@Composable
private fun ModelBadge(text: String, highlighted: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(vertical = 1.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** Free according to the id (":free" suffix) or the already downloaded catalogue. */
internal fun isFreeModelId(id: String): Boolean =
    id.endsWith(":free") || OpenRouterModels.cached()?.firstOrNull { it.id == id }?.isFree == true

/**
 * "Connect with OpenRouter": logs in in the browser and comes back with a key (see
 * OpenRouterConnect), plus pasting the code by hand if Miga does not open by itself.
 */
@Composable
private fun OpenRouterConnectBlock() {
    val context = LocalContext.current
    val status by OpenRouterConnect.status.collectAsStateWithLifecycle()
    var started by rememberSaveable { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(
            onClick = {
                started = true
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, OpenRouterConnect.authorizationUri(context))) }
            },
            enabled = status != OpenRouterConnect.Status.Connecting,
            modifier = Modifier.fillMaxWidth()
        ) { Text(L10n.str(R.string.openrouter_connect)) }
        Text(
            L10n.str(R.string.openrouter_connect_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        when (val current = status) {
            OpenRouterConnect.Status.Connecting -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(L10n.str(R.string.openrouter_connecting), style = MaterialTheme.typography.bodyMedium)
            }
            OpenRouterConnect.Status.Connected ->
                Text(L10n.str(R.string.openrouter_connected), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            is OpenRouterConnect.Status.Failed ->
                Text(current.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            OpenRouterConnect.Status.Idle -> Unit
        }
        if (started && status != OpenRouterConnect.Status.Connected) {
            TextButton(onClick = { pasting = true }) { Text(L10n.str(R.string.openrouter_paste_code)) }
        }
    }
    if (pasting) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pasting = false },
            title = { Text(L10n.str(R.string.openrouter_paste_code)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(L10n.str(R.string.openrouter_paste_code_hint), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text(L10n.str(R.string.openrouter_code)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = code.isNotBlank(), onClick = {
                    pasting = false
                    OpenRouterConnect.connectWithCode(context, code)
                }) { Text(L10n.str(R.string.openrouter_connect)) }
            },
            dismissButton = { TextButton(onClick = { pasting = false }) { Text(L10n.str(R.string.cancel)) } }
        )
    }
}
