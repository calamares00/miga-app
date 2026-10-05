package org.calamares.miga.ui.settings

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
import org.calamares.miga.data.remote.DEFAULT_PACKS_CATALOG_REPO
import org.calamares.miga.data.repository.RecipeRepository
import org.calamares.miga.data.vision.DEFAULT_ANTHROPIC_MODEL
import org.calamares.miga.data.vision.DEFAULT_GEMINI_MODEL
import org.calamares.miga.data.vision.VisionProviderType
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
            RecipeExporter.exportLibrary(context, destination, allBooks, recipes)
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
                "Se borraron ${result.bookCount} libro(s) y ${result.recipeCount} receta(s). "
            } else ""
            when (val result = RecipeExporter.importParsedLibrary(context, parsed.dto, parsed.entries, repository)) {
                is LibraryImportResult.Success -> onMessage("$wipeMessage" + "Se importaron ${result.count} recetas")
                is LibraryImportResult.Error -> onMessage("No se pudo importar: ${result.reason}")
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

    val visionProvider: StateFlow<VisionProviderType> = settingsRepository.observeVisionProvider()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VisionProviderType.GEMINI)

    fun setVisionProvider(provider: VisionProviderType) {
        viewModelScope.launch { settingsRepository.setVisionProvider(provider) }
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_PACKS_CATALOG_REPO)

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
