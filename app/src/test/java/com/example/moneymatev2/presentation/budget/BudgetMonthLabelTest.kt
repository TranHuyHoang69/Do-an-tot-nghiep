package com.example.moneymatev2.presentation.budget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class BudgetMonthLabelTest {
    private val zone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")

    private fun anchor(year: Int, month: Int): Calendar = Calendar.getInstance(zone).apply {
        clear()
        set(year, month - 1, 1)
    }

    @Test fun monthLabelUsesSelectedLanguageAndCrossesYearInBothDirections() {
        val october = anchor(2026, 10)
        assertEquals("Tháng 10, 2026", BudgetMonthLabel.format(october.timeInMillis, Locale.forLanguageTag("vi-VN"), zone))
        assertEquals("October 2026", BudgetMonthLabel.format(october.timeInMillis, Locale.US, zone))

        val december = anchor(2026, 12)
        december.add(Calendar.MONTH, 1)
        assertEquals("January 2027", BudgetMonthLabel.format(december.timeInMillis, Locale.US, zone))
        december.add(Calendar.MONTH, -1)
        assertEquals("Tháng 12, 2026", BudgetMonthLabel.format(december.timeInMillis, Locale.forLanguageTag("vi-VN"), zone))
    }

    @Test fun leapFebruaryAndJanuaryPreviousMonthKeepTheCorrectYear() {
        val march = anchor(2024, 3)
        march.add(Calendar.MONTH, -1)
        assertEquals("February 2024", BudgetMonthLabel.format(march.timeInMillis, Locale.US, zone))
        val january = anchor(2026, 1)
        january.add(Calendar.MONTH, -1)
        assertEquals("December 2025", BudgetMonthLabel.format(january.timeInMillis, Locale.US, zone))
    }
}
