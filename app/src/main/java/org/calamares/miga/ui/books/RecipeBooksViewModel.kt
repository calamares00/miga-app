package org.calamares.miga.ui.books

import kotlinx.coroutines.flow.combine
import android.content.Context
import org.calamares.miga.data.local.PhotoStorage
import org.calamares.miga.data.model.RecipePhoto
import org.calamares.miga.data.export.SharedRecipeImportResult
import org.calamares.miga.data.export.RecipeExportDto
import org.calamares.miga.data.export.RecipeImportResult
import org.calamares.miga.data.remote.ShareLinkClient
import java.io.File
import android.net.Uri
import org.calamares.miga.data.export.FileExports
import org.calamares.miga.data.export.LibraryImportParseResult
import org.calamares.miga.data.export.RecipeExporter
import org.calamares.miga.data.export.SharedBookImportResult
import org.calamares.miga.data.share.BookFileIntents
import org.calamares.miga.data.share.IncomingBookFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.calamares.miga.BuildConfig
import org.calamares.miga.R
import org.calamares.miga.L10n
import org.calamares.miga.ui.packs.PackInstaller
import org.calamares.miga.data.remote.PackEntryDto
import org.calamares.miga.crash.CrashReporter
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.model.ChangelogParser
import org.calamares.miga.data.model.RecipeBookSummary
import org.calamares.miga.data.model.RecipeListViewMode
import org.calamares.miga.data.remote.AppInstallSource
import org.calamares.miga.data.remote.AppRelease
import org.calamares.miga.data.remote.AppUpdateClient
import org.calamares.miga.data.repository.RecipeRepository
import org.calamares.miga.data.sync.SyncEngine
import org.calamares.miga.data.sync.SyncOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChangelogAnnouncement(val versionName: String, val entries: List<String>)

