package org.calamares.miga.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import org.calamares.miga.MigaApp
import org.calamares.miga.data.voice.DictationLanguages

/** Voice dictation language chosen in Settings, updated when the user changes it. */
@Composable
fun rememberDictationLanguage(): String {
    val context = LocalContext.current
    val settings = (context.applicationContext as MigaApp).settingsRepository
    val tag by settings.observeDictationLanguage().collectAsState(initial = DictationLanguages.DEFAULT)
    return tag
}
