package org.calamares.miga.ui.settings

import org.calamares.miga.L10n
import org.calamares.miga.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.calamares.miga.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ManageCategoriesViewModel(private val repository: RecipeRepository) : ViewModel() {
    private val _items = MutableStateFlow<List<CatalogItem>>(emptyList())
    val items: StateFlow<List<CatalogItem>> = _items

    init {
        viewModelScope.launch {
            repository.observeCategories().collect { categories ->
                _items.value = categories.map { CatalogItem(it.id, it.name, repository.countRecipesUsingCategory(it.id)) }
            }
        }
    }

    fun add(name: String) {
        viewModelScope.launch { repository.addCategory(name) }
    }

    fun rename(item: CatalogItem, newName: String) {
        viewModelScope.launch { repository.renameCategory(item.id, newName) }
    }

    fun delete(item: CatalogItem) {
        viewModelScope.launch { repository.deleteCategory(item.id) }
    }
}

@Composable
fun ManageCategoriesScreen(viewModel: ManageCategoriesViewModel, onBack: () -> Unit) {
    val items by viewModel.items.collectAsState()
    ManageCatalogScreen(
        title = L10n.str(R.string.categorias),
        items = items,
        onBack = onBack,
        onAdd = viewModel::add,
        onRename = viewModel::rename,
        onDelete = viewModel::delete
    )
}
