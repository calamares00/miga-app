package org.calamares.miga.data.polish

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.ai.AiKeepAlive
import org.calamares.miga.data.ai.runAi
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.toDraft
import org.calamares.miga.data.repository.RecipeRepository

/** Where one recipe of a [BulkPolish] is. */
sealed interface BulkPolishState {
    data object Queued : BulkPolishState
    data object Working : BulkPolishState
    /** Improved, waiting for the user to review it. */
    data class Ready(val polished: PolishedRecipe) : BulkPolishState
    data class Failed(val reason: String) : BulkPolishState
    data object Applied : BulkPolishState
    data object Discarded : BulkPolishState
}

data class BulkPolishItem(val recipe: Recipe, val state: BulkPolishState)

/**
 * "Improve with AI" on several recipes at once: each one is improved in turn (one request at a
 * time, to stay within the providers' limits) and waits for the user's review, like the improvement
 * of a single recipe; nothing is saved until the user applies it.
 *
 * Runs outside of any screen, like the PDF exports: leaving the screen or the app does not stop it
 * and its list stays until a new one starts. The "AI is working" notification counts the recipes;
 * when all are done, a notification opens the list (see AiJobs). One list at a time.
 */
object BulkPolish {
    /** Navigation route of the screen (see MigaNavHost); the notification opens it. */
    const val ROUTE = "bulkPolish"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var repository: RecipeRepository? = null
    private var settings: SettingsRepository? = null

    private val _items = MutableStateFlow<List<BulkPolishItem>>(emptyList())
    val items: StateFlow<List<BulkPolishItem>> = _items

    val isRunning: Boolean get() = job?.isActive == true

    /** Whether the user is looking at the list: then its end needs no notification. */
    @Volatile var watched: Boolean = false

    /**
     * Starts improving [recipeIds] (the user's own: recipes of a pack cannot be edited and are left
     * out). False, without changing anything, when another list is still running.
     */
    suspend fun start(repository: RecipeRepository, settings: SettingsRepository, recipeIds: Collection<Long>): Boolean {
        if (isRunning) return false
        this.repository = repository
        this.settings = settings
        val packBooks = repository.observeRecipeBooks().first().filter { it.isPack }.map { it.id }.toSet()
        val recipes = recipeIds.mapNotNull { repository.observeRecipe(it).first() }.filter { it.recipeBookId !in packBooks }
        _items.value = recipes.map { BulkPolishItem(it, BulkPolishState.Queued) }
        run()
        return true
    }

    /** Tries again the recipes that failed. */
    fun retryFailed() {
        if (isRunning) return
        _items.update { list -> list.map { if (it.state is BulkPolishState.Failed) it.copy(state = BulkPolishState.Queued) else it } }
        run()
    }

    /** Goes on with the recipes still queued after a cancel. */
    fun resume() {
        if (!isRunning && _items.value.any { it.state == BulkPolishState.Queued }) run()
    }

    fun cancel() {
        job?.cancel()
        job = null
        // What did not finish goes back to the queue's start: nothing half done is kept.
        _items.update { list -> list.map { if (it.state == BulkPolishState.Working) it.copy(state = BulkPolishState.Queued) else it } }
    }

    /** Saves [recipeId]'s improved texts; the rest of the recipe (photos, tags, rating...) is kept. */
    suspend fun apply(recipeId: Long) {
        val repository = repository ?: return
        val item = _items.value.firstOrNull { it.recipe.id == recipeId } ?: return
        val ready = item.state as? BulkPolishState.Ready ?: return
        // The recipe as it is now: it may have been edited while the list was being improved.
        val current = repository.observeRecipe(recipeId).first() ?: return
        repository.saveRecipe(
            current.toDraft().copy(
                name = ready.polished.name,
                notes = ready.polished.notes,
                ingredientGroups = ready.polished.ingredientGroups,
                stepGroups = ready.polished.stepGroups
            )
        )
        setState(recipeId, BulkPolishState.Applied)
    }

    /** Applies every recipe still waiting for review; returns how many. */
    suspend fun applyAllReady(): Int {
        val ready = _items.value.filter { it.state is BulkPolishState.Ready }.map { it.recipe.id }
        ready.forEach { apply(it) }
        return ready.size
    }

    fun discard(recipeId: Long) = setState(recipeId, BulkPolishState.Discarded)

    /** Forgets a finished list. */
    fun clear() {
        if (!isRunning) _items.value = emptyList()
    }

    private fun setState(recipeId: Long, state: BulkPolishState) {
        _items.update { list -> list.map { if (it.recipe.id == recipeId) it.copy(state = state) else it } }
    }

    private fun run() {
        val settings = settings ?: return
        job = scope.launch {
            val total = _items.value.count { it.state == BulkPolishState.Queued }
            AiKeepAlive.hold(L10n.str(R.string.bulk_polish_working_n, total)) {
                var done = 0
                while (true) {
                    val next = _items.value.firstOrNull { it.state == BulkPolishState.Queued } ?: break
                    progress(done, total)
                    status(next.recipe.name)
                    setState(next.recipe.id, BulkPolishState.Working)
                    val result = try {
                        settings.runAi<RecipePolishResult>(
                            errorOf = { (it as? RecipePolishResult.Error)?.reason },
                            error = { RecipePolishResult.Error(it) }
                        ) { ai -> ai.polishRecipe(next.recipe) } ?: RecipePolishResult.Error(L10n.str(R.string.ai_no_provider))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        RecipePolishResult.Error(e.message ?: e::class.simpleName.orEmpty())
                    }
                    setState(
                        next.recipe.id,
                        when (result) {
                            is RecipePolishResult.Success -> BulkPolishState.Ready(result.polished)
                            is RecipePolishResult.Error -> BulkPolishState.Failed(result.reason)
                        }
                    )
                    done++
                }
            }
            if (!watched) {
                val ready = _items.value.count { it.state is BulkPolishState.Ready }
                val failed = _items.value.count { it.state is BulkPolishState.Failed }
                AiKeepAlive.announce(
                    if (failed == 0) L10n.str(R.string.bulk_polish_done_n, ready) else L10n.str(R.string.bulk_polish_done_n_failed_m, ready, failed),
                    openRoute = ROUTE
                )
            }
        }
    }
}
