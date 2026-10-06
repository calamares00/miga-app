package org.calamares.miga.ui.components

import org.calamares.miga.L10n
import org.calamares.miga.R
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.calamares.miga.BuildConfig
import org.calamares.miga.data.support.AiContentReport
import org.calamares.miga.data.support.AiReportReason

/**
 * "Generated with AI" notice with a button to report the content (Google Play requirement for
 * generative AI apps). [feature] names the feature ("Import recipe", "Nutrition"...) and [content]
 * returns the generated text attached to the report.
 */
@Composable
fun AiContentNotice(feature: String, content: () -> String, modifier: Modifier = Modifier) {
    var reporting by remember { mutableStateOf(false) }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Text(
            L10n.str(R.string.ai_generated_may_contain_mistakes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(start = 6.dp)
        )
        TextButton(onClick = { reporting = true }) {
            Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(L10n.str(R.string.report), style = MaterialTheme.typography.labelLarge)
        }
    }
    if (reporting) {
        AiReportDialog(feature = feature, content = content, onDismiss = { reporting = false })
    }
}

@Composable
private fun AiReportDialog(feature: String, content: () -> String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var reason by remember { mutableStateOf(AiReportReason.OFFENSIVE) }
    var comment by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Flag, contentDescription = null) },
        title = { Text(L10n.str(R.string.report_ai_content)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    L10n.str(R.string.tell_us_whats_wrong_generated),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AiReportReason.entries.forEach { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { reason = option },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = reason == option, onClick = { reason = option })
                        Text(option.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text(L10n.str(R.string.comment_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val subject = AiContentReport.subject(feature)
                val body = AiContentReport.body(feature, reason, comment, content(), BuildConfig.VERSION_NAME)
                val opened = runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AiContentReport.targetUrl(subject, body))))
                }.isSuccess
                if (!opened) Toast.makeText(context, L10n.str(R.string.theres_no_app_send_report), Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text(L10n.str(R.string.send_report)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.str(R.string.cancel)) } }
    )
}
