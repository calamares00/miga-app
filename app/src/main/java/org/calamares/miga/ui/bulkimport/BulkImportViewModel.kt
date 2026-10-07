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
import org.calamares.miga.data.vision.RecipeExtractionResult
import org.calamares.miga.data.ai.AiImage
import org.calamares.miga.data.ai.AiKeepAlive
import org.calamares.miga.data.ai.AiProgress
import org.calamares.miga.data.ai.AiProgressReporter
import org.calamares.miga.data.ai.AiProvider
import org.calamares.miga.data.vision.toRecipeDraft
import org.calamares.miga.data.vision.extractRecipes
import org.calamares.miga.data.vision.mergedIntoOne
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

    /** What the photo being processed is going through right now. */
    private val _aiProgress = MutableStateFlow<AiProgress?>(null)
    val aiProgress: StateFlow<AiProgress?> = _aiProgress

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
            // One foreground service for the whole batch, so it is not restarted between photos
            // while the app is in the background (which Android does not allow).
            AiKeepAlive.hold(L10n.str(R.string.ai_task_bulk_import)) {
                photoUris.indices.forEach { index ->
                    progress(index + 1, photoUris.size)
                    processOne(context, index)
                }
            }
            val imported = _rows.value.count { it.state is BulkImportRowState.Success }
            AiKeepAlive.announceIfInBackground(L10n.str(R.string.ai_bulk_import_done_x_of_y, imported, photoUris.size))
        }
    }

    /** Retries a single failed photo without touching the other rows. */
    fun retry(context: Context, index: Int) {
        viewModelScope.launch {
            AiKeepAlive.hold(L10n.str(R.string.ai_task_bulk_import)) { processOne(context, index) }
        }
    }

    private suspend fun processOne(context: Context, index: Int) = AiKeepAlive.hold {
        val progress = AiProgressReporter { current ->
            _aiProgress.value = current
            status(current.detail ?: current.step)
        }
        try {
            processOne(context, index, progress)
        } finally {
            _aiProgress.value = null
        }
    }

    private suspend fun processOne(context: Context, index: Int, progress: AiProgressReporter) {
        updateRow(index) { it.copy(state = BulkImportRowState.Processing) }
        progress.step(L10n.str(R.string.ai_step_preparing_photos_n, 1))
        val uri = Uri.parse(photoUris[index])
        val bytes = PhotoStorage.readResizedJpegBytes(context, uri)
        if (bytes == null) {
            updateRow(index) { it.copy(state = BulkImportRowState.Failed(L10n.str(R.string.couldnt_read_photo))) }
            return
        }
        val images = listOf(AiImage(bytes, "image/jpeg"))
        val hints = listOf(
            L10n.str(R.string.ai_hint_reading_text),
            L10n.str(R.string.ai_hint_ingredients),
            L10n.str(R.string.ai_hint_steps),
            L10n.str(R.string.ai_hint_dish_photo)
        )
        val result = progress.withHints(hints) {
            settingsRepository.runAi<RecipeExtractionResult>(
                needsImages = true,
                errorOf = { (it as? RecipeExtractionResult.Error)?.reason },
                error = { RecipeExtractionResult.Error(it) },
                progress = progress
            ) { ai -> ai.extractRecipes(images) }
        } ?: RecipeExtractionResult.Error(L10n.str(R.string.ai_no_provider))
        when (result) {
            is RecipeExtractionResult.Success -> {
                // No one to ask in the middle of a batch: the AI's suggestion decides whether several
                // recipes on one photo become one recipe or one each.
                val recipes = if (result.together) listOf(result.recipes.mergedIntoOne()) else result.recipes
                val saved = recipes.map { recipe ->
                    if (recipe.dishPhotos.isNotEmpty()) progress.step(L10n.str(R.string.ai_step_cropping_photo))
                    val dishPhotoUris = withContext(Dispatchers.IO) {
                        DishPhotoCropper.extract(context.applicationContext, listOf(uri), recipe.dishPhotos)
                    }
                    progress.step(L10n.str(R.string.ai_step_saving_recipe))
                    val draft = recipe.toRecipeDraft(bookId)
                        .copy(photos = dishPhotoUris.mapIndexed { i, photoUri -> RecipePhoto(photoUri, isCover = i == 0) })
                    repository.saveRecipe(draft) to draft.name
                }
                updateRow(index) { it.copy(state = BulkImportRowState.Success(saved.first().first, saved.joinToString(" · ") { it.second })) }
            }
            is RecipeExtractionResult.Error -> updateRow(index) { it.copy(state = BulkImportRowState.Failed(result.reason)) }
        }
    }

    private fun updateRow(index: Int, transform: (BulkImportRow) -> BulkImportRow) {
        _rows.update { list -> list.toMutableList().also { it[index] = transform(it[index]) } }
    }
}
