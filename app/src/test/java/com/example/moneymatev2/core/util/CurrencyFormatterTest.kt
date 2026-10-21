package com.example.moneymatev2.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CurrencyFormatterTest {
    private val vi = Locale.forLanguageTag("vi-VN")
    private val en = Locale.US

    private fun vi(amount: Long) = CurrencyFormatter.formatCompact(amount, "Tr", "Tỷ", "Nghìn", vi)
    private fun en(amount: Long) = CurrencyFormatter.formatCompact(amount, "M", "B", "K", en)

    @Test fun vietnameseAmountsBelowMillionAreExactAndGrouped() {
        val expected = mapOf(
            0L to "0", 500L to "500", 999L to "999", 1_000L to "1.000",
            10_000L to "10.000", 25_500L to "25.500", 100_000L to "100.000",
            999_999L to "999.999", -10_000L to "-10.000", -500_000L to "-500.000"
        )
        expected.forEach { (amount, text) ->
            assertEquals(text, vi(amount))
            assertFalse(vi(amount).contains("Nghìn"))
        }
    }

    @Test fun englishAmountsBelowMillionUseVietnameseGrouping() {
        val expected = mapOf(
            0L to "0", 999L to "999", 1_000L to "1.000", 10_000L to "10.000",
            100_000L to "100.000", 999_999L to "999.999", -10_000L to "-10.000"
        )
        expected.forEach { (amount, text) ->
            assertEquals(text, en(amount))
            assertFalse(en(amount).contains("K"))
        }
    }

    @Test fun millionBoundaryDoesNotRoundUpSmallerAmounts() {
        assertEquals("999.999", vi(999_999))
        assertEquals("-999.999", vi(-999_999))
        assertEquals("999.999", en(999_999))
        assertEquals("1Tr", vi(1_000_000))
        assertEquals("1M", en(1_000_000))
    }

    @Test fun millionAndBillionOutputsKeepExistingSuffixAndRounding() {
        assertEquals("1,5Tr", vi(1_500_000))
        assertEquals("-1,5Tr", vi(-1_500_000))
        assertEquals("10Tr", vi(10_000_000))
        assertEquals("1Tỷ", vi(1_000_000_000))
        assertEquals("1,5M", en(1_500_000))
        assertEquals("-1,5M", en(-1_500_000))
        assertEquals("1B", en(1_000_000_000))
        assertTrue(vi(Long.MAX_VALUE).endsWith("Tỷ"))
        assertEquals(Long.MIN_VALUE.toString(), en(Long.MIN_VALUE))
    }
}
