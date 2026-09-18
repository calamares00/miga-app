package com.bmo00.miga.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmo00.miga.data.model.Difficulty
import com.bmo00.miga.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class MostCookedEntry(val recipeId: Long, val name: String, val timesCooked: Int)
data class CountEntry(val label: String, val count: Int)

data class StatsUiState(
    val isLoading: Boolean = true,
    val totalRecipes: Int = 0,
    val totalBooks: Int = 0,
    val favoritesCount: Int = 0,
    val addedThisMonth: Int = 0,
    val mostCooked: List<MostCookedEntry> = emptyList(),
    val byDifficulty: List<CountEntry> = emptyList(),
    val byCategory: List<CountEntry> = emptyList()
)

private const val MAX_CATEGORY_ENTRIES = 8
private const val MAX_MOST_COOKED_ENTRIES = 5

/**
 * Estadísticas calculadas en memoria a partir de lo que ya carga [RecipeRepository.getAllRecipesOnce]/
 * [RecipeRepository.getAllRecipeBooksOnce] (sin nuevas queries agregadas en Room) - no hay
 * histórico de "cuándo se cocinó cada vez" en el modelo de datos (solo un contador,
 * [com.bmo00.miga.data.model.Recipe.timesCooked]), así que no se calcula una racha de días, solo
 * un ranking de recetas más cocinadas.
 */
class StatsViewModel(private val repository: RecipeRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState

    init {
        viewModelScope.launch {
            val recipes = repository.getAllRecipesOnce()
            val books = repository.getAllRecipeBooksOnce()

            val byDifficulty = Difficulty.entries
                .map { difficulty -> CountEntry(difficulty.label, recipes.count { it.difficulty == difficulty }) }
                .filter { it.count > 0 }

            val byCategory = recipes
                .groupBy { it.categoryName?.takeIf { name -> name.isNotBlank() } ?: "Sin categoría" }
                .map { (name, group) -> CountEntry(name, group.size) }
                .sortedByDescending { it.count }
                .take(MAX_CATEGORY_ENTRIES)

            val mostCooked = recipes
                .filter { it.timesCooked > 0 }
                .sortedByDescending { it.timesCooked }
                .take(MAX_MOST_COOKED_ENTRIES)
                .map { MostCookedEntry(it.id, it.name, it.timesCooked) }

            _uiState.value = StatsUiState(
                isLoading = false,
                totalRecipes = recipes.size,
                totalBooks = books.size,
                favoritesCount = recipes.count { it.isFavorite },
                addedThisMonth = recipes.count { isThisMonth(it.createdAt) },
                mostCooked = mostCooked,
                byDifficulty = byDifficulty,
                byCategory = byCategory
            )
        }
    }

    private fun isThisMonth(epochMillis: Long): Boolean {
        val entryCalendar = Calendar.getInstance().apply { timeInMillis = epochMillis }
        val nowCalendar = Calendar.getInstance()
        return entryCalendar.get(Calendar.YEAR) == nowCalendar.get(Calendar.YEAR) &&
            entryCalendar.get(Calendar.MONTH) == nowCalendar.get(Calendar.MONTH)
    }
}
