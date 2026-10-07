package com.example.moneymatev2.presentation.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moneymatev2.core.util.AppPreferences
import com.example.moneymatev2.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed class AuthStartupState {
    object Loading : AuthStartupState()
    object LoggedIn : AuthStartupState()
    object Guest : AuthStartupState()
}

@HiltViewModel
class AppRootViewModel @Inject constructor(
    authRepository: AuthRepository,
    appPreferences: AppPreferences
) : ViewModel() {

    val authState: StateFlow<AuthStartupState> =
        authRepository.observeAuthState()
            .map { user ->
                if (user != null) {
                    AuthStartupState.LoggedIn
                } else {
                    AuthStartupState.Guest
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = AuthStartupState.Loading
            )

    val themeMode: StateFlow<String> =
        appPreferences.themeMode
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = "system"
            )
}
