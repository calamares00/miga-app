package org.calamares.miga.ui.detail

import org.calamares.miga.data.repository.copyRecipeToBook
import android.content.Context
import org.calamares.miga.ui.navigation.Destinations
import org.calamares.miga.R
import org.calamares.miga.L10n
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Job
import org.calamares.miga.data.ai.AiJobState
import org.calamares.miga.data.ai.AiJobs
import org.calamares.miga.data.ai.aiCandidates
import org.calamares.miga.data.ai.runAi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.calamares.miga.data.health.RecipeHealthResult
import org.calamares.miga.data.health.analyzeHealthiness
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.RecipeBookSummary
import org.calamares.miga.data.nutrition.RecipeNutritionResult
import org.calamares.miga.data.nutrition.analyzeNutrition
import org.calamares.miga.data.model.toDraft
import org.calamares.miga.data.polish.PolishedRecipe
import org.calamares.miga.data.polish.RecipePolishResult
import org.calamares.miga.data.polish.polishRecipe
import org.calamares.miga.data.repository.RecipeRepository
import org.calamares.miga.data.substitution.IngredientSubstitution
import org.calamares.miga.data.substitution.SubstitutionResult
import org.calamares.miga.data.substitution.suggestSubstitutes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Health rating of the recipe on screen. Saved values are always shown, with or without the
 * assistant; [NotConfigured] means there is none and the assistant is not available, [PackPending]
 * a pack recipe without values yet (installed before packs carried them).
 */
sealed interface HealthState {
    data object Idle : HealthState
    data object Loading : HealthState
    data object Loaded : HealthState
    data object NotConfigured : HealthState
    data object PackPending : HealthState
    data class Error(val reason: String) : HealthState
}

sealed interface NutritionState {
    data object Idle : NutritionState
    data object Loading : NutritionState
    data object Loaded : NutritionState
    data object NotConfigured : NutritionState
    data object PackPending : NutritionState
    data class Error(val reason: String) : NutritionState
}

/**
 * State of the "substitute ingredient" dialog (see RecipeDetailScreen). Unlike [HealthState] and
 * [NutritionState] nothing is cached: every [ingredientName] triggers a new request.
 */
sealed interface SubstitutionDialogState {
    data object Hidden : SubstitutionDialogState
    data class Loading(val ingredientName: String) : SubstitutionDialogState
    data class Loaded(val ingredientName: String, val substitutions: List<IngredientSubstitution>) : SubstitutionDialogState
    data class NotConfigured(val ingredientName: String) : SubstitutionDialogState
    data class Error(val ingredientName: String, val reason: String) : SubstitutionDialogState
}

/** "Improve texts with AI": the request, then the result to review before it is saved. */
sealed interface PolishState {
    data object Hidden : PolishState
    data object Loading : PolishState
    data object NotConfigured : PolishState
    data class Error(val reason: String) : PolishState
    data class Ready(val polished: PolishedRecipe) : PolishState
}

