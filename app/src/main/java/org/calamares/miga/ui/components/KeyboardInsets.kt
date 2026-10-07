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
 * While the calling screen is shown, the keyboard no longer pans the whole window up: the screen
 * stays in place and receives the keyboard height as insets, so a text field at the bottom that
 * pads itself with the IME insets sits right on top of the keyboard. Without this, the window is
 * panned and the insets are applied as well, leaving the top bar off screen and a blank gap above
 * the keyboard.
 */
@Composable
fun KeyboardResizesContent() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        val window = activity.window
        val previous = window.attributes.softInputMode
        @Suppress("DEPRECATION") // Still what stops the pan; with edge-to-edge nothing is resized.
        val adjustResize = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        window.setSoftInputMode((previous and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or adjustResize)
        onDispose { window.setSoftInputMode(previous) }
    }
}
