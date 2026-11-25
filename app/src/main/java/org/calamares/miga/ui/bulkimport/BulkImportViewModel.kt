package org.calamares.miga.ui.bulkimport

import androidx.core.net.toUri
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.ai.AiImage
import org.calamares.miga.data.ai.AiKeepAlive
import org.calamares.miga.data.ai.AiProgress
import org.calamares.miga.data.ai.AiProgressReporter
import org.calamares.miga.data.ai.KnownLabels
import org.calamares.miga.data.ai.aiCandidates
import org.calamares.miga.data.ai.runAi
import org.calamares.miga.data.local.PhotoStorage
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.model.RecipePhoto
import org.calamares.miga.data.repository.RecipeRepository
import org.calamares.miga.data.vision.ImageGroupingResult
import org.calamares.miga.data.vision.RecipeExtractionResult
import org.calamares.miga.data.vision.extractRecipes
import org.calamares.miga.data.vision.findDishPhotos
import org.calamares.miga.data.vision.groupRecipeImages
import org.calamares.miga.data.vision.matchedTo
import org.calamares.miga.data.vision.mergedIntoOne
import org.calamares.miga.data.vision.newLabels
import org.calamares.miga.data.vision.toRecipeDraft

/** Size of the copies sent to sort the photos into recipes: enough to read titles, cheap to send. */
private const val GROUPING_DIMENSION = 1024

sealed interface BulkImportRowState {
    data object Pending : BulkImportRowState
    data object Processing : BulkImportRowState
    /** [note] says which categories or equipment had to be created because none fitted. */
    data class Success(val recipeId: Long, val name: String, val note: String? = null) : BulkImportRowState
    data class Failed(val reason: String) : BulkImportRowState
}

/** The photos of one recipe (its pages, and maybe a photo of the dish) and how its import is going. */
data class BulkImportRow(val photoUris: List<String>, val state: BulkImportRowState)

/** What the import is doing: sorting the photos into recipes, stopped because that failed, or importing them. */
sealed interface BulkImportPhase {
    data object Grouping : BulkImportPhase
    data class GroupingFailed(val reason: String) : BulkImportPhase
    data object Importing : BulkImportPhase
}

/**
 * Imports recipes from several photos. The photos are first sorted into recipes by the AI (two
 * pages of one recipe go together, photos of different recipes apart). When they all turn out to
 * be one recipe, [singleRecipe] hands them to the editor, where the result is reviewed before
 * saving; otherwise each recipe is transcribed and saved in turn.
 */
