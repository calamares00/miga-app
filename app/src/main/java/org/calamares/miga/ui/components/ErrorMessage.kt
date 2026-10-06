package org.calamares.miga.ui.components

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.material3.Button
import org.calamares.miga.data.ai.OpenRouterModels
import kotlinx.coroutines.flow.first

import org.calamares.miga.data.vision.VisionProviderType
import org.calamares.miga.data.vision.GEMINI_MODELS
import org.calamares.miga.data.vision.DEFAULT_GEMINI_MODEL
import org.calamares.miga.data.vision.DEFAULT_ANTHROPIC_MODEL
import org.calamares.miga.data.vision.ANTHROPIC_MODELS
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
 * Error en dos niveles: el resumen para el usuario y, si lo hay, un "Ver detalle" con la parte
 * técnica (respuesta del modelo, código HTTP...) que se puede copiar para reportarla.
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
            // Errores de IA (cuota, modelo caído, respuesta rara...): ofrecer probar con otro modelo.
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
 * Hoja para cambiar de modelo tras un error de IA y reintentar: muestra cada proveedor con clave
 * (en su orden de prioridad) y deja elegir un modelo en cada uno. Al pulsar "Reintentar" se
 * guardan las elecciones como predeterminadas y se repite la operación.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiModelPickerSheet(onPicked: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { (context.applicationContext as MigaApp).settingsRepository }
    val scope = rememberCoroutineScope()
    val order by settings.observeProviderOrder().collectAsState(initial = VisionProviderType.entries.toList())
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
    // Modelo elegido en esta hoja para cada proveedor (sin entrada = se queda el guardado).
    val selection = remember { mutableStateMapOf<VisionProviderType, String>() }

    fun savedModel(provider: VisionProviderType): String = when (provider) {
        VisionProviderType.GEMINI -> geminiModel
        VisionProviderType.ANTHROPIC -> anthropicModel
        VisionProviderType.OPENROUTER -> openRouterModel
    }

    fun hasKey(provider: VisionProviderType): Boolean = when (provider) {
        VisionProviderType.GEMINI -> geminiKey.isNotBlank()
        VisionProviderType.ANTHROPIC -> anthropicKey.isNotBlank()
        VisionProviderType.OPENROUTER -> openRouterKey.isNotBlank()
    }

    fun modelsFor(provider: VisionProviderType): List<String> {
        val saved = listOfNotNull(savedModel(provider).takeIf { it.isNotBlank() })
        return when (provider) {
            VisionProviderType.GEMINI -> (saved + GEMINI_MODELS).distinct()
            VisionProviderType.ANTHROPIC -> (saved + ANTHROPIC_MODELS).distinct()
            // Los gratuitos del catálogo (los primeros) más el elegido; el catálogo completo, en Ajustes.
            VisionProviderType.OPENROUTER -> (saved + openRouterCatalog.filter { it.isFree }.take(15).map { it.id }).distinct()
        }
    }

    fun saveAndRetry() {
        scope.launch {
            for ((provider, model) in selection.toMap()) {
                when (provider) {
                    VisionProviderType.GEMINI -> settings.setGeminiModel(model)
                    VisionProviderType.ANTHROPIC -> settings.setAnthropicModel(model)
                    VisionProviderType.OPENROUTER -> settings.setOpenRouterModel(
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
                        if (groupProvider == VisionProviderType.OPENROUTER && openRouterCatalog.any { it.id == model && it.isFree }) {
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
