package org.calamares.miga.ui.components

import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.export.PdfExportState
import org.calamares.miga.data.export.PdfExports

/**
 * Shows the PDF export in progress (see [PdfExports]) and, when the file is ready, offers to save
 * it to the user's files or share it. Lives above the navigation, so it keeps working whatever
 * screen the user moves to while a long book is being exported.
 */
@Composable
fun PdfExportHost() {
    val state by PdfExports.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The progress dialog can be hidden to keep using the app; the result always shows.
    var progressHidden by remember { mutableStateOf(false) }
    LaunchedEffect(state is PdfExportState.Running) {
        if (state !is PdfExportState.Running) progressHidden = false
    }

    val ready = state as? PdfExportState.Ready
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val file = ready?.file
        if (uri != null && file != null) {
            scope.launch {
                val saved = PdfExports.saveTo(context, file, uri)
                Toast.makeText(context, L10n.str(if (saved) R.string.pdf_saved else R.string.pdf_save_failed), Toast.LENGTH_SHORT).show()
                if (saved) PdfExports.dismiss()
            }
        }
    }

    when (val current = state) {
        PdfExportState.Idle -> Unit

        is PdfExportState.Running -> if (!progressHidden) {
            AlertDialog(
                onDismissRequest = { progressHidden = true },
                icon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null) },
                title = { Text(L10n.str(R.string.pdf_exporting_x, current.title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (current.totalPages > 0) {
                            LinearProgressIndicator(
                                progress = { current.page.toFloat() / current.totalPages },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(L10n.str(R.string.pdf_page_x_of_y, current.page, current.totalPages), style = MaterialTheme.typography.bodyMedium)
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(L10n.str(R.string.pdf_preparing), style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            L10n.str(R.string.pdf_export_running_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = { TextButton(onClick = { progressHidden = true }) { Text(L10n.str(R.string.hide)) } },
                dismissButton = { TextButton(onClick = { PdfExports.cancel() }) { Text(L10n.str(R.string.cancel)) } }
            )
        }

        is PdfExportState.Ready -> AlertDialog(
            onDismissRequest = { PdfExports.dismiss() },
            icon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(L10n.str(R.string.pdf_ready_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        L10n.str(R.string.pdf_ready_x, current.title, Formatter.formatShortFileSize(context, current.file.length())),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { saveLauncher.launch(current.file.name) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  " + L10n.str(R.string.save))
                        }
                        Button(onClick = { PdfExports.share(context, current.file); PdfExports.dismiss() }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  " + L10n.str(R.string.share))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { PdfExports.dismiss() }) { Text(L10n.str(R.string.close)) } }
        )

        is PdfExportState.Failed -> AlertDialog(
            onDismissRequest = { PdfExports.dismiss() },
            icon = { Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(L10n.str(R.string.pdf_export_failed)) },
            text = { Text(current.message, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { PdfExports.dismiss() }) { Text(L10n.str(R.string.close)) } }
        )
    }
}
