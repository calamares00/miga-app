package org.calamares.miga.ui.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.calamares.miga.L10n
import org.calamares.miga.R

/** From this width (Material's "expanded" window) a book shows its list and a recipe side by side. */
private val TWO_PANE_MIN_WIDTH = 840.dp
private val LIST_PANE_WIDTH = 380.dp

/**
 * A book on a large screen: the list of recipes on the left and the recipe chosen on the right.
 * On a narrower window only [list] is shown and it gets no click handler, so the caller opens the
 * recipe as its own screen.
 *
 * The width is the one the app really has, so a tablet in split screen or a folded phone gets the
 * single pane.
 */
@Composable
fun BookPanes(
    list: @Composable (onRecipeClickInPane: ((Long) -> Unit)?) -> Unit,
    detail: @Composable (recipeId: Long, onClose: () -> Unit) -> Unit
) {
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth < TWO_PANE_MIN_WIDTH) {
            list(null)
            return@BoxWithConstraints
        }
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.width(LIST_PANE_WIDTH).fillMaxHeight()) {
                list { selectedId = it }
            }
            VerticalDivider()
            // Clipped: the recipe's photo header and its overlays must not draw over the list.
            Box(modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                val id = selectedId
                if (id == null) {
                    Text(
                        text = L10n.str(R.string.choose_recipe_to_see_it),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp)
                    )
                } else {
                    key(id) { detail(id) { selectedId = null } }
                }
            }
        }
    }
}
