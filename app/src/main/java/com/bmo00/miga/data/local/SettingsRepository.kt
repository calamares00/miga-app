package com.bmo00.miga.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bmo00.miga.data.model.RecipeListViewMode
import com.bmo00.miga.data.model.ThemeMode
import com.bmo00.miga.data.model.UpdateChannel
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
    private val visionProviderKey = stringPreferencesKey("vision_provider")
    private val geminiApiKeyKey = stringPreferencesKey("gemini_api_key")
    private val geminiModelKey = stringPreferencesKey("gemini_model")

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
}
