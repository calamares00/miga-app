package org.calamares.miga.ui.components

import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.runtime.remember
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
 * Keeps the scroll of a sheet's content in the content. When a list reaches either end, Compose
 * hands what is left of the drag or fling to the ModalBottomSheet around it; the sheet, already
 * expanded, tries to follow and settles again, and the user sees it bounce. Leftovers towards the
 * end (finger moving up) are always taken here. Towards the start, only a drag by the finger
 * reaches the sheet, so pulling down from the top still closes it, and the fling that ends that
 * drag too; the momentum of a list flung up to its top does not.
 */
private class KeepScrollInSheet : NestedScrollConnection {
    /** This gesture has moved the sheet down (the list was at its top). */
    private var draggingSheet = false

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = when {
        available.y < 0f -> Offset(0f, available.y)
        available.y > 0f && source == NestedScrollSource.UserInput -> {
            draggingSheet = true
            Offset.Zero
        }
        else -> Offset(0f, available.y)
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val passToSheet = draggingSheet && available.y > 0f
        draggingSheet = false
        return if (passToSheet) Velocity.Zero else Velocity(0f, available.y)
    }
}

/** Put on the root of a bottom sheet's content: see [KeepScrollInSheet]. */
fun Modifier.keepScrollInSheet(): Modifier = composed { nestedScroll(remember { KeepScrollInSheet() }) }
