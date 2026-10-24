package com.example.moneymatev2.presentation.root

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val securityManager: LocalSecurityManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _pendingReminderNavigation = MutableStateFlow(
        PendingReminderNavigation.fromSavedState(savedStateHandle[PendingReminderNavigation.NAV_KEY])
    )
    val pendingReminderNavigation: StateFlow<PendingReminderNavigation?> = _pendingReminderNavigation
    private var lastAuthenticatedUid: String? = null

    fun offerReminderNavigation(reminderId: String, ownerUid: String) {
        if (reminderId.isBlank() || ownerUid.isBlank()) return
        val nextId = (savedStateHandle["reminder_navigation_sequence"] ?: 0L) + 1L
        savedStateHandle["reminder_navigation_sequence"] = nextId
        val request = PendingReminderNavigation(reminderId, ownerUid, nextId)
        savedStateHandle[PendingReminderNavigation.NAV_KEY] = request.toSavedState()
        _pendingReminderNavigation.value = request
    }

    fun completeReminderNavigation(requestId: Long) {
        if (_pendingReminderNavigation.value?.requestId != requestId) return
        savedStateHandle.remove<ArrayList<String>>(PendingReminderNavigation.NAV_KEY)
        _pendingReminderNavigation.value = null
    }

    fun discardReminderNavigationForDifferentUser(uid: String) {
        _pendingReminderNavigation.value?.takeIf { it.ownerUid != uid }?.let {
            completeReminderNavigation(it.requestId)
        }
    }

    val securityState: StateFlow<LocalSecurityState> = securityManager.state

    val authState: StateFlow<AuthStartupState> =
        authRepository.observeAuthState()
            .onEach { user ->
                val uid = user?.userId
                if (uid == null) {
                    _pendingReminderNavigation.value?.let { completeReminderNavigation(it.requestId) }
                } else if (lastAuthenticatedUid != null && lastAuthenticatedUid != uid) {
                    _pendingReminderNavigation.value?.takeIf { it.ownerUid == lastAuthenticatedUid }
                        ?.let { completeReminderNavigation(it.requestId) }
                }
                lastAuthenticatedUid = uid
            }
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
