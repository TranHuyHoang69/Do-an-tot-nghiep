package com.example.moneymatev2.presentation.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moneymatev2.core.util.AppPreferences
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.presentation.security.LocalSecurityManager
import com.example.moneymatev2.presentation.security.LocalSecurityState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed class AuthStartupState {
    object Loading : AuthStartupState()
    data class LoggedIn(val uid: String) : AuthStartupState()
    object Guest : AuthStartupState()
}

@HiltViewModel
class AppRootViewModel @Inject constructor(
    authRepository: AuthRepository,
    appPreferences: AppPreferences,
    private val securityManager: LocalSecurityManager
) : ViewModel() {
    val securityState: StateFlow<LocalSecurityState> = securityManager.state

    val authState: StateFlow<AuthStartupState> =
        authRepository.observeAuthState()
            .map { user ->
                if (user != null) {
                    AuthStartupState.LoggedIn(user.userId)
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
