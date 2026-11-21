package org.calamares.miga.ui.editor

import org.calamares.miga.data.model.StepGroup
import org.calamares.miga.data.model.IngredientGroup
import org.calamares.miga.data.polish.polishRecipe
import org.calamares.miga.data.polish.PolishedRecipe
import org.calamares.miga.data.polish.RecipePolishResult
import org.calamares.miga.data.search.PageFetchResult
import org.calamares.miga.data.search.SchemaOrgRecipe
import org.calamares.miga.data.search.SchemaRecipe
import org.calamares.miga.data.search.SocialPost
import org.calamares.miga.data.search.SocialPostRecipe
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.searchKey
import org.calamares.miga.data.model.ParsedShoppingEntry
import org.calamares.miga.data.model.PastedLists
import kotlinx.coroutines.withContext
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
import org.calamares.miga.data.model.RecipeOrigin
import org.calamares.miga.data.model.RecipePhoto
import org.calamares.miga.data.repository.RecipeRepository
import org.calamares.miga.data.search.DishSuggestion
import org.calamares.miga.data.search.RecipeUrlFetcher
import org.calamares.miga.data.search.UrlFetchResult
import org.calamares.miga.data.search.generateRecipe
import org.calamares.miga.data.search.importRecipeFromPage
import org.calamares.miga.data.support.AppDispatchers
import org.calamares.miga.data.vision.RecipeVisionResult
import org.calamares.miga.data.vision.RecipeVisionResultDto
import org.calamares.miga.data.ai.AiImage
import org.calamares.miga.data.vision.extractRecipes
import org.calamares.miga.data.vision.findDishPhotos
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
import org.calamares.miga.data.model.ContentKind
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** An automatic improvement of a recipe read from the web: what changed and the texts before it. */
class WebPolish(
    val changes: List<String>,
    val name: String,
    val notes: String,
    val ingredientGroups: List<IngredientGroup>,
    val stepGroups: List<StepGroup>
)

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
    val utensils: List<String>,
    val origin: String = "",
    val originCountry: String? = null
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
    /** Where the recipe comes from (free text) and its country code for the flag (see RecipeOrigin). */
    var origin by mutableStateOf("")
    var originCountry by mutableStateOf<String?>(null)
    /** True once the user picks a country by hand, so typing no longer changes it. */
    private var originCountryPicked = false

    /** Updates the origin text and, unless a country was picked by hand, the country it refers to. */
    fun updateOrigin(text: String) {
        origin = text
        if (!originCountryPicked) originCountry = RecipeOrigin.guessCountry(text)
    }

    fun pickOriginCountry(code: String?) {
        originCountryPicked = code != null
        originCountry = code
    }

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

    /** Tags and utensils by how many recipes use them, most used first, offered on an empty field. */
    val popularTags: StateFlow<List<String>> = popularNames(ContentKind.TAG)
    val popularUtensils: StateFlow<List<String>> = popularNames(ContentKind.EQUIPMENT)

    private fun popularNames(kind: ContentKind): StateFlow<List<String>> = repository.observeContent(kind)
        .map { items -> items.filter { it.usage > 0 }.sortedByDescending { it.usage }.map { it.name } }
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
        // Dish photos located by the AI: confirmed, cropped, cleaned up and added to the recipe,
        // the one showing the whole dish first (the first photo added is the cover).
        settingsRepository.findDishPhotos(appContext, sourceUris, recipe, progress).forEach { addPhoto(it) }
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
                    val dishPhotoUris = settingsRepository.findDishPhotos(detected.appContext, detected.sourceUris, recipe)
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
     * Imports a recipe from a web page and pre-fills this form. Most recipe sites carry a schema.org
     * Recipe ([SchemaOrgRecipe]): it is read directly, with its photo, and no AI is needed. Other
     * pages go to the cooking assistant as readable text ([RecipeUrlFetcher]), the same mechanism
     * as [startVisionExtraction] and [startDishGeneration].
     */
    fun startUrlImport(context: Context, url: String) {
        if (isEditing || visionStarted || url.isBlank()) return
        visionStarted = true
        val appContext = context.applicationContext
        lastAiOperation = { startUrlImport(appContext, url) }
        launchAiTask(L10n.str(R.string.ai_task_importing_web)) { progress ->
            _visionState.value = VisionState.Loading
            progress.step(L10n.str(R.string.ai_step_downloading_page))
            val page = when (val fetchResult = RecipeUrlFetcher.fetchPage(url)) {
                is PageFetchResult.Success -> fetchResult
                is PageFetchResult.Error -> {
                    _visionState.value = VisionState.Error(fetchResult.reason)
                    return@launchAiTask
                }
            }
            val structured = withContext(AppDispatchers.default) { SchemaOrgRecipe.parse(page.html, page.url) }
            if (structured != null) {
                applyReadRecipe(appContext, structured, progress)
                return@launchAiTask
            }
            // Instagram and similar posts: the recipe is in the caption.
            val pagePost = withContext(AppDispatchers.default) { SocialPostRecipe.read(page.html, page.url) }
            // Instagram's embed page has the photo without the play button (and the caption, when
            // the post's page hides it).
            val postUrl = listOf(page.url, url).firstOrNull { SocialPostRecipe.isInstagram(it) } ?: page.url
            val embed = SocialPostRecipe.instagramEmbedUrl(postUrl)?.let { embedUrl ->
                (RecipeUrlFetcher.fetchPage(embedUrl) as? PageFetchResult.Success)?.let { embedPage ->
                    withContext(AppDispatchers.default) { SocialPostRecipe.readInstagramEmbed(embedPage.html) }
                }
            }
            val post = SocialPostRecipe.withEmbed(pagePost, embed, postUrl)
            val fromPost = post?.let { withContext(AppDispatchers.default) { SocialPostRecipe.parse(it) } }
            if (fromPost != null) {
                applyReadRecipe(appContext, fromPost, progress)
                return@launchAiTask
            }
            post?.let { SocialPostRecipe.recipeElsewhere(it) }?.let { website ->
                _visionState.value = VisionState.Error(L10n.str(R.string.web_import_post_recipe_elsewhere, website))
                return@launchAiTask
            }
            if (post == null && SocialPostRecipe.isInstagram(url)) {
                _visionState.value = VisionState.Error(L10n.str(R.string.web_import_instagram_no_text))
                return@launchAiTask
            }
            if (settingsRepository.aiCandidates().isEmpty()) {
                _visionState.value = VisionState.Error(L10n.str(if (post != null) R.string.web_import_post_needs_assistant else R.string.web_import_needs_assistant))
                return@launchAiTask
            }
            val pageText = if (post != null) postText(post) else when (val text = RecipeUrlFetcher.readableText(page.html)) {
                is UrlFetchResult.Success -> text.text
                is UrlFetchResult.Error -> {
                    _visionState.value = VisionState.Error(text.reason)
                    return@launchAiTask
                }
            }
            val result = progress.withHints(webHints()) {
                settingsRepository.runAi<RecipeVisionResult>(
                    errorOf = { (it as? RecipeVisionResult.Error)?.reason },
                    error = { RecipeVisionResult.Error(it) },
                    progress = progress
                ) { ai -> ai.importRecipeFromPage(post?.url ?: url, pageText, known) }
            } ?: RecipeVisionResult.Error(L10n.str(R.string.ai_no_provider))
            when (result) {
                is RecipeVisionResult.Success -> {
                    val recipe = if (post != null && result.recipe.source.isBlank()) result.recipe.copy(source = post.url) else result.recipe
                    applyVisionResult(recipe)
                    post?.imageUrl?.let { downloadPhoto(appContext, it, progress) }
                    _visionState.value = VisionState.Loaded
                }
                is RecipeVisionResult.Error -> _visionState.value = VisionState.Error(result.reason)
            }
        }
    }

    /**
     * Fills the form with a recipe read from a page without AI (schema.org or a post's caption),
     * downloads its photo and, with the assistant set up, improves the texts as with "Polish the
     * recipe"; the editor offers to undo it.
     */
    private suspend fun applyReadRecipe(appContext: Context, read: SchemaRecipe, progress: AiProgressReporter) {
        applyVisionResult(read.recipe, byAi = false)
        read.imageUrl?.let { downloadPhoto(appContext, it, progress) }
        if (settingsRepository.aiCandidates().isNotEmpty()) {
            progress.step(L10n.str(R.string.polish_working))
            val polish = settingsRepository.runAi<RecipePolishResult>(
                errorOf = { (it as? RecipePolishResult.Error)?.reason },
                error = { RecipePolishResult.Error(it) },
                progress = progress
            ) { ai -> ai.polishRecipe(formAsRecipe()) }
            when (polish) {
                is RecipePolishResult.Success -> applyWebPolish(polish.polished)
                is RecipePolishResult.Error -> webPolishError = polish.reason
                null -> Unit
            }
        }
        _visionState.value = VisionState.Loaded
    }

    private suspend fun downloadPhoto(appContext: Context, imageUrl: String, progress: AiProgressReporter) {
        progress.step(L10n.str(R.string.ai_step_downloading_photo))
        val saved = RecipeUrlFetcher.fetchImage(imageUrl)?.let { bytes ->
            withContext(AppDispatchers.io) { PhotoStorage.saveDownloaded(appContext, bytes) }
        }
        saved?.let { addPhoto(it) }
    }

    /** What the assistant reads of a post: its caption, saying where it was posted and by whom. */
    private fun postText(post: SocialPost): String =
        listOfNotNull("${post.site}${post.author?.let { " ($it)" }.orEmpty()}", post.text).joinToString("\n\n")

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

    /** Whether the form was filled by the cooking assistant (photos, suggestion, web text or improved). */
    var filledByAi by mutableStateOf(false)
        private set

    /** The improvement applied to a recipe read from a web page, which can be undone. */
    var webPolish by mutableStateOf<WebPolish?>(null)
        private set

    /** Why a recipe read from a web page could not be improved, shown without blocking. */
    var webPolishError by mutableStateOf<String?>(null)
        private set

    private fun applyVisionResult(found: RecipeVisionResultDto, byAi: Boolean = true) {
        filledByAi = byAi
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
        origin = recipe.origin?.trim().orEmpty()
        originCountry = RecipeOrigin.normalizeCountry(recipe.originCountry) ?: RecipeOrigin.guessCountry(origin)
    }

    /** The form's recipe texts as a Recipe, for the assistant. */
    private fun formAsRecipe() = Recipe(
        uid = "", recipeBookId = bookId, recipeBookName = "", name = name, categoryId = null, categoryName = categoryName,
        difficulty = difficulty, prepTimeMinutes = prepTimeMinutesText.toIntOrNull(), cookTimeMinutes = cookTimeMinutesText.toIntOrNull(),
        servings = servings, notes = notes, source = source, isFavorite = false, timesCooked = 0, createdAt = 0L, updatedAt = 0L,
        photos = emptyList(), ingredientGroups = ingredientGroups.map { it.toDomain() }, stepGroups = stepGroups.map { it.toDomain() },
        tags = emptyList(), utensils = emptyList()
    )

    private fun applyWebPolish(polished: PolishedRecipe) {
        webPolish = WebPolish(
            changes = polished.changes,
            name = name,
            notes = notes,
            ingredientGroups = ingredientGroups.map { it.toDomain() },
            stepGroups = stepGroups.map { it.toDomain() }
        )
        filledByAi = true
        name = polished.name
        notes = polished.notes
        ingredientGroups.clear(); ingredientGroups.addAll(polished.ingredientGroups.map { it.toUi() }.ifEmpty { listOf(IngredientGroupUi()) })
        stepGroups.clear(); stepGroups.addAll(polished.stepGroups.map { it.toUi() }.ifEmpty { listOf(StepGroupUi()) })
    }

    /** Puts back the texts as the web page had them. */
    fun undoWebPolish() {
        val original = webPolish ?: return
        name = original.name
        notes = original.notes
        ingredientGroups.clear(); ingredientGroups.addAll(original.ingredientGroups.map { it.toUi() }.ifEmpty { listOf(IngredientGroupUi()) })
        stepGroups.clear(); stepGroups.addAll(original.stepGroups.map { it.toUi() }.ifEmpty { listOf(StepGroupUi()) })
        webPolish = null
        filledByAi = false
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
                    origin = recipe.origin.orEmpty()
                    originCountry = recipe.originCountry
                    originCountryPicked = recipe.originCountry != null
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
        utensils = selectedUtensils.toList(),
        origin = origin,
        originCountry = originCountry
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

    /**
     * Turns pasted [lines] into ingredient rows: the first fills row [rowIndex] (its quantity and
     * unit only if still empty), the rest are inserted after it.
     */
    fun splitIngredientRow(groupIndex: Int, rowIndex: Int, lines: List<String>) {
        val rows = ingredientGroups.getOrNull(groupIndex)?.ingredients ?: return
        val parsed = lines.map { PastedLists.ingredient(it) }
        rows.getOrNull(rowIndex)?.let { row ->
            val first = parsed.first()
            row.name = first.name
            if (row.quantity.isBlank()) row.quantity = first.quantity?.let { formatEditorQuantity(it) }.orEmpty()
            if (row.unit.isBlank()) row.unit = first.unit.orEmpty()
        }
        rows.addAll((rowIndex + 1).coerceAtMost(rows.size), parsed.drop(1).map { it.toRow() })
    }

    /** Adds a pasted list of ingredients at the end of the group, replacing a last empty row. */
    fun pasteIngredients(groupIndex: Int, text: String) {
        val rows = ingredientGroups.getOrNull(groupIndex)?.ingredients ?: return
        val parsed = PastedLists.lines(text).map { PastedLists.ingredient(it).toRow() }
        if (parsed.isEmpty()) return
        if (rows.lastOrNull()?.let { it.name.isBlank() && it.quantity.isBlank() && it.unit.isBlank() } == true) rows.removeAt(rows.lastIndex)
        rows.addAll(parsed)
    }

    private fun ParsedShoppingEntry.toRow() =
        IngredientRowUi(name, quantity?.let { formatEditorQuantity(it) }.orEmpty(), unit.orEmpty())

    /** New order after dragging an ingredient. */
    fun reorderIngredients(groupIndex: Int, order: List<IngredientRowUi>) {
        val rows = ingredientGroups.getOrNull(groupIndex)?.ingredients ?: return
        if (order.toSet() != rows.toSet()) return
        rows.clear()
        rows.addAll(order)
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

    /** Turns pasted [lines] into steps: the first goes into row [rowIndex], the rest after it. */
    fun splitStepRow(groupIndex: Int, rowIndex: Int, lines: List<String>) {
        val rows = stepGroups.getOrNull(groupIndex)?.steps ?: return
        rows.getOrNull(rowIndex)?.text = lines.first()
        rows.addAll((rowIndex + 1).coerceAtMost(rows.size), lines.drop(1).map { StepRowUi(it) })
    }

    /** Adds a pasted list of steps at the end of the group, replacing a last empty step. */
    fun pasteSteps(groupIndex: Int, text: String) {
        val rows = stepGroups.getOrNull(groupIndex)?.steps ?: return
        val lines = PastedLists.lines(text)
        if (lines.isEmpty()) return
        if (rows.lastOrNull()?.text?.isBlank() == true) rows.removeAt(rows.lastIndex)
        rows.addAll(lines.map { StepRowUi(it) })
    }

    /** New order after dragging a step. */
    fun reorderSteps(groupIndex: Int, order: List<StepRowUi>) {
        val rows = stepGroups.getOrNull(groupIndex)?.steps ?: return
        if (order.toSet() != rows.toSet()) return
        rows.clear()
        rows.addAll(order)
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

    /** Another recipe with the same name, found when saving; the screen asks before saving it too. */
    var duplicate by mutableStateOf<Recipe?>(null)

    /**
     * Saves the recipe. Unless [allowDuplicate], a recipe of the same name elsewhere (accents and
     * case aside) stops it and fills [duplicate], so the user can decide.
     */
    fun save(onSaved: (Long) -> Unit, allowDuplicate: Boolean = false) {
        if (name.isBlank()) {
            nameError = true
            return
        }
        viewModelScope.launch {
            if (!allowDuplicate) {
                val key = searchKey(name.trim())
                val existing = repository.getAllRecipesOnce().firstOrNull { it.id != recipeId && searchKey(it.name.trim()) == key }
                if (existing != null) {
                    duplicate = existing
                    return@launch
                }
            }
            duplicate = null
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
                utensilNames = selectedUtensils.toList(),
                origin = origin.trim().ifEmpty { null },
                originCountry = originCountry
            )
            val id = repository.saveRecipe(draft)
            isSaving = false
            onSaved(id)
        }
    }
}
