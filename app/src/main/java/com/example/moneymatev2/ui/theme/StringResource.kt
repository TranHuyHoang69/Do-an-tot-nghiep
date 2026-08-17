package com.example.moneymatev2.ui.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.moneymatev2.util.Localization
val LocalLanguage = compositionLocalOf { "vi" }

@Composable
fun StringResource(@StringRes resId: Int): String {
    val context = LocalContext.current
    val language = LocalLanguage.current

    // Chỉ tạo lại localizedContext khi context hoặc language thật sự đổi,
    // không tạo mới ở mọi lần recompose
    val localizedContext = remember(context, language) {
        Localization.updateLocale(context, language)
    }
    return localizedContext.getString(resId)
}

@Composable
fun StringResource(@StringRes resId: Int, vararg formatArgs: Any): String {
    val context = LocalContext.current
    val language = LocalLanguage.current

    val localizedContext = remember(context, language) {
        Localization.updateLocale(context, language)
    }
    return localizedContext.getString(resId, *formatArgs)
}

@Composable
fun MoneyMateLocalizationProvider(
    language: String,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalLanguage provides language) {
        content()
    }
}