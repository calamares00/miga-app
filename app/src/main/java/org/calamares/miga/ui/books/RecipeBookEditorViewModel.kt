package org.calamares.miga.ui.books

import org.calamares.miga.L10n
import org.calamares.miga.R
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.calamares.miga.data.model.RecipeBookDraft
import org.calamares.miga.data.model.SyncConnection
import org.calamares.miga.data.repository.RecipeBookNotEmptyException
import org.calamares.miga.data.repository.RecipeRepository
import org.calamares.miga.data.sync.SyncEngine
import org.calamares.miga.data.sync.SyncOutcome
import org.calamares.miga.ui.navigation.Destinations
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
    /** Book installed from a pack (see RecipeBook.isPack): read-only, no Save, only uninstall. */
    var isPack by mutableStateOf(false)
        private set

    /**
     * Sync connection this book belongs to; null for a local book. Unlike a pack it stays fully
     * editable.
     */
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

    /**
     * Checked when leaving the editor (back button or cancel icon) to warn before losing changes.
     */
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
                deleteError = if (e.recipeCount == 1) L10n.str(R.string.book_not_empty_one)
                else L10n.str(R.string.book_not_empty_many, e.recipeCount)
            } finally {
                isDeleting = false
            }
        }
    }

    /**
     * Links this book to a connection so it syncs (read-write) with the other devices on the same
     * namespace.
     */
    fun linkToSyncConnection(connectionId: Long) {
        if (!isEditing || isPack) return
        viewModelScope.launch {
            repository.linkBookToSyncConnection(bookId, connectionId)
            syncConnectionId = connectionId
        }
    }

    /** Stops syncing: the book becomes local and nothing is deleted locally or on the server. */
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
            repository.resetSyncCursor(connectionId) // manual repair: download everything again to recover lost photos
            repository.enqueueFullBookResync(bookId, connectionId)
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
