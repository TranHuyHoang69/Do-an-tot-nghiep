package com.example.moneymatev2.util

import androidx.compose.runtime.Composable
import com.example.moneymatev2.StringRes
import com.example.moneymatev2.domain.model.TransactionError
import com.example.moneymatev2.ui.theme.StringResource

@Composable
fun TransactionError.toUiMessage(): String = when (this) {
    TransactionError.InvalidAmount -> StringResource(StringRes.error_invalid_amount)
    TransactionError.CategoryNotSelected -> StringResource(StringRes.error_category_not_selected)
    TransactionError.NotAuthenticated -> StringResource(StringRes.error_not_authenticated)
    is TransactionError.UnknownError -> StringResource(StringRes.error_unknown)
}