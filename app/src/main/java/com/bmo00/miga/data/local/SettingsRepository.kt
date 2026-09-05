package com.bmo00.miga.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import com.bmo00.miga.data.model.RecipeListViewMode
import com.bmo00.miga.data.model.ThemeMode
import com.bmo00.miga.data.model.UpdateChannel
import com.bmo00.miga.data.remote.DEFAULT_PACKS_CATALOG_REPO
import com.bmo00.miga.data.vision.DEFAULT_GEMINI_MODEL
import com.bmo00.miga.data.vision.VisionProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val biometricLockKey = booleanPreferencesKey("biometric_lock_enabled")
    private val autoCheckUpdatesKey = booleanPreferencesKey("auto_check_updates_enabled")
    private val updateChannelKey = stringPreferencesKey("update_channel")
    private val recipeListViewModeKey = stringPreferencesKey("recipe_list_view_mode")
    private val recipeBookListViewModeKey = stringPreferencesKey("recipe_book_list_view_mode")
    private val visionProviderKey = stringPreferencesKey("vision_provider")
    private val geminiApiKeyKey = stringPreferencesKey("gemini_api_key")
    private val geminiModelKey = stringPreferencesKey("gemini_model")
    private val ttsVoiceNameKey = stringPreferencesKey("tts_voice_name")
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

    fun observeBiometricLockEnabled(): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[biometricLockKey] ?: false }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[biometricLockKey] = enabled }
    }

    /** Por defecto activado: comprobar si hay una versión nueva cada vez que se abre la app. */
    fun observeAutoCheckUpdatesEnabled(): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[autoCheckUpdatesKey] ?: true }

    suspend fun setAutoCheckUpdatesEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[autoCheckUpdatesKey] = enabled }
    }

    fun observeUpdateChannel(): Flow<UpdateChannel> =
        context.settingsDataStore.data.map { prefs ->
            prefs[updateChannelKey]?.let { stored ->
                runCatching { UpdateChannel.valueOf(stored) }.getOrDefault(UpdateChannel.STABLE)
            } ?: UpdateChannel.STABLE
        }

    suspend fun setUpdateChannel(channel: UpdateChannel) {
        context.settingsDataStore.edit { prefs -> prefs[updateChannelKey] = channel.name }
    }

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

    /** Nombre interno de la voz de Android TTS elegida para el modo cocina; null = voz por defecto del sistema. */
    fun observeTtsVoiceName(): Flow<String?> =
        context.settingsDataStore.data.map { prefs -> prefs[ttsVoiceNameKey] }

    suspend fun setTtsVoiceName(name: String?) {
        context.settingsDataStore.edit { prefs ->
            if (name.isNullOrBlank()) prefs.remove(ttsVoiceNameKey) else prefs[ttsVoiceNameKey] = name
        }
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
}
