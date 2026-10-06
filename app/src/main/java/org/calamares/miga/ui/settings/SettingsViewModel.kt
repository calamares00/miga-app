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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ColorTheme.TERRACOTTA)

    fun setColorTheme(colorTheme: ColorTheme) {
        viewModelScope.launch { settingsRepository.setColorTheme(colorTheme) }
    }

    val biometricLockEnabled: StateFlow<Boolean> = settingsRepository.observeBiometricLockEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setBiometricLockEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBiometricLockEnabled(enabled) }
    }

    fun exportLibrary(context: Context, destination: Uri) {
        viewModelScope.launch {
            val allBooks = repository.getAllRecipeBooksOnce()
            val recipes = repository.getAllRecipesOnce()
            val templates = repository.observeShoppingTemplates().first()
            val stores = repository.observeShoppingStores().first()
            RecipeExporter.exportLibrary(context, destination, allBooks, recipes, templates, stores)
        }
    }

    /** Lee y valida el archivo elegido sin escribir nada; el resultado decide si se muestra el
     *  diálogo de "borrar antes de importar" o un error, sin haber tocado la base de datos. */
    suspend fun validateLibraryImport(context: Context, source: Uri): LibraryImportParseResult =
        RecipeExporter.parseLibraryImport(context, source)

    fun confirmLibraryImport(context: Context, parsed: LibraryImportParseResult.Success, wipeFirst: Boolean, onMessage: (String) -> Unit) {
        viewModelScope.launch {
            val wipeMessage = if (wipeFirst) {
                val result = repository.wipeUserRecipesAndBooks()
                L10n.str(R.string.deleted_x_book_x_recipe, result.bookCount, result.recipeCount)
            } else ""
            when (val result = RecipeExporter.importParsedLibrary(context, parsed.dto, parsed.entries, repository)) {
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

    val visionProvider: StateFlow<AiProvider> = settingsRepository.observeVisionProvider()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiProvider.GEMINI)

    fun setVisionProvider(provider: AiProvider) {
        viewModelScope.launch { settingsRepository.setVisionProvider(provider) }
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

    /** Resumen para la pantalla principal de Ajustes: proveedores con clave, en orden de prioridad. */
    val aiProvidersSummary: StateFlow<List<AiProvider>> = kotlinx.coroutines.flow.combine(
        settingsRepository.observeProviderOrder(),
        settingsRepository.observeGeminiApiKey(),
        settingsRepository.observeAnthropicApiKey(),
        settingsRepository.observeOpenRouterApiKey(),
        settingsRepository.observeOpenRouterModel()
    ) { order, gemini, anthropic, openRouter, openRouterModel ->
        order.filter { provider ->
            when (provider) {
                AiProvider.GEMINI -> gemini.isNotBlank()
                AiProvider.ANTHROPIC -> anthropic.isNotBlank()
                AiProvider.OPENROUTER -> openRouter.isNotBlank() && openRouterModel.isNotBlank()
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun importRecipeIntoBook(dto: RecipeExportDto, photos: List<RecipePhoto>, bookId: Long, onFinished: () -> Unit) {
        viewModelScope.launch {
            val recipeId = repository.saveRecipe(dto.toDraft(bookId, photos))
            RecipeExporter.applyHealthFromImport(repository, recipeId, dto.health)
            if (dto.rating != null) repository.setRating(recipeId, dto.rating)
            onFinished()
        }
    }
}
