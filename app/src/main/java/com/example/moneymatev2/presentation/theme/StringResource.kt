package com.example.moneymatev2.presentation.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
fun StringResource(
    @StringRes resId: Int
): String {
    return stringResource(resId)
}

@Composable
fun StringResource(
    @StringRes resId: Int,
    vararg formatArgs: Any
): String {
    return stringResource(
        id = resId,
        formatArgs = formatArgs
    )
}