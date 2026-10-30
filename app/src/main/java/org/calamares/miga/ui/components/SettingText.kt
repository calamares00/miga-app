package org.calamares.miga.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Text and change handler for a field bound to a stored setting. The field keeps its own text and
 * saves every change; it does not show the stored value back while typing. Showing it back made
 * each key round-trip through DataStore, and a value saved a moment earlier could replace newer
 * text, so fast typing lost letters.
 *
 * Until the user types, the field follows [saved], which can arrive after the first frame.
 */
@Composable
fun rememberSettingText(saved: String, save: (String) -> Unit): Pair<String, (String) -> Unit> {
    var text by remember { mutableStateOf(saved) }
    var edited by remember { mutableStateOf(false) }
    LaunchedEffect(saved) { if (!edited) text = saved }
    return text to { value: String ->
        edited = true
        text = value
        save(value)
    }
}
