package com.example.moneymatev2.presentation.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moneymatev2.core.util.AppPreferences
import com.example.moneymatev2.core.util.NightModeSync
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomizationViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _language = MutableStateFlow(currentLanguageTag())

    /*
     * State riêng cho theme.
     *
     * Mục đích:
     * - UI đổi theme NGAY khi user bấm.
     * - Không phải chờ DataStore ghi xong.
     * - Không phụ thuộc Activity recreate.
     */
    private val _themeMode = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CustomizationUiState> =
        combine(
            appPreferences.themeMode,
            _language,
            _themeMode
        ) { savedThemeMode, language, immediateThemeMode ->

            val currentThemeMode = immediateThemeMode ?: savedThemeMode

            CustomizationUiState.Success(
                UserPreferences(
                    language = language,
                    themeMode = currentThemeMode
                )
            ) as CustomizationUiState
        }
            .catch { e ->
                emit(
                    CustomizationUiState.Error(
                        e.message ?: "Đã có lỗi xảy ra"
                    )
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = CustomizationUiState.Loading
            )

    fun onEvent(event: CustomizationEvent) {
        when (event) {
            is CustomizationEvent.ChangeLanguage -> {
                changeLanguage(event.code)
            }

            is CustomizationEvent.ChangeTheme -> {
                changeTheme(event.mode)
            }
        }
    }

    private fun changeLanguage(code: String) {
        if (_language.value == code) return

        // Cập nhật UI trước để tick thay đổi ngay.
        _language.value = code

        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(code)
        )
    }

    private fun changeTheme(mode: String) {
        /*
         * Cập nhật state trước.
         *
         * AppRoot quan sát state này nên Compose sẽ đổi theme ngay,
         * không cần chờ Activity recreate.
         */
        _themeMode.value = mode

        /*
         * Đồng bộ AppCompat/XML theme.
         * Cần cho windowBackground và lần khởi động tiếp theo.
         */
        NightModeSync.save(
            context = context,
            mode = mode
        )

        /*
         * Persist lựa chọn lâu dài.
         */
        viewModelScope.launch {
            appPreferences.setThemeMode(mode)

            /*
             * DataStore hiện đã chứa giá trị mới,
             * state tạm thời không còn cần thiết.
             */
            _themeMode.value = null
        }
    }

    private fun currentLanguageTag(): String {
        val locales = AppCompatDelegate.getApplicationLocales()

        return if (locales.isEmpty) {
            "vi"
        } else {
            locales[0]?.language ?: "vi"
        }
    }
}