package com.bmo00.miga.ui.settings

import com.bmo00.miga.BuildConfig
import com.bmo00.miga.data.support.AiContentReport
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bmo00.miga.data.local.SettingsRepository

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HelpScreen(settingsRepository: SettingsRepository, onBack: () -> Unit, onChangelogClick: () -> Unit) {
    val context = LocalContext.current
    // Solo hace falta saber si hay algún changelog embebido para decidir si se muestra la
    // entrada; el contenido en sí se lee en ChangelogScreen, al entrar ahí.
    val hasChangelog = remember { settingsRepository.listAvailableChangelogVersionCodes().isNotEmpty() }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
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
                Text("Contacto", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "¿Has encontrado un fallo o tienes una sugerencia? Escríbenos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = {
                    val url = AiContentReport.targetUrl("Soporte Miga", "Versión de Miga: ${BuildConfig.VERSION_NAME}\n\n")
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }) { Text("Informar de un problema") }
            }

            if (hasChangelog) {
                HorizontalDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onChangelogClick)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                        Text("Historial de cambios", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Las novedades de cada versión",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