class RecipeBooksViewModel(
    private val repository: RecipeRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val syncEngine = SyncEngine(repository)

    /** True when AI is switched on and at least one provider is configured, so Ideas can be offered. */
    val aiReady: StateFlow<Boolean> = combine(
        settingsRepository.observeAiEnabled(),
        settingsRepository.observeConfiguredProviders()
    ) { enabled, providers -> enabled && providers.isNotEmpty() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    private var autoSyncStarted = false

    /** Null until the books have loaded, so the screen does not show "no books yet" meanwhile. */
    val books: StateFlow<List<RecipeBookSummary>?> = repository.observeRecipeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val viewMode: StateFlow<RecipeListViewMode> = settingsRepository.observeRecipeBookListViewMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecipeListViewMode.GRID)

    /** Whether the list is split into my books, server books and packs by series (stored). */
    val grouped: StateFlow<Boolean> = settingsRepository.observeRecipeBookListGrouped()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Kind of book shown; kept while the books tab lives, not stored, so no book stays hidden. */
    private val _filter = MutableStateFlow(BookFilter.ALL)
    val filter: StateFlow<BookFilter> = _filter

    /** Names of the sync connections, for the subgroups of server books. */
    val connectionNames: StateFlow<Map<Long, String>> = repository.observeSyncConnections()
        .map { connections -> connections.associate { it.id to it.label } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun setGrouped(grouped: Boolean) {
        viewModelScope.launch { settingsRepository.setRecipeBookListGrouped(grouped) }
    }

    /** Installed packs with a newer version, found when the app opens (see [checkPackUpdates]). */
    private val _packUpdates = MutableStateFlow<List<PackEntryDto>>(emptyList())
    val packUpdates: StateFlow<List<PackEntryDto>> = _packUpdates
    private var packCheckStarted = false

    /** Whether the found pack updates are being installed. */
    private val _updatingPacks = MutableStateFlow(false)
    val updatingPacks: StateFlow<Boolean> = _updatingPacks

    /**
     * Looks once per app start for newer versions of the installed packs. Versions already
     * answered with "Not now" are not announced again; a newer one is.
     */
    fun checkPackUpdates() {
        if (packCheckStarted) return
        packCheckStarted = true
        viewModelScope.launch {
            val updates = PackInstaller.findUpdates(repository, settingsRepository) ?: return@launch
            val dismissed = settingsRepository.dismissedPackUpdates()
            if (updates.any { "${it.id}:${it.latestVersion}" !in dismissed }) _packUpdates.value = updates
        }
    }

    /** Downloads and installs every found update; [onDone] gets the message to show. */
    fun installPackUpdates(context: Context, onDone: (String) -> Unit) {
        val updates = _packUpdates.value
        if (updates.isEmpty() || _updatingPacks.value) return
        _packUpdates.value = emptyList()
        _updatingPacks.value = true
        val appContext = context.applicationContext
        viewModelScope.launch {
            val failed = PackInstaller.installAll(appContext, repository, settingsRepository, updates)
            _updatingPacks.value = false
            onDone(
                if (failed.isEmpty()) L10n.str(R.string.x_packs_updated, updates.size)
                else L10n.str(R.string.couldnt_install_packs_x, failed.joinToString(", "))
            )
        }
    }

    /** "Not now": these versions are not announced again (the catalogue still offers them). */
    fun dismissPackUpdates() {
        val updates = _packUpdates.value
        _packUpdates.value = emptyList()
        viewModelScope.launch {
            settingsRepository.setDismissedPackUpdates(updates.map { "${it.id}:${it.latestVersion}" }.toSet())
        }
    }

    fun setFilter(filter: BookFilter) {
        _filter.value = filter
    }

    private val _syncing = MutableStateFlow(false)
    /** True while "Sync" (server filter) is syncing every connection. */
    val syncing: StateFlow<Boolean> = _syncing

    private val _syncMessage = MutableStateFlow<String?>(null)
    /** Result of the last "Sync", shown once in a snackbar. */
    val syncMessage: StateFlow<String?> = _syncMessage

    /** Syncs every connection now, on request (the button of the server filter). */
    fun syncNow(context: Context) {
        if (_syncing.value) return
        _syncing.value = true
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                val errors = repository.observeSyncConnections().first().mapNotNull { connection ->
                    (syncEngine.syncConnection(appContext, connection.id) as? SyncOutcome.Error)?.reason
                }
                _syncMessage.value = if (errors.isEmpty()) L10n.str(R.string.sync_finished) else L10n.str(R.string.sync_failed_x, errors.first())
            } finally {
                _syncing.value = false
            }
        }
    }

    fun dismissSyncMessage() {
        _syncMessage.value = null
    }

    /** A shared book file waiting for the user to confirm importing it. */
    data class IncomingBookPrompt(
        val parsed: LibraryImportParseResult.Success,
        val bookName: String,
        val recipeCount: Int,
        val alreadyHere: Boolean,
        val sharedBy: String?,
        val sharedAt: Long?
    )

    /**
     * A recipe shared alone, waiting for the user to choose its book ([existingBookName] when this
     * phone already has it: it is then updated where it is). Its photos are already copied.
     */
    data class IncomingRecipePrompt(
        val recipe: RecipeExportDto,
        val photos: List<RecipePhoto>,
        val existingBookName: String?
    )

    private val _incomingRecipe = MutableStateFlow<IncomingRecipePrompt?>(null)
    val incomingRecipe: StateFlow<IncomingRecipePrompt?> = _incomingRecipe

    private val _incomingBook = MutableStateFlow<IncomingBookPrompt?>(null)
    val incomingBook: StateFlow<IncomingBookPrompt?> = _incomingBook

    private val _importingBook = MutableStateFlow(false)
    val importingBook: StateFlow<Boolean> = _importingBook

    private val _downloadingBook = MutableStateFlow(false)
    /** True while a book link is being downloaded, before asking whether to import it. */
    val downloadingBook: StateFlow<Boolean> = _downloadingBook

    /** A Miga file opened from another app: reads it and asks before importing anything. */
    fun onIncomingFile(context: Context, event: IncomingBookFile) {
        BookFileIntents.consume()
        val appContext = context.applicationContext
        viewModelScope.launch {
            var password: CharArray? = null
            val file = when (event) {
                is IncomingBookFile.Ready -> event.file
                is IncomingBookFile.Link -> {
                    _downloadingBook.value = true
                    val dir = File(appContext.cacheDir, "incoming").apply { mkdirs() }
                    val target = File(dir, "${event.link.id}.miga")
                    val ok = try { ShareLinkClient.download(event.link, target) } finally { _downloadingBook.value = false }
                    if (!ok) {
                        _syncMessage.value = L10n.str(R.string.share_link_expired)
                        return@launch
                    }
                    password = event.link.key.toCharArray()
                    target
                }
                IncomingBookFile.Unreadable -> null
            }
            if (file == null) {
                _syncMessage.value = L10n.str(R.string.couldnt_open_file)
                return@launch
            }
            // A shared book (library format) or a single shared recipe.
            when (val result = RecipeExporter.importRecipe(appContext, Uri.fromFile(file), password)) {
                is RecipeImportResult.Collection -> {
                    val parsed = result.parsed
                    val book = parsed.dto.books.singleOrNull()
                    if (book == null) {
                        _syncMessage.value = L10n.str(R.string.shared_file_not_a_book)
                    } else {
                        _incomingBook.value = IncomingBookPrompt(
                            parsed, book.name, parsed.dto.recipes.size,
                            alreadyHere = repository.findSharedBookId(book.uid) != null,
                            sharedBy = book.sharedBy,
                            sharedAt = book.sharedAt
                        )
                    }
                }
                is RecipeImportResult.Success -> {
                    val existing = repository.findRecipeStateByUid(result.recipe.uid)?.takeIf { !it.inPack }
                    _incomingRecipe.value = IncomingRecipePrompt(
                        result.recipe, result.photos,
                        existingBookName = existing?.let { repository.recipeBookName(it.bookId) }
                    )
                }
                is RecipeImportResult.Error -> _syncMessage.value = result.reason
            }
        }
    }

    fun importIncomingBook(context: Context) {
        val prompt = _incomingBook.value ?: return
        _incomingBook.value = null
        val appContext = context.applicationContext
        _importingBook.value = true
        viewModelScope.launch {
            try {
                _syncMessage.value = when (val result = RecipeExporter.importSharedBook(appContext, prompt.parsed, repository)) {
                    is SharedBookImportResult.Success ->
                        L10n.str(R.string.shared_book_imported_x_x_x, result.bookName, result.added, result.updated)
                    is SharedBookImportResult.Error -> result.reason
                }
            } finally {
                _importingBook.value = false
            }
        }
    }

    fun dismissIncomingBook() {
        _incomingBook.value = null
    }

    /** Saves the shared recipe into [targetBookId], or into a new book called [newBookName]. */
    fun importIncomingRecipe(targetBookId: Long?, newBookName: String?) {
        val prompt = _incomingRecipe.value ?: return
        _incomingRecipe.value = null
        _importingBook.value = true
        viewModelScope.launch {
            try {
                _syncMessage.value = when (val result = RecipeExporter.importSharedRecipe(repository, prompt.recipe, prompt.photos, targetBookId, newBookName)) {
                    is SharedRecipeImportResult.Success -> {
                        val book = repository.recipeBookName(result.bookId).orEmpty()
                        L10n.str(
                            when {
                                result.unchanged -> R.string.shared_recipe_unchanged_x_x
                                result.updated -> R.string.shared_recipe_updated_x_x
                                else -> R.string.shared_recipe_saved_x_x
                            },
                            prompt.recipe.name, book
                        )
                    }
                    is SharedRecipeImportResult.Error -> result.reason
                }
            } finally {
                _importingBook.value = false
            }
        }
    }

    /** Not imported: the photos already copied for it are deleted. */
    fun dismissIncomingRecipe() {
        _incomingRecipe.value?.photos?.forEach { PhotoStorage.deleteFile(it.uri) }
        _incomingRecipe.value = null
    }

    /** "Share link": the book encrypted on a temporary link, sent as text. */
    fun shareBookLink(context: Context, summary: RecipeBookSummary) {
        viewModelScope.launch {
            val book = repository.getRecipeBookOnce(summary.id) ?: return@launch
            if (!FileExports.shareBookLink(context, book) { repository.getRecipesForBookOnce(summary.id) }) {
                _syncMessage.value = L10n.str(R.string.pdf_already_running)
            }
        }
    }

    /** "Share book": the book as a ".miga" file, straight to the share sheet. */
    fun shareBook(context: Context, summary: RecipeBookSummary) {
        viewModelScope.launch {
            val book = repository.getRecipeBookOnce(summary.id) ?: return@launch
            if (!FileExports.shareBook(context, book) { repository.getRecipesForBookOnce(summary.id) }) {
                _syncMessage.value = L10n.str(R.string.pdf_already_running)
            }
        }
    }

    /** Uninstalls the pack of [book] (its book and the recipes still in it). */
    fun uninstallPack(book: RecipeBookSummary) {
        if (!book.isPack) return
        viewModelScope.launch { repository.uninstallPack(book.id) }
    }

    fun togglePinned(book: RecipeBookSummary) {
        viewModelScope.launch { repository.setRecipeBookPinned(book.id, !book.isPinned) }
    }

    fun setViewMode(mode: RecipeListViewMode) {
        viewModelScope.launch { settingsRepository.setRecipeBookListViewMode(mode) }
    }

    private val _changelogAnnouncement = MutableStateFlow<ChangelogAnnouncement?>(null)
    val changelogAnnouncement: StateFlow<ChangelogAnnouncement?> = _changelogAnnouncement

    /**
     * Report of the last uncaught crash (see CrashReporter), if any. Local only; nothing is sent.
     */
    private val _crashReport = MutableStateFlow(CrashReporter.pendingReport())
    val crashReport: StateFlow<String?> = _crashReport

    /** A newer version on Google Play, found when the app opens (see [checkForUpdate]). */
    private val _availableUpdate = MutableStateFlow<AppRelease?>(null)
    val availableUpdate: StateFlow<AppRelease?> = _availableUpdate
    private var updateCheckStarted = false

    /**
     * Looks for a newer Miga each time the app opens, when it was installed from Google Play
     * (F-Droid and sideloaded installs update elsewhere, so they make no request). A release is
     * announced as soon as it is published, even if Play has not approved it yet. Once per
     * ViewModel instance, so coming back to the books tab does not ask again.
     */
    fun checkForUpdate(context: Context) {
        if (updateCheckStarted || !AppInstallSource.isGooglePlay(context)) return
        updateCheckStarted = true
        viewModelScope.launch {
            val latest = AppUpdateClient.fetchLatest() ?: return@launch
            if (AppUpdateClient.isNewer(latest.versionName, BuildConfig.VERSION_NAME) &&
                latest.versionName != settingsRepository.dismissedUpdateVersion()
            ) {
                _availableUpdate.value = latest
            }
        }
    }

    /** [forever] ("Not now") stops announcing this version; otherwise it comes back the next time the app opens. */
    fun dismissUpdate(forever: Boolean) {
        val release = _availableUpdate.value ?: return
        _availableUpdate.value = null
        if (forever) viewModelScope.launch { settingsRepository.setDismissedUpdateVersion(release.versionName) }
    }

    fun dismissCrashReport() {
        CrashReporter.dismiss()
        _crashReport.value = null
    }

    init {
        viewModelScope.launch {
            val lastSeen = settingsRepository.observeLastSeenVersionCode().first()
            val current = BuildConfig.VERSION_CODE
            if (lastSeen == 0) {
                // First run of this logic (fresh install, or update from a version without the
                // in-app changelog): nothing to show yet, just start recording the last seen
                // version.
                settingsRepository.setLastSeenVersionCode(current)
            } else if (lastSeen < current) {
                // The items of every version since the last one seen, without their "What's new in
                // x.y.z" line: the dialog title already names the version.
                val entries = (lastSeen + 1..current).flatMap { code ->
                    settingsRepository.readChangelog(code)?.let { ChangelogParser.parse(code, it).items }.orEmpty()
                }
                if (entries.isNotEmpty()) {
                    _changelogAnnouncement.value = ChangelogAnnouncement(BuildConfig.VERSION_NAME, entries)
                }
                settingsRepository.setLastSeenVersionCode(current)
            }
        }
    }

    /**
     * Syncs every configured connection when the app opens, so changes from other devices on the
     * same namespace arrive without a manual sync. Runs once per ViewModel instance (which lives as
     * long as the books tab) to avoid syncing on every recomposition or tab switch.
     */
    fun syncAllOnOpen(context: Context) {
        if (autoSyncStarted) return
        autoSyncStarted = true
        viewModelScope.launch {
            repository.observeSyncConnections().first().forEach { connection ->
                syncEngine.syncConnection(context, connection.id)
            }
        }
    }

    fun dismissChangelogAnnouncement() {
        _changelogAnnouncement.value = null
    }
}
