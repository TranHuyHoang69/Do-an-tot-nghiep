package com.example.moneymatev2.presentation.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moneymatev2.core.util.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomizationViewModel @Inject constructor(
    private val appPreferences: AppPreferences
) : ViewModel() {

    val uiState: StateFlow<CustomizationUiState> = appPreferences.themeMode
        .map<String, CustomizationUiState> { mode ->
            CustomizationUiState.Success(
                UserPreferences(language = currentLanguageTag(), themeMode = mode)
            )
        }
        .catch { e -> emit(CustomizationUiState.Error(e.message ?: "Đã có lỗi xảy ra")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustomizationUiState.Loading)

    fun onEvent(event: CustomizationEvent) {
        when (event) {
            is CustomizationEvent.ChangeLanguage -> changeLanguage(event.code)
            is CustomizationEvent.ChangeTheme -> changeTheme(event.mode)
        }
    }

    private fun changeLanguage(code: String) {
        // AppCompatDelegate tự lưu locale + gọi recreate() đúng chuẩn cho Activity hiện tại.
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
    }

    private fun changeTheme(mode: String) {
        viewModelScope.launch { appPreferences.setThemeMode(mode) }
    }

    private fun currentLanguageTag(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) "vi" else (locales[0]?.language ?: "vi")
    }
}