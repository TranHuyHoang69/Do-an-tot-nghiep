package com.example.moneymatev2.core.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.example.moneymatev2.StringRes
import com.example.moneymatev2.presentation.theme.StringResource
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

object CurrencyFormatter {

    /**
     * Hàm THUẦN, không @Composable -- gọi được từ bất kỳ đâu (ViewModel, remember{}, hàm thường).
     * Nhận nhãn đơn vị làm tham số thay vì tự gọi stringResource() bên trong.
     */
    @Suppress("UNUSED_PARAMETER")
    fun formatCompact(amount: Long, millionLabel: String, billionLabel: String, thousandLabel: String,
                      locale: Locale = Locale.getDefault()): String {
        // Keep the pre-existing Long.MIN_VALUE output; abs() cannot represent its magnitude.
        if (amount == Long.MIN_VALUE) return amount.toString()
        val absAmount = abs(amount)
        val sign = if (amount < 0) "-" else ""
        return when {
            absAmount >= 1_000_000_000L -> sign + formatUnit(absAmount, 1_000_000_000.0, billionLabel)
            absAmount >= 1_000_000L -> sign + formatUnit(absAmount, 1_000_000.0, millionLabel)
            else -> DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(locale)).format(amount)
        }
    }

    /**
     * Overload @Composable -- tiện dùng trực tiếp trong Text(text = ...) ở nơi KHÔNG bị
     * recompose liên tục mỗi frame (VD TransactionListItem, HistoryScreen). Chỗ nào bị
     * recompose theo scroll/animation (VD MorphingChartSection) PHẢI dùng bản thuần ở trên,
     * tự lấy label qua stringResource() ngoài remember rồi truyền vào.
     */
    @Composable
    fun formatCompact(amount: Long): String {
        val million = stringResource(StringRes.million)
        val billion = stringResource(StringRes.billion)
        val thousand = stringResource(StringRes.thousand) // cần xác nhận key này đã có trong StringRes
        val locale = LocalConfiguration.current.locales.get(0) ?: Locale.getDefault()
        return formatCompact(amount, million, billion, thousand, locale)
    }

    private fun formatUnit(amount: Long, divisor: Double, suffix: String): String {
        val value = amount / divisor
        val rounded = Math.round(value * 10) / 10.0
        val text = if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            String.format("%.1f", rounded).replace('.', ',')
        }
        return "$text$suffix"
    }

    fun formatFull(amount: Long): String {
        val formatter = DecimalFormat(
            "#,###",
            DecimalFormatSymbols().apply { groupingSeparator = '.' }
        )
        return formatter.format(amount)
    }
}
