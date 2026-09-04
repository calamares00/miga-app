package com.bmo00.miga.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * TODO: sustituir por el enlace real de donación (PayPal.me, Ko-fi...) antes de publicar la app.
 * Se le añade el importe al final, ej. "$DONATION_BASE_URL/0.99".
 */
private const val DONATION_BASE_URL = "https://TODO_DONATION_URL"
private val DONATION_AMOUNTS = listOf("0.99", "2.99", "4.99", "9.99")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayuda y soporte") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Volver") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            HelpSection(
                title = "Libros de recetas",
                body = "Cada libro es el recetario de una persona (por ejemplo Josi o Helen), con su propia portada. " +
                    "Cuando tienes un libro abierto, las recetas nuevas que crees se guardan ahí por defecto. " +
                    "Puedes mover una receta a otro libro desde su menú de opciones (⋮)."
            )
            HelpSection(
                title = "Categorías, etiquetas y utensilios",
                body = "Las recetas se agrupan por categoría en el listado. Puedes añadir, renombrar o borrar " +
                    "categorías, utensilios e ingredientes desde Ajustes → Gestionar."
            )
            HelpSection(
                title = "Ingredientes",
                body = "Al escribir el nombre de un ingrediente en una receta, la app sugiere los que ya has " +
                    "usado antes para que no tengas que volver a escribirlos."
            )
            HelpSection(
                title = "Modo cocina",
                body = "Desde una receta con pasos, pulsa \"Modo cocina\" para verlos en pantalla completa, uno " +
                    "a uno, sin que la pantalla se apague mientras cocinas."
            )
            HelpSection(
                title = "Exportar e importar",
                body = "Desde una receta puedes exportarla como texto, PDF o JSON. Desde un libro puedes " +
                    "exportarlo entero. Y desde Ajustes puedes hacer o restaurar una copia de seguridad completa."
            )
            HelpSection(
                title = "Añadir receta con foto (beta)",
                body = "Desde el menú de un libro, \"Añadir con foto\" reconoce el texto de una foto (cámara o " +
                    "galería) con Google Gemini y precarga el editor para que solo tengas que revisarlo antes de " +
                    "guardar. Necesita conexión a internet y tu propia API key gratuita de Gemini, configurable " +
                    "en Ajustes → Importar con IA; sin ella no se envía ninguna foto a ningún sitio."
            )
            HelpSection(
                title = "Bloqueo biométrico",
                body = "Actívalo en Ajustes → Seguridad para que la app pida tu huella, rostro o PIN cada vez " +
                    "que la abras."
            )

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Apoya la app", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Si te resulta útil, puedes invitarnos a un café con una pequeña donación.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DONATION_AMOUNTS.forEach { amount ->
                        OutlinedButton(onClick = { openDonationLink(context, amount) }) {
                            Text("${amount.replace('.', ',')} €")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun openDonationLink(context: Context, amount: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$DONATION_BASE_URL/$amount"))
    runCatching { context.startActivity(intent) }
}
