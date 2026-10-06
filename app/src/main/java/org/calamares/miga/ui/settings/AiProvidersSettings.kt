package org.calamares.miga.ui.settings

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
import androidx.compose.runtime.collectAsState
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
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.ai.OpenRouterModel
import org.calamares.miga.data.ai.OpenRouterModels
import org.calamares.miga.data.ai.ANTHROPIC_MODELS
import org.calamares.miga.data.ai.GEMINI_MODELS
import org.calamares.miga.data.ai.AiProvider
import org.calamares.miga.ui.components.ReorderableColumn
import org.calamares.miga.ui.components.moved

/**
 * AI providers sorted by priority. Drag the handle to reorder; tap a provider to expand its key and
 * model.
 */
@Composable
fun AiProvidersSettings(viewModel: SettingsViewModel) {
    val order by viewModel.providerOrder.collectAsState()
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()
    val geminiModel by viewModel.geminiModel.collectAsState()
    val anthropicApiKey by viewModel.anthropicApiKey.collectAsState()
    val anthropicModel by viewModel.anthropicModel.collectAsState()
    val openRouterApiKey by viewModel.openRouterApiKey.collectAsState()
    val openRouterModel by viewModel.openRouterModel.collectAsState()
    val openRouterModelImages by viewModel.openRouterModelImages.collectAsState()
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
            val status = when (provider) {
                AiProvider.GEMINI ->
                    if (geminiApiKey.isBlank()) L10n.str(R.string.ai_provider_no_key) else geminiModel
                AiProvider.ANTHROPIC ->
                    if (anthropicApiKey.isBlank()) L10n.str(R.string.ai_provider_no_key) else anthropicModel
                AiProvider.OPENROUTER -> when {
                    openRouterApiKey.isBlank() -> L10n.str(R.string.ai_provider_no_key)
                    openRouterModel.isBlank() -> L10n.str(R.string.ai_provider_no_model)
                    else -> buildList {
                        add(openRouterModel)
                        if (isFreeModelId(openRouterModel)) add(L10n.str(R.string.ai_free))
                        if (!openRouterModelImages) add(L10n.str(R.string.ai_no_images))
                    }.joinToString(" · ")
                }
            }
            val active = when (provider) {
                AiProvider.GEMINI -> geminiApiKey.isNotBlank()
                AiProvider.ANTHROPIC -> anthropicApiKey.isNotBlank()
                AiProvider.OPENROUTER -> openRouterApiKey.isNotBlank() && openRouterModel.isNotBlank()
            }
            ProviderRow(
                provider = provider,
                position = index + 1,
                status = status,
                active = active,
                expanded = expanded == provider,
                isDragging = isDragging,
                dragHandle = dragHandle,
                onToggle = { expanded = if (expanded == provider) null else provider },
                onMoveUp = if (index > 0) ({ viewModel.setProviderOrder(order.moved(index, index - 1)) }) else null,
                onMoveDown = if (index < order.lastIndex) ({ viewModel.setProviderOrder(order.moved(index, index + 1)) }) else null
            ) {
                when (provider) {
                    AiProvider.GEMINI -> {
                        ApiKeyField(
                            value = geminiApiKey,
                            onValueChange = { viewModel.setGeminiApiKey(it) },
                            label = L10n.str(R.string.api_key_gemini),
                            placeholder = L10n.str(R.string.get_one_free_aistudio_google)
                        )
                        ModelDropdownField(
                            label = L10n.str(R.string.gemini_model),
                            models = GEMINI_MODELS,
                            current = geminiModel,
                            customPlaceholder = L10n.str(R.string.e_g_gemini_3_6),
                            onSelect = { viewModel.setGeminiModel(it) }
                        )
                    }
                    AiProvider.ANTHROPIC -> {
                        ApiKeyField(
                            value = anthropicApiKey,
                            onValueChange = { viewModel.setAnthropicApiKey(it) },
                            label = L10n.str(R.string.api_key_anthropic),
                            placeholder = L10n.str(R.string.get_one_console_anthropic_com)
                        )
                        ModelDropdownField(
                            label = L10n.str(R.string.claude_model),
                            models = ANTHROPIC_MODELS,
                            current = anthropicModel,
                            customPlaceholder = L10n.str(R.string.e_g_claude_haiku_4),
                            onSelect = { viewModel.setAnthropicModel(it) }
                        )
                    }
                    AiProvider.OPENROUTER -> {
                        ApiKeyField(
                            value = openRouterApiKey,
                            onValueChange = { viewModel.setOpenRouterApiKey(it) },
                            label = L10n.str(R.string.api_key_openrouter),
                            placeholder = L10n.str(R.string.openrouter_key_hint)
                        )
                        OpenRouterModelField(
                            current = openRouterModel,
                            supportsImages = openRouterModelImages,
                            onSelect = { id, images -> viewModel.setOpenRouterModel(id, images) }
                        )
                        Text(
                            L10n.str(R.string.openrouter_free_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderRow(
    provider: AiProvider,
    position: Int,
    status: String,
    active: Boolean,
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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Dropdown with the known models plus a "Custom" option to type an id by hand. */
@Composable
private fun ModelDropdownField(
    label: String,
    models: List<String>,
    current: String,
    customPlaceholder: String,
    onSelect: (String) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isCustom = current !in models
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = if (isCustom) L10n.str(R.string.custom_2) else current,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = L10n.str(R.string.open_model_picker)) },
            modifier = Modifier.fillMaxWidth()
        )
        // Transparent layer over the field that opens the menu on tap, so the read-only TextField
        // does not take the tap and show a cursor.
        Box(modifier = Modifier.matchParentSize().clickable { menuExpanded = true })
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            models.forEach { modelId ->
                DropdownMenuItem(text = { Text(modelId) }, onClick = { onSelect(modelId); menuExpanded = false })
            }
            DropdownMenuItem(
                text = { Text(L10n.str(R.string.custom)) },
                onClick = { onSelect(""); menuExpanded = false }
            )
        }
    }
    if (isCustom) {
        OutlinedTextField(
            value = current,
            onValueChange = onSelect,
            label = { Text(L10n.str(R.string.model_id)) },
            placeholder = { Text(customPlaceholder) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun OpenRouterModelField(current: String, supportsImages: Boolean, onSelect: (String, Boolean) -> Unit) {
    var showSheet by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = current,
            onValueChange = {},
            readOnly = true,
            label = { Text(L10n.str(R.string.openrouter_model)) },
            placeholder = { Text(L10n.str(R.string.ai_provider_no_model)) },
            supportingText = if (current.isNotBlank()) {
                {
                    Text(
                        listOfNotNull(
                            L10n.str(R.string.ai_free).takeIf { isFreeModelId(current) },
                            if (supportsImages) L10n.str(R.string.ai_reads_images) else L10n.str(R.string.ai_no_images)
                        ).joinToString(" · ")
                    )
                }
            } else null,
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = L10n.str(R.string.open_model_picker)) },
            modifier = Modifier.fillMaxWidth()
        )
        Box(modifier = Modifier.matchParentSize().clickable { showSheet = true })
    }
    if (showSheet) {
        OpenRouterModelSheet(
            current = current,
            onPick = { id, images -> onSelect(id, images); showSheet = false },
            onDismiss = { showSheet = false }
        )
    }
}

private sealed interface CatalogState {
    data object Loading : CatalogState
    data object Error : CatalogState
    data class Loaded(val models: List<OpenRouterModel>) : CatalogState
}

/**
 * OpenRouter model picker over the public catalogue downloaded on the spot: search, "Free" and
 * "Reads images" filters, and an option to type an id by hand.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenRouterModelSheet(current: String, onPick: (id: String, supportsImages: Boolean) -> Unit, onDismiss: () -> Unit) {
    var state by remember { mutableStateOf<CatalogState>(CatalogState.Loading) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var onlyFree by rememberSaveable { mutableStateOf(true) }
    var onlyImages by rememberSaveable { mutableStateOf(false) }
    var customId by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(reloadToken) {
        state = CatalogState.Loading
        state = OpenRouterModels.fetch(forceRefresh = reloadToken > 0)?.let { CatalogState.Loaded(it) } ?: CatalogState.Error
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(L10n.str(R.string.openrouter_models_title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text(L10n.str(R.string.openrouter_search_models)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = onlyFree, onClick = { onlyFree = !onlyFree }, label = { Text(L10n.str(R.string.ai_free)) })
                FilterChip(selected = onlyImages, onClick = { onlyImages = !onlyImages }, label = { Text(L10n.str(R.string.ai_reads_images)) })
            }
        }
        when (val current0 = state) {
            CatalogState.Loading -> Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            CatalogState.Error -> Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(L10n.str(R.string.openrouter_models_error), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { reloadToken++ }) { Text(L10n.str(R.string.retry)) }
            }
            is CatalogState.Loaded -> {
                val filtered = current0.models.filter { model ->
                    (!onlyFree || model.isFree) &&
                        (!onlyImages || model.supportsImages) &&
                        (query.isBlank() || model.name.contains(query, ignoreCase = true) || model.id.contains(query, ignoreCase = true))
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                L10n.str(R.string.openrouter_no_models_match),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    items(filtered, key = { it.id }) { model ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { onPick(model.id, model.supportsImages) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = model.id == current, onClick = { onPick(model.id, model.supportsImages) })
                            Column(modifier = Modifier.weight(1f)) {
                                Text(model.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    model.id,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp, end = 8.dp)) {
                                if (model.isFree) ModelBadge(L10n.str(R.string.ai_free), highlighted = true)
                                if (model.supportsImages) ModelBadge(L10n.str(R.string.ai_images_badge), highlighted = false)
                            }
                        }
                    }
                }
            }
        }
        // Id typed by hand (new models or ones missing from the catalogue).
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = customId,
                onValueChange = { customId = it },
                label = { Text(L10n.str(R.string.model_id)) },
                placeholder = { Text("vendor/model:free") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { onPick(customId.trim(), true) },
                enabled = customId.isNotBlank(),
                modifier = Modifier.padding(start = 8.dp)
            ) { Text(L10n.str(R.string.use_model)) }
        }
    }
}

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
