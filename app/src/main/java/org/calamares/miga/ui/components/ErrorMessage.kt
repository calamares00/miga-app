package org.calamares.miga.ui.components

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.material3.Button
import org.calamares.miga.data.ai.OpenRouterModels
import kotlinx.coroutines.flow.first

import org.calamares.miga.data.ai.AiProvider
import org.calamares.miga.data.ai.GEMINI_MODELS
import org.calamares.miga.data.ai.DEFAULT_GEMINI_MODEL
import org.calamares.miga.data.ai.DEFAULT_ANTHROPIC_MODEL
import org.calamares.miga.data.ai.ANTHROPIC_MODELS
import org.calamares.miga.MigaApp
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.support.ErrorDetail

/**
 * Two-level error: a summary for the user and, when present, a "Show details" section with the
 * technical part (model answer, HTTP code...) that can be copied to report it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ErrorMessage(reason: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    var showDetail by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    val detail = ErrorDetail.detail(reason)
    Column(modifier = modifier) {
        Text(ErrorDetail.summary(reason), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        FlowRow {
            if (onRetry != null) TextButton(onClick = onRetry) { Text(L10n.str(R.string.retry)) }
            // AI errors (quota, model down, odd answer...): offer to try another model.
            if (onRetry != null && ErrorDetail.isAiError(reason)) {
                TextButton(onClick = { showModelPicker = true }) { Text(L10n.str(R.string.change_model)) }
            }
            if (detail != null) TextButton(onClick = { showDetail = true }) { Text(L10n.str(R.string.view_details)) }
        }
    }
    if (showDetail && detail != null) ErrorDetailDialog(detail = detail, onDismiss = { showDetail = false })
    if (showModelPicker && onRetry != null) {
        AiModelPickerSheet(onPicked = { showModelPicker = false; onRetry() }, onDismiss = { showModelPicker = false })
    }
}

/**
 * Sheet to switch models after an AI error and retry. It lists every provider with a key, in
 * priority order, and lets the user pick a model for each. "Retry" saves the choices as defaults
 * and repeats the operation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiModelPickerSheet(onPicked: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { (context.applicationContext as MigaApp).settingsRepository }
    val scope = rememberCoroutineScope()
    val order by settings.observeProviderOrder().collectAsState(initial = AiProvider.entries.toList())
    val geminiKey by settings.observeGeminiApiKey().collectAsState(initial = "")
    val anthropicKey by settings.observeAnthropicApiKey().collectAsState(initial = "")
    val openRouterKey by settings.observeOpenRouterApiKey().collectAsState(initial = "")
    val geminiModel by settings.observeGeminiModel().collectAsState(initial = DEFAULT_GEMINI_MODEL)
    val anthropicModel by settings.observeAnthropicModel().collectAsState(initial = DEFAULT_ANTHROPIC_MODEL)
    val openRouterModel by settings.observeOpenRouterModel().collectAsState(initial = "")
    var openRouterCatalog by remember { mutableStateOf(OpenRouterModels.cached().orEmpty()) }
    LaunchedEffect(openRouterKey) {
        if (openRouterKey.isNotBlank()) OpenRouterModels.fetch()?.let { openRouterCatalog = it }
    }
    /** Model chosen in this sheet for each provider (no entry keeps the saved one). */
    val selection = remember { mutableStateMapOf<AiProvider, String>() }

    fun savedModel(provider: AiProvider): String = when (provider) {
        AiProvider.GEMINI -> geminiModel
        AiProvider.ANTHROPIC -> anthropicModel
        AiProvider.OPENROUTER -> openRouterModel
    }

    fun hasKey(provider: AiProvider): Boolean = when (provider) {
        AiProvider.GEMINI -> geminiKey.isNotBlank()
        AiProvider.ANTHROPIC -> anthropicKey.isNotBlank()
        AiProvider.OPENROUTER -> openRouterKey.isNotBlank()
    }

    fun modelsFor(provider: AiProvider): List<String> {
        val saved = listOfNotNull(savedModel(provider).takeIf { it.isNotBlank() })
        return when (provider) {
            AiProvider.GEMINI -> (saved + GEMINI_MODELS).distinct()
            AiProvider.ANTHROPIC -> (saved + ANTHROPIC_MODELS).distinct()
            // The first free models of the catalogue plus the chosen one; the full catalogue is in
            // Settings.
            AiProvider.OPENROUTER -> (saved + openRouterCatalog.filter { it.isFree }.take(15).map { it.id }).distinct()
        }
    }

    fun saveAndRetry() {
        scope.launch {
            for ((provider, model) in selection.toMap()) {
                when (provider) {
                    AiProvider.GEMINI -> settings.setGeminiModel(model)
                    AiProvider.ANTHROPIC -> settings.setAnthropicModel(model)
                    AiProvider.OPENROUTER -> settings.setOpenRouterModel(
                        model,
                        openRouterCatalog.firstOrNull { it.id == model }?.supportsImages ?: settings.observeOpenRouterModelImages().first()
                    )
                }
            }
            onPicked()
        }
    }

    val providers = order.filter { hasKey(it) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            item {
                Text(L10n.str(R.string.change_model_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    L10n.str(R.string.change_model_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
            }
            if (providers.isEmpty()) {
                item {
                    Text(L10n.str(R.string.no_ai_keys), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
            providers.forEachIndexed { index, groupProvider ->
                item(key = "header-${groupProvider.name}") {
                    Text(
                        "${index + 1}. ${groupProvider.label}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                items(modelsFor(groupProvider), key = { "${groupProvider.name}-$it" }) { model ->
                    val selected = (selection[groupProvider] ?: savedModel(groupProvider)) == model
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { selection[groupProvider] = model }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = { selection[groupProvider] = model })
                        Text(model, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (groupProvider == AiProvider.OPENROUTER && openRouterCatalog.any { it.id == model && it.isFree }) {
                            Text(
                                L10n.str(R.string.ai_free),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        if (model == savedModel(groupProvider)) {
                            Text(
                                L10n.str(R.string.current_model),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }
            }
            if (providers.isNotEmpty()) {
                item {
                    Button(onClick = { saveAndRetry() }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Text(L10n.str(R.string.retry_with_these_models))
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorDetailDialog(detail: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.str(R.string.error_details)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                SelectionContainer { Text(detail, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(detail)) }) { Text(L10n.str(R.string.copy)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.str(R.string.close)) } }
    )
}
