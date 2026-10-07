package com.example.moneymatev2.core.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object NightModeSync {
    private const val PREFS_NAME = "night_mode_sync"
    private const val KEY = "theme_mode"

    fun save(context: Context, mode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY, mode).apply()
        apply(mode)
    }

    fun applySavedModeOnStartup(context: Context) {
        val mode = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY, "system") ?: "system"
        apply(mode)
    }

    private fun apply(mode: String) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_NO
            }
        )
    }
}