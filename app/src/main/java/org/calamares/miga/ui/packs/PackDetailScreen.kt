package org.calamares.miga.ui.packs

import org.calamares.miga.ui.components.AdaptiveScaffold
import org.calamares.miga.L10n
import org.calamares.miga.R
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
    var confirmUninstall by remember { mutableStateOf(false) }

    AdaptiveScaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(L10n.str(R.string.pack_details)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = L10n.str(R.string.back)) }
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
                    modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
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
                        L10n.str(R.string.x_x_recipes_version_x, state.entry.author, state.entry.recipeCount, state.entry.latestVersion),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.entry.description.isNotBlank()) {
                        Text(state.entry.description, style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(
                        L10n.str(R.string.book_installed_read_only_view),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val buttonLabel = when {
                        installState is InstallState.Installing -> L10n.str(R.string.installing)
                        state.installedVersion == null -> L10n.str(R.string.install)
                        state.entry.latestVersion > state.installedVersion -> L10n.str(R.string.update)
                        else -> L10n.str(R.string.reinstall)
                    }
                    Button(
                        onClick = { viewModel.install(context, onInstalled) },
                        enabled = installState !is InstallState.Installing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(buttonLabel)
                    }

                    if (state.installedVersion != null) {
                        OutlinedButton(
                            onClick = { confirmUninstall = true },
                            enabled = installState !is InstallState.Installing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(L10n.str(R.string.uninstall_pack), color = MaterialTheme.colorScheme.error)
                        }
                    }

                    if (confirmUninstall) {
                        AlertDialog(
                            onDismissRequest = { confirmUninstall = false },
                            title = { Text(L10n.str(R.string.uninstall_pack)) },
                            text = { Text(L10n.str(R.string.sure_want_uninstall_x_recipes, state.entry.name)) },
                            confirmButton = {
                                TextButton(onClick = { confirmUninstall = false; viewModel.uninstall() }) {
                                    Text(L10n.str(R.string.uninstall), color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { confirmUninstall = false }) { Text(L10n.str(R.string.cancel)) }
                            }
                        )
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
