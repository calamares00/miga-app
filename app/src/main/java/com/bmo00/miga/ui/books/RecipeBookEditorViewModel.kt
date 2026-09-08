package com.bmo00.miga.ui.books

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bmo00.miga.data.model.RecipeBookDraft
import com.bmo00.miga.data.model.SyncConnection
import com.bmo00.miga.data.repository.RecipeBookNotEmptyException
import com.bmo00.miga.data.repository.RecipeRepository
import com.bmo00.miga.data.sync.SyncEngine
import com.bmo00.miga.data.sync.SyncOutcome
import com.bmo00.miga.ui.navigation.Destinations
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class BookSnapshot(val name: String, val coverPhotoUri: String?)

class RecipeBookEditorViewModel(
    private val repository: RecipeRepository,
    private val bookId: Long
) : ViewModel() {

    private val syncEngine = SyncEngine(repository)

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
    /** Libro instalado desde un pack (ver RecipeBook.isPack): de solo lectura, sin Guardar, solo desinstalar. */
    var isPack by mutableStateOf(false)
        private set

    /** Conexión de sincronización a la que pertenece este libro; null = libro local. A diferencia
     *  de un pack, sigue siendo totalmente editable. */
    var syncConnectionId by mutableStateOf<Long?>(null)
        private set
    val availableSyncConnections: StateFlow<List<SyncConnection>> = repository.observeSyncConnections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    var isSyncingNow by mutableStateOf(false)
        private set
    var syncNowError by mutableStateOf<String?>(null)

    private var initialSnapshot: BookSnapshot? = null

    init {
        if (isEditing) {
            viewModelScope.launch {
                repository.observeRecipeBook(bookId).first()?.let { book ->
                    name = book.name
                    coverPhotoUri = book.coverPhotoUri
                    isPack = book.isPack
                    syncConnectionId = book.syncConnectionId
                }
                isLoading = false
                initialSnapshot = BookSnapshot(name, coverPhotoUri)
            }
        } else {
            initialSnapshot = BookSnapshot(name, coverPhotoUri)
        }
    }

    /** Se comprueba al intentar salir del editor (botón atrás o icono de cancelar) para avisar antes de perder cambios. */
    fun hasUnsavedChanges(): Boolean = initialSnapshot?.let { it != BookSnapshot(name, coverPhotoUri) } ?: false

    fun save(onSaved: () -> Unit) {
        if (isPack) return
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
            if (isPack) {
                repository.uninstallPack(bookId)
                isDeleting = false
                onDeleted()
                return@launch
            }
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

    /** Vincula este libro a una conexión: pasa a sincronizarse (lectura-escritura) con las demás
     *  apps conectadas al mismo namespace. */
    fun linkToSyncConnection(connectionId: Long) {
        if (!isEditing || isPack) return
        viewModelScope.launch {
            repository.linkBookToSyncConnection(bookId, connectionId)
            syncConnectionId = connectionId
        }
    }

    /** Deja de sincronizar: el libro pasa a ser local, sin borrar nada de su contenido ni del servidor. */
    fun unlinkFromSyncConnection() {
        if (!isEditing) return
        viewModelScope.launch {
            repository.unlinkBookFromSyncConnection(bookId)
            syncConnectionId = null
        }
    }

    fun syncNow(context: Context) {
        val connectionId = syncConnectionId ?: return
        viewModelScope.launch {
            isSyncingNow = true
            syncNowError = null
            when (val outcome = syncEngine.syncConnection(context, connectionId)) {
                is SyncOutcome.Error -> syncNowError = outcome.reason
                is SyncOutcome.Success -> repository.observeRecipeBook(bookId).first()?.let { book ->
                    name = book.name
                    coverPhotoUri = book.coverPhotoUri
                    initialSnapshot = BookSnapshot(name, coverPhotoUri)
                }
            }
            isSyncingNow = false
        }
    }
}
