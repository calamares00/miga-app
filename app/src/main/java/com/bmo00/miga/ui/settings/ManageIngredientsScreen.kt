package com.bmo00.miga.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmo00.miga.data.repository.RecipeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ManageIngredientsViewModel(private val repository: RecipeRepository) : ViewModel() {
    val items: StateFlow<List<CatalogItem>> = repository.observeIngredientCatalog()
        .map { list -> list.map { CatalogItem(it.id, it.name) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(name: String) {
        viewModelScope.launch { repository.addIngredientName(name) }
    }

    fun rename(item: CatalogItem, newName: String) {
        viewModelScope.launch { repository.renameIngredientName(item.id, newName) }
    }

    fun delete(item: CatalogItem) {
        viewModelScope.launch { repository.deleteIngredientName(item.id) }
    }
}

@Composable
fun ManageIngredientsScreen(viewModel: ManageIngredientsViewModel, onBack: () -> Unit) {
    val items by viewModel.items.collectAsState()
    ManageCatalogScreen(
        title = "Ingredientes",
        items = items,
        onBack = onBack,
        onAdd = viewModel::add,
        onRename = viewModel::rename,
        onDelete = viewModel::delete
    )
}
