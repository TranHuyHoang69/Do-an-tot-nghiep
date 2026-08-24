package com.example.moneymatev2.ui.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

private const val DEFAULT_LANGUAGE = "vi"
    object Localization {
        fun updateLocale(context: Context, language: String): ContextWrapper {
            val locale = Locale.forLanguageTag(language.ifBlank { DEFAULT_LANGUAGE })
            val configuration =
                Configuration(context.resources.configuration)
            configuration.setLocale(locale)
            configuration.setLocales(LocaleList(locale))
            return ContextWrapper(context.createConfigurationContext(configuration))
        }
    }
