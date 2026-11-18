package org.calamares.miga.ui.settings

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import org.calamares.miga.data.model.ContentKind
import org.calamares.miga.L10n
import org.calamares.miga.R
import kotlinx.coroutines.flow.first
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.calamares.miga.BuildConfig
import org.calamares.miga.data.export.LibraryImportParseResult
import org.calamares.miga.data.export.LibraryImportResult
import org.calamares.miga.data.export.RecipeExportDto
import org.calamares.miga.data.export.RecipeExporter
import org.calamares.miga.data.export.RecipeImportResult
import org.calamares.miga.data.export.toDraft
import org.calamares.miga.data.local.DEFAULT_CARB_PORTION_GRAMS
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.model.ColorTheme
import org.calamares.miga.data.model.PhotoFrame
import org.calamares.miga.data.model.RecipeBookSummary
import org.calamares.miga.data.model.RecipePhoto
import org.calamares.miga.data.model.ThemeMode
import org.calamares.miga.data.remote.DEFAULT_PACKS_CATALOG
import org.calamares.miga.data.repository.RecipeRepository
import org.calamares.miga.data.ai.ProviderModels
import org.calamares.miga.data.ai.defaultModelFor
import org.calamares.miga.data.ai.AiProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

class SettingsViewModel(
    private val repository: RecipeRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.observeThemeMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    val colorTheme: StateFlow<ColorTheme> = settingsRepository.observeColorTheme()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ColorTheme.DEFAULT)

    fun setColorTheme(colorTheme: ColorTheme) {
        viewModelScope.launch { settingsRepository.setColorTheme(colorTheme) }
    }

    val photoFrame: StateFlow<PhotoFrame> = settingsRepository.observePhotoFrame()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PhotoFrame.DEFAULT)

    val disabledProviders: StateFlow<Set<AiProvider>> = settingsRepository.observeDisabledProviders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun setProviderEnabled(provider: AiProvider, enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setProviderEnabled(provider, enabled) }
    }

    /** Per list of Manage content: how many entries and how many of the user's own are unused. */
    val contentSummary: StateFlow<Map<ContentKind, ContentCounts>> = combine(
        ContentKind.entries.map { kind -> repository.observeContent(kind).map { items -> kind to ContentCounts(items.size, items.count { it.usage == 0 && !it.isDefault }) } }
    ) { pairs -> pairs.toMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** One of the user's own photos for the frame previews; null without any. */
    val samplePhoto: StateFlow<String?> = repository.observeSamplePhotoUri()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setPhotoFrame(frame: PhotoFrame) {
        viewModelScope.launch { settingsRepository.setPhotoFrame(frame) }
    }

    val biometricLockEnabled: StateFlow<Boolean> = settingsRepository.observeBiometricLockEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setBiometricLockEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBiometricLockEnabled(enabled) }
    }

    /**
     * Writes the full backup to [destination] with what [contents] includes, encrypted when
     * [password] is given; the password is wiped from memory afterwards. [onDone] reports the outcome.
     */
    fun exportLibrary(context: Context, destination: Uri, password: CharArray?, contents: BackupContents, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val message = try {
                val books = repository.books.getAllRecipeBooksOnce().filter { book ->
                    (contents.serverBooks || book.syncConnectionId == null) && (contents.packs || book.packId == null)
                }
                val bookIds = books.map { it.id }.toSet()
                val recipes = repository.getAllRecipesOnce().filter { it.recipeBookId in bookIds }
                val templates = repository.shopping.observeShoppingTemplates().first()
                val stores = repository.shopping.observeShoppingStores().first()
                val settings = if (contents.settings) settingsRepository.exportForBackup() else null
                RecipeExporter.exportLibrary(context, destination, books, recipes, templates, stores, settings, password)
                L10n.str(if (password != null) R.string.backup_exported_encrypted else R.string.backup_exported)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                L10n.str(R.string.couldnt_export_x, e.message ?: e::class.simpleName.orEmpty())
            } finally {
                password?.fill('\u0000')
            }
            onDone(message)
        }
    }

    /**
     * Reads and validates the chosen file without writing anything. The result decides whether the
     * "delete before importing" dialog or an error is shown, with the database untouched.
     */
    suspend fun validateLibraryImport(context: Context, source: Uri, password: CharArray? = null): LibraryImportParseResult {
        val result = RecipeExporter.parseLibraryImport(context, source, password)
        // Kept only by a successful result, which uses it to read the photos during the import.
        if (result !is LibraryImportParseResult.Success) password?.fill('\u0000')
        return result
    }

    /** Forgets the password of a backup the user decided not to import. */
    fun discardLibraryImport(parsed: LibraryImportParseResult.Success) {
        parsed.password?.fill('\u0000')
    }

    fun confirmLibraryImport(context: Context, parsed: LibraryImportParseResult.Success, wipeFirst: Boolean, onMessage: (String) -> Unit) {
        viewModelScope.launch {
            val wipeMessage = if (wipeFirst) {
                val result = repository.books.wipeUserRecipesAndBooks()
                L10n.str(R.string.deleted_x_book_x_recipe, result.bookCount, result.recipeCount)
            } else ""
            val result = try {
                RecipeExporter.importParsedLibrary(context, parsed, repository, restorePacks = true).also { result ->
                    val settings = parsed.dto.settings
                    if (result is LibraryImportResult.Success && settings != null) settingsRepository.restoreFromBackup(settings)
                }
            } finally {
                parsed.password?.fill('\u0000')
            }
            when (result) {
                is LibraryImportResult.Success -> onMessage(wipeMessage + L10n.str(R.string.imported_n_recipes, result.count))
                is LibraryImportResult.Error -> onMessage(L10n.str(R.string.couldnt_import_x, result.reason))
            }
        }
    }

    val books: StateFlow<List<RecipeBookSummary>> = repository.books.observeRecipeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val aiEnabled: StateFlow<Boolean> = settingsRepository.observeAiEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val aiHealthEnabled: StateFlow<Boolean> = settingsRepository.observeAiHealthEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val aiNutritionEnabled: StateFlow<Boolean> = settingsRepository.observeAiNutritionEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setAiEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAiEnabled(enabled) }
    }

    fun setAiHealthEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAiHealthEnabled(enabled) }
    }

    fun setAiNutritionEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAiNutritionEnabled(enabled) }
    }

    val carbCountingEnabled: StateFlow<Boolean> = settingsRepository.observeCarbCountingEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val carbPortionGrams: StateFlow<Int> = settingsRepository.observeCarbPortionGrams()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_CARB_PORTION_GRAMS)

    fun setCarbCountingEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setCarbCountingEnabled(enabled) }
    }

    fun setCarbPortionGrams(grams: Int) {
        viewModelScope.launch { settingsRepository.setCarbPortionGrams(grams) }
    }

    val providerOrder: StateFlow<List<AiProvider>> = settingsRepository.observeProviderOrder()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiProvider.entries.toList())

    fun setProviderOrder(order: List<AiProvider>) {
        viewModelScope.launch { settingsRepository.setProviderOrder(order) }
    }

    /** Summary for the main Settings screen: providers with a key, in priority order. */
    val aiProvidersSummary: StateFlow<List<AiProvider>> = settingsRepository.observeConfiguredProviders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Key, model and "reads images" setting of each provider. */
    val apiKeys: Map<AiProvider, StateFlow<String>> = AiProvider.entries.associateWith { provider ->
        settingsRepository.observeApiKey(provider).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    }
    val models: Map<AiProvider, StateFlow<String>> = AiProvider.entries.associateWith { provider ->
        settingsRepository.observeModel(provider).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), defaultModelFor(provider))
    }
    val modelImages: Map<AiProvider, StateFlow<Boolean>> = AiProvider.entries.associateWith { provider ->
        settingsRepository.observeModelImages(provider).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    }
    val customBaseUrl: StateFlow<String> = settingsRepository.observeCustomBaseUrl()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setApiKey(provider: AiProvider, apiKey: String) {
        viewModelScope.launch { settingsRepository.setApiKey(provider, apiKey) }
    }

    /** Chooses a model; whether it reads images comes from the provider's list when known. */
    fun setModel(provider: AiProvider, model: String, readsImages: Boolean? = ProviderModels.knownImageSupport(provider, model)) {
        viewModelScope.launch { settingsRepository.setModel(provider, model, readsImages) }
    }

    fun setModelImages(provider: AiProvider, readsImages: Boolean) {
        viewModelScope.launch { settingsRepository.setModelImages(provider, readsImages) }
    }

    fun setCustomBaseUrl(url: String) {
        viewModelScope.launch { settingsRepository.setCustomBaseUrl(url) }
    }

    val packsCatalogRepo: StateFlow<String> = settingsRepository.observePacksCatalogRepo()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_PACKS_CATALOG)

    fun setPacksCatalogRepo(repo: String) {
        viewModelScope.launch { settingsRepository.setPacksCatalogRepo(repo) }
    }

    /** "Your name": signs shared shopping lists and shared books and recipes. */
    val yourName: StateFlow<String> = settingsRepository.observeShoppingAuthor()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setYourName(name: String) {
        viewModelScope.launch { settingsRepository.setShoppingAuthor(name) }
    }

    val packInstallStats: StateFlow<Boolean> = settingsRepository.observePackInstallStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setPackInstallStats(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPackInstallStats(enabled) }
    }

    val ttsVoiceName: StateFlow<String?> = settingsRepository.observeTtsVoiceName()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val dictationLanguage: StateFlow<String> = settingsRepository.observeDictationLanguage()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), org.calamares.miga.data.voice.DictationLanguages.DEFAULT)

    fun setDictationLanguage(tag: String) {
        viewModelScope.launch { settingsRepository.setDictationLanguage(tag) }
    }

    fun setTtsVoiceName(name: String?) {
        viewModelScope.launch { settingsRepository.setTtsVoiceName(name) }
    }

    suspend fun parseRecipeJson(context: Context, source: Uri): RecipeImportResult =
        RecipeExporter.importRecipe(context, source)

    /** Imports an exported book or selection into [targetBookId], or as its own book when null. */
    fun importCollection(context: Context, collection: RecipeImportResult.Collection, targetBookId: Long?, onMessage: (String) -> Unit) {
        viewModelScope.launch {
            when (val result = RecipeExporter.importParsedLibrary(context, collection.parsed, repository, targetBookId)) {
                is LibraryImportResult.Success -> onMessage(L10n.str(R.string.imported_n_recipes, result.count))
                is LibraryImportResult.Error -> onMessage(L10n.str(R.string.couldnt_import_x, result.reason))
            }
        }
    }

    fun importRecipeIntoBook(dto: RecipeExportDto, photos: List<RecipePhoto>, bookId: Long, onFinished: () -> Unit) {
        viewModelScope.launch {
            val recipeId = repository.saveRecipe(dto.toDraft(bookId, photos))
            RecipeExporter.applyHealthFromImport(repository, recipeId, dto.health)
            RecipeExporter.applyJournalFromImport(repository, recipeId, dto.journal)
            if (dto.rating != null) repository.setRating(recipeId, dto.rating)
            onFinished()
        }
    }
}

data class ContentCounts(val total: Int, val unusedMine: Int)
