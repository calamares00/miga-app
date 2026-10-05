package org.calamares.miga.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.support.ErrorDetail

/**
 * Error en dos niveles: el resumen para el usuario y, si lo hay, un "Ver detalle" con la parte
 * técnica (respuesta del modelo, código HTTP...) que se puede copiar para reportarla.
 */
@Composable
fun ErrorMessage(reason: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    var showDetail by remember { mutableStateOf(false) }
    val detail = ErrorDetail.detail(reason)
    Column(modifier = modifier) {
        Text(ErrorDetail.summary(reason), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        androidx.compose.foundation.layout.Row {
            if (onRetry != null) TextButton(onClick = onRetry) { Text(L10n.str(R.string.reintentar)) }
            if (detail != null) TextButton(onClick = { showDetail = true }) { Text(L10n.str(R.string.view_details)) }
        }
    }
    if (showDetail && detail != null) ErrorDetailDialog(detail = detail, onDismiss = { showDetail = false })
}

@Composable
fun ErrorDetailDialog(detail: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.str(R.string.detalle_error)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                SelectionContainer { Text(detail, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(detail)) }) { Text(L10n.str(R.string.copiar)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.str(R.string.cerrar)) } }
    )
}
