package org.calamares.miga.ui.settings

import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.BuildConfig
import org.calamares.miga.data.support.AiContentReport
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
import org.calamares.miga.data.local.SettingsRepository

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(L10n.str(R.string.ayuda_soporte)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = L10n.str(R.string.volver)) }
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
                title = L10n.str(R.string.libros_recetas),
                body = L10n.str(R.string.cada_libro_es_recetario_persona)
            )
            HelpSection(
                title = L10n.str(R.string.categorias_etiquetas_utensilios),
                body = L10n.str(R.string.recetas_agrupan_categoria_listado_puedes)
            )
            HelpSection(
                title = L10n.str(R.string.ingredientes),
                body = L10n.str(R.string.escribir_nombre_ingrediente_receta_app)
            )
            HelpSection(
                title = L10n.str(R.string.modo_cocina),
                body = L10n.str(R.string.desde_receta_pasos_pulsa_modo)
            )
            HelpSection(
                title = L10n.str(R.string.exportar_e_importar),
                body = L10n.str(R.string.desde_receta_puedes_exportarla_como)
            )
            HelpSection(
                title = L10n.str(R.string.anadir_receta_foto_beta),
                body = L10n.str(R.string.desde_menu_libro_anadir_foto)
            )
            HelpSection(
                title = L10n.str(R.string.bloqueo_biometrico),
                body = L10n.str(R.string.activalo_ajustes_seguridad_app_pida)
            )

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(L10n.str(R.string.contacto), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = L10n.str(R.string.has_encontrado_fallo_tienes_sugerencia),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = {
                    val url = AiContentReport.targetUrl(L10n.str(R.string.soporte_miga), L10n.str(R.string.version_miga_x_2, BuildConfig.VERSION_NAME))
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }) { Text(L10n.str(R.string.informar_problema)) }
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
