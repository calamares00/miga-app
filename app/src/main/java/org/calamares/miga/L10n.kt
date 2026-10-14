package org.calamares.miga

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
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
 */
object L10n {
    private const val PREFS = "miga_locale"
    private const val KEY_LANGUAGE = "app_language"

    private lateinit var app: Context
    private var forcedResources: Resources? = null

    /** Must be called as early as possible (Application.attachBaseContext). */
    fun init(context: Context) {
        app = context.applicationContext ?: context
        forcedResources = language(context).tag?.let { localized(context, Locale.forLanguageTag(it)).resources }
    }

    fun language(context: Context): AppLanguage =
        AppLanguage.fromTag(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null))

    /** Stores the language and restarts the app to apply it. */
    fun setLanguage(activity: Activity, language: AppLanguage) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LANGUAGE, language.tag).commit()
        restart(activity)
    }

    /**
     * Returns [base] with the chosen language applied (for Activity.attachBaseContext), or [base]
     * itself when following the system.
     */
    fun wrap(base: Context): Context {
        val tag = language(base).tag ?: return base
        return localized(base, Locale.forLanguageTag(tag))
    }

    /** Effective app locale: the one chosen in Settings or the system one. */
    fun locale(): Locale = Locale.getDefault()

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

    private fun restart(activity: Activity) {
        val intent = Intent.makeRestartActivityTask(ComponentName(activity, MainActivity::class.java))
        activity.startActivity(intent)
        exitProcess(0)
    }
}
