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
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import org.calamares.miga.data.model.ColorTheme
import org.calamares.miga.data.model.PhotoFrame
import org.calamares.miga.data.model.RecipeListViewMode
import org.calamares.miga.data.model.ThemeMode
import org.calamares.miga.data.remote.DEFAULT_PACKS_CATALOG
import org.calamares.miga.data.ai.DEFAULT_ANTHROPIC_MODEL
import org.calamares.miga.data.ai.DEFAULT_GEMINI_MODEL
import org.calamares.miga.data.ai.DEFAULT_OPENAI_MODEL
import org.calamares.miga.data.ai.openAiModelReadsImages
import org.calamares.miga.data.ai.AiProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

private const val ENCRYPTED_KEY_PREFIX = "enc1:"

/** Decrypting through the Keystore on every settings change would be wasteful: cipher text -> key. */
private val decryptedKeys = java.util.concurrent.ConcurrentHashMap<String, String>()

class SettingsRepository(private val context: Context) {

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val colorThemeKey = stringPreferencesKey("color_theme")
    private val photoFrameKey = stringPreferencesKey("photo_frame")
    private val biometricLockKey = booleanPreferencesKey("biometric_lock_enabled")
    private val aiEnabledKey = booleanPreferencesKey("ai_enabled")
    private val aiHealthEnabledKey = booleanPreferencesKey("ai_health_enabled")
    private val aiNutritionEnabledKey = booleanPreferencesKey("ai_nutrition_enabled")
    private val onboardingDoneKey = booleanPreferencesKey("onboarding_done")
    private val welcomeOnNextStartKey = booleanPreferencesKey("welcome_on_next_start")
    private val seedLanguageKey = stringPreferencesKey("seed_language")
    /** Default kitchen equipment and categories created (and old equipment normalised) once. */
    private val defaultsSetUpKey = booleanPreferencesKey("defaults_set_up_v2")
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
    private val openAiApiKeyKey = stringPreferencesKey("openai_api_key")
    private val openAiModelKey = stringPreferencesKey("openai_model")
    private val aiProviderOrderKey = stringPreferencesKey("ai_provider_order")
    private val ttsVoiceNameKey = stringPreferencesKey("tts_voice_name")
    private val dictationLanguageKey = stringPreferencesKey("dictation_language")
    private val lastSeenVersionCodeKey = intPreferencesKey("last_seen_version_code")
    private val packsCatalogRepoKey = stringPreferencesKey("packs_catalog_repo")

    /**
     * AI API keys are stored encrypted with the device Keystore key of [TokenCipher], as
     * "enc1:" + cipher text. The settings are included in Android's backup and device transfer
     * (see backup_rules.xml), and a key that cannot be decrypted there (another phone) reads as
     * empty, so the user is simply asked for it again. A value without the prefix was stored before
     * encryption existed and is used as is until [encryptStoredApiKeys] rewrites it.
     */
    private fun decodeApiKey(stored: String?): String = when {
        stored.isNullOrBlank() -> ""
        stored.startsWith(ENCRYPTED_KEY_PREFIX) -> decryptedKeys.getOrPut(stored) {
            TokenCipher.decrypt(stored.removePrefix(ENCRYPTED_KEY_PREFIX)).orEmpty()
        }
        else -> stored
    }

