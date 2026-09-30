package com.bmo00.miga.ui.shoppinglist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmo00.miga.data.export.RecipeExporter
import com.bmo00.miga.data.model.Ingredient
import com.bmo00.miga.data.model.ParsedShoppingEntry
import com.bmo00.miga.data.model.ShoppingEntryParser
import com.bmo00.miga.data.model.ShoppingListGroup
import com.bmo00.miga.data.model.ShoppingListItem
import com.bmo00.miga.data.model.ShoppingSuggestion
import com.bmo00.miga.data.repository.RecipeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShoppingListViewModel(private val repository: RecipeRepository) : ViewModel() {

    val groups: StateFlow<List<ShoppingListGroup>> = repository.observeShoppingList()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<ShoppingSuggestion>> = repository.observeShoppingHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val catalogNames: StateFlow<List<String>> = repository.observeIngredientNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setChecked(id: Long, checked: Boolean) {
        viewModelScope.launch { repository.setShoppingListItemChecked(id, checked) }
    }

    /** [onDeleted] se llama una vez quitado, para ofrecer "Deshacer" con [restoreItem]. */
    fun deleteItem(item: ShoppingListItem, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteShoppingListItem(item.id)
            onDeleted()
        }
    }

    fun restoreItem(item: ShoppingListItem) {
        viewModelScope.launch { repository.restoreShoppingListItem(item) }
    }

    /** Interpreta [text] (uno o varios artículos, con cantidad/unidad) y los añade; devuelve cuántos. */
    fun addEntries(text: String, splitOnY: Boolean = false): Int {
        val entries = ShoppingEntryParser.parse(text, splitOnY)
        addParsedEntries(entries)
        return entries.size
    }

    fun addParsedEntries(entries: List<ParsedShoppingEntry>) {
        if (entries.isEmpty()) return
        viewModelScope.launch {
            repository.addShoppingListEntries(entries.map { Ingredient(it.name, it.quantity, it.unit) })
        }
    }

    /** Añade una sugerencia; lo ya escrito ([typed]) manda sobre lo recordado del historial. */
    fun addSuggestion(suggestion: ShoppingSuggestion, typed: ParsedShoppingEntry?) {
        val quantity = typed?.quantity ?: suggestion.quantity
        val unit = typed?.unit ?: if (typed?.quantity == null) suggestion.unit else null
        addParsedEntries(listOf(ParsedShoppingEntry(suggestion.name, quantity, unit)))
    }

    /** Artículos pendientes (sin marcar), tal cual se comparten por QR. */
    fun pendingEntries(): List<ParsedShoppingEntry> =
        groups.value.flatMap { it.items }.filter { !it.checked }.map { ParsedShoppingEntry(it.name, it.quantity, it.unit) }

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
