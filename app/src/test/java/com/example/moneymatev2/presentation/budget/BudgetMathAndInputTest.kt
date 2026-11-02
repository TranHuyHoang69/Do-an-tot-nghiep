package com.example.moneymatev2.presentation.budget

import com.example.moneymatev2.domain.model.BudgetModel
import com.example.moneymatev2.domain.model.Money
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.util.Locale

class BudgetMathAndInputTest {
    @Test fun moneyAndFractionalProgressUseLocaleWithoutLosingBigIntegerPrecision() {
        val amount = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)
        assertEquals("9.223.372.036.854.775.808", BudgetMoneyFormatter.format(amount, Locale.forLanguageTag("vi-VN")))
        assertEquals("9,223,372,036,854,775,808", BudgetMoneyFormatter.format(amount, Locale.US))
        assertEquals("-9,223,372,036,854,775,808 đ",
            BudgetMoneyFormatter.formatWithCurrency(amount.negate(), "VND", Locale.US))
        assertEquals("12,5%", BudgetMath.percentText(BigInteger.ONE, BigInteger.valueOf(8), Locale.forLanguageTag("vi-VN")))
        assertEquals("12.5%", BudgetMath.percentText(BigInteger.ONE, BigInteger.valueOf(8), Locale.US))
    }

    @Test fun parserAcceptsOnlyPositiveIntegerAndValidGrouping() {
        val valid = mapOf("1" to 1L, "500000" to 500_000L, "1.000.000" to 1_000_000L,
            Long.MAX_VALUE.toString() to Long.MAX_VALUE)
        valid.forEach { (text, amount) ->
            assertEquals(BudgetLimitParser.Result.Valid(amount), BudgetLimitParser.parse(text))
        }
        listOf("", " ").forEach { assertEquals(BudgetLimitParser.Result.Empty, BudgetLimitParser.parse(it)) }
        listOf("0", "0000", "0.000").forEach {
            assertEquals(BudgetLimitParser.Result.NonPositive, BudgetLimitParser.parse(it))
        }
        listOf("-1", "-1000", "1.5", "1.5000", "abc123", "1,000", "+1").forEach {
            assertEquals(BudgetLimitParser.Result.Invalid, BudgetLimitParser.parse(it))
        }
        listOf("9223372036854775808", "9".repeat(1000)).forEach {
            assertEquals(BudgetLimitParser.Result.OutOfRange, BudgetLimitParser.parse(it))
        }
    }

    @Test fun exactTotalsProgressAndLegacyLimitNeverWrapOrDivideByZero() {
        val max = BigInteger.valueOf(Long.MAX_VALUE)
        val over = max.add(BigInteger.ONE)
        assertEquals("9.223.372.036.854.775.808", BudgetMoneyFormatter.format(over))
        assertEquals("-9.223.372.036.854.775.808", BudgetMoneyFormatter.format(BigInteger.valueOf(Long.MIN_VALUE)))
        assertEquals("0%", BudgetMath.percentText(BigInteger.ZERO, BigInteger.valueOf(100)))
        assertEquals("50%", BudgetMath.percentText(BigInteger.valueOf(50), BigInteger.valueOf(100)))
        assertEquals("100%", BudgetMath.percentText(BigInteger.valueOf(100), BigInteger.valueOf(100)))
        assertEquals("150%", BudgetMath.percentText(BigInteger.valueOf(150), BigInteger.valueOf(100)))
        assertEquals(">100%", BudgetMath.percentText(max.add(BigInteger.ONE), max))
        assertEquals("", BudgetMath.percentText(BigInteger.valueOf(100), BigInteger.ZERO))
        assertEquals(0f, BudgetMath.progress(BigInteger.ZERO, BigInteger.ZERO), 0f)
        assertEquals(1f, BudgetMath.progress(BigInteger.ONE, BigInteger.ZERO), 0f)
        assertEquals(0.5f, BudgetMath.progress(BigInteger.valueOf(50), BigInteger.valueOf(100)), 0f)
        assertEquals(1f, BudgetMath.progress(over, BigInteger.ONE), 0f)
        assertTrue(BudgetMath.progress(over, BigInteger.ONE).isFinite())

        val model = BudgetModel("id", "cat", Money(Long.MAX_VALUE, "VND"), over, 10, 2026)
        assertEquals(BigInteger.valueOf(-1), model.remainingMinor)
        try { model.spentAmount; fail("Checked Long accessor must reject overflow") }
        catch (_: ArithmeticException) { }
        val legacy = BudgetProgress("A", BudgetModel("legacy", "cat", Money(0, "VND"),
            BigInteger.valueOf(100), 10, 2026), "cat", "#000000")
        assertFalse(legacy.hasValidLimit)
        assertTrue(legacy.isExceeded)
        assertEquals("", legacy.percentText)
        assertEquals(BigInteger.valueOf(-100), legacy.remainingAmount)
        val negativeLegacy = BudgetProgress("A", BudgetModel("negative", "cat", Money(-1, "VND"),
            BigInteger.valueOf(Long.MIN_VALUE), 10, 2026), "cat", "#000000")
        assertFalse(negativeLegacy.hasValidLimit)
        assertTrue(negativeLegacy.progress.isFinite())
        assertEquals(BigInteger.valueOf(-1).subtract(BigInteger.valueOf(Long.MIN_VALUE)),
            negativeLegacy.remainingAmount)
    }
}
