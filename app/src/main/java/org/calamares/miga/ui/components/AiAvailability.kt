package org.calamares.miga.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.calamares.miga.MigaApp

/**
 * `true` when the global AI switch (Settings > AI) is on. With it off, no AI option or text is
 * shown anywhere in the app.
 */
@Composable
fun rememberAiEnabled(): Boolean {
    val context = LocalContext.current
    val flow = remember { (context.applicationContext as MigaApp).settingsRepository.observeAiEnabled() }
    val enabled by flow.collectAsStateWithLifecycle(initialValue = true)
    return enabled
}

/**
 * `true` when the cooking assistant can be used: the switch is on and at least one provider is
 * configured. Buttons and options that call the assistant are only shown then; with the switch on
 * but no provider they would only answer "set it up in Settings".
 */
@Composable
fun rememberAiReady(): Boolean {
    val context = LocalContext.current
    val flow = remember { (context.applicationContext as MigaApp).settingsRepository.observeAiReady() }
    val ready by flow.collectAsStateWithLifecycle(initialValue = false)
    return ready
}
