package org.calamares.miga.ui.bulkimport

import org.calamares.miga.L10n
import org.calamares.miga.R
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.calamares.miga.data.local.PhotoStorage
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.repository.RecipeRepository
import org.calamares.miga.data.vision.RecipeVisionResult
import org.calamares.miga.data.vision.VisionImageInput
import org.calamares.miga.data.vision.VisionProviderType
import org.calamares.miga.data.vision.toRecipeDraft
import org.calamares.miga.data.vision.visionClientFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface BulkImportRowState {
    data object Pending : BulkImportRowState
    data object Processing : BulkImportRowState
    data class Success(val recipeId: Long, val name: String) : BulkImportRowState
    data class Failed(val reason: String) : BulkImportRowState
}

data class BulkImportRow(val photoUri: String, val state: BulkImportRowState)

class BulkImportViewModel(
    private val repository: RecipeRepository,
    private val settingsRepository: SettingsRepository,
    private val bookId: Long,
    private val photoUris: List<String>
) : ViewModel() {

    private val _rows = MutableStateFlow(photoUris.map { BulkImportRow(it, BulkImportRowState.Pending) })
    val rows: StateFlow<List<BulkImportRow>> = _rows

    private var started = false

    /** Procesa todas las fotos en orden, una por una. Llamar una sola vez, desde la pantalla. */
    fun start(context: Context) {
        if (started) return
        started = true
        viewModelScope.launch {
            val provider = settingsRepository.observeVisionProvider().first()
            val apiKey = settingsRepository.apiKeyFor(provider)
            if (apiKey.isBlank()) {
                _rows.update { rows -> rows.map { it.copy(state = BulkImportRowState.Failed(L10n.str(R.string.configura_api_key_x_ajustes, provider.label))) } }
                return@launch
            }
            val model = settingsRepository.modelFor(provider)
            photoUris.indices.forEach { index -> processOne(context, index, apiKey, provider, model) }
        }
    }

    /** Reintenta una única foto que falló, sin tocar las demás filas. */
    fun retry(context: Context, index: Int) {
        viewModelScope.launch {
            val provider = settingsRepository.observeVisionProvider().first()
            val apiKey = settingsRepository.apiKeyFor(provider)
            if (apiKey.isBlank()) {
                updateRow(index) { it.copy(state = BulkImportRowState.Failed(L10n.str(R.string.configura_api_key_x_ajustes, provider.label))) }
                return@launch
            }
            val model = settingsRepository.modelFor(provider)
            processOne(context, index, apiKey, provider, model)
        }
    }

    private suspend fun processOne(context: Context, index: Int, apiKey: String, provider: VisionProviderType, model: String) {
        updateRow(index) { it.copy(state = BulkImportRowState.Processing) }
        val uri = Uri.parse(photoUris[index])
        val bytes = PhotoStorage.readResizedJpegBytes(context, uri)
        if (bytes == null) {
            updateRow(index) { it.copy(state = BulkImportRowState.Failed(L10n.str(R.string.no_pudo_leer_foto))) }
            return
        }
        when (val result = visionClientFor(provider).extractRecipe(listOf(VisionImageInput(bytes, "image/jpeg")), apiKey, model)) {
            is RecipeVisionResult.Success -> {
                val draft = result.recipe.toRecipeDraft(bookId)
                val id = repository.saveRecipe(draft)
                updateRow(index) { it.copy(state = BulkImportRowState.Success(id, draft.name)) }
            }
            is RecipeVisionResult.Error -> updateRow(index) { it.copy(state = BulkImportRowState.Failed(result.reason)) }
        }
    }

    private fun updateRow(index: Int, transform: (BulkImportRow) -> BulkImportRow) {
        _rows.update { list -> list.toMutableList().also { it[index] = transform(it[index]) } }
    }
}
