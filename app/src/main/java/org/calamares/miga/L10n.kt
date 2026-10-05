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

/** Idioma de la interfaz elegido en Ajustes. [tag] null = el del sistema. */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    SPANISH("es"),
    ENGLISH("en");

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

/**
 * Textos de la app en el idioma elegido. Todos los textos visibles viven en strings.xml (inglés por
 * defecto en values/, español en values-es/) y se leen con [str], que funciona en cualquier sitio
 * (composables, ViewModels, clientes de red...). El idioma se guarda en SharedPreferences para poder
 * leerlo de forma síncrona al arrancar; al cambiarlo se reinicia la app ([restart]) para que todo,
 * incluidos los textos ya calculados en enums y objetos, salga en el idioma nuevo.
 */
object L10n {
    private const val PREFS = "miga_locale"
    private const val KEY_LANGUAGE = "app_language"

    private lateinit var app: Context
    private var forcedResources: Resources? = null

    /** Llamar lo antes posible (Application.attachBaseContext). */
    fun init(context: Context) {
        app = context.applicationContext ?: context
        forcedResources = language(context).tag?.let { localized(context, Locale.forLanguageTag(it)).resources }
    }

    fun language(context: Context): AppLanguage =
        AppLanguage.fromTag(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null))

    /** Guarda el idioma y reinicia la app para aplicarlo. */
    fun setLanguage(activity: Activity, language: AppLanguage) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LANGUAGE, language.tag).commit()
        restart(activity)
    }

    /** Contexto con el idioma elegido aplicado (para Activity.attachBaseContext); sin cambios si es el del sistema. */
    fun wrap(base: Context): Context {
        val tag = language(base).tag ?: return base
        return localized(base, Locale.forLanguageTag(tag))
    }

    /** Idioma efectivo de la app (el elegido en Ajustes o el del sistema). */
    fun locale(): Locale = Locale.getDefault()

    fun str(@StringRes id: Int, vararg args: Any?): String {
        // Tests JVM (sin Android): texto estable a partir del id para poder comparar resultados.
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
