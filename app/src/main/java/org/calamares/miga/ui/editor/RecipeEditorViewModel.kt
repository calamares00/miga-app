package org.calamares.miga.ui.editor

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.calamares.miga.data.local.DishPhotoCropper
import org.calamares.miga.data.ai.aiCandidates
import org.calamares.miga.data.ai.AiKeepAlive
import org.calamares.miga.data.ai.AiProgress
import org.calamares.miga.data.ai.AiProgressReporter
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
import org.calamares.miga.data.model.RecipePhoto
import org.calamares.miga.data.repository.RecipeRepository
import org.calamares.miga.data.search.DishSuggestion
import org.calamares.miga.data.search.RecipeUrlFetcher
import org.calamares.miga.data.search.UrlFetchResult
import org.calamares.miga.data.search.generateRecipe
import org.calamares.miga.data.search.importRecipeFromPage
import org.calamares.miga.data.vision.RecipeVisionResult
import org.calamares.miga.data.vision.RecipeVisionResultDto
import org.calamares.miga.data.ai.AiImage
import org.calamares.miga.data.vision.extractRecipes
import org.calamares.miga.data.vision.matchedTo
import org.calamares.miga.data.vision.newLabels
import org.calamares.miga.data.vision.NewLabels
import org.calamares.miga.data.ai.KnownLabels
import org.calamares.miga.data.vision.mergedIntoOne
import org.calamares.miga.data.vision.toRecipeDraft
import org.calamares.miga.data.vision.RecipeExtractionResult
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
    /** Several recipes were found in the photos; see [DetectedRecipes]. */
    data object Choosing : VisionState
    data class Error(val reason: String) : VisionState
}

/**
 * More than one complete recipe found in the photos, waiting for the user to choose between one
 * recipe each or a single recipe joining them. [together] is the AI's suggestion; [sourceUris] are
 * the images sent, which the dish photo boxes refer to.
 */
