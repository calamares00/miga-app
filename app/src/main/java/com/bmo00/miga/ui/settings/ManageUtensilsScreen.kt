package com.bmo00.miga.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmo00.miga.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ManageUtensilsViewModel(private val repository: RecipeRepository) : ViewModel() {
    private val _items = MutableStateFlow<List<CatalogItem>>(emptyList())
    val items: StateFlow<List<CatalogItem>> = _items

    init {
        viewModelScope.launch {
            repository.observeUtensils().collect { utensils ->
                _items.value = utensils.map { CatalogItem(it.id, it.name, repository.countRecipesUsingUtensil(it.id)) }
            }
        }
    }

    fun add(name: String) {
        viewModelScope.launch { repository.addUtensil(name) }
    }

    fun rename(item: CatalogItem, newName: String) {
        viewModelScope.launch { repository.renameUtensil(item.id, newName) }
    }

    fun delete(item: CatalogItem) {
        viewModelScope.launch { repository.deleteUtensil(item.id) }
    }
}

@Composable
fun ManageUtensilsScreen(viewModel: ManageUtensilsViewModel, onBack: () -> Unit) {
    val items by viewModel.items.collectAsState()
    ManageCatalogScreen(
        title = "Utensilios",
        items = items,
        onBack = onBack,
        onAdd = viewModel::add,
        onRename = viewModel::rename,
        onDelete = viewModel::delete
    )
}
