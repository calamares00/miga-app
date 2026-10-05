package com.bmo00.miga.ui.components

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
import com.bmo00.miga.BuildConfig
import com.bmo00.miga.data.support.AiContentReport
import com.bmo00.miga.data.support.AiReportReason

/**
 * Aviso "Generado con IA" con botón para reportar el contenido (requisito de Google Play para apps
 * con IA generativa). [feature] nombra la función ("Importar receta", "Nutrición"...) y [content]
 * devuelve, al reportar, el texto generado que se adjunta al reporte.
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
            "Generado con IA · puede contener errores",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(start = 6.dp)
        )
        TextButton(onClick = { reporting = true }) {
            Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(" Reportar", style = MaterialTheme.typography.labelLarge)
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
        title = { Text("Reportar contenido de IA") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Cuéntanos qué está mal. Se enviará el texto generado junto con tu reporte para revisarlo.",
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
                    label = { Text("Comentario (opcional)") },
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
                if (!opened) Toast.makeText(context, "No hay ninguna app para enviar el reporte", Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text("Enviar reporte") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
