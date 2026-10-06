package org.calamares.miga.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.calamares.miga.MigaApp

/** `true` si el interruptor global de IA (Ajustes → IA) está activado. Con él apagado no se
 *  muestra ninguna opción ni texto de IA en la app. */
@Composable
fun rememberAiEnabled(): Boolean {
    val context = LocalContext.current
    val flow = remember { (context.applicationContext as MigaApp).settingsRepository.observeAiEnabled() }
    val enabled by flow.collectAsState(initial = true)
    return enabled
}
