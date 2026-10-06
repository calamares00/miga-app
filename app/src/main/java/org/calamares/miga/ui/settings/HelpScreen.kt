package org.calamares.miga.ui.settings

import org.calamares.miga.ui.components.rememberAiEnabled
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
                title = { Text(L10n.str(R.string.help_support)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = L10n.str(R.string.back)) }
                }
            )
        }
    ) { padding ->
        val aiEnabled = rememberAiEnabled()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            HelpSection(
                title = L10n.str(R.string.recipe_books),
                body = L10n.str(R.string.each_book_someones_recipe_collection)
            )
            HelpSection(
                title = L10n.str(R.string.categories_tags_utensils),
                body = L10n.str(R.string.recipes_grouped_category_list_add)
            )
            HelpSection(
                title = L10n.str(R.string.ingredients),
                body = L10n.str(R.string.when_type_ingredient_name_recipe)
            )
            HelpSection(
                title = L10n.str(R.string.cooking_mode),
                body = L10n.str(R.string.recipe_steps_tap_cooking_mode)
            )
            HelpSection(
                title = L10n.str(R.string.export_import),
                body = L10n.str(R.string.export_recipe_text_pdf_json)
            )
            if (aiEnabled) {
                HelpSection(
                    title = L10n.str(R.string.add_recipe_photo_beta),
                    body = L10n.str(R.string.books_menu_add_photo_reads)
                )
            }
            HelpSection(
                title = L10n.str(R.string.biometric_lock),
                body = L10n.str(R.string.turn_settings_security_app_asks)
            )

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(L10n.str(R.string.contact), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = L10n.str(R.string.found_bug_have_suggestion_write),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = {
                    val url = AiContentReport.targetUrl(L10n.str(R.string.miga_support), L10n.str(R.string.version_miga_x_2, BuildConfig.VERSION_NAME))
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }) { Text(L10n.str(R.string.report_problem)) }
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
