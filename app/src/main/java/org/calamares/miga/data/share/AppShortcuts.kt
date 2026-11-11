package org.calamares.miga.data.share

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * App shortcuts (long press on the app icon, see res/xml/shortcuts.xml) that open a screen. Like
 * [ShoppingIntents], the request is kept until the navigation consumes it, so it survives the
 * biometric lock. "Add to shopping list" reuses [ShoppingIntents.ACTION_QUICK_ADD].
 */
object AppShortcuts {

    const val ACTION_NEW_RECIPE = "org.calamares.miga.action.NEW_RECIPE"

    private val _newRecipe = MutableStateFlow(false)
    val newRecipe: StateFlow<Boolean> = _newRecipe

    fun handle(intent: Intent?): Boolean {
        if (intent?.action != ACTION_NEW_RECIPE) return false
        _newRecipe.value = true
        return true
    }

    fun consume() {
        _newRecipe.value = false
    }
}
