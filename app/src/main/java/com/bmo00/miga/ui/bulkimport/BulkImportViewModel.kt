package com.bmo00.miga.ui.bulkimport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmo00.miga.data.local.PhotoStorage
import com.bmo00.miga.data.local.SettingsRepository
import com.bmo00.miga.data.repository.RecipeRepository
import com.bmo00.miga.data.vision.RecipeVisionResult
import com.bmo00.miga.data.vision.VisionImageInput
import com.bmo00.miga.data.vision.VisionProviderType
import com.bmo00.miga.data.vision.toRecipeDraft
import com.bmo00.miga.data.vision.visionClientFor
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
            val apiKey = settingsRepository.observeGeminiApiKey().first()
            if (apiKey.isBlank()) {
                _rows.update { rows -> rows.map { it.copy(state = BulkImportRowState.Failed("Configura una API key de Gemini en Ajustes")) } }
                return@launch
            }
            val provider = settingsRepository.observeVisionProvider().first()
            val model = settingsRepository.observeGeminiModel().first()
            photoUris.indices.forEach { index -> processOne(context, index, apiKey, provider, model) }
        }
    }

    /** Reintenta una única foto que falló, sin tocar las demás filas. */
    fun retry(context: Context, index: Int) {
        viewModelScope.launch {
            val apiKey = settingsRepository.observeGeminiApiKey().first()
            if (apiKey.isBlank()) {
                updateRow(index) { it.copy(state = BulkImportRowState.Failed("Configura una API key de Gemini en Ajustes")) }
                return@launch
            }
            val provider = settingsRepository.observeVisionProvider().first()
            val model = settingsRepository.observeGeminiModel().first()
            processOne(context, index, apiKey, provider, model)
        }
    }

    private suspend fun processOne(context: Context, index: Int, apiKey: String, provider: VisionProviderType, model: String) {
        updateRow(index) { it.copy(state = BulkImportRowState.Processing) }
        val uri = Uri.parse(photoUris[index])
        val bytes = PhotoStorage.readResizedJpegBytes(context, uri)
        if (bytes == null) {
            updateRow(index) { it.copy(state = BulkImportRowState.Failed("No se pudo leer la foto")) }
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