class RecipeDetailViewModel(
    private val repository: RecipeRepository,
    private val recipeId: Long,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val recipe: StateFlow<Recipe?> = repository.observeRecipe(recipeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val ttsVoiceName: StateFlow<String?> = settingsRepository.observeTtsVoiceName()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val recipeBooks: StateFlow<List<RecipeBookSummary>> = repository.observeRecipeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _healthState = MutableStateFlow<HealthState>(HealthState.Idle)
    val healthState: StateFlow<HealthState> = _healthState
    private var healthCheckStarted = false

    private val _nutritionState = MutableStateFlow<NutritionState>(NutritionState.Idle)
    val nutritionState: StateFlow<NutritionState> = _nutritionState
    private var nutritionCheckStarted = false

    /**
     * Whether the saved health and nutrition values can be recalculated: the assistant is ready and
     * the recipe is not from a pack (pack values are fixed and come with the pack).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val canRecalculateAnalysis: StateFlow<Boolean> = combine(
        settingsRepository.observeAiReady(),
        recipe.filterNotNull().map { it.recipeBookId }.distinctUntilChanged()
            .flatMapLatest { bookId -> repository.observeRecipeBook(bookId) }
            .map { it?.isPack == true }
    ) { ready, isPack -> ready && !isPack }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private suspend fun isPackRecipe(recipe: Recipe): Boolean = repository.getRecipeBookOnce(recipe.recipeBookId)?.isPack == true

    /**
     * Shows the saved health rating, or creates it with the assistant when there is none. Values
     * are never regenerated just by opening the recipe ([recalculateAnalysis] does it on request),
     * and pack recipes are never sent to the assistant.
     */
    fun fetchHealthinessIfNeeded() {
        if (healthCheckStarted) return
        healthCheckStarted = true
        viewModelScope.launch {
            val current = recipe.filterNotNull().first()
            _healthState.value = when {
                current.healthRating != null -> HealthState.Loaded
                isPackRecipe(current) -> HealthState.PackPending
                !settingsRepository.observeAiReady().first() -> HealthState.NotConfigured
                // Automatic rating turned off in Settings: nothing is created or shown.
                !settingsRepository.observeAiHealthEnabled().first() -> HealthState.Idle
                else -> generateHealth(current)
            }
        }
    }

    fun retryHealthCheck() {
        healthCheckStarted = false
        fetchHealthinessIfNeeded()
    }

    /** Shows the saved nutrition estimate, or creates it; same rules as [fetchHealthinessIfNeeded]. */
    fun fetchNutritionIfNeeded() {
        if (nutritionCheckStarted) return
        nutritionCheckStarted = true
        viewModelScope.launch {
            val current = recipe.filterNotNull().first()
            _nutritionState.value = when {
                current.nutritionInfo != null -> NutritionState.Loaded
                isPackRecipe(current) -> NutritionState.PackPending
                !settingsRepository.observeAiReady().first() -> NutritionState.NotConfigured
                !settingsRepository.observeAiNutritionEnabled().first() -> NutritionState.Idle
                else -> generateNutrition(current)
            }
        }
    }

    fun retryNutritionCheck() {
        nutritionCheckStarted = false
        fetchNutritionIfNeeded()
    }

    /** Recreates the saved health rating and nutrition estimate with the assistant, on request. */
    fun recalculateAnalysis() {
        viewModelScope.launch {
            val current = recipe.filterNotNull().first()
            if (!canRecalculateAnalysis.value || isPackRecipe(current)) return@launch
            launch { _healthState.value = generateHealth(current) }
            launch { _nutritionState.value = generateNutrition(current) }
        }
    }

    private suspend fun generateHealth(current: Recipe): HealthState {
        _healthState.value = HealthState.Loading
        val result = settingsRepository.runAi<RecipeHealthResult>(
            errorOf = { (it as? RecipeHealthResult.Error)?.reason },
            error = { RecipeHealthResult.Error(it) }
        ) { ai -> ai.analyzeHealthiness(ingredientsText(current), stepsText(current)) }
        return when (result) {
            null -> HealthState.NotConfigured
            is RecipeHealthResult.Success -> {
                val fingerprint = repository.computeHealthFingerprint(current.ingredientGroups, current.stepGroups)
                repository.saveHealthRating(current.id, result.colorLevel, result.description, fingerprint, System.currentTimeMillis())
                HealthState.Loaded
            }
            is RecipeHealthResult.Error -> HealthState.Error(result.reason)
        }
    }

    private suspend fun generateNutrition(current: Recipe): NutritionState {
        _nutritionState.value = NutritionState.Loading
        val result = settingsRepository.runAi<RecipeNutritionResult>(
            errorOf = { (it as? RecipeNutritionResult.Error)?.reason },
            error = { RecipeNutritionResult.Error(it) }
        ) { ai -> ai.analyzeNutrition(ingredientsText(current), stepsText(current), current.servings) }
        return when (result) {
            null -> NutritionState.NotConfigured
            is RecipeNutritionResult.Success -> {
                val fingerprint = repository.computeNutritionFingerprint(current.ingredientGroups, current.stepGroups)
                repository.saveNutritionInfo(
                    current.id, result.caloriesPerServing, result.proteinGrams, result.carbsGrams, result.fatGrams,
                    fingerprint, System.currentTimeMillis()
                )
                NutritionState.Loaded
            }
            is RecipeNutritionResult.Error -> NutritionState.Error(result.reason)
        }
    }

    private fun ingredientsText(recipe: Recipe): String = recipe.ingredientGroups.joinToString("\n") { group ->
        val header = group.name?.let { "$it:\n" }.orEmpty()
        header + group.ingredients.joinToString("\n") { "- ${formatIngredient(it, 1.0)}" }
    }

    private fun stepsText(recipe: Recipe): String = recipe.stepGroups.joinToString("\n") { group ->
        val header = group.name?.let { "$it:\n" }.orEmpty()
        header + group.instructions.joinToString("\n") { "- $it" }
    }

    private val _substitutionDialogState = MutableStateFlow<SubstitutionDialogState>(SubstitutionDialogState.Hidden)
    val substitutionDialogState: StateFlow<SubstitutionDialogState> = _substitutionDialogState

    /**
     * Asks the AI for substitutes for [ingredientName], without caching. Opens the result dialog
     * (see RecipeDetailScreen).
     */
    fun findSubstitutesFor(ingredientName: String) {
        substitutionJob?.cancel()
        substitutionJob = viewModelScope.launch {
            _substitutionDialogState.value = SubstitutionDialogState.Loading(ingredientName)
            val current = recipe.filterNotNull().first()
            val result = settingsRepository.runAi<SubstitutionResult>(
                errorOf = { (it as? SubstitutionResult.Error)?.reason },
                error = { SubstitutionResult.Error(it) }
            ) { ai -> ai.suggestSubstitutes(ingredientName, current.name) }
            when (result) {
                null -> _substitutionDialogState.value = SubstitutionDialogState.NotConfigured(ingredientName)
                is SubstitutionResult.Success ->
                    _substitutionDialogState.value = SubstitutionDialogState.Loaded(ingredientName, result.substitutions)
                is SubstitutionResult.Error ->
                    _substitutionDialogState.value = SubstitutionDialogState.Error(ingredientName, result.reason)
            }
        }
    }

    /** Closing the dialog stops a search still running: nothing would show its answer. */
    fun dismissSubstitutionDialog() {
        substitutionJob?.cancel()
        substitutionJob = null
        _substitutionDialogState.value = SubstitutionDialogState.Hidden
    }

    private var substitutionJob: Job? = null

    /**
     * The improvement runs in AiJobs, not here: leaving the recipe (or the app) does not stop it,
     * and coming back shows its result. [polishVisible] is whether its progress is on screen.
     */
    private val polishKey = "polish:$recipeId"
    private val polishVisible = MutableStateFlow(false)

    val polishState: StateFlow<PolishState> = combine(AiJobs.observe<RecipePolishResult?>(polishKey), polishVisible) { job, visible ->
        when (job) {
            null -> PolishState.Hidden
            AiJobState.Running -> if (visible) PolishState.Loading else PolishState.Hidden
            is AiJobState.Finished -> when (val result = job.result) {
                null -> PolishState.NotConfigured
                is RecipePolishResult.Success -> PolishState.Ready(result.polished)
                is RecipePolishResult.Error -> PolishState.Error(result.reason)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PolishState.Hidden)

    /** Running with its progress hidden: the menu offers to show it again. */
    val polishInBackground: StateFlow<Boolean> = combine(AiJobs.observe<RecipePolishResult?>(polishKey), polishVisible) { job, visible ->
        job == AiJobState.Running && !visible
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** The recipe as it was before the last improvement applied, for "Undo". */
    private var beforePolish: Recipe? = null

    fun polishRecipe() {
        showPolishProgress()
        viewModelScope.launch {
            val current = recipe.filterNotNull().first()
            AiJobs.start(
                key = polishKey,
                openRoute = Destinations.detail(recipeId),
                doneMessage = { result: RecipePolishResult? ->
                    when (result) {
                        is RecipePolishResult.Success -> L10n.str(R.string.polish_ready_x, current.name)
                        is RecipePolishResult.Error -> L10n.str(R.string.polish_failed_x, current.name)
                        null -> null
                    }
                }
            ) {
                settingsRepository.runAi<RecipePolishResult>(
                    errorOf = { (it as? RecipePolishResult.Error)?.reason },
                    error = { RecipePolishResult.Error(it) }
                ) { ai -> ai.polishRecipe(current) }
            }
        }
    }

    fun showPolishProgress() {
        polishVisible.value = true
        AiJobs.watch(polishKey, true)
    }

    /** "Continue in the background": the work goes on and a notification says when it is ready. */
    fun hidePolishProgress() {
        polishVisible.value = false
        AiJobs.watch(polishKey, false)
    }

    fun cancelPolish() {
        AiJobs.cancel(polishKey)
        hidePolishProgress()
    }

    /** Saves the improved texts; the rest of the recipe (photos, tags, rating...) is kept. */
    fun applyPolish(onApplied: () -> Unit) {
        val ready = polishState.value as? PolishState.Ready ?: return
        val current = recipe.value ?: return
        dismissPolish()
        viewModelScope.launch {
            beforePolish = current
            val polished = ready.polished
            repository.saveRecipe(
                current.toDraft().copy(
                    name = polished.name,
                    notes = polished.notes,
                    ingredientGroups = polished.ingredientGroups,
                    stepGroups = polished.stepGroups
                )
            )
            onApplied()
        }
    }

    fun undoPolish() {
        val previous = beforePolish ?: return
        beforePolish = null
        viewModelScope.launch { repository.saveRecipe(previous.toDraft()) }
    }

    /** Closes the result (discarded, or an error read): the job is forgotten. */
    fun dismissPolish() {
        AiJobs.clear(polishKey)
        hidePolishProgress()
    }

    override fun onCleared() {
        // The job goes on without this screen; its end is then notified.
        AiJobs.watch(polishKey, false)
    }

    fun toggleFavorite() {
        val current = recipe.value ?: return
        viewModelScope.launch { repository.toggleFavorite(current.id, !current.isFavorite) }
    }

    /** Tapping the star that is already set clears the rating. */
    fun setRating(stars: Int) {
        val current = recipe.value ?: return
        val newRating = if (current.rating == stars) null else stars
        viewModelScope.launch { repository.setRating(current.id, newRating) }
    }

    fun addJournalNote(text: String) {
        viewModelScope.launch { repository.addRecipeNote(recipeId, text) }
    }

    fun editJournalNote(id: Long, text: String) {
        viewModelScope.launch { repository.updateRecipeNote(id, text) }
    }

    fun deleteJournalNote(id: Long) {
        viewModelScope.launch { repository.deleteRecipeNote(id) }
    }

    fun markCooked() {
        viewModelScope.launch { repository.markCooked(recipeId) }
    }

    fun moveToBook(newBookId: Long) {
        viewModelScope.launch { repository.moveRecipeToBook(recipeId, newBookId) }
    }

    /** A copy in [targetBookId] (with its own photos): how a pack recipe goes into one's own book. */
    fun copyToBook(context: Context, targetBookId: Long) {
        val current = recipe.value ?: return
        viewModelScope.launch { repository.copyRecipeToBook(context, current, targetBookId) }
    }

    fun addIngredientsToShoppingList() {
        val current = recipe.value ?: return
        viewModelScope.launch { repository.addIngredientsToShoppingList(current.ingredientGroups.flatMap { it.ingredients }) }
    }

    fun deleteRecipe(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteRecipe(recipeId)
            onDeleted()
        }
    }
}
