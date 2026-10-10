package org.calamares.miga.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

private const val SHEET_ANIMATION_MILLIS = 250

/**
 * A bottom sheet in a window of its own, so it covers the whole screen even when it is opened
 * from inside a dialog (Material's ModalBottomSheet then stays inside the dialog's window,
 * cut off). Tapping outside it or going back closes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogSheet(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    // Plays the slide out before the window goes.
    LaunchedEffect(closing) {
        if (closing) {
            visible = false
            delay(SHEET_ANIMATION_MILLIS.toLong())
            onDismissRequest()
        }
    }
    val close = { closing = true }

    Dialog(
        onDismissRequest = close,
        // Between the system bars: behind them, the bottom of the sheet (its main button) could end
        // up under the navigation bar of phones with buttons.
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BackHandler(onBack = close)
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            AnimatedVisibility(visible = visible, enter = fadeIn(tween(SHEET_ANIMATION_MILLIS)), exit = fadeOut(tween(SHEET_ANIMATION_MILLIS))) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.32f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close)
                )
            }
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(tween(SHEET_ANIMATION_MILLIS)) { it },
                exit = slideOutVertically(tween(SHEET_ANIMATION_MILLIS)) { it }
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.9f)
                ) {
                    Column(modifier = Modifier.navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                        BottomSheetDefaults.DragHandle()
                        Column(modifier = Modifier.fillMaxWidth(), content = content)
                    }
                }
            }
        }
    }
}
