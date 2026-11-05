package com.example.moneymatev2.domain.model

sealed interface PasswordResetError {
    data object EmptyEmail : PasswordResetError
    data object InvalidEmail : PasswordResetError
    data object Network : PasswordResetError
    data object TooManyRequests : PasswordResetError
    data object Unavailable : PasswordResetError
}
