package com.example.moneymatev2.presentation.budget

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal object BudgetMonthLabel {
    fun format(anchor: Long, locale: Locale, zone: TimeZone = TimeZone.getDefault()): String {
        val pattern = if (locale.language == "vi") "'Tháng' M, yyyy" else "LLLL yyyy"
        return SimpleDateFormat(pattern, locale).apply { timeZone = zone }.format(Date(anchor))
    }
}
