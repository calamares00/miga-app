package org.calamares.miga.ui.sync

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.repository.RecipeRepository
import org.calamares.miga.data.sync.BookCatalogResult
import org.calamares.miga.data.sync.ServerBookDto
import org.calamares.miga.data.sync.SyncClient
import org.calamares.miga.data.sync.SyncEngine
import org.calamares.miga.data.sync.SyncOutcome

/** A book of the server: whether it is on this device and whether the user has seen it listed. */
data class ServerBook(val dto: ServerBookDto, val onDevice: Boolean, val isNew: Boolean)

data class ServerBooksUiState(
    val label: String = "",
    val server: String = "",
    val loading: Boolean = true,
    /** The server is older than 1.3.0: it syncs every book and cannot be asked for some. */
    val unsupported: Boolean = false,
    val error: String? = null,
    val books: List<ServerBook> = emptyList(),
    /** Books ticked: the ones this device will sync. */
    val chosen: Set<String> = emptySet(),
    /** Books leaving this device that stay as a local copy instead of being removed. */
    val keepCopies: Set<String> = emptySet(),
    val followNewBooks: Boolean = true,
    /** A book on this device the user unticked: asked before it is removed. */
    val askRemove: ServerBook? = null,
    val saving: Boolean = false
) {
    val toDownload: List<ServerBook> get() = books.filter { it.dto.uid in chosen && !it.onDevice }
    val toRemove: List<ServerBook> get() = books.filter { it.onDevice && it.dto.uid !in chosen }
    val chosenBytes: Long get() = books.filter { it.dto.uid in chosen }.sumOf { it.dto.bytes }
    val hasChanges: Boolean get() = toDownload.isNotEmpty() || toRemove.isNotEmpty()
}

/**
 * The books of one sync server and which of them this device syncs (miga-server 1.3.0). Saving
 * uploads pending changes first, tells the server the new choice, removes from this device the
 * books left out (or keeps them as local copies) and downloads the new ones. [firstTime] is the
 * step right after joining a server, before anything was downloaded.
 */
class ServerBooksViewModel(
    private val repository: RecipeRepository,
    private val connectionId: Long,
    val firstTime: Boolean,
    context: Context
) : ViewModel() {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val engine = SyncEngine(repository)

    private val _state = MutableStateFlow(ServerBooksUiState())
    val state: StateFlow<ServerBooksUiState> = _state.asStateFlow()

    /** One-off message for a snackbar; [done] closes the screen once shown. */
    private val _message = MutableStateFlow<Pair<String, Boolean>?>(null)
    val message: StateFlow<Pair<String, Boolean>?> = _message.asStateFlow()

    init {
        load()
    }

    private fun seenKey() = "seen_$connectionId"

    fun load() {
        viewModelScope.launch {
            val connection = repository.sync.getSyncConnectionOnce(connectionId) ?: return@launch
            _state.update { it.copy(label = connection.label, server = connection.serverUrl.substringAfter("://"), loading = true, error = null) }
            when (val result = SyncClient.fetchBookCatalog(connection)) {
                BookCatalogResult.Unsupported -> _state.update { it.copy(loading = false, unsupported = true) }
                is BookCatalogResult.Error -> _state.update { it.copy(loading = false, error = result.reason) }
                is BookCatalogResult.Success -> {
                    val local = repository.sync.syncedBooks(connectionId)
                    val seen = prefs.getStringSet(seenKey(), emptySet()).orEmpty()
                    val catalog = result.catalog
                    val books = catalog.books.map { dto ->
                        ServerBook(dto, onDevice = dto.uid in local, isNew = !firstTime && !dto.followed && dto.uid !in seen && dto.uid !in local)
                    }
                    _state.update {
                        it.copy(
                            loading = false,
                            books = books,
                            chosen = books.filter { book -> book.dto.followed || book.onDevice }.map { book -> book.dto.uid }.toSet(),
                            keepCopies = emptySet(),
                            followNewBooks = catalog.followNewBooks
                        )
                    }
                }
            }
        }
    }

    /** Ticks or unticks a book; unticking one that is on this device asks first. */
    fun toggle(uid: String) {
        val current = _state.value
        val book = current.books.firstOrNull { it.dto.uid == uid } ?: return
        if (uid in current.chosen) {
            if (book.onDevice) _state.update { it.copy(askRemove = book) } else _state.update { it.copy(chosen = it.chosen - uid) }
        } else {
            _state.update { it.copy(chosen = it.chosen + uid, keepCopies = it.keepCopies - uid) }
        }
    }

    fun confirmRemove(keepCopy: Boolean) {
        val book = _state.value.askRemove ?: return
        _state.update {
            it.copy(
                askRemove = null,
                chosen = it.chosen - book.dto.uid,
                keepCopies = if (keepCopy) it.keepCopies + book.dto.uid else it.keepCopies - book.dto.uid
            )
        }
    }

    fun cancelRemove() = _state.update { it.copy(askRemove = null) }

    fun setFollowNewBooks(follow: Boolean) = _state.update { it.copy(followNewBooks = follow) }

    fun consumeMessage() {
        _message.value = null
    }

    fun save(context: Context) {
        val current = _state.value
        if (current.saving || current.loading) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            // Changes made on this device reach the server before any book leaves it.
            if (current.toRemove.isNotEmpty()) {
                val pushed = engine.syncConnection(context, connectionId)
                if (pushed is SyncOutcome.Error) {
                    _state.update { it.copy(saving = false) }
                    _message.value = L10n.str(R.string.server_books_not_saved, pushed.reason) to false
                    return@launch
                }
            }
            val connection = repository.sync.getSyncConnectionOnce(connectionId) ?: return@launch
            val selection = SyncClient.putBookSelection(connection, current.chosen, current.followNewBooks)
            if (selection !is BookCatalogResult.Success) {
                _state.update { it.copy(saving = false) }
                val reason = (selection as? BookCatalogResult.Error)?.reason ?: L10n.str(R.string.server_books_unsupported)
                _message.value = L10n.str(R.string.server_books_not_saved, reason) to false
                return@launch
            }
            val local = repository.sync.syncedBooks(connectionId)
            current.toRemove.forEach { book ->
                val id = local[book.dto.uid] ?: return@forEach
                // The server already knows: no "unfollow" is queued.
                if (book.dto.uid in current.keepCopies) repository.sync.keepSyncedBookAsLocal(id, unfollow = false)
                else repository.sync.removeSyncedBookLocally(id, unfollow = false)
            }
            val failed = engine.downloadBooks(context, connectionId, current.toDownload.map { it.dto.uid })
            prefs.edit().putStringSet(seenKey(), current.books.map { it.dto.uid }.toSet()).apply()
            _state.update { it.copy(saving = false) }
            _message.value = when {
                failed > 0 -> L10n.str(R.string.server_books_some_failed, failed) to false
                else -> L10n.str(R.string.server_books_saved) to true
            }
            if (failed > 0) load()
        }
    }

    companion object {
        private const val PREFS = "server_books"
    }
}
