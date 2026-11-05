package com.example.moneymatev2.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moneymatev2.domain.model.AppResult
import com.example.moneymatev2.domain.model.PasswordResetError
import com.example.moneymatev2.domain.usecase.auth.SendPasswordResetEmailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PasswordResetStatus {
    data object Idle : PasswordResetStatus
    data object Loading : PasswordResetStatus
    data object Accepted : PasswordResetStatus
    data class Error(val reason: PasswordResetError) : PasswordResetStatus
}

data class PasswordResetUiState(
    val email: String = "",
    val status: PasswordResetStatus = PasswordResetStatus.Idle
)

@HiltViewModel
class PasswordResetViewModel @Inject constructor(
    private val sendPasswordResetEmail: SendPasswordResetEmailUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(PasswordResetUiState())
    val uiState: StateFlow<PasswordResetUiState> = _uiState

    fun onEmailChanged(email: String) {
        if (_uiState.value.status == PasswordResetStatus.Loading) return
        _uiState.value = PasswordResetUiState(email)
    }

    fun submit() {
        val current = _uiState.value
        if (current.status == PasswordResetStatus.Loading ||
            current.status == PasswordResetStatus.Accepted) return
        _uiState.value = current.copy(status = PasswordResetStatus.Loading)
        viewModelScope.launch {
            try {
                val status = when (val result = sendPasswordResetEmail(current.email)) {
                    is AppResult.Success -> PasswordResetStatus.Accepted
                    is AppResult.Failure -> PasswordResetStatus.Error(
                        result.error as? PasswordResetError ?: PasswordResetError.Unavailable)
                    AppResult.Loading -> PasswordResetStatus.Error(PasswordResetError.Unavailable)
                }
                _uiState.value = _uiState.value.copy(status = status)
            } catch (e: CancellationException) {
                _uiState.value = _uiState.value.copy(status = PasswordResetStatus.Idle)
                throw e
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    status = PasswordResetStatus.Error(PasswordResetError.Unavailable))
            }
        }
    }
}
