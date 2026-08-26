package com.example.moneymatev2.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.moneymatev2.R
import com.example.moneymatev2.domain.model.AuthError

@Composable
fun AuthError.toUiMessage(): String = when(this){
    AuthError.EmptyCredentials -> stringResource(R.string.error_empty_credentials)
    AuthError.InvalidEmail -> stringResource(R.string.error_invalid_email)
    AuthError.WeakPassword -> stringResource(R.string.error_weak_password)
    AuthError.EmailAlreadyInUse -> stringResource(R.string.error_email_in_use)
    AuthError.InvalidCredentials -> stringResource(R.string.error_invalid_credentials)
    AuthError.NetworkError -> stringResource(R.string.error_network)
    is AuthError.Unknown -> stringResource(R.string.error_unknown)
}