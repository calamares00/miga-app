package org.calamares.miga.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.calamares.miga.MigaApp

/**
 * `true` when the global AI switch (Settings > AI) is on. With it off, no AI option or text is
 * shown anywhere in the app.
 */
@Composable
fun rememberAiEnabled(): Boolean {
    val context = LocalContext.current
    val flow = remember { (context.applicationContext as MigaApp).settingsRepository.observeAiEnabled() }
    val enabled by flow.collectAsState(initial = true)
    return enabled
}
