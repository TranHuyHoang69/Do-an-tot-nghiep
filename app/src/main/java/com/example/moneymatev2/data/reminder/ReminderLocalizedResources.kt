package com.example.moneymatev2.data.reminder

import android.content.Context
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import com.example.moneymatev2.core.util.Localization

/** AppCompat's per-app locale also applies when a receiver runs without an Activity. */
internal object ReminderLocalizedResources {
    fun getString(context: Context, @StringRes id: Int): String {
        val locale = AppCompatDelegate.getApplicationLocales().get(0) ?: return context.getString(id)
        return Localization.updateLocale(context, locale.toLanguageTag()).getString(id)
    }
}
