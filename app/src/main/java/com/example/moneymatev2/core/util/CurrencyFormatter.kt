package com.example.moneymatev2.core.util

import androidx.compose.runtime.Composable
import com.example.moneymatev2.StringRes
import com.example.moneymatev2.presentation.theme.StringResource
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import kotlin.math.abs

object CurrencyFormatter{
    @Composable
    fun formatCompact(amount: Long): String{
        val absAmount = abs(amount)
        val sign = if(amount < 0) "-" else ""
        return when{
            absAmount >= 1_000_000_000 -> sign + formatUnit(absAmount, 1_000_000_000.0, StringResource(StringRes.billion))
            absAmount >= 1_000_000 -> sign + formatUnit(absAmount, 1_000_000.0, StringResource(StringRes.million))else -> amount.toString()
        }
    }

    private fun formatUnit(amount: Long, divisor: Double, suffix:String): String {
        val value = amount / divisor
        val rounded = Math.round(value * 10) / 10.0
        val text = if( rounded == rounded.toLong().toDouble()){
            rounded.toLong().toString()
        }else{
            String.format("%.1f", rounded).replace('.', ',')
        }
        return "$text $suffix"
    }

    fun formatFull(amount: Long): String{
        val formatter = DecimalFormat(
            "#,###",
            DecimalFormatSymbols().apply { groupingSeparator = '.' }
        )
        return formatter.format(amount)
    }
}