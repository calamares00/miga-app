package org.calamares.miga.ui.settings

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.ai.runAi
import org.calamares.miga.data.content.AiCleanupResult
import org.calamares.miga.data.content.CleanupAction
import org.calamares.miga.data.content.CleanupProposal
import org.calamares.miga.data.content.ContentCleanup
import org.calamares.miga.data.content.suggestContentCleanup
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.model.ContentItem
import org.calamares.miga.data.model.ContentKind
import org.calamares.miga.data.repository.ContentSnapshot
import org.calamares.miga.data.repository.RecipeRepository
import org.calamares.miga.ui.components.ButtonContent
import org.calamares.miga.ui.components.ErrorMessage
import org.calamares.miga.ui.components.rememberAiEnabled

/** What the cleanup screen shows. */
data class CleanupUiState(
    val scanning: Boolean = true,
    val proposals: List<CleanupProposal> = emptyList(),
    /** Keys of the ticked proposals. */
    val ticked: Set<String> = emptySet(),
    val askingAi: Boolean = false,
    val aiAsked: Boolean = false,
    val aiError: String? = null,
    val applying: Boolean = false,
    /** Set once applied: what was done, and how to undo it. */
    val done: CleanupDone? = null
)

data class CleanupDone(val merged: Int, val renamed: Int, val deleted: Int, val snapshot: ContentSnapshot, val undone: Boolean = false)

class ContentCleanupViewModel(
    private val repository: RecipeRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(CleanupUiState())
    val state: StateFlow<CleanupUiState> = _state

    private var content: Map<ContentKind, List<ContentItem>> = emptyMap()
    private val language: String get() = L10n.locale().language

    init {
        scan()
    }

    private fun scan() {
        viewModelScope.launch {
            val (lists, shoppingNames) = repository.contentForCleanup()
            content = lists
            val proposals = ContentCleanup.propose(lists, language, shoppingNames)
            _state.value = CleanupUiState(
                scanning = false,
                proposals = proposals,
                ticked = proposals.filter { it.recommended }.map { it.key }.toSet()
            )
        }
    }

    fun toggle(proposal: CleanupProposal) = _state.update { state ->
        state.copy(ticked = if (proposal.key in state.ticked) state.ticked - proposal.key else state.ticked + proposal.key)
    }

    /** Ticks every proposal of [action], or unticks them all when they already are. */
    fun toggleAll(action: CleanupAction) = _state.update { state ->
        val keys = state.proposals.filter { it.action == action }.map { it.key }.toSet()
        state.copy(ticked = if (state.ticked.containsAll(keys)) state.ticked - keys else state.ticked + keys)
    }

    fun refineWithAi() {
        if (_state.value.askingAi) return
        _state.update { it.copy(askingAi = true, aiError = null) }
        viewModelScope.launch {
            val current = _state.value.proposals
            val result = settingsRepository.runAi<AiCleanupResult>(
                errorOf = { (it as? AiCleanupResult.Error)?.reason },
                error = { AiCleanupResult.Error(it) }
            ) { ai -> ai.suggestContentCleanup(content, language, current) }
            _state.update { state ->
                when (result) {
                    null -> state.copy(askingAi = false, aiError = L10n.str(R.string.ai_no_provider))
                    is AiCleanupResult.Error -> state.copy(askingAi = false, aiError = result.reason)
                    is AiCleanupResult.Success -> state.copy(
                        askingAi = false,
                        aiAsked = true,
                        proposals = state.proposals + result.proposals.filter { new -> state.proposals.none { it.key == new.key } }
                    )
                }
            }
        }
    }

    fun apply() {
        val state = _state.value
        val chosen = state.proposals.filter { it.key in state.ticked }
        if (chosen.isEmpty() || state.applying) return
        _state.update { it.copy(applying = true) }
        viewModelScope.launch {
            val snapshot = repository.applyCleanup(chosen)
            _state.update {
                it.copy(
                    applying = false,
                    done = CleanupDone(
                        merged = chosen.count { p -> p.action == CleanupAction.MERGE },
                        renamed = chosen.count { p -> p.action == CleanupAction.RENAME },
                        deleted = chosen.count { p -> p.action == CleanupAction.DELETE },
                        snapshot = snapshot
                    )
                )
            }
        }
    }

    fun undo() {
        val done = _state.value.done ?: return
        if (done.undone) return
        viewModelScope.launch {
            repository.undoCleanup(done.snapshot)
            _state.update { it.copy(done = done.copy(undone = true)) }
        }
    }
}

