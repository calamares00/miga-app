package org.calamares.miga.ui.settings

import org.calamares.miga.L10n
import org.calamares.miga.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.calamares.miga.data.repository.DefaultCatalog
import org.calamares.miga.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ManageIngredientCategoriesViewModel(private val repository: RecipeRepository) : ViewModel() {
    private val _items = MutableStateFlow<List<CatalogItem>>(emptyList())
    val items: StateFlow<List<CatalogItem>> = _items

    init {
        viewModelScope.launch {
            repository.observeIngredientCategories().collect { categories ->
                _items.value = categories.map { CatalogItem(it.id, it.name, repository.countIngredientsUsingCategory(it.id), DefaultCatalog.isDefaultIngredientCategory(it.name)) }
            }
        }
    }

    fun add(name: String) {
        viewModelScope.launch { repository.addIngredientCategory(name) }
    }

    fun rename(item: CatalogItem, newName: String) {
        viewModelScope.launch { repository.renameIngredientCategory(item.id, newName) }
    }

    fun delete(item: CatalogItem) {
        viewModelScope.launch { repository.deleteIngredientCategory(item.id) }
    }
}

@Composable
fun ManageIngredientCategoriesScreen(viewModel: ManageIngredientCategoriesViewModel, onBack: () -> Unit) {
    val items by viewModel.items.collectAsState()
    ManageCatalogScreen(
        title = L10n.str(R.string.ingredient_categories),
        items = items,
        onBack = onBack,
        onAdd = viewModel::add,
        onRename = viewModel::rename,
        onDelete = viewModel::delete,
        usageLabel = { count -> if (count == 1) L10n.str(R.string.ingredient_count_one) else L10n.str(R.string.ingredient_count_many, count) }
    )
}
