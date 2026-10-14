package org.calamares.miga.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tells MainActivity when the first screen has its content, so the start screen (logo, name and
 * slogan) covers it until then and it never appears empty ("You have no books yet") for a moment.
 * The first screen calls [contentReady] once its data has loaded.
 */
object StartupGate {
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    fun contentReady() {
        _ready.value = true
    }
}
