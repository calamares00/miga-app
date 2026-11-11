package org.calamares.miga

import android.app.Activity
import android.app.LocaleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import java.util.Locale
import kotlin.system.exitProcess

/** UI language chosen in Settings. A null [tag] means "follow the system language". */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    SPANISH("es"),
    ENGLISH("en");

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

/**
 * Access to the app texts in the chosen language.
 *
 * Every visible text lives in strings.xml (English in values/, Spanish in values-es/) and is read
 * through [str], which works anywhere: composables, ViewModels or network clients. The language is
 * stored in SharedPreferences so it can be read synchronously at startup. Changing it restarts the
 * process so that texts already computed in enums and objects are rebuilt in the new language.
 *
 * On Android 13+ the same choice is the app language of the system settings (see
 * res/xml/locales_config.xml): it is sent there when chosen here and read back from there.
 */
object L10n {
    private const val PREFS = "miga_locale"
    private const val KEY_LANGUAGE = "app_language"
    private const val KEY_SYSTEM_SYNCED = "system_synced"

    private lateinit var app: Context
    private var forcedResources: Resources? = null
    private var forcedLocale: Locale? = null

    /** Language the process started with: a different one later means the texts must be rebuilt. */
    private var startedWith: AppLanguage? = null

    /** Must be called as early as possible (Application.attachBaseContext). */
    fun init(context: Context) {
        app = context.applicationContext ?: context
        syncWithSystem(context)
        startedWith = language(context)
        forcedLocale = language(context).tag?.let { Locale.forLanguageTag(it) }
        forcedResources = forcedLocale?.let { localized(context, it).resources }
    }

    fun language(context: Context): AppLanguage =
        AppLanguage.fromTag(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null))

    /** Stores the language and restarts the app to apply it. */
    fun setLanguage(activity: Activity, language: AppLanguage) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LANGUAGE, language.tag).commit()
        setSystemAppLanguage(activity, language)
        restart(activity)
    }

    /**
     * True when the language was changed in the system settings while the app was running: the
     * activity then restarts the app (see [restart]), as when it is changed in the app.
     */
    fun changedOutside(context: Context): Boolean {
        syncWithSystem(context)
        return startedWith != null && language(context) != startedWith
    }

    /**
     * Android 13+: keeps the stored language equal to the app language of the system settings.
     * The first time, a language already chosen in the app is sent to the system instead.
     */
    private fun syncWithSystem(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        runCatching {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_SYSTEM_SYNCED, false)) {
                prefs.edit().putBoolean(KEY_SYSTEM_SYNCED, true).commit()
                val chosen = language(context)
                if (chosen != AppLanguage.SYSTEM) {
                    setSystemAppLanguage(context, chosen)
                    return
                }
            }
            val manager = context.getSystemService(LocaleManager::class.java) ?: return
            val locales = manager.applicationLocales
            val system = if (locales.isEmpty) AppLanguage.SYSTEM else AppLanguage.fromTag(locales[0].language)
            if (system != language(context)) prefs.edit().putString(KEY_LANGUAGE, system.tag).commit()
        }
    }

    private fun setSystemAppLanguage(context: Context, language: AppLanguage) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        runCatching {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                language.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
        }
    }

    /**
     * Returns [base] with the chosen language applied (for Activity.attachBaseContext), or [base]
     * itself when following the system.
     */
    fun wrap(base: Context): Context {
        val tag = language(base).tag ?: return base
        return localized(base, Locale.forLanguageTag(tag))
    }

    /**
     * Effective app locale: the one chosen in Settings or the system one. The chosen one is kept
     * apart from Locale.getDefault(), which Android resets to the system language on configuration
     * changes (rotation, dark mode...): prompts built after that asked the AI for the system
     * language instead of the one chosen.
     */
    fun locale(): Locale = forcedLocale ?: Locale.getDefault()

    fun str(@StringRes id: Int, vararg args: Any?): String {
        // Plain JVM tests have no Android context: return a stable text built from the id so
        // results can still be compared.
        if (!::app.isInitialized) return "#$id" + args.joinToString(prefix = if (args.isEmpty()) "" else ":", separator = ",")
        val res = resources()
        return if (args.isEmpty()) res.getString(id) else res.getString(id, *args)
    }

    private fun resources(): Resources = forcedResources ?: app.resources

    private fun localized(base: Context, locale: Locale): Context {
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    fun restart(activity: Activity) {
        val intent = Intent.makeRestartActivityTask(ComponentName(activity, MainActivity::class.java))
        activity.startActivity(intent)
        exitProcess(0)
    }
}
