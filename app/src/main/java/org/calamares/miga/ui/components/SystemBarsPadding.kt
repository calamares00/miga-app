package org.calamares.miga.ui.components

import android.app.Activity
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

private val BAR_TYPES = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()

/**
 * Padding that keeps content clear of the status and navigation bars in a window of its own
 * (Dialog). There Compose's WindowInsets stay at 0, so navigationBarsPadding/safeDrawing do
 * nothing, and the system places the dialog window differently on each phone: on some it starts
 * below the status bar and reaches past the bottom of the screen. Buttons at the bottom then ended
 * up under a three-button navigation bar.
 *
 * Each side takes the larger of two measures: the insets the system gives this window, and the
 * overlap between the window's place on screen and the activity's bars, so it holds whatever the
 * window's position. It updates on every layout (rotation, bars shown or hidden).
 */
@Composable
fun rememberSystemBarsPadding(): PaddingValues {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()
    var bars by remember(view) { mutableStateOf(barsOverlap(view, activity)) }
    DisposableEffect(view, activity) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val now = barsOverlap(view, activity)
            if (now != bars) bars = now
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
    return with(LocalDensity.current) {
        PaddingValues.Absolute(left = bars.left.toDp(), top = bars.top.toDp(), right = bars.right.toDp(), bottom = bars.bottom.toDp())
    }
}

/** Pixels of [view]'s window covered by the system bars on each side (left and right as on screen). */
private fun barsOverlap(view: View, activity: Activity?): Insets {
    val root = view.rootView
    val own = ViewCompat.getRootWindowInsets(root)?.getInsets(BAR_TYPES) ?: Insets.NONE
    val screen = activity?.window?.decorView ?: return own
    val screenBars = ViewCompat.getRootWindowInsets(screen)?.getInsets(BAR_TYPES) ?: return own
    if (root.width == 0 || screen.width == 0) return own

    val window = IntArray(2).also { root.getLocationOnScreen(it) }
    val app = IntArray(2).also { screen.getLocationOnScreen(it) }
    // Free area of the screen, between the activity's bars, in screen coordinates.
    val freeLeft = app[0] + screenBars.left
    val freeTop = app[1] + screenBars.top
    val freeRight = app[0] + screen.width - screenBars.right
    val freeBottom = app[1] + screen.height - screenBars.bottom
    return Insets.max(
        own,
        Insets.of(
            (freeLeft - window[0]).coerceAtLeast(0),
            (freeTop - window[1]).coerceAtLeast(0),
            (window[0] + root.width - freeRight).coerceAtLeast(0),
            (window[1] + root.height - freeBottom).coerceAtLeast(0)
        )
    )
}
