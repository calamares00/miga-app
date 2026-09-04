package com.bmo00.miga.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bmo00.miga.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val biometricLockKey = booleanPreferencesKey("biometric_lock_enabled")
    private val autoCheckUpdatesKey = booleanPreferencesKey("auto_check_updates_enabled")

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
}
