package com.bmo00.miga.ui.packs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackDetailScreen(
    viewModel: PackDetailViewModel,
    onBack: () -> Unit,
    onInstalled: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val installState by viewModel.installState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle del pack") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Volver") }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is PackDetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is PackDetailUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(state.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
            is PackDetailUiState.Loaded -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .size(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (state.entry.coverImageUrl != null) {
                            AsyncImage(
                                model = state.entry.coverImageUrl,
                                contentDescription = state.entry.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(56.dp).align(Alignment.Center)
                            )
                        }
                    }

                    Text(state.entry.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "De ${state.entry.author} · ${state.entry.recipeCount} recetas · versión ${state.entry.latestVersion}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.entry.description.isNotBlank()) {
                        Text(state.entry.description, style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(
                        "Este libro se instala de solo lectura: podrás verlo, cocinar sus recetas y " +
                            "exportarlas, pero no editarlas. Se actualizará cuando el autor publique una " +
                            "versión nueva.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val buttonLabel = when {
                        installState is InstallState.Installing -> "Instalando..."
                        state.installedVersion == null -> "Instalar"
                        state.entry.latestVersion > state.installedVersion -> "Actualizar"
                        else -> "Reinstalar"
                    }
                    Button(
                        onClick = { viewModel.install(context, onInstalled) },
                        enabled = installState !is InstallState.Installing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(buttonLabel)
                    }

                    val error = installState as? InstallState.Error
                    if (error != null) {
                        Text(error.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
