package com.bmo00.miga.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.bmo00.miga.RecetarioApp
import com.bmo00.miga.data.voice.DictationLanguages

/** Idioma del dictado por voz elegido en Ajustes (se actualiza solo si el usuario lo cambia). */
@Composable
fun rememberDictationLanguage(): String {
    val context = LocalContext.current
    val settings = (context.applicationContext as RecetarioApp).settingsRepository
    val tag by settings.observeDictationLanguage().collectAsState(initial = DictationLanguages.DEFAULT)
    return tag
}
