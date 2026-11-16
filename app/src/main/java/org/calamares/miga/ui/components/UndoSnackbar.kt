package org.calamares.miga.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.calamares.miga.L10n
import org.calamares.miga.MigaApp
import org.calamares.miga.R

/**
 * Shows the latest deletion that can still be undone (see PendingDeletions) in [snackbarHostState],
 * with "Undo". Screens that list recipes or books call it, so a recipe deleted from its own
 * screen is offered back on the list the user returns to.
 */
@Composable
fun UndoDeletionSnackbar(snackbarHostState: SnackbarHostState) {
    val pending = (LocalContext.current.applicationContext as MigaApp).repository.pendingDeletions
    val offer by pending.offer.collectAsStateWithLifecycle()
    LaunchedEffect(offer) {
        val current = offer ?: return@LaunchedEffect
        pending.offered(current)
        val result = snackbarHostState.showSnackbar(
            message = current.message,
            actionLabel = L10n.str(R.string.undo),
            withDismissAction = true,
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) pending.undo(current)
    }
}
