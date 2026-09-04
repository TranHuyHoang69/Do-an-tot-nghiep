package com.example.moneymatev2.presentation.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moneymatev2.domain.model.AppResult
import com.example.moneymatev2.domain.model.AuthError
import com.example.moneymatev2.domain.usecase.auth.SignInUseCase
import com.example.moneymatev2.domain.usecase.auth.SignInWithGoogleUseCase
import com.example.moneymatev2.domain.usecase.auth.SignUpUseCase
import com.example.moneymatev2.core.util.requestGoogleIdToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthUiState{
    object Idle: AuthUiState()
    object Loading: AuthUiState()
    object Success: AuthUiState()
    data class Error(val error: AuthError): AuthUiState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val signInUseCase: SignInUseCase,
    private val signUpUseCase: SignUpUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase
): ViewModel(){
    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState

    fun signIn(email: String, password: String) = launchAuth{
        signInUseCase(email, password)
    }

    fun signUp(email: String, password: String, displayName: String) = launchAuth{
        signUpUseCase(email, password, displayName)
    }

    fun signInWithGoogle(context: Context, webClientId: String) = launchAuth{
        val idToken = requestGoogleIdToken(context, webClientId)
            ?: return@launchAuth AppResult.Failure(AuthError.Unknown("Không lấy được id từ google"))
        signInWithGoogleUseCase(idToken)
    }

    fun signInWithGoogleToken(idToken: String) = launchAuth{
        signInWithGoogleUseCase(idToken)
    }

    private fun launchAuth(action: suspend() -> AppResult<*>){
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            _uiState.value = when(val result = action()){
                is AppResult.Success -> AuthUiState.Success
                is AppResult.Failure -> AuthUiState.Error(result.error as AuthError)
                AppResult.Loading -> AuthUiState.Loading
            }
        }
    }
}