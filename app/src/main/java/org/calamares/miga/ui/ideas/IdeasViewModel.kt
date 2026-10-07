package org.calamares.miga.ui.ideas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.ai.AiKeepAlive
import org.calamares.miga.data.ai.runAi
import org.calamares.miga.data.ideas.IdeasAnswer
import org.calamares.miga.data.ideas.IdeasPreset
import org.calamares.miga.data.ideas.IdeasResult
import org.calamares.miga.data.ideas.IdeasTurn
import org.calamares.miga.data.ideas.askIdeas
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.RecipeBookSummary
import org.calamares.miga.data.repository.RecipeRepository

/** State of one question in the conversation. */
sealed interface IdeasEntryState {
    data object Loading : IdeasEntryState
    data class Answered(val answer: IdeasAnswer) : IdeasEntryState
    data class Failed(val reason: String) : IdeasEntryState
}

/** A question (typed or a preset) and its answer. [label] is what the conversation shows. */
data class IdeasEntry(val id: Int, val label: String, val question: String, val preset: IdeasPreset?, val state: IdeasEntryState)

/**
 * AI recommendations based on the user's recipes: ready-made requests (weekly menu, breakfasts,
 * seasonal...) and free questions, kept as a conversation so follow-up questions have context.
 */
class IdeasViewModel(
    private val repository: RecipeRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _entries = MutableStateFlow<List<IdeasEntry>>(emptyList())
    val entries: StateFlow<List<IdeasEntry>> = _entries

    /** The user's recipes by id, to show the ones the AI recommends. */
    private val _recipes = MutableStateFlow<Map<Long, Recipe>>(emptyMap())
    val recipes: StateFlow<Map<Long, Recipe>> = _recipes

    /** Books a new recipe can be created in (packs are read-only). */
    val targetBooks: StateFlow<List<RecipeBookSummary>> = repository.observeRecipeBooks()
        .map { books -> books.filter { !it.isPack } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var nextId = 0

    fun askPreset(preset: IdeasPreset) = ask(presetLabel(preset), "", preset)

    fun askQuestion(question: String) {
        val trimmed = question.trim()
        if (trimmed.isNotEmpty()) ask(trimmed, trimmed, null)
    }

    fun retry(entryId: Int) {
        val entry = _entries.value.firstOrNull { it.id == entryId } ?: return
        run(entry.copy(state = IdeasEntryState.Loading))
    }

    fun clear() {
        _entries.value = emptyList()
    }

    /** Adds the ingredients of every recipe in [answer] to the shopping list; [onDone] gets a message. */
    fun addToShoppingList(answer: IdeasAnswer, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val recipes = answer.recipeIds.mapNotNull { _recipes.value[it] }
            repository.addIngredientsToShoppingList(recipes.flatMap { recipe -> recipe.ingredientGroups.flatMap { it.ingredients } })
            onDone(L10n.str(R.string.ideas_added_to_shopping_n, recipes.size))
        }
    }

    private fun ask(label: String, question: String, preset: IdeasPreset?) {
        if (_entries.value.any { it.state == IdeasEntryState.Loading }) return
        run(IdeasEntry(nextId++, label, question, preset, IdeasEntryState.Loading))
    }

    private fun run(entry: IdeasEntry) {
        _entries.update { list -> if (list.any { it.id == entry.id }) list.map { if (it.id == entry.id) entry else it } else list + entry }
        viewModelScope.launch {
            val all = repository.getAllRecipesOnce()
            _recipes.value = all.associateBy { it.id }
            val history = _entries.value
                .takeWhile { it.id != entry.id }
                .mapNotNull { previous -> (previous.state as? IdeasEntryState.Answered)?.let { IdeasTurn(previous.label, it.answer) } }
            val result = AiKeepAlive.hold(entry.label) {
                settingsRepository.runAi<IdeasResult>(
                    errorOf = { (it as? IdeasResult.Error)?.reason },
                    error = { IdeasResult.Error(it) }
                ) { ai -> ai.askIdeas(all, entry.question, entry.preset, history) }
                    ?: IdeasResult.Error(L10n.str(R.string.ai_no_provider))
            }
            val state = when (result) {
                is IdeasResult.Success -> IdeasEntryState.Answered(result.answer)
                is IdeasResult.Error -> IdeasEntryState.Failed(result.reason)
            }
            _entries.update { list -> list.map { if (it.id == entry.id) it.copy(state = state) else it } }
            if (state is IdeasEntryState.Answered) {
                AiKeepAlive.announceIfInBackground(L10n.str(R.string.ideas_ready_x, state.answer.title.ifBlank { entry.label }))
            }
        }
    }
}

fun presetLabel(preset: IdeasPreset): String = L10n.str(
    when (preset) {
        IdeasPreset.WEEKLY_MENU -> R.string.ideas_weekly_menu
        IdeasPreset.BREAKFASTS -> R.string.ideas_breakfasts
        IdeasPreset.SNACKS -> R.string.ideas_snacks
        IdeasPreset.LIGHT_DINNERS -> R.string.ideas_light_dinners
        IdeasPreset.RICE_DISHES -> R.string.ideas_rice_dishes
        IdeasPreset.DESSERTS -> R.string.ideas_desserts
        IdeasPreset.SEASONAL -> R.string.ideas_seasonal
    }
)

fun presetEmoji(preset: IdeasPreset): String = when (preset) {
    IdeasPreset.WEEKLY_MENU -> "📅"
    IdeasPreset.BREAKFASTS -> "🥐"
    IdeasPreset.SNACKS -> "🍪"
    IdeasPreset.LIGHT_DINNERS -> "🥗"
    IdeasPreset.RICE_DISHES -> "🥘"
    IdeasPreset.DESSERTS -> "🍰"
    IdeasPreset.SEASONAL -> when (java.time.LocalDate.now().monthValue) {
        12, 1, 2 -> "❄️"
        3, 4, 5 -> "🌸"
        6, 7, 8 -> "☀️"
        else -> "🍂"
    }
}
