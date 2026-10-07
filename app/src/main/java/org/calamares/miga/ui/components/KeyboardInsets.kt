package org.calamares.miga.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * While the calling screen is shown, the window neither pans nor resizes when the keyboard opens:
 * the screen keeps the whole window and only the keyboard insets move its content.
 *
 * The app draws edge to edge, where the system can no longer resize the window and falls back to
 * panning it up by the keyboard height. A screen that then also pads itself with the keyboard
 * insets moves away from the keyboard twice: its top bar ends up off screen and a blank band is
 * left above the keyboard. Insets are still reported with this mode, so the screen alone decides
 * what moves.
 */
@Composable
fun KeyboardMovesContentOnly() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        val window = activity.window
        val previous = window.attributes.softInputMode
        val adjustNothing = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        window.setSoftInputMode((previous and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or adjustNothing)
        onDispose { window.setSoftInputMode(previous) }
    }
}
