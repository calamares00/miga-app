package com.bmo00.miga.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bmo00.miga.data.model.RecipeListViewMode
import com.bmo00.miga.data.model.ThemeMode
import com.bmo00.miga.data.model.UpdateChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val biometricLockKey = booleanPreferencesKey("biometric_lock_enabled")
    private val autoCheckUpdatesKey = booleanPreferencesKey("auto_check_updates_enabled")
    private val updateChannelKey = stringPreferencesKey("update_channel")
    private val recipeListViewModeKey = stringPreferencesKey("recipe_list_view_mode")

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
}
