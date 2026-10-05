package com.example.moneymatev2.presentation.settings

data class UserPreferences(
    val language: String,   // "vi" | "en"
    val themeMode: String   // "light" | "dark" | "system"
)

sealed class CustomizationUiState {
    object Loading : CustomizationUiState()
    data class Error(val message: String) : CustomizationUiState()
    data class Success(val preferences: UserPreferences) : CustomizationUiState()
}

sealed class CustomizationEvent {
    data class ChangeLanguage(val code: String) : CustomizationEvent()
    data class ChangeTheme(val mode: String) : CustomizationEvent()
}