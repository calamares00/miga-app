package org.calamares.miga.ui.settings

import org.calamares.miga.L10n
import org.calamares.miga.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.calamares.miga.data.local.SettingsRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(settingsRepository: SettingsRepository, onBack: () -> Unit) {
    /**
     * Changelogs are asset files bundled in the APK and never change at runtime, so reading them
     * once is enough.
     */
    val changelogEntries = remember {
        settingsRepository.listAvailableChangelogVersionCodes()
            .mapNotNull { versionCode -> settingsRepository.readChangelog(versionCode)?.let { versionCode to it } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(L10n.str(R.string.changelog)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = L10n.str(R.string.back)) }
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
            changelogEntries.forEach { (versionCode, text) ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "v1.0.${versionCode - 1}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
