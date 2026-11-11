package org.calamares.miga.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** What Material's ModalBottomSheet puts above its content: the drag handle and its padding. */
private val SHEET_DRAG_HANDLE = 48.dp

/**
 * Height for the content of a ModalBottomSheet that takes most of the screen: [fraction] of the
 * window, but never so tall that the sheet reaches the status bar or goes under the keyboard.
 *
 * Use it instead of fillMaxHeight(fraction). The sheet pads its content by the part of the status
 * bar it covers, which depends on where the sheet is while it moves; content that fills the sheet
 * grows with that padding, the sheet's expanded position moves with its height, and dragging the
 * sheet up left it bouncing up and down on its own. A height taken from the window does not
 * change while the sheet moves, so the sheet settles.
 */
@Composable
fun sheetContentHeight(fraction: Float): Dp {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize.height
    val insets = WindowInsets.safeDrawing
    val available = window - insets.getTop(density) - insets.getBottom(density) - with(density) { SHEET_DRAG_HANDLE.roundToPx() }
    val height = minOf(window * fraction, available.toFloat()).coerceAtLeast(0f)
    return with(density) { height.toDp() }
}
