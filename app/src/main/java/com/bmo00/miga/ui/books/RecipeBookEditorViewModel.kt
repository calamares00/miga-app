package com.bmo00.miga.ui.books

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmo00.miga.data.model.RecipeBookDraft
import com.bmo00.miga.data.repository.RecipeBookNotEmptyException
import com.bmo00.miga.data.repository.RecipeRepository
import com.bmo00.miga.ui.navigation.Destinations
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RecipeBookEditorViewModel(
    private val repository: RecipeRepository,
    private val bookId: Long
) : ViewModel() {

    val isEditing: Boolean = bookId != Destinations.NEW_BOOK_ID

    var isLoading by mutableStateOf(isEditing)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var nameError by mutableStateOf(false)

    var name by mutableStateOf("")
    var coverPhotoUri by mutableStateOf<String?>(null)
    var isDeleting by mutableStateOf(false)
        private set
    var deleteError by mutableStateOf<String?>(null)

    init {
        if (isEditing) {
            viewModelScope.launch {
                repository.observeRecipeBook(bookId).first()?.let { book ->
                    name = book.name
                    coverPhotoUri = book.coverPhotoUri
                }
                isLoading = false
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        if (name.isBlank()) {
            nameError = true
            return
        }
        viewModelScope.launch {
            isSaving = true
            repository.saveRecipeBook(
                RecipeBookDraft(id = if (isEditing) bookId else 0L, name = name, coverPhotoUri = coverPhotoUri)
            )
            isSaving = false
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        if (!isEditing) return
        viewModelScope.launch {
            isDeleting = true
            try {
                repository.deleteRecipeBook(bookId)
                onDeleted()
            } catch (e: RecipeBookNotEmptyException) {
                deleteError = "Este libro tiene ${e.recipeCount} " +
                    (if (e.recipeCount == 1) "receta" else "recetas") +
                    ". Muévelas o bórralas antes de eliminar el libro."
            } finally {
                isDeleting = false
            }
        }
    }
}
