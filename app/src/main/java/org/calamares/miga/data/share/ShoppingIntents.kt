package org.calamares.miga.data.share

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A request for the shopping list coming from another app or from the widget. */
sealed interface ShoppingIntentEvent {
    /** Text shared to Miga from another app (notes, messages...), parsed as items. */
    data class SharedText(val text: String) : ShoppingIntentEvent

    /** Open the list with the add field ready to type (widget button). */
    data object QuickAdd : ShoppingIntentEvent
}

/**
 * Mailbox between MainActivity, which receives the intents, and the shopping list screen. The event
 * is kept until the screen consumes it, so it is not lost when the app starts behind the biometric
 * lock or the screen is not composed yet.
 */
object ShoppingIntents {

    const val ACTION_QUICK_ADD = "org.calamares.miga.action.SHOPPING_QUICK_ADD"
    private const val MAX_SHARED_CHARS = 20_000

    private val _event = MutableStateFlow<ShoppingIntentEvent?>(null)
    val event: StateFlow<ShoppingIntentEvent?> = _event

    /**
     * Returns true when [intent] was meant for the shopping list and has been stored as an event.
     */
    fun handle(intent: Intent?): Boolean {
        intent ?: return false
        when {
            intent.action == ACTION_QUICK_ADD -> _event.value = ShoppingIntentEvent.QuickAdd
            intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.take(MAX_SHARED_CHARS)
                if (text.isNullOrEmpty()) return false
                _event.value = ShoppingIntentEvent.SharedText(text)
            }
            else -> return false
        }
        return true
    }

    fun consume() {
        _event.value = null
    }
}
