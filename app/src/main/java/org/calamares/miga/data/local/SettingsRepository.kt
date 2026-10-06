package org.calamares.miga.data.local

import org.calamares.miga.R
import org.calamares.miga.L10n
import android.content.Context
import org.calamares.miga.data.voice.DictationLanguages
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import org.calamares.miga.data.model.ColorTheme
import org.calamares.miga.data.model.RecipeListViewMode
import org.calamares.miga.data.model.ThemeMode
import org.calamares.miga.data.remote.DEFAULT_PACKS_CATALOG
import org.calamares.miga.data.vision.DEFAULT_ANTHROPIC_MODEL
import org.calamares.miga.data.vision.DEFAULT_GEMINI_MODEL
import org.calamares.miga.data.vision.VisionProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val colorThemeKey = stringPreferencesKey("color_theme")
    private val biometricLockKey = booleanPreferencesKey("biometric_lock_enabled")
    private val aiEnabledKey = booleanPreferencesKey("ai_enabled")
    private val aiHealthEnabledKey = booleanPreferencesKey("ai_health_enabled")
    private val aiNutritionEnabledKey = booleanPreferencesKey("ai_nutrition_enabled")
    private val onboardingDoneKey = booleanPreferencesKey("onboarding_done")
    private val seedLanguageKey = stringPreferencesKey("seed_language")
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
    private val openRouterApiKeyKey = stringPreferencesKey("openrouter_api_key")
    private val openRouterModelKey = stringPreferencesKey("openrouter_model")
    private val openRouterModelImagesKey = booleanPreferencesKey("openrouter_model_images")
    private val aiProviderOrderKey = stringPreferencesKey("ai_provider_order")
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

    /**
     * Idioma ("es" o "en") del catálogo inicial (utensilios, categorías, ingredientes). Se fija en la
     * primera ejecución según el idioma de la app y no cambia después, para que cambiar de idioma no
     * duplique los datos ya creados.
     */
    suspend fun seedLanguage(current: String): String {
        val stored = context.settingsDataStore.data.first()[seedLanguageKey]
        if (stored != null) return stored
        val chosen = if (current == "es") "es" else "en"
        context.settingsDataStore.edit { prefs -> prefs[seedLanguageKey] = chosen }
        return chosen
    }

    /** true cuando ya se ha visto (o saltado) la bienvenida de la primera ejecución. */
    fun observeOnboardingDone(): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[onboardingDoneKey] ?: false }

    suspend fun setOnboardingDone() {
        context.settingsDataStore.edit { prefs -> prefs[onboardingDoneKey] = true }
    }

    /** Interruptor global de IA: si está apagado, la app no muestra ni usa ninguna función de IA. */
    fun observeAiEnabled(): Flow<Boolean> = context.settingsDataStore.data.map { prefs -> prefs[aiEnabledKey] ?: true }

    suspend fun setAiEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[aiEnabledKey] = enabled }
    }

    /** Valoración automática de salud al abrir una receta (solo con la IA global activada). */
    fun observeAiHealthEnabled(): Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        (prefs[aiEnabledKey] ?: true) && (prefs[aiHealthEnabledKey] ?: true)
    }

    suspend fun setAiHealthEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[aiHealthEnabledKey] = enabled }
    }

    /** Estimación nutricional automática al abrir una receta (solo con la IA global activada). */
    fun observeAiNutritionEnabled(): Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        (prefs[aiEnabledKey] ?: true) && (prefs[aiNutritionEnabledKey] ?: true)
    }

    suspend fun setAiNutritionEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[aiNutritionEnabledKey] = enabled }
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

    /**
     * Orden de prioridad de los proveedores de IA (el primero se prueba antes). Siempre contiene
     * todos los proveedores; los que no estén guardados (instalaciones antiguas o proveedores
     * nuevos) se añaden al final. Si no hay orden guardado, se parte del proveedor elegido en
     * versiones anteriores ("vision_provider").
     */
    fun observeProviderOrder(): Flow<List<VisionProviderType>> =
        context.settingsDataStore.data.map { prefs ->
            val stored = prefs[aiProviderOrderKey]?.split(',')
                ?.mapNotNull { name -> runCatching { VisionProviderType.valueOf(name.trim()) }.getOrNull() }
                ?: listOfNotNull(prefs[visionProviderKey]?.let { runCatching { VisionProviderType.valueOf(it) }.getOrNull() })
            (stored + VisionProviderType.entries).distinct()
        }

    suspend fun setProviderOrder(order: List<VisionProviderType>) {
        context.settingsDataStore.edit { prefs -> prefs[aiProviderOrderKey] = (order + VisionProviderType.entries).distinct().joinToString(",") { it.name } }
    }

    /** Proveedor de mayor prioridad. */
    fun observeVisionProvider(): Flow<VisionProviderType> = observeProviderOrder().map { it.first() }

    /** Sube [provider] al primer puesto de la prioridad (p. ej. al elegir otro modelo tras un error). */
    suspend fun setVisionProvider(provider: VisionProviderType) {
        setProviderOrder(listOf(provider) + observeProviderOrder().first())
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

    /** API key de OpenRouter introducida por el propio usuario (BYOK); vacía si no se ha configurado. */
    fun observeOpenRouterApiKey(): Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[openRouterApiKeyKey].orEmpty() }

    suspend fun setOpenRouterApiKey(apiKey: String) {
        context.settingsDataStore.edit { prefs -> prefs[openRouterApiKeyKey] = apiKey.trim() }
    }

    /** Id del modelo de OpenRouter (p. ej. "vendor/modelo:free"); vacío hasta que el usuario elige uno. */
    fun observeOpenRouterModel(): Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[openRouterModelKey].orEmpty() }

    /** Si el modelo de OpenRouter elegido acepta imágenes (se guarda al elegirlo del catálogo). */
    fun observeOpenRouterModelImages(): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[openRouterModelImagesKey] ?: true }

    suspend fun setOpenRouterModel(model: String, supportsImages: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[openRouterModelKey] = model.trim()
            prefs[openRouterModelImagesKey] = supportsImages
        }
    }

    /** Resuelve la API key configurada para [provider] (una por proveedor, BYOK). */
    suspend fun apiKeyFor(provider: VisionProviderType): String = when (provider) {
        VisionProviderType.GEMINI -> observeGeminiApiKey().first()
        VisionProviderType.ANTHROPIC -> observeAnthropicApiKey().first()
        VisionProviderType.OPENROUTER -> observeOpenRouterApiKey().first()
    }

    /** Resuelve el modelo configurado para [provider]. */
    suspend fun modelFor(provider: VisionProviderType): String = when (provider) {
        VisionProviderType.GEMINI -> observeGeminiModel().first()
        VisionProviderType.ANTHROPIC -> observeAnthropicModel().first()
        VisionProviderType.OPENROUTER -> observeOpenRouterModel().first()
    }

    /** Si el modelo configurado para [provider] puede leer imágenes (todos los de Gemini y Claude pueden). */
    suspend fun supportsImages(provider: VisionProviderType): Boolean = when (provider) {
        VisionProviderType.GEMINI, VisionProviderType.ANTHROPIC -> true
        VisionProviderType.OPENROUTER -> observeOpenRouterModelImages().first()
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
        context.settingsDataStore.data.map { prefs -> prefs[packsCatalogRepoKey]?.takeIf { it.isNotBlank() } ?: DEFAULT_PACKS_CATALOG }

    suspend fun setPacksCatalogRepo(repo: String) {
        context.settingsDataStore.edit { prefs -> prefs[packsCatalogRepoKey] = repo.trim() }
    }

    /** Texto breve de novedades de [versionCode] embebido en `assets/changelogs/`, o null si no existe. */
    /** Nota de la versión en el idioma de la app (assets/changelogs-en para inglés), o en español si no hay traducción. */
    fun readChangelog(versionCode: Int): String? =
        readAsset("${L10n.str(R.string.changelog_assets_dir)}/$versionCode.txt") ?: readAsset("changelogs/$versionCode.txt")

    private fun readAsset(path: String): String? = try {
        context.assets.open(path).bufferedReader().use { it.readText() }
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