class BulkImportViewModel(
    private val repository: RecipeRepository,
    private val settingsRepository: SettingsRepository,
    private val bookId: Long,
    private val photoUris: List<String>
) : ViewModel() {

    /** What the photo being processed is going through right now. */
    private val _aiProgress = MutableStateFlow<AiProgress?>(null)
    val aiProgress: StateFlow<AiProgress?> = _aiProgress

    private val _phase = MutableStateFlow<BulkImportPhase>(BulkImportPhase.Grouping)
    val phase: StateFlow<BulkImportPhase> = _phase

    private val _rows = MutableStateFlow<List<BulkImportRow>>(emptyList())
    val rows: StateFlow<List<BulkImportRow>> = _rows

    /** Set when every photo belongs to the same recipe: the photos to open in the editor. */
    private val _singleRecipe = MutableStateFlow<List<String>?>(null)
    val singleRecipe: StateFlow<List<String>?> = _singleRecipe

    private var started = false

    /** The user's categories and equipment, offered to the AI and used to file the results. */
    private var known = KnownLabels.NONE

    /** Sorts the photos into recipes and imports them one at a time. Call it once, from the screen. */
    fun start(context: Context) {
        if (started) return
        started = true
        sortAndImport(context.applicationContext)
    }

    /** Tries again to sort the photos into recipes after that failed. */
    fun retryGrouping(context: Context) {
        if (_phase.value is BulkImportPhase.GroupingFailed) sortAndImport(context.applicationContext)
    }

    /** Skips the sorting after it failed: every photo is imported as a recipe of its own. */
    fun importOnePerPhoto(context: Context) {
        if (_phase.value !is BulkImportPhase.GroupingFailed) return
        viewModelScope.launch { importGroups(context.applicationContext, photoUris.indices.map { listOf(it) }) }
    }

    private fun sortAndImport(context: Context) {
        viewModelScope.launch {
            _phase.value = BulkImportPhase.Grouping
            if (settingsRepository.aiCandidates().isEmpty()) {
                _phase.value = BulkImportPhase.GroupingFailed(L10n.str(R.string.ai_no_provider))
                return@launch
            }
            when (val grouping = groupPhotos(context)) {
                is ImageGroupingResult.Error -> _phase.value = BulkImportPhase.GroupingFailed(grouping.reason)
                is ImageGroupingResult.Success ->
                    if (grouping.groups.size == 1) _singleRecipe.value = grouping.groups.single().map { photoUris[it] }
                    else importGroups(context, grouping.groups)
            }
        }
    }

    /** Asks the AI which photos belong to the same recipe; a single photo needs no question. */
    private suspend fun groupPhotos(context: Context): ImageGroupingResult {
        if (photoUris.size == 1) return ImageGroupingResult.Success(listOf(listOf(0)))
        return AiKeepAlive.hold(L10n.str(R.string.ai_task_bulk_import)) {
            val progress = AiProgressReporter { current ->
                _aiProgress.value = current
                status(current.detail ?: current.step)
            }
            try {
                progress.step(L10n.plural(R.plurals.ai_step_preparing_photos_n, photoUris.size))
                // A photo that cannot be read is left out of the question and becomes a recipe of
                // its own, where its row says so.
                val readable = photoUris.indices.mapNotNull { index ->
                    PhotoStorage.readResizedJpegBytes(context, photoUris[index].toUri(), GROUPING_DIMENSION)?.let { index to AiImage(it, "image/jpeg") }
                }
                if (readable.isEmpty()) return@hold ImageGroupingResult.Error(L10n.str(R.string.couldnt_read_photos))
                progress.step(L10n.str(R.string.ai_step_sorting_photos))
                val result = settingsRepository.runAi<ImageGroupingResult>(
                    needsImages = true,
                    errorOf = { (it as? ImageGroupingResult.Error)?.reason },
                    error = { ImageGroupingResult.Error(it) },
                    progress = progress
                ) { ai -> ai.groupRecipeImages(readable.map { it.second }) } ?: ImageGroupingResult.Error(L10n.str(R.string.ai_no_provider))
                when (result) {
                    is ImageGroupingResult.Error -> result
                    is ImageGroupingResult.Success -> {
                        // Back from positions in the request to positions in the photos picked.
                        val groups = result.groups.map { group -> group.map { readable[it].first } }
                        val unreadable = photoUris.indices.filter { index -> readable.none { it.first == index } }.map { listOf(it) }
                        ImageGroupingResult.Success((groups + unreadable).sortedBy { it.min() })
                    }
                }
            } finally {
                _aiProgress.value = null
            }
        }
    }

    private suspend fun importGroups(context: Context, groups: List<List<Int>>) {
        _rows.value = groups.map { group -> BulkImportRow(group.map { photoUris[it] }, BulkImportRowState.Pending) }
        _phase.value = BulkImportPhase.Importing
        known = KnownLabels(
            categories = repository.observeCategories().first().map { it.name },
            equipment = repository.observeUtensils().first().map { it.name }
        )
        // One foreground service for the whole batch, so it is not restarted between recipes
        // while the app is in the background (which Android does not allow).
        AiKeepAlive.hold(L10n.str(R.string.ai_task_bulk_import)) {
            groups.indices.forEach { index ->
                progress(index + 1, groups.size)
                processOne(context, index)
            }
        }
        val imported = _rows.value.count { it.state is BulkImportRowState.Success }
        AiKeepAlive.announceIfInBackground(L10n.plural(R.plurals.ai_bulk_import_done_x_of_y, groups.size, imported, groups.size))
    }

    /** Retries a single failed recipe without touching the other rows. */
    fun retry(context: Context, index: Int) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            AiKeepAlive.hold(L10n.str(R.string.ai_task_bulk_import)) { processOne(appContext, index) }
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
        val row = _rows.value.getOrNull(index) ?: return
        updateRow(index) { it.copy(state = BulkImportRowState.Processing) }
        progress.step(L10n.plural(R.plurals.ai_step_preparing_photos_n, row.photoUris.size))
        // Pages that cannot be read are skipped; the uri of each image sent is kept because the
        // AI's "dishPhotos" indices refer to the images sent.
        val readable = row.photoUris.map { it.toUri() }.mapNotNull { uri ->
            PhotoStorage.readResizedJpegBytes(context, uri)?.let { uri to AiImage(it, "image/jpeg") }
        }
        if (readable.isEmpty()) {
            updateRow(index) { it.copy(state = BulkImportRowState.Failed(L10n.str(R.string.couldnt_read_photo))) }
            return
        }
        val sources = readable.map { it.first }
        val images = readable.map { it.second }
        val result = progress.doing({ L10n.str(R.string.ai_doing_reading_photos, it) }) {
            settingsRepository.runAi<RecipeExtractionResult>(
                needsImages = true,
                errorOf = { (it as? RecipeExtractionResult.Error)?.reason },
                error = { RecipeExtractionResult.Error(it) },
                progress = progress
            ) { ai -> ai.extractRecipes(images, known) }
        } ?: RecipeExtractionResult.Error(L10n.str(R.string.ai_no_provider))
        when (result) {
            is RecipeExtractionResult.Success -> {
                // No one to ask in the middle of a batch: the AI's suggestion decides whether several
                // recipes on the same photos become one recipe or one each.
                val recipes = (if (result.together) listOf(result.recipes.mergedIntoOne()) else result.recipes).map { it.matchedTo(known) }
                val created = mutableListOf<String>()
                val saved = recipes.map { recipe ->
                    val dishPhotoUris = settingsRepository.findDishPhotos(context, sources, recipe, progress)
                    progress.step(L10n.str(R.string.ai_step_saving_recipe))
                    val draft = recipe.toRecipeDraft(bookId)
                        .copy(photos = dishPhotoUris.mapIndexed { i, photoUri -> RecipePhoto(photoUri, isCover = i == 0) })
                    val toCreate = newLabels(draft.categoryName, draft.utensilNames, known)
                    toCreate.category?.let { created += L10n.str(R.string.new_labels_category_x, it) }
                    if (toCreate.equipment.isNotEmpty()) created += L10n.str(R.string.new_labels_equipment_x, toCreate.equipment.joinToString(", "))
                    val id = repository.saveRecipe(draft)
                    // What this recipe created is known to the next ones, so they reuse it.
                    known = KnownLabels(
                        categories = known.categories + listOfNotNull(toCreate.category),
                        equipment = known.equipment + toCreate.equipment
                    )
                    id to draft.name
                }
                updateRow(index) {
                    it.copy(
                        state = BulkImportRowState.Success(
                            recipeId = saved.first().first,
                            name = saved.joinToString(" · ") { entry -> entry.second },
                            note = created.distinct().joinToString(" · ").ifEmpty { null }
                        )
                    )
                }
            }
            is RecipeExtractionResult.Error -> updateRow(index) { it.copy(state = BulkImportRowState.Failed(result.reason)) }
        }
    }

    private fun updateRow(index: Int, transform: (BulkImportRow) -> BulkImportRow) {
        _rows.update { list -> list.toMutableList().also { it[index] = transform(it[index]) } }
    }
}
