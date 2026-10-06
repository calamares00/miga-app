package org.calamares.miga.ui.bulkimport

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.calamares.miga.data.model.RecipePhoto
import org.calamares.miga.data.local.DishPhotoCropper
import org.calamares.miga.data.ai.aiCandidates
import org.calamares.miga.data.ai.runAi
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
import org.calamares.miga.data.ai.AiImage
import org.calamares.miga.data.ai.AiProvider
import org.calamares.miga.data.vision.toRecipeDraft
import org.calamares.miga.data.vision.extractRecipe
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

    /** Processes every photo in order, one at a time. Call it once, from the screen. */
    fun start(context: Context) {
        if (started) return
        started = true
        viewModelScope.launch {
            if (settingsRepository.aiCandidates().isEmpty()) {
                _rows.update { rows -> rows.map { it.copy(state = BulkImportRowState.Failed(L10n.str(R.string.ai_no_provider))) } }
                return@launch
            }
            photoUris.indices.forEach { index -> processOne(context, index) }
        }
    }

    /** Retries a single failed photo without touching the other rows. */
    fun retry(context: Context, index: Int) {
        viewModelScope.launch {
            processOne(context, index)
        }
    }

    private suspend fun processOne(context: Context, index: Int) {
        updateRow(index) { it.copy(state = BulkImportRowState.Processing) }
        val uri = Uri.parse(photoUris[index])
        val bytes = PhotoStorage.readResizedJpegBytes(context, uri)
        if (bytes == null) {
            updateRow(index) { it.copy(state = BulkImportRowState.Failed(L10n.str(R.string.couldnt_read_photo))) }
            return
        }
        val images = listOf(AiImage(bytes, "image/jpeg"))
        val result = settingsRepository.runAi<RecipeVisionResult>(
            needsImages = true,
            errorOf = { (it as? RecipeVisionResult.Error)?.reason },
            error = { RecipeVisionResult.Error(it) }
        ) { ai -> ai.extractRecipe(images) }
            ?: RecipeVisionResult.Error(L10n.str(R.string.ai_no_provider))
        when (result) {
            is RecipeVisionResult.Success -> {
                val dishPhotoUris = withContext(Dispatchers.IO) {
                    DishPhotoCropper.extract(context.applicationContext, listOf(uri), result.recipe.dishPhotos)
                }
                val draft = result.recipe.toRecipeDraft(bookId)
                    .copy(photos = dishPhotoUris.mapIndexed { i, photoUri -> RecipePhoto(photoUri, isCover = i == 0) })
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
