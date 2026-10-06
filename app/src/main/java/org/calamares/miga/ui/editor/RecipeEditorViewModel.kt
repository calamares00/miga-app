package org.calamares.miga.ui.editor

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.calamares.miga.data.local.DishPhotoCropper
import org.calamares.miga.data.ai.aiCandidates
import org.calamares.miga.data.ai.AiKeepAlive
import org.calamares.miga.data.ai.runAi
import org.calamares.miga.L10n
import org.calamares.miga.R
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.calamares.miga.data.dictation.DictationCleanupResult
import org.calamares.miga.data.dictation.cleanUpDictation
import org.calamares.miga.data.local.PhotoStorage
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.model.Difficulty
import org.calamares.miga.data.model.RecipeDraft
import org.calamares.miga.data.repository.RecipeRepository
import org.calamares.miga.data.search.DishSuggestion
import org.calamares.miga.data.search.RecipeUrlFetcher
import org.calamares.miga.data.search.UrlFetchResult
import org.calamares.miga.data.search.generateRecipe
import org.calamares.miga.data.search.importRecipeFromPage
import org.calamares.miga.data.vision.RecipeVisionResult
import org.calamares.miga.data.vision.RecipeVisionResultDto
import org.calamares.miga.data.ai.AiImage
import org.calamares.miga.data.vision.extractRecipe
import org.calamares.miga.ui.navigation.Destinations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface VisionState {
    data object Idle : VisionState
    data object Loading : VisionState
    data object Loaded : VisionState
    data class Error(val reason: String) : VisionState
}

private data class IngredientRowSnapshot(val name: String, val quantity: String, val unit: String)
private data class IngredientGroupSnapshot(val name: String?, val ingredients: List<IngredientRowSnapshot>)
private data class StepGroupSnapshot(val name: String?, val steps: List<String>)
private data class PhotoSnapshot(val uri: String, val isCover: Boolean)

private data class EditorSnapshot(
    val name: String,
    val categoryName: String?,
    val difficulty: Difficulty,
    val prepTimeMinutesText: String,
    val cookTimeMinutesText: String,
    val servings: Int,
    val notes: String,
    val source: String,
    val isFavorite: Boolean,
    val photos: List<PhotoSnapshot>,
    val ingredientGroups: List<IngredientGroupSnapshot>,
    val stepGroups: List<StepGroupSnapshot>,
    val tags: List<String>,
    val utensils: List<String>
)

