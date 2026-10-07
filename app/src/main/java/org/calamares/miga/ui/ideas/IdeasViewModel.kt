package org.calamares.miga.ui.ideas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
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
import org.calamares.miga.data.ideas.IdeasDish
import org.calamares.miga.data.ideas.IdeasFilters
import org.calamares.miga.data.ideas.IdeasMeal
import org.calamares.miga.data.ideas.IdeasStyle
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

/** A question (typed or built from the options) and its answer. [label] is what the conversation shows. */
data class IdeasEntry(val id: Int, val label: String, val question: String, val filters: IdeasFilters?, val state: IdeasEntryState)

/**
 * AI recommendations based on the user's recipes: options that can be combined (meal, style, kind
 * of dish, utensil) and free questions, kept as a conversation so follow-ups have context.
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

    private val _filters = MutableStateFlow(IdeasFilters())

    /** Options chosen in the "What do you fancy?" panel. */
    val filters: StateFlow<IdeasFilters> = _filters

    /** Utensils typed by the user (e.g. "Thermomix TM31"), offered next to the saved ones. */
    private val customUtensils = MutableStateFlow<List<String>>(emptyList())

    /** Saved utensils plus the ones typed in this screen, without duplicates. */
    val utensilOptions: StateFlow<List<String>> = combine(repository.observeUtensils(), customUtensils) { saved, custom ->
        (saved.map { it.name } + custom).distinctBy { it.trim().lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setMeal(meal: IdeasMeal) = _filters.update { it.copy(meal = if (it.meal == meal) null else meal) }

    fun toggleStyle(style: IdeasStyle) = _filters.update { it.copy(styles = it.styles.toggled(style)) }

    fun toggleDish(dish: IdeasDish) = _filters.update { it.copy(dishes = it.dishes.toggled(dish)) }

    fun toggleUtensil(name: String) = _filters.update { filters ->
        val selected = filters.utensils.any { it.equals(name, ignoreCase = true) }
        filters.copy(utensils = if (selected) filters.utensils.filterNot { it.equals(name, ignoreCase = true) } else filters.utensils + name)
    }

    /** Adds a utensil typed by the user and selects it. */
    fun addCustomUtensil(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        customUtensils.update { list -> if (list.any { it.equals(trimmed, ignoreCase = true) }) list else list + trimmed }
        if (_filters.value.utensils.none { it.equals(trimmed, ignoreCase = true) }) toggleUtensil(trimmed)
    }

    fun clearFilters() {
        _filters.value = IdeasFilters()
    }

    /** Asks for ideas with the chosen options. */
    fun askWithFilters() {
        val filters = _filters.value
        if (filters.isEmpty) return
        ask(filtersLabel(filters), "", filters)
    }

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

    private fun ask(label: String, question: String, filters: IdeasFilters?) {
        if (_entries.value.any { it.state == IdeasEntryState.Loading }) return
        run(IdeasEntry(nextId++, label, question, filters, IdeasEntryState.Loading))
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
                ) { ai -> ai.askIdeas(all, entry.question, entry.filters, history) }
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

private fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item

/** Short summary of the chosen options, e.g. "Dinner · Healthy · Airfryer". */
fun filtersLabel(filters: IdeasFilters): String =
    (listOfNotNull(filters.meal?.let { mealLabel(it) }) + filters.styles.map { styleLabel(it) } +
        filters.dishes.map { dishLabel(it) } + filters.utensils).joinToString(" · ")

fun mealLabel(meal: IdeasMeal): String = L10n.str(
    when (meal) {
        IdeasMeal.WEEKLY_MENU -> R.string.ideas_weekly_menu
        IdeasMeal.BREAKFAST -> R.string.ideas_meal_breakfast
        IdeasMeal.LUNCH -> R.string.ideas_meal_lunch
        IdeasMeal.SNACK -> R.string.ideas_meal_snack
        IdeasMeal.DINNER -> R.string.ideas_meal_dinner
        IdeasMeal.STARTER -> R.string.ideas_meal_starter
        IdeasMeal.DESSERT -> R.string.ideas_meal_dessert
    }
)

fun mealEmoji(meal: IdeasMeal): String = when (meal) {
    IdeasMeal.WEEKLY_MENU -> "📅"
    IdeasMeal.BREAKFAST -> "🥐"
    IdeasMeal.LUNCH -> "🍲"
    IdeasMeal.SNACK -> "🍪"
    IdeasMeal.DINNER -> "🌙"
    IdeasMeal.STARTER -> "🫒"
    IdeasMeal.DESSERT -> "🍰"
}

fun styleLabel(style: IdeasStyle): String = L10n.str(
    when (style) {
        IdeasStyle.HEALTHY -> R.string.ideas_style_healthy
        IdeasStyle.LIGHT -> R.string.ideas_style_light
        IdeasStyle.QUICK -> R.string.ideas_style_quick
        IdeasStyle.BUDGET -> R.string.ideas_style_budget
        IdeasStyle.VEGETARIAN -> R.string.ideas_style_vegetarian
        IdeasStyle.VEGAN -> R.string.ideas_style_vegan
        IdeasStyle.GLUTEN_FREE -> R.string.ideas_style_gluten_free
        IdeasStyle.KIDS -> R.string.ideas_style_kids
        IdeasStyle.SEASONAL -> R.string.ideas_seasonal
    }
)

fun dishLabel(dish: IdeasDish): String = L10n.str(
    when (dish) {
        IdeasDish.RICE -> R.string.ideas_dish_rice
        IdeasDish.PASTA -> R.string.ideas_dish_pasta
        IdeasDish.LEGUMES -> R.string.ideas_dish_legumes
        IdeasDish.VEGETABLES -> R.string.ideas_dish_vegetables
        IdeasDish.FISH -> R.string.ideas_dish_fish
        IdeasDish.MEAT -> R.string.ideas_dish_meat
        IdeasDish.EGGS -> R.string.ideas_dish_eggs
        IdeasDish.SOUPS -> R.string.ideas_dish_soups
        IdeasDish.SALADS -> R.string.ideas_dish_salads
        IdeasDish.BAKING -> R.string.ideas_dish_baking
    }
)

/** Season emoji for the "in season" option (northern hemisphere). */
fun seasonEmoji(): String = when (java.time.LocalDate.now().monthValue) {
    12, 1, 2 -> "❄️"
    3, 4, 5 -> "🌸"
    6, 7, 8 -> "☀️"
    else -> "🍂"
}
