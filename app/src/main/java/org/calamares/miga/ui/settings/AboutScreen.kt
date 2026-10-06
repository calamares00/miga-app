package org.calamares.miga.ui.settings

import org.calamares.miga.L10n
import org.calamares.miga.R
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.calamares.miga.BuildConfig

// Publicada en la web de Miga (mismo texto que PRIVACY.md en la raíz del repo); se abre en el
// navegador en vez de duplicar el texto dentro de la app. Es la misma URL que se da a Google Play.
private const val PRIVACY_POLICY_URL = "https://miga.calamares.org/privacy"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

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
            OutlinedButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))
                runCatching { context.startActivity(intent) }
            }) {
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
