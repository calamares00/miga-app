package com.bmo00.miga.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmo00.miga.data.model.Recipe
import com.bmo00.miga.data.model.RecipeBookSummary
import com.bmo00.miga.data.repository.RecipeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecipeDetailViewModel(
    private val repository: RecipeRepository,
    private val recipeId: Long
) : ViewModel() {

    val recipe: StateFlow<Recipe?> = repository.observeRecipe(recipeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val recipeBooks: StateFlow<List<RecipeBookSummary>> = repository.observeRecipeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleFavorite() {
        val current = recipe.value ?: return
        viewModelScope.launch { repository.toggleFavorite(current.id, !current.isFavorite) }
    }

    fun markCooked() {
        viewModelScope.launch { repository.markCooked(recipeId) }
    }

    fun moveToBook(newBookId: Long) {
        viewModelScope.launch { repository.moveRecipeToBook(recipeId, newBookId) }
    }

    fun deleteRecipe(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteRecipe(recipeId)
            onDeleted()
        }
    }
}