/**
 * Settings > Manage content > Clean up: duplicates to merge, names to write the Miga way and the
 * user's own unused entries, found by rules (and on request by AI), to review and apply at once,
 * with an undo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentCleanupScreen(viewModel: ContentCleanupViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val aiEnabled = rememberAiEnabled()
    val chosen = state.proposals.count { it.key in state.ticked }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(L10n.str(R.string.cleanup_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = L10n.str(R.string.back)) } }
            )
        },
        bottomBar = {
            if (state.done == null && state.proposals.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { viewModel.apply() },
                        enabled = chosen > 0 && !state.applying,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)
                    ) {
                        if (state.applying) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            ButtonContent(Icons.Filled.CleaningServices, L10n.str(R.string.cleanup_apply_n, chosen))
                        }
                    }
                }
            }
        }
    ) { padding ->
        val done = state.done
        when {
            state.scanning -> Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            done != null -> CleanupDoneView(done, onUndo = { viewModel.undo() }, onClose = onBack, modifier = Modifier.padding(padding))
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                item(key = "intro") {
                    Text(
                        L10n.str(R.string.cleanup_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (aiEnabled) {
                    item(key = "ai") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { viewModel.refineWithAi() }, enabled = !state.askingAi && !state.aiAsked) {
                                if (state.askingAi) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text(L10n.str(R.string.cleanup_ai_working))
                                } else {
                                    ButtonContent(Icons.Filled.AutoAwesome, L10n.str(if (state.aiAsked) R.string.cleanup_ai_done else R.string.cleanup_ai))
                                }
                            }
                            state.aiError?.let { ErrorMessage(it, onRetry = { viewModel.refineWithAi() }) }
                        }
                    }
                }
                if (state.proposals.isEmpty()) {
                    item(key = "clean") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                            Text(L10n.str(R.string.cleanup_nothing), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                listOf(CleanupAction.MERGE, CleanupAction.RENAME, CleanupAction.DELETE).forEach { action ->
                    val group = state.proposals.filter { it.action == action }
                    if (group.isEmpty()) return@forEach
                    item(key = "header-$action") {
                        CleanupSectionHeader(
                            action = action,
                            count = group.size,
                            allTicked = group.all { it.key in state.ticked },
                            onToggleAll = { viewModel.toggleAll(action) }
                        )
                    }
                    items(group, key = { it.key }) { proposal ->
                        ProposalRow(proposal, ticked = proposal.key in state.ticked, onToggle = { viewModel.toggle(proposal) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanupSectionHeader(action: CleanupAction, count: Int, allTicked: Boolean, onToggleAll: () -> Unit) {
    val (icon, title) = when (action) {
        CleanupAction.MERGE -> Icons.Filled.CallMerge to L10n.str(R.string.cleanup_section_merge, count)
        CleanupAction.RENAME -> Icons.Filled.DriveFileRenameOutline to L10n.str(R.string.cleanup_section_rename, count)
        CleanupAction.DELETE -> Icons.Filled.DeleteSweep to L10n.str(R.string.cleanup_section_delete, count)
    }
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 8.dp))
        TextButton(onClick = onToggleAll) { Text(L10n.str(if (allTicked) R.string.cleanup_untick_all else R.string.cleanup_tick_all)) }
    }
}

@Composable
private fun ProposalRow(proposal: CleanupProposal, ticked: Boolean, onToggle: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = ticked, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(proposalText(proposal), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                val details = buildList {
                    add(proposal.kind.title)
                    if (proposal.affected > 0) add(proposal.kind.usageLabel(proposal.affected))
                }.joinToString(" · ")
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (proposal.fromAi) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = L10n.str(R.string.cleanup_from_ai),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp).size(18.dp)
                )
            }
        }
    }
}

private fun proposalText(proposal: CleanupProposal): String = when (proposal.action) {
    CleanupAction.MERGE -> proposal.items.joinToString(", ") { "«${it.name}»" } + " → «${proposal.newName ?: proposal.target?.name}»"
    CleanupAction.RENAME -> "«${proposal.items.single().name}» → «${proposal.newName}»"
    CleanupAction.DELETE -> "«${proposal.items.single().name}»"
}

@Composable
private fun CleanupDoneView(done: CleanupDone, onUndo: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        Icon(
            if (done.undone) Icons.Filled.Undo else Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Text(
            L10n.str(if (done.undone) R.string.cleanup_undone else R.string.cleanup_done),
            style = MaterialTheme.typography.titleLarge
        )
        if (!done.undone) {
            DoneLine(Icons.Filled.CallMerge, L10n.str(R.string.cleanup_done_merged, done.merged))
            DoneLine(Icons.Filled.DriveFileRenameOutline, L10n.str(R.string.cleanup_done_renamed, done.renamed))
            DoneLine(Icons.Filled.DeleteSweep, L10n.str(R.string.cleanup_done_deleted, done.deleted))
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!done.undone) {
                OutlinedButton(onClick = onUndo) { ButtonContent(Icons.Filled.Undo, L10n.str(R.string.undo)) }
            }
            Button(onClick = onClose) { Text(L10n.str(R.string.close)) }
        }
    }
}

@Composable
private fun DoneLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}
