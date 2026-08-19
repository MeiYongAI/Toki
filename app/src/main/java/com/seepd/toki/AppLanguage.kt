package com.seepd.toki

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

internal enum class AppLanguage(val storedValue: String, val languageTag: String?) {
    SYSTEM("system", null),
    ENGLISH("en", "en"),
    CHINESE("zh", "zh-Hans"),
    ;

    companion object {
        fun fromStoredValue(value: String?): AppLanguage =
            entries.firstOrNull { it.storedValue == value } ?: SYSTEM
    }
}

internal object AppLanguagePreferences {
    private const val PREFERENCES = "toki_ui"
    private const val KEY_LANGUAGE = "app_language"

    fun load(context: Context): AppLanguage = AppLanguage.fromStoredValue(
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, AppLanguage.SYSTEM.storedValue),
    )

    @SuppressLint("ApplySharedPref")
    fun save(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.storedValue)
            .commit()
    }

    @SuppressLint("ApplySharedPref")
    fun reset(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LANGUAGE)
            .commit()
    }

    fun wrap(context: Context): Context {
        val language = load(context)
        val languageTag = language.languageTag ?: return context
        val locale = Locale.forLanguageTag(languageTag)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(locale))
        }
        return context.createConfigurationContext(configuration)
    }
}
