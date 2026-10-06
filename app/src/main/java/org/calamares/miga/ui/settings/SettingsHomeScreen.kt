package org.calamares.miga.ui.settings

import org.calamares.miga.L10n
import org.calamares.miga.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.calamares.miga.BuildConfig
import org.calamares.miga.data.voice.DictationLanguages

private data class HomeItem(val icon: ImageVector, val title: String, val summary: String, val onClick: () -> Unit)

/**
 * Main Settings screen, styled like Google's apps: groups of rows (icon, title and a summary of the
 * current setting), each opening its own screen.
 * "What's new" (changelog) has its own entry, separate from Help.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHomeScreen(
    viewModel: SettingsViewModel,
    hasChangelog: Boolean,
    onOpenSection: (SettingsSection) -> Unit,
    onOpenStats: () -> Unit,
    onOpenChangelog: () -> Unit,
    onHelp: () -> Unit,
    onAbout: () -> Unit
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val colorTheme by viewModel.colorTheme.collectAsState()
    val biometricLockEnabled by viewModel.biometricLockEnabled.collectAsState()
    val aiEnabled by viewModel.aiEnabled.collectAsState()
    val aiProviders by viewModel.aiProvidersSummary.collectAsState()
    val dictationLanguage by viewModel.dictationLanguage.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.navigationBars),
        topBar = {
            TopAppBar(
                title = { Text(L10n.str(R.string.settings)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HomeGroup(
                listOf(
                    HomeItem(SettingsSection.APPEARANCE.icon, SettingsSection.APPEARANCE.title, "${themeMode.label} · ${colorTheme.label}") {
                        onOpenSection(SettingsSection.APPEARANCE)
                    },
                    HomeItem(
                        SettingsSection.SECURITY.icon,
                        SettingsSection.SECURITY.title,
                        if (biometricLockEnabled) L10n.str(R.string.biometric_lock_2) else L10n.str(R.string.biometric_lock_off)
                    ) { onOpenSection(SettingsSection.SECURITY) },
                    HomeItem(SettingsSection.VOICE.icon, SettingsSection.VOICE.title, L10n.str(R.string.dictation_x, DictationLanguages.label(dictationLanguage))) {
                        onOpenSection(SettingsSection.VOICE)
                    }
                )
            )
            HomeGroup(
                listOf(
                    HomeItem(SettingsSection.CONTENT.icon, SettingsSection.CONTENT.title, L10n.str(R.string.categories_utensils_ingredients)) {
                        onOpenSection(SettingsSection.CONTENT)
                    },
                    HomeItem(SettingsSection.BACKUP.icon, SettingsSection.BACKUP.title, L10n.str(R.string.export_import_books_recipes)) {
                        onOpenSection(SettingsSection.BACKUP)
                    },
                    HomeItem(Icons.Filled.BarChart, L10n.str(R.string.statistics), L10n.str(R.string.recipes_favourites_most_cooked), onOpenStats)
                )
            )
            HomeGroup(
                listOf(
                    HomeItem(SettingsSection.SYNC.icon, SettingsSection.SYNC.title, L10n.str(R.string.own_server_books_recipes_shopping)) {
                        onOpenSection(SettingsSection.SYNC)
                    },
                    HomeItem(SettingsSection.PACKS.icon, SettingsSection.PACKS.title, L10n.str(R.string.ready_install_recipe_books)) {
                        onOpenSection(SettingsSection.PACKS)
                    },
                    HomeItem(SettingsSection.AI.icon, SettingsSection.AI.title, when {
                        !aiEnabled -> L10n.str(R.string.ai_disabled_summary)
                        aiProviders.isEmpty() -> L10n.str(R.string.ai_no_provider_summary)
                        else -> aiProviders.joinToString(" → ") { it.shortName }
                    }) {
                        onOpenSection(SettingsSection.AI)
                    }
                )
            )
            HomeGroup(
                buildList {
                    if (hasChangelog) {
                        add(HomeItem(Icons.Filled.NewReleases, L10n.str(R.string.whats_new), L10n.str(R.string.whats_new_each_version), onOpenChangelog))
                    }
                    add(HomeItem(Icons.Filled.HelpOutline, L10n.str(R.string.help_support), L10n.str(R.string.faq_contact), onHelp))
                    add(HomeItem(Icons.Filled.Info, L10n.str(R.string.about), L10n.str(R.string.version_x_privacy_policy, BuildConfig.VERSION_NAME), onAbout))
                }
            )
        }
    }
}

/** A group of rows inside a rounded container with thin dividers between them. */
@Composable
private fun HomeGroup(items: List<HomeItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        items.forEachIndexed { index, item ->
            HomeRow(item)
            if (index < items.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun HomeRow(item: HomeItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = item.onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                item.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
