package com.bmo00.miga.data.local

import android.content.Context
import com.bmo00.miga.data.voice.DictationLanguages
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import com.bmo00.miga.data.model.ColorTheme
import com.bmo00.miga.data.model.RecipeListViewMode
import com.bmo00.miga.data.model.ThemeMode
import com.bmo00.miga.data.remote.DEFAULT_PACKS_CATALOG_REPO
import com.bmo00.miga.data.vision.DEFAULT_ANTHROPIC_MODEL
import com.bmo00.miga.data.vision.DEFAULT_GEMINI_MODEL
import com.bmo00.miga.data.vision.VisionProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val colorThemeKey = stringPreferencesKey("color_theme")
    private val biometricLockKey = booleanPreferencesKey("biometric_lock_enabled")
    private val shoppingImagesKey = booleanPreferencesKey("shopping_images_enabled")
    private val shoppingStoreKey = longPreferencesKey("shopping_store_id")
    private val shoppingListUidKey = stringPreferencesKey("shopping_list_uid")
    private val shoppingAuthorKey = stringPreferencesKey("shopping_author_name")
    private val recipeListViewModeKey = stringPreferencesKey("recipe_list_view_mode")
    private val recipeBookListViewModeKey = stringPreferencesKey("recipe_book_list_view_mode")
    private val visionProviderKey = stringPreferencesKey("vision_provider")
    private val geminiApiKeyKey = stringPreferencesKey("gemini_api_key")
    private val geminiModelKey = stringPreferencesKey("gemini_model")
    private val anthropicApiKeyKey = stringPreferencesKey("anthropic_api_key")
    private val anthropicModelKey = stringPreferencesKey("anthropic_model")
    private val ttsVoiceNameKey = stringPreferencesKey("tts_voice_name")
    private val dictationLanguageKey = stringPreferencesKey("dictation_language")
    private val lastSeenVersionCodeKey = intPreferencesKey("last_seen_version_code")
    private val packsCatalogRepoKey = stringPreferencesKey("packs_catalog_repo")

    fun observeThemeMode(): Flow<ThemeMode> =
        context.settingsDataStore.data.map { prefs ->
            prefs[themeModeKey]?.let { stored ->
                runCatching { ThemeMode.valueOf(stored) }.getOrDefault(ThemeMode.SYSTEM)
            } ?: ThemeMode.SYSTEM
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs -> prefs[themeModeKey] = mode.name }
    }

    fun observeColorTheme(): Flow<ColorTheme> =
        context.settingsDataStore.data.map { prefs ->
            prefs[colorThemeKey]?.let { stored ->
                runCatching { ColorTheme.valueOf(stored) }.getOrDefault(ColorTheme.TERRACOTTA)
            } ?: ColorTheme.TERRACOTTA
        }

    suspend fun setColorTheme(colorTheme: ColorTheme) {
        context.settingsDataStore.edit { prefs -> prefs[colorThemeKey] = colorTheme.name }
    }

    /** Lista de la compra en la que se está trabajando; "main" = la lista por defecto. */
    fun observeShoppingListUid(): Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[shoppingListUidKey] ?: "main" }

    suspend fun setShoppingListUid(uid: String) {
        context.settingsDataStore.edit { prefs -> prefs[shoppingListUidKey] = uid }
    }

    /** Nombre con el que se firman los cambios en una lista compartida (vacío = sin firma). */
    fun observeShoppingAuthor(): Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[shoppingAuthorKey].orEmpty() }

    suspend fun setShoppingAuthor(name: String) {
        context.settingsDataStore.edit { prefs -> prefs[shoppingAuthorKey] = name.trim().take(60) }
    }

    /** Supermercado elegido para ordenar la lista de la compra por sus pasillos; 0 = ninguno. */
    fun observeShoppingStoreId(): Flow<Long> =
        context.settingsDataStore.data.map { prefs -> prefs[shoppingStoreKey] ?: 0L }

    suspend fun setShoppingStoreId(id: Long) {
        context.settingsDataStore.edit { prefs -> prefs[shoppingStoreKey] = id }
    }

    /** Por defecto desactivado: mostrar las fotos de producto de la lista de la compra descarga imágenes de Open Food Facts. */
    fun observeShoppingImagesEnabled(): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[shoppingImagesKey] ?: false }

    suspend fun setShoppingImagesEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[shoppingImagesKey] = enabled }
    }

    fun observeBiometricLockEnabled(): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[biometricLockKey] ?: false }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[biometricLockKey] = enabled }
    }

    /** Por defecto activado: comprobar si hay una versión nueva cada vez que se abre la app. */
    /** Vista de la lista de recetas: se guarda de forma global (no por libro), como el tema. */
    fun observeRecipeListViewMode(): Flow<RecipeListViewMode> =
        context.settingsDataStore.data.map { prefs ->
            prefs[recipeListViewModeKey]?.let { stored ->
                runCatching { RecipeListViewMode.valueOf(stored) }.getOrDefault(RecipeListViewMode.NORMAL)
            } ?: RecipeListViewMode.NORMAL
        }

    suspend fun setRecipeListViewMode(mode: RecipeListViewMode) {
        context.settingsDataStore.edit { prefs -> prefs[recipeListViewModeKey] = mode.name }
    }

    /** Vista de la lista de libros; guardada aparte de la de recetas (misma escala COMPACT/NORMAL/GRID). */
    fun observeRecipeBookListViewMode(): Flow<RecipeListViewMode> =
        context.settingsDataStore.data.map { prefs ->
            prefs[recipeBookListViewModeKey]?.let { stored ->
                runCatching { RecipeListViewMode.valueOf(stored) }.getOrDefault(RecipeListViewMode.GRID)
            } ?: RecipeListViewMode.GRID
        }

    suspend fun setRecipeBookListViewMode(mode: RecipeListViewMode) {
        context.settingsDataStore.edit { prefs -> prefs[recipeBookListViewModeKey] = mode.name }
    }

    /** Proveedor de LLM usado para reconocer recetas a partir de una foto (ver `data/vision`). */
    fun observeVisionProvider(): Flow<VisionProviderType> =
        context.settingsDataStore.data.map { prefs ->
            prefs[visionProviderKey]?.let { stored ->
                runCatching { VisionProviderType.valueOf(stored) }.getOrDefault(VisionProviderType.GEMINI)
            } ?: VisionProviderType.GEMINI
        }

    suspend fun setVisionProvider(provider: VisionProviderType) {
        context.settingsDataStore.edit { prefs -> prefs[visionProviderKey] = provider.name }
    }

    /** API key de Gemini introducida por el propio usuario (BYOK); vacía si no se ha configurado. */
    fun observeGeminiApiKey(): Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[geminiApiKeyKey].orEmpty() }

    suspend fun setGeminiApiKey(apiKey: String) {
        context.settingsDataStore.edit { prefs -> prefs[geminiApiKeyKey] = apiKey.trim() }
    }

    /** Id del modelo de Gemini a usar (ver `data/vision/GeminiModels.kt`); uno de la lista o uno escrito a mano. */
    fun observeGeminiModel(): Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[geminiModelKey] ?: DEFAULT_GEMINI_MODEL }

    suspend fun setGeminiModel(model: String) {
        context.settingsDataStore.edit { prefs -> prefs[geminiModelKey] = model.trim() }
    }

    /** API key de Anthropic (Claude) introducida por el propio usuario (BYOK); vacía si no se ha configurado. */
    fun observeAnthropicApiKey(): Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[anthropicApiKeyKey].orEmpty() }

    suspend fun setAnthropicApiKey(apiKey: String) {
        context.settingsDataStore.edit { prefs -> prefs[anthropicApiKeyKey] = apiKey.trim() }
    }

    /** Id del modelo de Claude a usar (ver `data/vision/AnthropicModels.kt`); uno de la lista o uno escrito a mano. */
    fun observeAnthropicModel(): Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[anthropicModelKey] ?: DEFAULT_ANTHROPIC_MODEL }

    suspend fun setAnthropicModel(model: String) {
        context.settingsDataStore.edit { prefs -> prefs[anthropicModelKey] = model.trim() }
    }

    /** Resuelve la API key configurada para [provider] (una por proveedor, BYOK). */
    suspend fun apiKeyFor(provider: VisionProviderType): String = when (provider) {
        VisionProviderType.GEMINI -> observeGeminiApiKey().first()
        VisionProviderType.ANTHROPIC -> observeAnthropicApiKey().first()
    }

    /** Resuelve el modelo configurado para [provider]. */
    suspend fun modelFor(provider: VisionProviderType): String = when (provider) {
        VisionProviderType.GEMINI -> observeGeminiModel().first()
        VisionProviderType.ANTHROPIC -> observeAnthropicModel().first()
    }

    /** Nombre interno de la voz de Android TTS elegida para el modo cocina; null = voz por defecto del sistema. */
    fun observeTtsVoiceName(): Flow<String?> =
        context.settingsDataStore.data.map { prefs -> prefs[ttsVoiceNameKey] }

    suspend fun setTtsVoiceName(name: String?) {
        context.settingsDataStore.edit { prefs ->
            if (name.isNullOrBlank()) prefs.remove(ttsVoiceNameKey) else prefs[ttsVoiceNameKey] = name
        }
    }

    /** Idioma del dictado por voz (etiqueta BCP-47, ver DictationLanguages); español de España por defecto. */
    fun observeDictationLanguage(): Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[dictationLanguageKey] ?: DictationLanguages.DEFAULT }

    suspend fun setDictationLanguage(tag: String) {
        context.settingsDataStore.edit { prefs -> prefs[dictationLanguageKey] = tag }
    }

    /** Último versionCode instalado del que ya se mostró el changelog; 0 si aún no se ha registrado ninguno. */
    fun observeLastSeenVersionCode(): Flow<Int> =
        context.settingsDataStore.data.map { prefs -> prefs[lastSeenVersionCodeKey] ?: 0 }

    suspend fun setLastSeenVersionCode(versionCode: Int) {
        context.settingsDataStore.edit { prefs -> prefs[lastSeenVersionCodeKey] = versionCode }
    }

    /** Repositorio de GitHub ("owner/repo") del catálogo de packs de recetas; editable en Ajustes. */
    fun observePacksCatalogRepo(): Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[packsCatalogRepoKey]?.takeIf { it.isNotBlank() } ?: DEFAULT_PACKS_CATALOG_REPO }

    suspend fun setPacksCatalogRepo(repo: String) {
        context.settingsDataStore.edit { prefs -> prefs[packsCatalogRepoKey] = repo.trim() }
    }

    /** Texto breve de novedades de [versionCode] embebido en `assets/changelogs/`, o null si no existe. */
    fun readChangelog(versionCode: Int): String? = try {
        context.assets.open("changelogs/$versionCode.txt").bufferedReader().use { it.readText() }
            .trim()
            .ifBlank { null }
    } catch (e: IOException) {
        null
    }

    /** Todos los versionCode con changelog embebido en el APK actual, de más reciente a más
     *  antiguo. Es el mismo historial tanto en beta como en estable: cada build (beta o release)
     *  lleva embebidos los changelogs de todas las versiones hasta esa, así que en un build beta
     *  se ve el detalle de cada beta intermedia, y en un build release se ve cada versión que ha
     *  ido saliendo. Usado por la pantalla de Ayuda para mostrar el historial completo. */
    fun listAvailableChangelogVersionCodes(): List<Int> = try {
        context.assets.list("changelogs")
            ?.mapNotNull { it.removeSuffix(".txt").toIntOrNull() }
            ?.sortedDescending()
            .orEmpty()
    } catch (e: IOException) {
        emptyList()
    }
}
