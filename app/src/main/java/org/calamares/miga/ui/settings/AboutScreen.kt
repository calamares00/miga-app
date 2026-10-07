package org.calamares.miga.ui.settings

import org.calamares.miga.L10n
import org.calamares.miga.R
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.calamares.miga.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, onOpenPrivacy: () -> Unit) {

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(L10n.str(R.string.about)) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Miga", style = MaterialTheme.typography.headlineSmall)
            AboutRow(L10n.str(R.string.version), "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
            AboutRow(L10n.str(R.string.build_type), if (BuildConfig.DEBUG) L10n.str(R.string.beta_development) else L10n.str(R.string.stable))
            AboutRow(L10n.str(R.string.architecture), Build.SUPPORTED_ABIS.firstOrNull() ?: L10n.str(R.string.unknown))
            // The policy is read inside the app (assets/docs, same text as PRIVACY.md at the
            // repository root, which is also published on the Miga website for Google Play).
            OutlinedButton(onClick = onOpenPrivacy) {
                Text(L10n.str(R.string.privacy_policy))
            }
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
