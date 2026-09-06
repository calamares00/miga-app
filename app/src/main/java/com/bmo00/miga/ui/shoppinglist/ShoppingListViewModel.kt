package com.bmo00.miga.ui.shoppinglist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmo00.miga.data.export.RecipeExporter
import com.bmo00.miga.data.model.ShoppingListGroup
import com.bmo00.miga.data.repository.RecipeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShoppingListViewModel(private val repository: RecipeRepository) : ViewModel() {

    val groups: StateFlow<List<ShoppingListGroup>> = repository.observeShoppingList()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setChecked(id: Long, checked: Boolean) {
        viewModelScope.launch { repository.setShoppingListItemChecked(id, checked) }
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch { repository.deleteShoppingListItem(id) }
    }

    fun addManualItem(name: String, quantity: Double?, unit: String?) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.addManualShoppingListItem(name, quantity, unit) }
    }

    fun clearAll() {
        viewModelScope.launch { repository.clearShoppingList() }
    }

    fun clearChecked() {
        viewModelScope.launch { repository.clearCheckedShoppingListItems() }
    }

    fun share(context: Context) {
        RecipeExporter.shareShoppingListAsText(context, groups.value)
    }
}