data class DetectedRecipes(
    val recipes: List<RecipeVisionResultDto>,
    val together: Boolean,
    val sourceUris: List<Uri>,
    val appContext: Context
)

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

    /** What the running AI operation is doing, shown while [visionState] is loading. */
    private val _aiProgress = MutableStateFlow<AiProgress?>(null)
    val aiProgress: StateFlow<AiProgress?> = _aiProgress
    private var visionStarted = false

    /**
     * The user's categories and equipment when the AI operation started: offered to the model and
     * used to file the result under existing ones (see matchedTo).
     */
    private var known = KnownLabels.NONE

    /**
     * Category and equipment of the form that do not exist yet and would be created on saving;
     * shown after an AI fill so the user knows before saving.
     */
    fun newLabelsToCreate(): NewLabels = newLabels(categoryName, selectedUtensils.toList(), known)

    private val _detectedRecipes = MutableStateFlow<DetectedRecipes?>(null)
    val detectedRecipes: StateFlow<DetectedRecipes?> = _detectedRecipes

    /** Whether the recipes can be saved straight into a book, one each (the editor knows the book). */
    val canSaveSeparately: Boolean get() = bookId != Destinations.NEW_BOOK_ID

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
        launchAiTask(L10n.str(R.string.ai_task_reading_photo)) { progress ->
            _visionState.value = VisionState.Loading
            if (settingsRepository.aiCandidates().isEmpty()) {
                _visionState.value = VisionState.Error(L10n.str(R.string.ai_no_provider))
                return@launchAiTask
            }
            // If some pages cannot be read but others can, carry on with the readable ones; it only
            // fails when all of them fail. The source uri of every image sent is kept because the
            // AI's "dishPhotos" indices refer to the images sent, not to every image picked.
            progress.step(L10n.str(R.string.ai_step_preparing_photos_n, photoUris.size))
            val readable = photoUris.mapNotNull { uri ->
                PhotoStorage.readResizedJpegBytes(context, uri)?.let { uri to AiImage(it, "image/jpeg") }
            }
            val images = readable.map { it.second }
            if (images.isEmpty()) {
                _visionState.value = VisionState.Error(L10n.str(R.string.couldnt_read_photos))
                return@launchAiTask
            }
            val result = progress.withHints(photoHints()) {
                settingsRepository.runAi<RecipeExtractionResult>(
                    needsImages = true,
                    errorOf = { (it as? RecipeExtractionResult.Error)?.reason },
                    error = { RecipeExtractionResult.Error(it) },
                    progress = progress
                ) { ai -> ai.extractRecipes(images, known) }
            } ?: RecipeExtractionResult.Error(L10n.str(R.string.ai_no_provider))
            when (result) {
                is RecipeExtractionResult.Success -> if (result.recipes.size == 1) {
                    fillFromPhotos(result.recipes.single(), appContext, readable.map { it.first }, progress)
                } else {
                    _detectedRecipes.value = DetectedRecipes(result.recipes, result.together, readable.map { it.first }, appContext)
                    _visionState.value = VisionState.Choosing
                }
                is RecipeExtractionResult.Error -> _visionState.value = VisionState.Error(result.reason)
            }
        }
    }

    /** Fills the form with [recipe] and adds the dish photos the AI located in [sourceUris]. */
    private suspend fun fillFromPhotos(recipe: RecipeVisionResultDto, appContext: Context, sourceUris: List<Uri>, progress: AiProgressReporter?) {
        applyVisionResult(recipe)
        // Dish photos located by the AI: cropped, cleaned up and added to the recipe.
        if (recipe.dishPhotos.isNotEmpty()) progress?.step(L10n.str(R.string.ai_step_cropping_photo))
        val dishPhotoUris = withContext(Dispatchers.IO) { DishPhotoCropper.extract(appContext, sourceUris, recipe.dishPhotos) }
        dishPhotoUris.forEach { addPhoto(it) }
        _visionState.value = VisionState.Loaded
    }

    /** Joins the [selected] detected recipes (indices) into this form, as one recipe. */
    fun importDetectedTogether(selected: List<Int>) {
        val detected = _detectedRecipes.value ?: return
        val recipes = selected.mapNotNull { detected.recipes.getOrNull(it) }.ifEmpty { return }
        _detectedRecipes.value = null
        _visionState.value = VisionState.Loading
        viewModelScope.launch { fillFromPhotos(recipes.mergedIntoOne(), detected.appContext, detected.sourceUris, null) }
    }

    /**
     * Saves each of the [selected] detected recipes (indices) as its own recipe in the book, with
     * its own dish photos, and reports how many were saved.
     */
    fun importDetectedSeparately(selected: List<Int>, onSaved: (count: Int, created: List<String>) -> Unit) {
        val detected = _detectedRecipes.value ?: return
        val recipes = selected.mapNotNull { detected.recipes.getOrNull(it) }.ifEmpty { return }
        if (recipes.size == 1) return importDetectedTogether(selected)
        _detectedRecipes.value = null
        _visionState.value = VisionState.Loading
        viewModelScope.launch {
            val created = mutableListOf<String>()
            AiKeepAlive.hold(L10n.str(R.string.ai_task_reading_photo)) {
                recipes.forEach { recipe ->
                    val dishPhotoUris = withContext(Dispatchers.IO) {
                        DishPhotoCropper.extract(detected.appContext, detected.sourceUris, recipe.dishPhotos)
                    }
                    val draft = recipe.matchedTo(known).toRecipeDraft(bookId)
                        .copy(photos = dishPhotoUris.mapIndexed { i, uri -> RecipePhoto(uri, isCover = i == 0) })
                    val toCreate = newLabels(draft.categoryName, draft.utensilNames, known)
                    toCreate.category?.let { created += L10n.str(R.string.new_labels_category_x, it) }
                    if (toCreate.equipment.isNotEmpty()) created += L10n.str(R.string.new_labels_equipment_x, toCreate.equipment.joinToString(", "))
                    repository.saveRecipe(draft)
                    // What this recipe created is known to the next ones, so they reuse it.
                    known = KnownLabels(known.categories + listOfNotNull(toCreate.category), known.equipment + toCreate.equipment)
                }
            }
            _visionState.value = VisionState.Idle
            onSaved(recipes.size, created.distinct())
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
        launchAiTask(L10n.str(R.string.ai_task_generating_dish_x, dishName)) { progress ->
            _visionState.value = VisionState.Loading
            if (settingsRepository.aiCandidates().isEmpty()) {
                _visionState.value = VisionState.Error(L10n.str(R.string.ai_no_provider))
                return@launchAiTask
            }
            val dish = DishSuggestion(dishName, dishDescription, dishOrigin)
            val result = progress.withHints(generationHints()) {
                settingsRepository.runAi<RecipeVisionResult>(
                    errorOf = { (it as? RecipeVisionResult.Error)?.reason },
                    error = { RecipeVisionResult.Error(it) },
                    progress = progress
                ) { ai -> ai.generateRecipe(dish, known) }
            } ?: RecipeVisionResult.Error(L10n.str(R.string.ai_no_provider))
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
        launchAiTask(L10n.str(R.string.ai_task_importing_web)) { progress ->
            _visionState.value = VisionState.Loading
            if (settingsRepository.aiCandidates().isEmpty()) {
                _visionState.value = VisionState.Error(L10n.str(R.string.ai_no_provider))
                return@launchAiTask
            }
            progress.step(L10n.str(R.string.ai_step_downloading_page))
            val pageText = when (val fetchResult = RecipeUrlFetcher.fetchReadableText(url)) {
                is UrlFetchResult.Success -> fetchResult.text
                is UrlFetchResult.Error -> {
                    _visionState.value = VisionState.Error(fetchResult.reason)
                    return@launchAiTask
                }
            }
            val result = progress.withHints(webHints()) {
                settingsRepository.runAi<RecipeVisionResult>(
                    errorOf = { (it as? RecipeVisionResult.Error)?.reason },
                    error = { RecipeVisionResult.Error(it) },
                    progress = progress
                ) { ai -> ai.importRecipeFromPage(url, pageText, known) }
            } ?: RecipeVisionResult.Error(L10n.str(R.string.ai_no_provider))
            when (result) {
                is RecipeVisionResult.Success -> {
                    applyVisionResult(result.recipe)
                    _visionState.value = VisionState.Loaded
                }
                is RecipeVisionResult.Error -> _visionState.value = VisionState.Error(result.reason)
            }
        }
    }

    private fun photoHints() = listOf(
        L10n.str(R.string.ai_hint_reading_text),
        L10n.str(R.string.ai_hint_ingredients),
        L10n.str(R.string.ai_hint_steps),
        L10n.str(R.string.ai_hint_times),
        L10n.str(R.string.ai_hint_dish_photo)
    )

    private fun generationHints() = listOf(
        L10n.str(R.string.ai_hint_choosing_ingredients),
        L10n.str(R.string.ai_hint_writing_steps),
        L10n.str(R.string.ai_hint_times)
    )

    private fun webHints() = listOf(
        L10n.str(R.string.ai_hint_finding_recipe_in_page),
        L10n.str(R.string.ai_hint_ingredients),
        L10n.str(R.string.ai_hint_steps)
    )

    /**
     * Runs an AI operation that fills this form, showing [title] in the AI notification and, when
     * the user has left the app meanwhile, a notification with the outcome.
     */
    private fun launchAiTask(title: String, block: suspend (AiProgressReporter) -> Unit) = viewModelScope.launch {
        known = KnownLabels(
            categories = repository.observeCategories().first().map { it.name },
            equipment = repository.observeUtensils().first().map { it.name }
        )
        AiKeepAlive.hold(title) {
            val reporter = AiProgressReporter { progress ->
                _aiProgress.value = progress
                status(progress.detail ?: progress.step)
            }
            block(reporter)
        }
        _aiProgress.value = null
        when (_visionState.value) {
            VisionState.Loaded -> AiKeepAlive.announceIfInBackground(
                L10n.str(R.string.ai_recipe_ready_x, name.ifBlank { L10n.str(R.string.untitled) })
            )
            is VisionState.Error -> AiKeepAlive.announceIfInBackground(L10n.str(R.string.ai_recipe_failed))
            VisionState.Choosing -> AiKeepAlive.announceIfInBackground(
                L10n.str(R.string.ai_several_recipes_found_x, _detectedRecipes.value?.recipes?.size ?: 0)
            )
            else -> Unit
        }
    }

    private fun applyVisionResult(found: RecipeVisionResultDto) {
        val recipe = found.matchedTo(known)
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
