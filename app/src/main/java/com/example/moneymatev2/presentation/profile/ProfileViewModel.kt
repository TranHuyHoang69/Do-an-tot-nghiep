package com.example.moneymatev2.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data object Guest : ProfileUiState
    data class SignedIn(
        val userId: String,
        val displayName: String?,
        val email: String?,
        val hasPasswordProvider: Boolean,
        val hasGoogleProvider: Boolean
    ) : ProfileUiState
}

enum class ProfileDeleteStage { NONE, CONFIRMATION, REAUTHENTICATION }

data class ProfileDeleteState(
    val ownerId: String? = null,
    val stage: ProfileDeleteStage = ProfileDeleteStage.NONE,
    val isProcessing: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    userRepository: UserRepository
) : ViewModel() {
    private val _deleteState = MutableStateFlow(ProfileDeleteState())
    val deleteState: StateFlow<ProfileDeleteState> = _deleteState.asStateFlow()

    /** Firebase Auth is authoritative. Room is a same-UID fallback for missing fields only. */
    val uiState: StateFlow<ProfileUiState> = authRepository.observeAuthState()
        .flatMapLatest { account ->
            if (account == null) {
                flowOf(ProfileUiState.Guest)
            } else {
                userRepository.getCurrentUser(account.userId)
                    .onStart { emit(null) }
                    .map { local ->
                        if (authRepository.getCurrentUserId() != account.userId) {
                            ProfileUiState.Guest
                        } else {
                            val providers = authRepository.getCurrentProviderIds()
                            ProfileUiState.SignedIn(
                                userId = account.userId,
                                displayName = account.displayName?.takeIf(String::isNotBlank)
                                    ?: local?.displayName?.takeIf(String::isNotBlank),
                                email = account.email?.takeIf(String::isNotBlank)
                                    ?: local?.email?.takeIf(String::isNotBlank),
                                hasPasswordProvider = "password" in providers,
                                hasGoogleProvider = "google.com" in providers
                            )
                        }
                    }
                    // Clear the previous account before Room emits for the new UID.
                    .onStart { emit(ProfileUiState.Loading) }
            }
        }
        .onEach { state ->
            val currentOwner = (state as? ProfileUiState.SignedIn)?.userId
            if (_deleteState.value.ownerId != currentOwner) {
                _deleteState.value = ProfileDeleteState()
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ProfileUiState.Loading)

    fun openDeleteConfirmation(userId: String) {
        if (authRepository.getCurrentUserId() != userId || _deleteState.value.stage != ProfileDeleteStage.NONE) return
        _deleteState.value = ProfileDeleteState(ownerId = userId, stage = ProfileDeleteStage.CONFIRMATION)
    }

    fun continueToReauthentication(userId: String) {
        if (authRepository.getCurrentUserId() != userId ||
            _deleteState.value != ProfileDeleteState(userId, ProfileDeleteStage.CONFIRMATION)) return
        _deleteState.value = ProfileDeleteState(ownerId = userId, stage = ProfileDeleteStage.REAUTHENTICATION)
    }

    fun dismissDelete() {
        if (!_deleteState.value.isProcessing) _deleteState.value = ProfileDeleteState()
    }

    // Deliberately no deletion call: no trusted cloud+local deletion workflow exists yet.
    // Keeping the final actions disabled prevents an Auth-only deletion.
}