    /** Encrypted form of [apiKey]; kept as typed if the Keystore fails, so the key is never lost. */
    private fun encodeApiKey(apiKey: String): String {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) return ""
        return runCatching { ENCRYPTED_KEY_PREFIX + TokenCipher.encrypt(trimmed) }.getOrDefault(trimmed)
    }

    /** Encrypts the API keys stored in plain text by earlier versions. Run once at startup. */
    suspend fun encryptStoredApiKeys() {
        context.settingsDataStore.edit { prefs ->
            listOf(geminiApiKeyKey, anthropicApiKeyKey, openRouterApiKeyKey, openAiApiKeyKey).forEach { key ->
                val stored = prefs[key]
                if (!stored.isNullOrBlank() && !stored.startsWith(ENCRYPTED_KEY_PREFIX)) prefs[key] = encodeApiKey(stored)
            }
        }
    }

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
                runCatching { ColorTheme.valueOf(stored) }.getOrDefault(ColorTheme.DEFAULT)
            } ?: ColorTheme.DEFAULT
        }

    suspend fun setColorTheme(colorTheme: ColorTheme) {
        context.settingsDataStore.edit { prefs -> prefs[colorThemeKey] = colorTheme.name }
    }

    fun observePhotoFrame(): Flow<PhotoFrame> =
        context.settingsDataStore.data.map { prefs ->
            prefs[photoFrameKey]?.let { stored ->
                runCatching { PhotoFrame.valueOf(stored) }.getOrDefault(PhotoFrame.DEFAULT)
            } ?: PhotoFrame.DEFAULT
        }

    suspend fun setPhotoFrame(frame: PhotoFrame) {
        context.settingsDataStore.edit { prefs -> prefs[photoFrameKey] = frame.name }
    }

    /** Shopping list currently open; "main" is the default list. */
    fun observeShoppingListUid(): Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[shoppingListUidKey] ?: "main" }

    suspend fun setShoppingListUid(uid: String) {
        context.settingsDataStore.edit { prefs -> prefs[shoppingListUidKey] = uid }
    }

    /** Name used to sign changes in a shared list (empty means unsigned). */
    fun observeShoppingAuthor(): Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[shoppingAuthorKey].orEmpty() }

    suspend fun setShoppingAuthor(name: String) {
        context.settingsDataStore.edit { prefs -> prefs[shoppingAuthorKey] = name.trim().take(60) }
    }

    /** Supermarket used to sort the shopping list by its aisles; 0 means none. */
    fun observeShoppingStoreId(): Flow<Long> =
        context.settingsDataStore.data.map { prefs -> prefs[shoppingStoreKey] ?: 0L }

    suspend fun setShoppingStoreId(id: Long) {
        context.settingsDataStore.edit { prefs -> prefs[shoppingStoreKey] = id }
    }

    /** Off by default: showing product photos downloads images from Open Food Facts. */
    fun observeShoppingImagesEnabled(): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[shoppingImagesKey] ?: false }

    suspend fun setShoppingImagesEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[shoppingImagesKey] = enabled }
    }

    /**
     * Language ("es" or "en") of the initial catalogue (utensils, categories, ingredients). Fixed
     * on the first run from the app language and never changed afterwards, so switching languages
     * does not duplicate data.
     */
    suspend fun seedLanguage(current: String): String {
        val stored = context.settingsDataStore.data.first()[seedLanguageKey]
        if (stored != null) return stored
        val chosen = if (current == "es") "es" else "en"
        context.settingsDataStore.edit { prefs -> prefs[seedLanguageKey] = chosen }
        return chosen
    }

    suspend fun areDefaultsSetUp(): Boolean = context.settingsDataStore.data.first()[defaultsSetUpKey] ?: false

    suspend fun setDefaultsSetUp() {
        context.settingsDataStore.edit { prefs -> prefs[defaultsSetUpKey] = true }
    }

    /** True once the first-run welcome has been seen or skipped. */
    fun observeOnboardingDone(): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[onboardingDoneKey] ?: false }

    suspend fun setOnboardingDone() {
        context.settingsDataStore.edit { prefs -> prefs[onboardingDoneKey] = true }
    }

    /** Whether the welcome tour is shown again the next time the app starts (Settings > Help). */
    fun observeWelcomeOnNextStart(): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[welcomeOnNextStartKey] ?: false }

    suspend fun setWelcomeOnNextStart(show: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[welcomeOnNextStartKey] = show }
    }

    /**
     * Called once at startup, before the first screen is chosen: a pending "show the welcome on
     * the next start" turns the welcome back on, only for this start.
     */
    suspend fun applyWelcomeRequest() {
        context.settingsDataStore.edit { prefs ->
            if (prefs[welcomeOnNextStartKey] == true) {
                prefs[onboardingDoneKey] = false
                prefs[welcomeOnNextStartKey] = false
            }
        }
    }

    /** Global AI switch: when off, no AI feature is shown or used. */
    fun observeAiEnabled(): Flow<Boolean> = context.settingsDataStore.data.map { prefs -> prefs[aiEnabledKey] ?: true }

    suspend fun setAiEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[aiEnabledKey] = enabled }
    }

    /** Automatic health rating when opening a recipe (only with AI enabled). */
    fun observeAiHealthEnabled(): Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        (prefs[aiEnabledKey] ?: true) && (prefs[aiHealthEnabledKey] ?: true)
    }

    suspend fun setAiHealthEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[aiHealthEnabledKey] = enabled }
    }

    /** Automatic nutrition estimate when opening a recipe (only with AI enabled). */
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

    /** Recipe list layout, stored globally (not per book) like the theme. */
    fun observeRecipeListViewMode(): Flow<RecipeListViewMode> =
        context.settingsDataStore.data.map { prefs ->
            prefs[recipeListViewModeKey]?.let { stored ->
                runCatching { RecipeListViewMode.valueOf(stored) }.getOrDefault(RecipeListViewMode.NORMAL)
            } ?: RecipeListViewMode.NORMAL
        }

    suspend fun setRecipeListViewMode(mode: RecipeListViewMode) {
        context.settingsDataStore.edit { prefs -> prefs[recipeListViewModeKey] = mode.name }
    }

    private fun collapsedCategoriesKey(bookId: Long) = stringSetPreferencesKey("collapsed_categories_$bookId")

    /** Categories the user has collapsed in the recipe list of book [bookId]. */
    fun observeCollapsedCategories(bookId: Long): Flow<Set<String>> =
        context.settingsDataStore.data.map { prefs -> prefs[collapsedCategoriesKey(bookId)].orEmpty() }

    suspend fun setCollapsedCategories(bookId: Long, categories: Set<String>) {
        context.settingsDataStore.edit { prefs ->
            if (categories.isEmpty()) prefs.remove(collapsedCategoriesKey(bookId)) else prefs[collapsedCategoriesKey(bookId)] = categories
        }
    }

    /** Book list layout, stored separately from the recipe list one. */
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
     * Priority order of the AI providers (the first one is tried first). It always contains every
     * provider: missing ones (older installs, new providers) are appended. Without a stored order,
     * the provider chosen in older versions ("vision_provider") goes first.
     */
    fun observeProviderOrder(): Flow<List<AiProvider>> =
        context.settingsDataStore.data.map { prefs ->
            val stored = prefs[aiProviderOrderKey]?.split(',')
                ?.mapNotNull { name -> runCatching { AiProvider.valueOf(name.trim()) }.getOrNull() }
                ?: listOfNotNull(prefs[visionProviderKey]?.let { runCatching { AiProvider.valueOf(it) }.getOrNull() })
            (stored + AiProvider.entries).distinct()
        }

    suspend fun setProviderOrder(order: List<AiProvider>) {
        context.settingsDataStore.edit { prefs -> prefs[aiProviderOrderKey] = (order + AiProvider.entries).distinct().joinToString(",") { it.name } }
    }

    /** Gemini API key entered by the user; empty when not configured. */
    fun observeGeminiApiKey(): Flow<String> =
        context.settingsDataStore.data.map { prefs -> decodeApiKey(prefs[geminiApiKeyKey]) }

    suspend fun setGeminiApiKey(apiKey: String) {
        context.settingsDataStore.edit { prefs -> prefs[geminiApiKeyKey] = encodeApiKey(apiKey) }
    }

    /** Gemini model id, from GEMINI_MODELS or typed by hand. */
    fun observeGeminiModel(): Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[geminiModelKey] ?: DEFAULT_GEMINI_MODEL }

    suspend fun setGeminiModel(model: String) {
        context.settingsDataStore.edit { prefs -> prefs[geminiModelKey] = model.trim() }
    }

    /** Anthropic (Claude) API key entered by the user; empty when not configured. */
    fun observeAnthropicApiKey(): Flow<String> =
        context.settingsDataStore.data.map { prefs -> decodeApiKey(prefs[anthropicApiKeyKey]) }

    suspend fun setAnthropicApiKey(apiKey: String) {
        context.settingsDataStore.edit { prefs -> prefs[anthropicApiKeyKey] = encodeApiKey(apiKey) }
    }

    /** Claude model id, from ANTHROPIC_MODELS or typed by hand. */
    fun observeAnthropicModel(): Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[anthropicModelKey] ?: DEFAULT_ANTHROPIC_MODEL }

    suspend fun setAnthropicModel(model: String) {
        context.settingsDataStore.edit { prefs -> prefs[anthropicModelKey] = model.trim() }
    }

    /** OpenRouter API key entered by the user; empty when not configured. */
    fun observeOpenRouterApiKey(): Flow<String> =
        context.settingsDataStore.data.map { prefs -> decodeApiKey(prefs[openRouterApiKeyKey]) }

    suspend fun setOpenRouterApiKey(apiKey: String) {
        context.settingsDataStore.edit { prefs -> prefs[openRouterApiKeyKey] = encodeApiKey(apiKey) }
    }

    /** OpenRouter model id (for example "vendor/model:free"); empty until the user picks one. */
    fun observeOpenRouterModel(): Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[openRouterModelKey].orEmpty() }

    /**
     * Whether the chosen OpenRouter model accepts images (stored when picked from the catalogue).
     */
    fun observeOpenRouterModelImages(): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[openRouterModelImagesKey] ?: true }

    suspend fun setOpenRouterModel(model: String, supportsImages: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[openRouterModelKey] = model.trim()
            prefs[openRouterModelImagesKey] = supportsImages
        }
    }

    /** OpenAI API key entered by the user; empty when not configured. */
    fun observeOpenAiApiKey(): Flow<String> =
        context.settingsDataStore.data.map { prefs -> decodeApiKey(prefs[openAiApiKeyKey]) }

    suspend fun setOpenAiApiKey(apiKey: String) {
        context.settingsDataStore.edit { prefs -> prefs[openAiApiKeyKey] = encodeApiKey(apiKey) }
    }

    /** OpenAI model id, from the account's list (see ProviderModels) or typed by hand. */
    fun observeOpenAiModel(): Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[openAiModelKey] ?: DEFAULT_OPENAI_MODEL }

    suspend fun setOpenAiModel(model: String) {
        context.settingsDataStore.edit { prefs -> prefs[openAiModelKey] = model.trim() }
    }

    /** API key configured for [provider]. */
    suspend fun apiKeyFor(provider: AiProvider): String = when (provider) {
        AiProvider.GEMINI -> observeGeminiApiKey().first()
        AiProvider.ANTHROPIC -> observeAnthropicApiKey().first()
        AiProvider.OPENROUTER -> observeOpenRouterApiKey().first()
        AiProvider.OPENAI -> observeOpenAiApiKey().first()
    }

    /** Model configured for [provider]. */
    suspend fun modelFor(provider: AiProvider): String = when (provider) {
        AiProvider.GEMINI -> observeGeminiModel().first()
        AiProvider.ANTHROPIC -> observeAnthropicModel().first()
        AiProvider.OPENROUTER -> observeOpenRouterModel().first()
        AiProvider.OPENAI -> observeOpenAiModel().first()
    }

    /**
     * Providers ready to use (with a key and a model), in priority order. Used to tell whether AI
     * can be offered at all and to summarise the configuration.
     */
    fun observeConfiguredProviders(): Flow<List<AiProvider>> =
        combine(observeProviderOrder(), context.settingsDataStore.data) { order, prefs ->
            order.filter { provider ->
                val (key, model) = when (provider) {
                    AiProvider.GEMINI -> decodeApiKey(prefs[geminiApiKeyKey]) to (prefs[geminiModelKey] ?: DEFAULT_GEMINI_MODEL)
                    AiProvider.ANTHROPIC -> decodeApiKey(prefs[anthropicApiKeyKey]) to (prefs[anthropicModelKey] ?: DEFAULT_ANTHROPIC_MODEL)
                    AiProvider.OPENROUTER -> decodeApiKey(prefs[openRouterApiKeyKey]) to prefs[openRouterModelKey]
                    AiProvider.OPENAI -> decodeApiKey(prefs[openAiApiKeyKey]) to (prefs[openAiModelKey] ?: DEFAULT_OPENAI_MODEL)
                }
                !key.isNullOrBlank() && !model.isNullOrBlank()
            }
        }

    /**
     * Whether the model configured for [provider] can read images; every Gemini and Claude model
     * can.
     */
    suspend fun supportsImages(provider: AiProvider): Boolean = when (provider) {
        AiProvider.GEMINI, AiProvider.ANTHROPIC -> true
        AiProvider.OPENROUTER -> observeOpenRouterModelImages().first()
        AiProvider.OPENAI -> openAiModelReadsImages(observeOpenAiModel().first())
    }

    /** Android text-to-speech voice for cooking mode; null means the system default voice. */
    fun observeTtsVoiceName(): Flow<String?> =
        context.settingsDataStore.data.map { prefs -> prefs[ttsVoiceNameKey] }

    suspend fun setTtsVoiceName(name: String?) {
        context.settingsDataStore.edit { prefs ->
            if (name.isNullOrBlank()) prefs.remove(ttsVoiceNameKey) else prefs[ttsVoiceNameKey] = name
        }
    }

    /** Voice dictation language as a BCP-47 tag (see DictationLanguages). */
    fun observeDictationLanguage(): Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[dictationLanguageKey] ?: DictationLanguages.DEFAULT }

    suspend fun setDictationLanguage(tag: String) {
        context.settingsDataStore.edit { prefs -> prefs[dictationLanguageKey] = tag }
    }

    /** Last version code whose release notes were already shown; 0 when none. */
    fun observeLastSeenVersionCode(): Flow<Int> =
        context.settingsDataStore.data.map { prefs -> prefs[lastSeenVersionCodeKey] ?: 0 }

    suspend fun setLastSeenVersionCode(versionCode: Int) {
        context.settingsDataStore.edit { prefs -> prefs[lastSeenVersionCodeKey] = versionCode }
    }

    /** GitHub repository ("owner/repo") of the recipe pack catalogue; editable in Settings. */
    fun observePacksCatalogRepo(): Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[packsCatalogRepoKey]?.takeIf { it.isNotBlank() } ?: DEFAULT_PACKS_CATALOG }

    suspend fun setPacksCatalogRepo(repo: String) {
        context.settingsDataStore.edit { prefs -> prefs[packsCatalogRepoKey] = repo.trim() }
    }

    /**
     * Release notes of [versionCode] in the app language (assets/changelogs-en for English),
     * falling back to Spanish when there is no translation.
     */
    fun readChangelog(versionCode: Int): String? =
        readAsset("${L10n.str(R.string.changelog_assets_dir)}/$versionCode.txt") ?: readAsset("changelogs/$versionCode.txt")

    private fun readAsset(path: String): String? = try {
        context.assets.open(path).bufferedReader().use { it.readText() }
            .trim()
            .ifBlank { null }
    } catch (e: IOException) {
        null
    }

    /**
     * Every version code with release notes bundled in this APK, newest first. Used by the help
     * screen to show the full history.
     */
    fun listAvailableChangelogVersionCodes(): List<Int> = try {
        context.assets.list("changelogs")
            ?.mapNotNull { it.removeSuffix(".txt").toIntOrNull() }
            ?.sortedDescending()
            .orEmpty()
    } catch (e: IOException) {
        emptyList()
    }
}
