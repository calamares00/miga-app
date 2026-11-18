package org.calamares.miga.ui.components

import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
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
private val SHEET_TOP_MARGIN = 24.dp

@Composable
fun sheetContentHeight(fraction: Float): Dp {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize.height
    val insets = WindowInsets.safeDrawing
    // A margin under the status bar: a sheet that ends exactly at its edge can flip between
    // touching it (and being padded) and not, and keep bouncing on some screens.
    val available = window - insets.getTop(density) - insets.getBottom(density) -
        with(density) { (SHEET_DRAG_HANDLE + SHEET_TOP_MARGIN).roundToPx() }
    val height = minOf(window * fraction, available.toFloat()).coerceAtLeast(0f)
    return with(density) { height.toDp() }
}

/**
 * Keeps the scroll of a sheet's content in the content. When a list reaches its end, Compose
 * hands what is left of the drag or fling to the ModalBottomSheet around it; the sheet, already
 * expanded, tries to follow and settles again, and the user sees it bounce. Leftovers towards the
 * end (finger moving up) are taken here; those towards the start still reach the sheet, so pulling
 * down from the top keeps closing it.
 */
private object KeepScrollInSheet : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (available.y < 0f) Offset(0f, available.y) else Offset.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        if (available.y < 0f) Velocity(0f, available.y) else Velocity.Zero
}

/** Put on the root of a bottom sheet's content: see [KeepScrollInSheet]. */
fun Modifier.keepScrollInSheet(): Modifier = nestedScroll(KeepScrollInSheet)
