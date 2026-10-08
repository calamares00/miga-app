package org.calamares.miga.ui.components

import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.export.ExportFormat
import org.calamares.miga.data.export.FileExportState
import org.calamares.miga.data.export.FileExports

/**
 * The one "Export" choice of a recipe, a selection or a book: PDF to read or send, or a Miga file
 * to import later (plus plain text for a single recipe when [onText] is given).
 */
@Composable
fun ExportFormatDialog(
    title: String,
    onFormat: (ExportFormat) -> Unit,
    onDismiss: () -> Unit,
    onText: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
        title = { Text(L10n.str(R.string.export_x, title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(L10n.str(R.string.export_choose_format), style = MaterialTheme.typography.bodyMedium)
                ExportOption(Icons.Filled.PictureAsPdf, L10n.str(R.string.export_format_pdf), L10n.str(R.string.export_format_pdf_desc)) {
                    onFormat(ExportFormat.PDF)
                }
                ExportOption(Icons.Filled.Archive, L10n.str(R.string.export_format_miga), L10n.str(R.string.export_format_miga_desc)) {
                    onFormat(ExportFormat.MIGA_FILE)
                }
                if (onText != null) {
                    ExportOption(Icons.Filled.ShortText, L10n.str(R.string.export_format_text), L10n.str(R.string.export_format_text_desc), onText)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.str(R.string.cancel)) } }
    )
}

@Composable
private fun ExportOption(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Shows the export in progress (see [FileExports]) and, when the file is ready, offers to save it
 * to the user's files or share it. Lives above the navigation, so it keeps working whatever screen
 * the user moves to while a long book is being exported.
 */
@Composable
fun FileExportHost() {
    val state by FileExports.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The progress dialog can be hidden to keep using the app; the result always shows.
    var progressHidden by remember { mutableStateOf(false) }
    LaunchedEffect(state is FileExportState.Running) {
        if (state !is FileExportState.Running) progressHidden = false
    }

    val ready = state as? FileExportState.Ready
    val onSaved: (android.net.Uri?) -> Unit = { uri ->
        val file = ready?.file
        if (uri != null && file != null) {
            scope.launch {
                val saved = FileExports.saveTo(context, file, uri)
                Toast.makeText(context, L10n.str(if (saved) R.string.pdf_saved else R.string.pdf_save_failed), Toast.LENGTH_SHORT).show()
                if (saved) FileExports.dismiss()
            }
        }
    }
    // One picker per type: the system file picker needs the real MIME type of the new file.
    val savePdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.PDF.mimeType), onSaved)
    val saveZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.MIGA_FILE.mimeType), onSaved)

    when (val current = state) {
        FileExportState.Idle -> Unit

        is FileExportState.Running -> if (!progressHidden) {
            AlertDialog(
                onDismissRequest = { progressHidden = true },
                icon = { Icon(formatIcon(current.format), contentDescription = null) },
                title = {
                    Text(
                        L10n.str(
                            if (current.format == ExportFormat.PDF) R.string.pdf_exporting_x else R.string.miga_file_exporting_x,
                            current.title
                        )
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (current.total > 0) {
                            LinearProgressIndicator(
                                progress = { current.current.toFloat() / current.total },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(FileExports.progressText(current.format, current.current, current.total), style = MaterialTheme.typography.bodyMedium)
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
                dismissButton = { TextButton(onClick = { FileExports.cancel() }) { Text(L10n.str(R.string.cancel)) } }
            )
        }

        is FileExportState.Ready -> AlertDialog(
            onDismissRequest = { FileExports.dismiss() },
            icon = { Icon(formatIcon(current.format), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(L10n.str(R.string.export_ready_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        L10n.str(R.string.export_ready_x, current.file.name, Formatter.formatShortFileSize(context, current.file.length())),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = {
                                val launcher = if (current.format == ExportFormat.PDF) savePdfLauncher else saveZipLauncher
                                launcher.launch(current.file.name)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  " + L10n.str(R.string.save))
                        }
                        Button(onClick = { FileExports.share(context, current); FileExports.dismiss() }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  " + L10n.str(R.string.share))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { FileExports.dismiss() }) { Text(L10n.str(R.string.close)) } }
        )

        is FileExportState.Failed -> AlertDialog(
            onDismissRequest = { FileExports.dismiss() },
            icon = { Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(L10n.str(R.string.pdf_export_failed)) },
            text = { Text(current.message, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { FileExports.dismiss() }) { Text(L10n.str(R.string.close)) } }
        )
    }
}

private fun formatIcon(format: ExportFormat): ImageVector = when (format) {
    ExportFormat.PDF -> Icons.Filled.PictureAsPdf
    ExportFormat.MIGA_FILE -> Icons.Filled.Archive
}
