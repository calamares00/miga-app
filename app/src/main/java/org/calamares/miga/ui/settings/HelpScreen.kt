package org.calamares.miga.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.calamares.miga.BuildConfig
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.model.MarkdownDocument
import org.calamares.miga.data.support.AiContentReport
import org.calamares.miga.ui.components.MarkdownBlocks
import org.calamares.miga.ui.components.readDocumentAsset
import org.calamares.miga.ui.components.rememberAiEnabled
import java.text.Normalizer

/** Marks a help topic that only applies when AI is turned on ("## ✨ Ideas {ai}"). */
private const val AI_ONLY_MARKER = "{ai}"

/** A help topic: a "## " section of the help document, with its leading emoji split off. */
private data class HelpTopic(val emoji: String?, val title: String, val blocks: List<MarkdownDocument.Block>, val searchText: String)

private fun helpTopics(markdown: String, aiEnabled: Boolean): List<HelpTopic> =
    MarkdownDocument.sections(markdown)
        .filter { it.title.isNotBlank() }
        .filter { aiEnabled || !it.title.trimEnd().endsWith(AI_ONLY_MARKER) }
        .map { section ->
            val title = section.title.trimEnd().removeSuffix(AI_ONLY_MARKER).trim()
            val firstWord = title.substringBefore(' ')
            val emoji = firstWord.takeIf { word -> word.isNotEmpty() && word != title && word.none { it.isLetterOrDigit() } }
            val name = if (emoji != null) title.removePrefix(emoji).trim() else title
            HelpTopic(emoji, name, section.blocks, normalizeForSearch(name + " " + section.blocks.joinToString(" ") { it.text }))
        }

/** Lower case without accents or formatting marks, so "economia" finds "Economía". */
private fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").replace("*", "")

/**
 * Help topics from the help document in the app language (assets/docs), as expandable cards with
 * a search box, followed by the ways to get in touch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit, onOpenPrivacy: () -> Unit, onOpenChangelog: (() -> Unit)?) {
    val context = LocalContext.current
    val aiEnabled = rememberAiEnabled()
    val topics = remember(aiEnabled) { helpTopics(readDocumentAsset(context, L10n.str(R.string.help_asset)), aiEnabled) }
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }
    val normalizedQuery = normalizeForSearch(query.trim())
    val searching = normalizedQuery.isNotEmpty()
    val shown = if (searching) topics.filter { normalizedQuery in it.searchText } else topics

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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(L10n.str(R.string.search_help)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.clear)) }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                )
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        L10n.str(R.string.help_no_results),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }
            items(shown, key = { it.title }) { topic ->
                HelpTopicCard(
                    topic = topic,
                    // While searching, every match is open so the text found is visible.
                    expanded = searching || topic.title in expanded,
                    onToggle = { expanded = if (topic.title in expanded) expanded - topic.title else expanded + topic.title }
                )
            }
            item {
                ContactCard(
                    onReport = {
                        val url = AiContentReport.targetUrl(L10n.str(R.string.miga_support), L10n.str(R.string.version_miga_x_2, BuildConfig.VERSION_NAME))
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    },
                    onOpenPrivacy = onOpenPrivacy,
                    onOpenChangelog = onOpenChangelog
                )
            }
        }
    }
}

@Composable
private fun HelpTopicCard(topic: HelpTopic, expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "helpArrow")
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(topic.emoji ?: "•", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(topic.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = L10n.str(if (expanded) R.string.expanded else R.string.collapsed),
                    modifier = Modifier.rotate(rotation)
                )
            }
            AnimatedVisibility(visible = expanded) {
                MarkdownBlocks(
                    blocks = topic.blocks,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun ContactCard(onReport: () -> Unit, onOpenPrivacy: () -> Unit, onOpenChangelog: (() -> Unit)?) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).navigationBarsPadding()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(L10n.str(R.string.help_contact_title), style = MaterialTheme.typography.titleMedium)
            Text(L10n.str(R.string.found_bug_have_suggestion_write), style = MaterialTheme.typography.bodyMedium)
            FilledTonalButton(onClick = onReport, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(L10n.str(R.string.report_problem))
            }
            OutlinedButton(onClick = onOpenPrivacy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Policy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(L10n.str(R.string.privacy_policy))
            }
            if (onOpenChangelog != null) {
                OutlinedButton(onClick = onOpenChangelog, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.NewReleases, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(L10n.str(R.string.whats_new))
                }
            }
            Text(
                L10n.str(R.string.version_miga_x_2, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}
