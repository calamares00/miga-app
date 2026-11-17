package org.calamares.miga.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How long a deletion can be undone: a little longer than a long snackbar. */
const val UNDO_WINDOW_MILLIS = 12_000L

/** A deletion that can still be undone, shown as a snackbar with "Undo". */
class UndoOffer internal constructor(
    val message: String,
    internal val recipeIds: Set<Long>,
    internal val bookIds: Set<Long>,
    internal val delete: suspend () -> Unit
) {
    internal var job: Job? = null
}

/**
 * Deletions of recipes and books that wait for their "Undo" to expire. The items are hidden at
 * once (the repositories leave [recipeIds] and [bookIds] out of their lists) and really deleted
 * after [windowMillis], unless undone. When the app goes to the background they are deleted at
 * once ([commitAll]): Android may close it before the window is over, and a deleted recipe must
 * not come back.
 */
class PendingDeletions(private val scope: CoroutineScope, private val windowMillis: Long = UNDO_WINDOW_MILLIS) {

    private val _recipeIds = MutableStateFlow<Set<Long>>(emptySet())
    val recipeIds: StateFlow<Set<Long>> = _recipeIds

    private val _bookIds = MutableStateFlow<Set<Long>>(emptySet())
    val bookIds: StateFlow<Set<Long>> = _bookIds

    /** The latest deletion not yet offered to the user; a screen shows it and calls [offered]. */
    private val _offer = MutableStateFlow<UndoOffer?>(null)
    val offer: StateFlow<UndoOffer?> = _offer

    /** Hides the items now and runs [delete] when the undo window is over. */
    fun schedule(message: String, recipeIds: Set<Long> = emptySet(), bookIds: Set<Long> = emptySet(), delete: suspend () -> Unit): UndoOffer {
        val offer = UndoOffer(message, recipeIds, bookIds, delete)
        _recipeIds.update { it + recipeIds }
        _bookIds.update { it + bookIds }
        synchronized(offers) { offers += offer }
        offer.job = scope.launch {
            delay(windowMillis)
            run(offer)
        }
        _offer.value = offer
        return offer
    }

    /** Deletes everything still waiting, now; called when the app goes to the background. */
    fun commitAll() {
        synchronized(offers) { offers.toList() }.forEach { offer ->
            if (offer.job?.isActive != true) return@forEach
            offer.job?.cancel()
            scope.launch { run(offer) }
        }
    }

    private val offers = mutableListOf<UndoOffer>()

    private suspend fun run(offer: UndoOffer) {
        try {
            offer.delete()
        } finally {
            release(offer)
        }
    }

    /** Brings the items back; nothing happens once the deletion has run. */
    fun undo(offer: UndoOffer) {
        if (offer.job?.isActive != true) return
        offer.job?.cancel()
        release(offer)
    }

    /** The screen has shown [offer], so returning to it does not show it again. */
    fun offered(offer: UndoOffer) {
        _offer.update { if (it === offer) null else it }
    }

    private fun release(offer: UndoOffer) {
        synchronized(offers) { offers.remove(offer) }
        _recipeIds.update { it - offer.recipeIds }
        _bookIds.update { it - offer.bookIds }
        offered(offer)
    }
}
