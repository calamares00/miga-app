package org.calamares.miga.ui.settings

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
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.model.ColorTheme
import org.calamares.miga.data.model.RecipeBookSummary
import org.calamares.miga.data.model.RecipePhoto
import org.calamares.miga.data.model.ThemeMode
import org.calamares.miga.data.remote.DEFAULT_PACKS_CATALOG
import org.calamares.miga.data.repository.RecipeRepository
import org.calamares.miga.data.ai.DEFAULT_ANTHROPIC_MODEL
import org.calamares.miga.data.ai.DEFAULT_OPENAI_MODEL
import org.calamares.miga.data.ai.DEFAULT_GEMINI_MODEL
import org.calamares.miga.data.ai.AiProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    val biometricLockEnabled: StateFlow<Boolean> = settingsRepository.observeBiometricLockEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setBiometricLockEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBiometricLockEnabled(enabled) }
    }

    /**
     * Writes the full backup to [destination], encrypted when [password] is given; the password is
     * wiped from memory afterwards. [onDone] reports the outcome.
     */
    fun exportLibrary(context: Context, destination: Uri, password: CharArray?, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val message = try {
                val allBooks = repository.getAllRecipeBooksOnce()
                val recipes = repository.getAllRecipesOnce()
                val templates = repository.observeShoppingTemplates().first()
                val stores = repository.observeShoppingStores().first()
                RecipeExporter.exportLibrary(context, destination, allBooks, recipes, templates, stores, password)
                L10n.str(if (password != null) R.string.backup_exported_encrypted else R.string.backup_exported)
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
                val result = repository.wipeUserRecipesAndBooks()
                L10n.str(R.string.deleted_x_book_x_recipe, result.bookCount, result.recipeCount)
            } else ""
            val result = try {
                RecipeExporter.importParsedLibrary(context, parsed, repository)
            } finally {
                parsed.password?.fill('\u0000')
            }
            when (result) {
                is LibraryImportResult.Success -> onMessage(wipeMessage + L10n.str(R.string.imported_n_recipes, result.count))
                is LibraryImportResult.Error -> onMessage(L10n.str(R.string.couldnt_import_x, result.reason))
            }
        }
    }

    val books: StateFlow<List<RecipeBookSummary>> = repository.observeRecipeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val geminiApiKey: StateFlow<String> = settingsRepository.observeGeminiApiKey()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setGeminiApiKey(apiKey: String) {
        viewModelScope.launch { settingsRepository.setGeminiApiKey(apiKey) }
    }

    val geminiModel: StateFlow<String> = settingsRepository.observeGeminiModel()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_GEMINI_MODEL)

    fun setGeminiModel(model: String) {
        viewModelScope.launch { settingsRepository.setGeminiModel(model) }
    }

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

    val providerOrder: StateFlow<List<AiProvider>> = settingsRepository.observeProviderOrder()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiProvider.entries.toList())

    fun setProviderOrder(order: List<AiProvider>) {
        viewModelScope.launch { settingsRepository.setProviderOrder(order) }
    }

    val openRouterApiKey: StateFlow<String> = settingsRepository.observeOpenRouterApiKey()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setOpenRouterApiKey(apiKey: String) {
        viewModelScope.launch { settingsRepository.setOpenRouterApiKey(apiKey) }
    }

    val openRouterModel: StateFlow<String> = settingsRepository.observeOpenRouterModel()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val openRouterModelImages: StateFlow<Boolean> = settingsRepository.observeOpenRouterModelImages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setOpenRouterModel(model: String, supportsImages: Boolean) {
        viewModelScope.launch { settingsRepository.setOpenRouterModel(model, supportsImages) }
    }

    /** Summary for the main Settings screen: providers with a key, in priority order. */
    val aiProvidersSummary: StateFlow<List<AiProvider>> = settingsRepository.observeConfiguredProviders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val openAiApiKey: StateFlow<String> = settingsRepository.observeOpenAiApiKey()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setOpenAiApiKey(apiKey: String) {
        viewModelScope.launch { settingsRepository.setOpenAiApiKey(apiKey) }
    }

    val openAiModel: StateFlow<String> = settingsRepository.observeOpenAiModel()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_OPENAI_MODEL)

    fun setOpenAiModel(model: String) {
        viewModelScope.launch { settingsRepository.setOpenAiModel(model) }
    }

    val anthropicApiKey: StateFlow<String> = settingsRepository.observeAnthropicApiKey()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setAnthropicApiKey(apiKey: String) {
        viewModelScope.launch { settingsRepository.setAnthropicApiKey(apiKey) }
    }

    val anthropicModel: StateFlow<String> = settingsRepository.observeAnthropicModel()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_ANTHROPIC_MODEL)

    fun setAnthropicModel(model: String) {
        viewModelScope.launch { settingsRepository.setAnthropicModel(model) }
    }

    val packsCatalogRepo: StateFlow<String> = settingsRepository.observePacksCatalogRepo()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_PACKS_CATALOG)

    fun setPacksCatalogRepo(repo: String) {
        viewModelScope.launch { settingsRepository.setPacksCatalogRepo(repo) }
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
            if (dto.rating != null) repository.setRating(recipeId, dto.rating)
            onFinished()
        }
    }
}
