package com.bmo00.miga.data.share

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Algo que otra app o el widget le pide a la lista de la compra. */
sealed interface ShoppingIntentEvent {
    /** Texto compartido hacia Miga desde otra app (notas, mensajes...): se interpreta como artículos. */
    data class SharedText(val text: String) : ShoppingIntentEvent

    /** Abrir la lista con el campo de añadir listo para escribir (botón del widget). */
    data object QuickAdd : ShoppingIntentEvent
}

/**
 * Buzón entre MainActivity (que recibe los intents) y la pantalla de la lista de la compra. El
 * evento se queda guardado hasta que la pantalla lo consume, así no se pierde si la app arranca
 * bloqueada por la huella o la pantalla aún no está compuesta.
 */
object ShoppingIntents {

    const val ACTION_QUICK_ADD = "com.bmo00.miga.action.SHOPPING_QUICK_ADD"
    private const val MAX_SHARED_CHARS = 20_000

    private val _event = MutableStateFlow<ShoppingIntentEvent?>(null)
    val event: StateFlow<ShoppingIntentEvent?> = _event

    /** true si [intent] era para la lista de la compra y se ha guardado como evento. */
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
