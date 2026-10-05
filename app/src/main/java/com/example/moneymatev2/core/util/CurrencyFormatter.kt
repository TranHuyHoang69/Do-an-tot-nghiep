package com.example.moneymatev2.core.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.moneymatev2.StringRes
import com.example.moneymatev2.presentation.theme.StringResource
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import kotlin.math.abs

object CurrencyFormatter {

    /**
     * Hàm THUẦN, không @Composable -- gọi được từ bất kỳ đâu (ViewModel, remember{}, hàm thường).
     * Nhận nhãn đơn vị làm tham số thay vì tự gọi stringResource() bên trong.
     */
    fun formatCompact(amount: Long, millionLabel: String, billionLabel: String, thousandLabel: String): String {
        val absAmount = abs(amount)
        val sign = if (amount < 0) "-" else ""
        return when {
            absAmount >= 1_000_000_000L -> sign + formatUnit(absAmount, 1_000_000_000.0, billionLabel)
            absAmount >= 1_000_000L -> sign + formatUnit(absAmount, 1_000_000.0, millionLabel)
            absAmount >= 1_000L -> sign + formatUnit(absAmount, 1_000.0, thousandLabel) // khôi phục nhánh bị mất
            else -> amount.toString()
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
        return formatCompact(amount, million, billion, thousand)
    }

    private fun formatUnit(amount: Long, divisor: Double, suffix: String): String {
        val value = amount / divisor
        val rounded = Math.round(value * 10) / 10.0
        val text = if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            String.format("%.1f", rounded).replace('.', ',')
        }
        return "$text $suffix"
    }

    fun formatFull(amount: Long): String {
        val formatter = DecimalFormat(
            "#,###",
            DecimalFormatSymbols().apply { groupingSeparator = '.' }
        )
        return formatter.format(amount)
    }
}