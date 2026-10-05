package org.calamares.miga.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import org.calamares.miga.RecetarioApp
import org.calamares.miga.data.voice.DictationLanguages

/** Idioma del dictado por voz elegido en Ajustes (se actualiza solo si el usuario lo cambia). */
@Composable
fun rememberDictationLanguage(): String {
    val context = LocalContext.current
    val settings = (context.applicationContext as RecetarioApp).settingsRepository
    val tag by settings.observeDictationLanguage().collectAsState(initial = DictationLanguages.DEFAULT)
    return tag
}
