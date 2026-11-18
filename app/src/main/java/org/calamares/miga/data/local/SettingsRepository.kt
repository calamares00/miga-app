package org.calamares.miga.data.local

import org.calamares.miga.data.model.SavedFilter
import org.calamares.miga.R
import org.calamares.miga.L10n
import android.content.Context
import org.calamares.miga.data.voice.DictationLanguages
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import org.calamares.miga.data.export.SettingsBackupDto
import org.calamares.miga.data.model.ColorTheme
import org.calamares.miga.data.model.PhotoFrame
import org.calamares.miga.data.model.RecipeListViewMode
import org.calamares.miga.data.model.ThemeMode
import org.calamares.miga.data.remote.DEFAULT_PACKS_CATALOG
import org.calamares.miga.data.ai.defaultModelFor
import org.calamares.miga.data.ai.guessModelReadsImages
import org.calamares.miga.data.ai.AiProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

private const val ENCRYPTED_KEY_PREFIX = "enc1:"

private const val MAX_RECENT_SEARCHES = 8

/** Grams of carbohydrate in one portion by default: the Spanish "ración de hidratos". */
const val DEFAULT_CARB_PORTION_GRAMS = 10
const val MAX_CARB_PORTION_GRAMS = 30

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
    private val recipeBookListGroupedKey = booleanPreferencesKey("recipe_book_list_grouped")
    private val visionProviderKey = stringPreferencesKey("vision_provider")
    private val customBaseUrlKey = stringPreferencesKey("custom_ai_base_url")

    /**
     * Key, model and "reads images" setting of each provider. The names follow the provider
     * ("gemini_api_key", "openrouter_model"...), as earlier versions stored them.
     */
    private class ProviderKeys(provider: AiProvider) {
        private val prefix = provider.name.lowercase()
        val apiKey = stringPreferencesKey("${prefix}_api_key")
        val model = stringPreferencesKey("${prefix}_model")
        val readsImages = booleanPreferencesKey("${prefix}_model_images")
    }

    private val providerKeys = AiProvider.entries.associateWith { ProviderKeys(it) }

    private fun keys(provider: AiProvider): ProviderKeys = providerKeys.getValue(provider)
    private val aiProviderOrderKey = stringPreferencesKey("ai_provider_order")
    private val aiDisabledProvidersKey = stringPreferencesKey("ai_disabled_providers")
    private val ttsVoiceNameKey = stringPreferencesKey("tts_voice_name")
    private val dictationLanguageKey = stringPreferencesKey("dictation_language")
    private val lastSeenVersionCodeKey = intPreferencesKey("last_seen_version_code")
    private val packsCatalogRepoKey = stringPreferencesKey("packs_catalog_repo")
    private val packInstallStatsKey = booleanPreferencesKey("pack_install_stats")
    private val dismissedUpdateKey = stringPreferencesKey("dismissed_update_version")
    private val dismissedPackUpdatesKey = stringPreferencesKey("dismissed_pack_updates")
    private val savedTipsKey = stringPreferencesKey("saved_tips")
    private val recentSearchesKey = stringPreferencesKey("recent_searches")
    private val savedFiltersKey = stringPreferencesKey("saved_filters")
    private val pantryItemsKey = stringPreferencesKey("pantry_items")
    private val tipOfTheDayKey = booleanPreferencesKey("tip_of_the_day_enabled")
    private val carbCountingKey = booleanPreferencesKey("carb_counting_enabled")
    private val carbPortionGramsKey = intPreferencesKey("carb_portion_grams")
    private val tipOfTheDayClosedKey = longPreferencesKey("tip_of_the_day_closed_day")

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
            providerKeys.values.map { it.apiKey }.forEach { key ->
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
     * Language ("es" or "en") of the initial catalogue (utensils, categories, ingredients). Set on
     * the first run from the app language; when the app language changes later the catalogue is
     * translated (see RecipeRepository.relocalizeDefaults) and this is updated with
     * [setSeedLanguage], so switching languages never duplicates data.
     */
    suspend fun seedLanguage(current: String): String {
        val stored = context.settingsDataStore.data.first()[seedLanguageKey]
        if (stored != null) return stored
        val chosen = if (current == "es") "es" else "en"
        context.settingsDataStore.edit { prefs -> prefs[seedLanguageKey] = chosen }
        return chosen
    }

    suspend fun setSeedLanguage(language: String) {
        context.settingsDataStore.edit { prefs -> prefs[seedLanguageKey] = language }
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

    /**
     * The cooking assistant can actually be used: switched on and with at least one provider
     * configured. Options that need it are only offered then.
     */
    fun observeAiReady(): Flow<Boolean> =
        combine(observeAiEnabled(), observeConfiguredProviders()) { enabled, providers -> enabled && providers.isNotEmpty() }

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

    /** Carbohydrates in each recipe, for people who count them (diabetes). Off by default. */
    fun observeCarbCountingEnabled(): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[carbCountingKey] ?: false }

    suspend fun setCarbCountingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[carbCountingKey] = enabled }
    }

    /**
     * Grams of carbohydrate in one portion ("ración"; 10 in Spain, 15 for US "carb choices"). 0
     * shows grams only.
     */
    fun observeCarbPortionGrams(): Flow<Int> =
        context.settingsDataStore.data.map { prefs -> (prefs[carbPortionGramsKey] ?: DEFAULT_CARB_PORTION_GRAMS).coerceIn(0, MAX_CARB_PORTION_GRAMS) }

    suspend fun setCarbPortionGrams(grams: Int) {
        context.settingsDataStore.edit { prefs -> prefs[carbPortionGramsKey] = grams.coerceIn(0, MAX_CARB_PORTION_GRAMS) }
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

    /** Whether the books list is split into packs (by series), the user's books and synced books. */
    fun observeRecipeBookListGrouped(): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[recipeBookListGroupedKey] ?: false }

    suspend fun setRecipeBookListGrouped(grouped: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[recipeBookListGroupedKey] = grouped }
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

    /** Providers switched off in Settings: kept with their key and model, but never asked. */
    fun observeDisabledProviders(): Flow<Set<AiProvider>> =
        context.settingsDataStore.data.map { prefs -> parseProviders(prefs[aiDisabledProvidersKey]) }

    suspend fun setProviderEnabled(provider: AiProvider, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val disabled = parseProviders(prefs[aiDisabledProvidersKey])
            prefs[aiDisabledProvidersKey] = (if (enabled) disabled - provider else disabled + provider).joinToString(",") { it.name }
        }
    }

    private fun parseProviders(stored: String?): Set<AiProvider> =
        stored?.split(',')?.mapNotNull { name -> runCatching { AiProvider.valueOf(name.trim()) }.getOrNull() }?.toSet().orEmpty()

    suspend fun setProviderOrder(order: List<AiProvider>) {
        context.settingsDataStore.edit { prefs -> prefs[aiProviderOrderKey] = (order + AiProvider.entries).distinct().joinToString(",") { it.name } }
    }

    /** API key of [provider] (optional for a custom server); empty when not set. */
    fun observeApiKey(provider: AiProvider): Flow<String> =
        context.settingsDataStore.data.map { prefs -> decodeApiKey(prefs[keys(provider).apiKey]) }

    suspend fun setApiKey(provider: AiProvider, apiKey: String) {
        context.settingsDataStore.edit { prefs -> prefs[keys(provider).apiKey] = encodeApiKey(apiKey) }
    }

    /** Model of [provider]; its default until one is chosen (none for OpenRouter or a custom server). */
    fun observeModel(provider: AiProvider): Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[keys(provider).model] ?: defaultModelFor(provider) }

    /** Chooses [model] and whether it reads images (null keeps a guess from its name). */
    suspend fun setModel(provider: AiProvider, model: String, readsImages: Boolean? = null) {
        val trimmed = model.trim()
        context.settingsDataStore.edit { prefs ->
            prefs[keys(provider).model] = trimmed
            prefs[keys(provider).readsImages] = readsImages ?: guessModelReadsImages(provider, trimmed)
        }
    }

    /** Whether the model of [provider] reads images: chosen in Settings, or guessed from its name. */
    fun observeModelImages(provider: AiProvider): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> modelImages(prefs, provider) }

    private fun modelImages(prefs: Preferences, provider: AiProvider): Boolean =
        prefs[keys(provider).readsImages] ?: guessModelReadsImages(provider, prefs[keys(provider).model] ?: defaultModelFor(provider))

    suspend fun setModelImages(provider: AiProvider, readsImages: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[keys(provider).readsImages] = readsImages }
    }

    /** Address of the custom OpenAI-compatible server; empty when not set. */
    fun observeCustomBaseUrl(): Flow<String> =
        context.settingsDataStore.data.map { prefs -> prefs[customBaseUrlKey].orEmpty() }

    suspend fun setCustomBaseUrl(url: String) {
        context.settingsDataStore.edit { prefs -> prefs[customBaseUrlKey] = url.trim() }
    }

    /** API key configured for [provider]. */
    suspend fun apiKeyFor(provider: AiProvider): String = observeApiKey(provider).first()

    /** Model configured for [provider]. */
    suspend fun modelFor(provider: AiProvider): String = observeModel(provider).first()

    /**
     * Providers ready to use (with a key and a model), in priority order. Used to tell whether AI
     * can be offered at all and to summarise the configuration.
     */
    fun observeConfiguredProviders(): Flow<List<AiProvider>> =
        combine(observeProviderOrder(), context.settingsDataStore.data) { order, prefs ->
            val disabled = parseProviders(prefs[aiDisabledProvidersKey])
            order.filter { provider -> provider !in disabled }.filter { provider ->
                val model = prefs[keys(provider).model] ?: defaultModelFor(provider)
                // A home server needs no key: its address takes the key's place.
                val key = if (provider == AiProvider.CUSTOM) prefs[customBaseUrlKey] else decodeApiKey(prefs[keys(provider).apiKey])
                !key.isNullOrBlank() && !model.isNullOrBlank()
            }
        }

    /** Whether the model configured for [provider] can read images. */
    suspend fun supportsImages(provider: AiProvider): Boolean = observeModelImages(provider).first()

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

    /** Whether installing a pack from the official catalogue adds one to its anonymous counter (on by default). */
    fun observePackInstallStats(): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[packInstallStatsKey] ?: true }

    suspend fun setPackInstallStats(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[packInstallStatsKey] = enabled }
    }

    /** Version the user answered "Not now" to; it is not announced again. */
    /** Pack updates ("id:version") the user answered "Not now" to; they are not announced again. */
    suspend fun dismissedPackUpdates(): Set<String> =
        context.settingsDataStore.data.first()[dismissedPackUpdatesKey]?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty()

    suspend fun setDismissedPackUpdates(updates: Set<String>) {
        context.settingsDataStore.edit { prefs -> prefs[dismissedPackUpdatesKey] = updates.joinToString(",") }
    }

    /** Ids of the cooking tips the user has saved (starred). */
    fun observeSavedTips(): Flow<Set<String>> =
        context.settingsDataStore.data.map { prefs -> prefs[savedTipsKey]?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty() }

    suspend fun setTipSaved(id: String, saved: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[savedTipsKey]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
            val updated = if (saved) (current + id).distinct() else current - id
            if (updated.isEmpty()) prefs.remove(savedTipsKey) else prefs[savedTipsKey] = updated.joinToString(",")
        }
    }

    /** Latest searches, newest first (one per line). */
    fun observeRecentSearches(): Flow<List<String>> =
        context.settingsDataStore.data.map { prefs -> prefs[recentSearchesKey]?.lines()?.filter { it.isNotBlank() }.orEmpty() }

    suspend fun addRecentSearch(query: String) {
        val trimmed = query.trim().replace('\n', ' ')
        if (trimmed.length < 2) return
        context.settingsDataStore.edit { prefs ->
            val current = prefs[recentSearchesKey]?.lines()?.filter { it.isNotBlank() }.orEmpty()
            val updated = (listOf(trimmed) + current.filterNot { it.equals(trimmed, ignoreCase = true) }).take(MAX_RECENT_SEARCHES)
            prefs[recentSearchesKey] = updated.joinToString("\n")
        }
    }

    suspend fun clearRecentSearches() {
        context.settingsDataStore.edit { prefs -> prefs.remove(recentSearchesKey) }
    }

    /** Searches saved by name, in the order they were saved. */
    fun observeSavedFilters(): Flow<List<SavedFilter>> =
        context.settingsDataStore.data.map { prefs -> SavedFilter.decode(prefs[savedFiltersKey]) }

    /** Saves [filter] under its name, replacing one with the same name. */
    suspend fun saveFilter(filter: SavedFilter) {
        context.settingsDataStore.edit { prefs ->
            val current = SavedFilter.decode(prefs[savedFiltersKey]).filterNot { it.name.equals(filter.name, ignoreCase = true) }
            prefs[savedFiltersKey] = SavedFilter.encode(current + filter)
        }
    }

    suspend fun deleteSavedFilter(name: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[savedFiltersKey] = SavedFilter.encode(SavedFilter.decode(prefs[savedFiltersKey]).filterNot { it.name == name })
        }
    }

    /** What the user said they have at home, for "What can I cook?" (one per line). */
    fun observePantryItems(): Flow<List<String>> =
        context.settingsDataStore.data.map { prefs -> prefs[pantryItemsKey]?.lines()?.filter { it.isNotBlank() }.orEmpty() }

    suspend fun setPantryItems(items: List<String>) {
        context.settingsDataStore.edit { prefs ->
            val cleaned = items.map { it.trim().replace('\n', ' ') }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
            if (cleaned.isEmpty()) prefs.remove(pantryItemsKey) else prefs[pantryItemsKey] = cleaned.joinToString("\n")
        }
    }

    /** Whether the books screen shows the tip of the day (on unless turned off). */
    fun observeTipOfTheDayEnabled(): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[tipOfTheDayKey] ?: true }

    suspend fun setTipOfTheDayEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[tipOfTheDayKey] = enabled }
    }

    /** Day (epoch day) the tip of the day was closed: it comes back the next day. */
    fun observeTipOfTheDayClosedDay(): Flow<Long?> =
        context.settingsDataStore.data.map { prefs -> prefs[tipOfTheDayClosedKey] }

    suspend fun setTipOfTheDayClosedDay(epochDay: Long) {
        context.settingsDataStore.edit { prefs -> prefs[tipOfTheDayClosedKey] = epochDay }
    }

    suspend fun dismissedUpdateVersion(): String? = context.settingsDataStore.data.first()[dismissedUpdateKey]

    suspend fun setDismissedUpdateVersion(versionName: String) {
        context.settingsDataStore.edit { prefs -> prefs[dismissedUpdateKey] = versionName }
    }

    // Settings carried by the full backup. API keys (encrypted with this phone's Keystore, useless
    // elsewhere), the biometric lock and per-device state (onboarding, seed language, selected
    // supermarket and shopping list, last seen version) are deliberately left out.
    private val backupStringKeys = listOf(
        themeModeKey, colorThemeKey, photoFrameKey, shoppingAuthorKey, recipeListViewModeKey,
        recipeBookListViewModeKey, visionProviderKey, customBaseUrlKey, aiProviderOrderKey, aiDisabledProvidersKey, ttsVoiceNameKey, dictationLanguageKey,
        packsCatalogRepoKey, savedTipsKey, recentSearchesKey, savedFiltersKey, pantryItemsKey
    ) + providerKeys.values.map { it.model }
    private val backupBooleanKeys = listOf(
        aiEnabledKey, aiHealthEnabledKey, aiNutritionEnabledKey, shoppingImagesKey, recipeBookListGroupedKey,
        packInstallStatsKey, tipOfTheDayKey
    ) + providerKeys.values.map { it.readsImages }

    /** The settings to include in a full backup (only those the user has set). */
    suspend fun exportForBackup(): SettingsBackupDto {
        val prefs = context.settingsDataStore.data.first()
        return SettingsBackupDto(
            strings = backupStringKeys.mapNotNull { key -> prefs[key]?.let { key.name to it } }.toMap(),
            booleans = backupBooleanKeys.mapNotNull { key -> prefs[key]?.let { key.name to it } }.toMap()
        )
    }

    /** Restores the settings of a full backup; unknown or excluded names are ignored. */
    suspend fun restoreFromBackup(dto: SettingsBackupDto) {
        context.settingsDataStore.edit { prefs ->
            backupStringKeys.forEach { key -> dto.strings[key.name]?.let { prefs[key] = it } }
            backupBooleanKeys.forEach { key -> dto.booleans[key.name]?.let { prefs[key] = it } }
        }
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