class RecipeEditorViewModel(
    private val repository: RecipeRepository,
    private val settingsRepository: SettingsRepository,
    private val recipeId: Long,
    private val bookId: Long
) : ViewModel() {

    val isEditing: Boolean = recipeId != Destinations.NEW_RECIPE_ID

    var isLoading by mutableStateOf(isEditing)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var nameError by mutableStateOf(false)

    var name by mutableStateOf("")
    var categoryName by mutableStateOf<String?>(null)
    var difficulty by mutableStateOf(Difficulty.MEDIUM)
    var prepTimeMinutesText by mutableStateOf("")
    var cookTimeMinutesText by mutableStateOf("")
    var servings by mutableIntStateOf(4)
    var notes by mutableStateOf("")
    var source by mutableStateOf("")
    var isFavorite by mutableStateOf(false)

    val photos = mutableStateListOf<PhotoUi>()
    val ingredientGroups = mutableStateListOf<IngredientGroupUi>().apply { add(IngredientGroupUi()) }
    val stepGroups = mutableStateListOf<StepGroupUi>().apply { add(StepGroupUi()) }
    val selectedTags = mutableStateListOf<String>()
    val selectedUtensils = mutableStateListOf<String>()

    val availableCategories: StateFlow<List<String>> = repository.observeCategories()
        .map { it.map { c -> c.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableTags: StateFlow<List<String>> = repository.observeTags()
        .map { it.map { t -> t.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableUtensils: StateFlow<List<String>> = repository.observeUtensils()
        .map { it.map { u -> u.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableIngredientNames: StateFlow<List<String>> = repository.observeIngredientNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _visionState = MutableStateFlow<VisionState>(VisionState.Idle)
    val visionState: StateFlow<VisionState> = _visionState
    private var visionStarted = false

    /** Last AI operation started (photo, dish or URL), so it can be retried after an error. */
    private var lastAiOperation: (() -> Unit)? = null

    /** Retries the last AI operation (e.g. after switching models from the error notice). */
    fun retryAi() {
        val operation = lastAiOperation ?: return
        visionStarted = false
        operation()
    }

    /**
     * Recognises a recipe from one or more photos (pages of the same recipe) and pre-fills this
     * form with the combined result.
     */
    fun startVisionExtraction(context: Context, photoUris: List<Uri>) {
        if (isEditing || visionStarted || photoUris.isEmpty()) return
        visionStarted = true
        val appContext = context.applicationContext
        lastAiOperation = { startVisionExtraction(appContext, photoUris) }
        launchAiTask(L10n.str(R.string.ai_task_reading_photo)) {
            _visionState.value = VisionState.Loading
            if (settingsRepository.aiCandidates().isEmpty()) {
                _visionState.value = VisionState.Error(L10n.str(R.string.ai_no_provider))
                return@launchAiTask
            }
            // If some pages cannot be read but others can, carry on with the readable ones; it only
            // fails when all of them fail. The source uri of every image sent is kept because the
            // AI's "dishPhotos" indices refer to the images sent, not to every image picked.
            val readable = photoUris.mapNotNull { uri ->
                PhotoStorage.readResizedJpegBytes(context, uri)?.let { uri to AiImage(it, "image/jpeg") }
            }
            val images = readable.map { it.second }
            if (images.isEmpty()) {
                _visionState.value = VisionState.Error(L10n.str(R.string.couldnt_read_photos))
                return@launchAiTask
            }
            val result = settingsRepository.runAi<RecipeVisionResult>(
                needsImages = true,
                errorOf = { (it as? RecipeVisionResult.Error)?.reason },
                error = { RecipeVisionResult.Error(it) }
            ) { ai -> ai.extractRecipe(images) }
                ?: RecipeVisionResult.Error(L10n.str(R.string.ai_no_provider))
            when (result) {
                is RecipeVisionResult.Success -> {
                    applyVisionResult(result.recipe)
                    // Dish photos located by the AI: cropped, cleaned up and added to the recipe.
                    val dishPhotoUris = withContext(Dispatchers.IO) {
                        DishPhotoCropper.extract(appContext, readable.map { it.first }, result.recipe.dishPhotos)
                    }
                    dishPhotoUris.forEach { addPhoto(it) }
                    _visionState.value = VisionState.Loaded
                }
                is RecipeVisionResult.Error -> _visionState.value = VisionState.Error(result.reason)
            }
        }
    }

    /**
     * Generates a full recipe from a dish picked in the AI dish search (see DishSearchScreen) and
     * pre-fills this form. Same mechanism as [startVisionExtraction] (shares the [visionStarted]
     * guard and [visionState]), starting from a dish name and description instead of a photo.
     */
    fun startDishGeneration(dishName: String, dishDescription: String, dishOrigin: String?) {
        if (isEditing || visionStarted || dishName.isBlank()) return
        visionStarted = true
        lastAiOperation = { startDishGeneration(dishName, dishDescription, dishOrigin) }
        launchAiTask(L10n.str(R.string.ai_task_generating_dish_x, dishName)) {
            _visionState.value = VisionState.Loading
            if (settingsRepository.aiCandidates().isEmpty()) {
                _visionState.value = VisionState.Error(L10n.str(R.string.ai_no_provider))
                return@launchAiTask
            }
            val dish = DishSuggestion(dishName, dishDescription, dishOrigin)
            val result = settingsRepository.runAi<RecipeVisionResult>(
                errorOf = { (it as? RecipeVisionResult.Error)?.reason },
                error = { RecipeVisionResult.Error(it) }
            ) { ai -> ai.generateRecipe(dish) }
                ?: RecipeVisionResult.Error(L10n.str(R.string.ai_no_provider))
            when (result) {
                is RecipeVisionResult.Success -> {
                    applyVisionResult(result.recipe)
                    _visionState.value = VisionState.Loaded
                }
                is RecipeVisionResult.Error -> _visionState.value = VisionState.Error(result.reason)
            }
        }
    }

    /**
     * Imports a recipe from the readable text of a web page ([RecipeUrlFetcher]) and pre-fills this
     * form. Same mechanism as [startVisionExtraction] and [startDishGeneration].
     */
    fun startUrlImport(url: String) {
        if (isEditing || visionStarted || url.isBlank()) return
        visionStarted = true
        lastAiOperation = { startUrlImport(url) }
        launchAiTask(L10n.str(R.string.ai_task_importing_web)) {
            _visionState.value = VisionState.Loading
            if (settingsRepository.aiCandidates().isEmpty()) {
                _visionState.value = VisionState.Error(L10n.str(R.string.ai_no_provider))
                return@launchAiTask
            }
            val pageText = when (val fetchResult = RecipeUrlFetcher.fetchReadableText(url)) {
                is UrlFetchResult.Success -> fetchResult.text
                is UrlFetchResult.Error -> {
                    _visionState.value = VisionState.Error(fetchResult.reason)
                    return@launchAiTask
                }
            }
            val result = settingsRepository.runAi<RecipeVisionResult>(
                errorOf = { (it as? RecipeVisionResult.Error)?.reason },
                error = { RecipeVisionResult.Error(it) }
            ) { ai -> ai.importRecipeFromPage(url, pageText) }
                ?: RecipeVisionResult.Error(L10n.str(R.string.ai_no_provider))
            when (result) {
                is RecipeVisionResult.Success -> {
                    applyVisionResult(result.recipe)
                    _visionState.value = VisionState.Loaded
                }
                is RecipeVisionResult.Error -> _visionState.value = VisionState.Error(result.reason)
            }
        }
    }

    /**
     * Runs an AI operation that fills this form, showing [title] in the AI notification and, when
     * the user has left the app meanwhile, a notification with the outcome.
     */
    private fun launchAiTask(title: String, block: suspend () -> Unit) = viewModelScope.launch {
        AiKeepAlive.hold(title) { block() }
        when (_visionState.value) {
            VisionState.Loaded -> AiKeepAlive.announceIfInBackground(
                L10n.str(R.string.ai_recipe_ready_x, name.ifBlank { L10n.str(R.string.untitled) })
            )
            is VisionState.Error -> AiKeepAlive.announceIfInBackground(L10n.str(R.string.ai_recipe_failed))
            else -> Unit
        }
    }

    private fun applyVisionResult(recipe: RecipeVisionResultDto) {
        // Some books title a section with several comma-separated categories ("Rice, pulses,
        // potatoes and pasta"). Only the first one becomes the recipe category (the data model
        // allows one) and the rest become tags.
        val categoryParts = recipe.categoryName?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        name = recipe.name
        categoryName = categoryParts.firstOrNull()
        difficulty = Difficulty.parse(recipe.difficulty)
        prepTimeMinutesText = recipe.prepTimeMinutes?.toString().orEmpty()
        cookTimeMinutesText = recipe.cookTimeMinutes?.toString().orEmpty()
        servings = recipe.servings.coerceIn(1, 99)
        notes = recipe.notes
        source = recipe.source
        ingredientGroups.clear(); ingredientGroups.addAll(recipe.ingredientGroups.map { it.toUi() }.ifEmpty { listOf(IngredientGroupUi()) })
        stepGroups.clear(); stepGroups.addAll(recipe.stepGroups.map { it.toUi() }.ifEmpty { listOf(StepGroupUi()) })
        selectedTags.clear(); selectedTags.addAll((recipe.tags + categoryParts.drop(1)).distinct())
        selectedUtensils.clear(); selectedUtensils.addAll(recipe.utensils)
    }

    private var initialSnapshot: EditorSnapshot? = null

    init {
        if (isEditing) {
            viewModelScope.launch {
                repository.observeRecipe(recipeId).first()?.let { recipe ->
                    name = recipe.name
                    categoryName = recipe.categoryName
                    difficulty = recipe.difficulty
                    prepTimeMinutesText = recipe.prepTimeMinutes?.toString().orEmpty()
                    cookTimeMinutesText = recipe.cookTimeMinutes?.toString().orEmpty()
                    servings = recipe.servings
                    notes = recipe.notes
                    source = recipe.source
                    isFavorite = recipe.isFavorite
                    photos.clear(); photos.addAll(recipe.photos.map { it.toUi() })
                    ingredientGroups.clear(); ingredientGroups.addAll(recipe.ingredientGroups.map { it.toUi() }.ifEmpty { listOf(IngredientGroupUi()) })
                    stepGroups.clear(); stepGroups.addAll(recipe.stepGroups.map { it.toUi() }.ifEmpty { listOf(StepGroupUi()) })
                    selectedTags.clear(); selectedTags.addAll(recipe.tags)
                    selectedUtensils.clear(); selectedUtensils.addAll(recipe.utensils)
                }
                isLoading = false
                initialSnapshot = snapshot()
            }
        } else {
            initialSnapshot = snapshot()
        }
    }

    private fun snapshot() = EditorSnapshot(
        name = name,
        categoryName = categoryName,
        difficulty = difficulty,
        prepTimeMinutesText = prepTimeMinutesText,
        cookTimeMinutesText = cookTimeMinutesText,
        servings = servings,
        notes = notes,
        source = source,
        isFavorite = isFavorite,
        photos = photos.map { PhotoSnapshot(it.uri, it.isCover) },
        ingredientGroups = ingredientGroups.map { group ->
            IngredientGroupSnapshot(group.name, group.ingredients.map { IngredientRowSnapshot(it.name, it.quantity, it.unit) })
        },
        stepGroups = stepGroups.map { group -> StepGroupSnapshot(group.name, group.steps.map { it.text }) },
        tags = selectedTags.toList(),
        utensils = selectedUtensils.toList()
    )

    /**
     * Checked when leaving the editor (back button or cancel icon) to warn before losing changes.
     */
    fun hasUnsavedChanges(): Boolean = initialSnapshot?.let { it != snapshot() } ?: false

    // --- Ingredients ---
    fun addIngredientRow(groupIndex: Int) {
        ingredientGroups.getOrNull(groupIndex)?.ingredients?.add(IngredientRowUi())
    }

    fun removeIngredientRow(groupIndex: Int, rowIndex: Int) {
        ingredientGroups.getOrNull(groupIndex)?.ingredients?.removeAt(rowIndex)
    }

    fun addIngredientSubGroup() {
        ingredientGroups.add(IngredientGroupUi(name = L10n.str(R.string.new_sub_recipe), ingredients = mutableListOf(IngredientRowUi())))
    }

    fun removeIngredientGroup(groupIndex: Int) {
        if (ingredientGroups.size > 1) ingredientGroups.removeAt(groupIndex)
    }

    // --- Steps ---
    fun addStepRow(groupIndex: Int) {
        stepGroups.getOrNull(groupIndex)?.steps?.add(StepRowUi())
    }

    /**
     * Tidies up the dictated text of [row] (removes filler words, adds punctuation) with the
     * configured AI provider and stores it in [StepRowUi.text]. Without an API key, or if the call
     * fails, the raw dictated text is used instead of being lost: the AI cleanup improves dictation
     * but is not required for it.
     */
    fun cleanUpDictatedText(row: StepRowUi, rawText: String) {
        row.isTranscribing = true
        viewModelScope.launch {
            val result = if (settingsRepository.observeAiEnabled().first()) {
                settingsRepository.runAi<DictationCleanupResult>(
                    errorOf = { (it as? DictationCleanupResult.Error)?.reason },
                    error = { DictationCleanupResult.Error(it) }
                ) { ai -> ai.cleanUpDictation(rawText) }
            } else {
                null
            }
            row.text = (result as? DictationCleanupResult.Success)?.text ?: rawText
            row.isTranscribing = false
        }
    }

    fun removeStepRow(groupIndex: Int, rowIndex: Int) {
        stepGroups.getOrNull(groupIndex)?.steps?.removeAt(rowIndex)
    }

    fun addStepSubGroup() {
        stepGroups.add(StepGroupUi(name = L10n.str(R.string.new_sub_recipe), steps = mutableListOf(StepRowUi())))
    }

    fun removeStepGroup(groupIndex: Int) {
        if (stepGroups.size > 1) stepGroups.removeAt(groupIndex)
    }

    // --- Photos ---
    fun addPhoto(uri: String) {
        photos.add(PhotoUi(uri, isCover = photos.isEmpty()))
    }

    fun removePhoto(photo: PhotoUi) {
        val wasCover = photo.isCover
        photos.remove(photo)
        if (wasCover && photos.isNotEmpty()) photos[0].isCover = true
    }

    fun setCoverPhoto(photo: PhotoUi) {
        photos.forEach { it.isCover = it === photo }
    }

    fun updatePhotoUri(photo: PhotoUi, newUri: String) {
        photo.uri = newUri
    }

    // --- Tags and utensils ---
    fun toggleTag(name: String) {
        if (selectedTags.contains(name)) selectedTags.remove(name) else selectedTags.add(name)
    }

    fun toggleUtensil(name: String) {
        if (selectedUtensils.contains(name)) selectedUtensils.remove(name) else selectedUtensils.add(name)
    }

    fun addCustomTag(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty() && !selectedTags.contains(trimmed)) selectedTags.add(trimmed)
    }

    fun addCustomUtensil(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty() && !selectedUtensils.contains(trimmed)) selectedUtensils.add(trimmed)
    }

    fun save(onSaved: (Long) -> Unit) {
        if (name.isBlank()) {
            nameError = true
            return
        }
        viewModelScope.launch {
            isSaving = true
            val draft = RecipeDraft(
                id = if (isEditing) recipeId else 0L,
                recipeBookId = bookId,
                name = name,
                categoryName = categoryName,
                difficulty = difficulty,
                prepTimeMinutes = prepTimeMinutesText.toIntOrNull(),
                cookTimeMinutes = cookTimeMinutesText.toIntOrNull(),
                servings = servings,
                notes = notes,
                source = source,
                isFavorite = isFavorite,
                photos = photos.map { it.toDomain() },
                ingredientGroups = ingredientGroups.map { it.toDomain() },
                stepGroups = stepGroups.map { it.toDomain() },
                tagNames = selectedTags.toList(),
                utensilNames = selectedUtensils.toList()
            )
            val id = repository.saveRecipe(draft)
            isSaving = false
            onSaved(id)
        }
    }
}
