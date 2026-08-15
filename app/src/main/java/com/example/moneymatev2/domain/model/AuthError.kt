package com.example.moneymatev2.domain.model

sealed class AuthError{
    object EmptyCredentials: AuthError()
    object InvalidEmail: AuthError()
    object WeakPassword: AuthError()
    object EmailAlreadyInUse: AuthError()
    object InvalidCredentials: AuthError()
    object NetworkError: AuthError()
    data class Unknown(val rawMessage: String?): AuthError()
}

