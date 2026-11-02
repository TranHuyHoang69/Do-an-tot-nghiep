package com.example.moneymatev2.presentation.budget

import com.example.moneymatev2.core.util.DefaultCurrency
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.util.Locale

internal object BudgetMath {
    fun progress(spent: BigInteger, limit: BigInteger): Float = when {
        limit <= BigInteger.ZERO -> if (spent > BigInteger.ZERO) 1f else 0f
        spent <= BigInteger.ZERO -> 0f
        spent >= limit -> 1f
        else -> BigDecimal(spent).divide(BigDecimal(limit), 6, RoundingMode.HALF_UP).toFloat()
            .coerceIn(0f, 1f)
    }

    fun percentText(spent: BigInteger, limit: BigInteger, locale: Locale = Locale.ENGLISH): String {
        if (limit <= BigInteger.ZERO) return ""
        val percent = BigDecimal(spent).multiply(BigDecimal(100))
            .divide(BigDecimal(limit), 2, RoundingMode.DOWN)
        if (spent > limit && percent <= BigDecimal(100)) return ">100%"
        val decimalSeparator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        return "${percent.stripTrailingZeros().toPlainString().replace('.', decimalSeparator)}%"
    }
}

internal object BudgetMoneyFormatter {
    fun format(amount: BigInteger, locale: Locale = Locale.forLanguageTag("vi-VN")): String {
        val digits = amount.abs().toString()
        val separator = DecimalFormatSymbols.getInstance(locale).groupingSeparator
        val grouped = digits.reversed().chunked(3).joinToString(separator.toString()).reversed()
        return if (amount.signum() < 0) "-$grouped" else grouped
    }

    fun formatWithCurrency(amount: BigInteger, currency: String, locale: Locale): String =
        "${format(amount, locale)} ${if (currency == DefaultCurrency.CODE) "đ" else currency}"
}
