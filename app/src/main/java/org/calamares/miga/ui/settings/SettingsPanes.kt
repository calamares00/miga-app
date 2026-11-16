package org.calamares.miga.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp

/** From this width (Material's "expanded" window) Settings shows its list and a section side by side. */
private val TWO_PANE_MIN_WIDTH = 840.dp
private val HOME_PANE_WIDTH = 400.dp

/**
 * Settings on a large screen: the list of sections on the left and the chosen one on the right,
 * starting with the first. On a narrower window only [home] is shown, with no pane handler, so
 * the caller opens each section as its own screen.
 */
@Composable
fun SettingsPanes(
    home: @Composable (selected: SettingsSection?, onOpenInPane: ((SettingsSection) -> Unit)?) -> Unit,
    section: @Composable (SettingsSection) -> Unit
) {
    var selectedId by rememberSaveable { mutableStateOf(SettingsSection.entries.first().id) }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth < TWO_PANE_MIN_WIDTH) {
            home(null, null)
            return@BoxWithConstraints
        }
        val selected = SettingsSection.fromId(selectedId)
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.width(HOME_PANE_WIDTH).fillMaxHeight()) {
                home(selected) { selectedId = it.id }
            }
            VerticalDivider()
            Box(modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                key(selected) { section(selected) }
            }
        }
    }
}
