package com.example.moneymatev2.presentation.statistics

import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.CategoryModel
import com.example.moneymatev2.domain.model.Money
import com.example.moneymatev2.domain.model.TransactionModel
import com.example.moneymatev2.domain.model.TransactionWithCategory
import com.example.moneymatev2.domain.model.missingCategory
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StatisticsCalculatorTest {
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private fun at(day: String) = LocalDate.parse(day).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun item(id: String, owner: String = "A", amount: Long, day: String, type: TransactionType = TransactionType.EXPENSE,
                     categoryId: String = "food", archived: Boolean = false, missing: Boolean = false, pending: Boolean = false): TransactionWithCategory {
        val category = if (missing) missingCategory(owner, categoryId, type) else CategoryModel(owner, categoryId, null, categoryId, type, "ic_category", "#00AA00", false, archived)
        return TransactionWithCategory(TransactionModel(id, owner, type, Money(amount, "VND"), categoryId, null, at(day), at(day), pending), category)
    }

    @Test fun emptyAndIncomeOnly() {
        val range = StatisticsDates.month(2026, 10, zone)
        val empty = StatisticsCalculator.calculate(emptyList(), range, zone)
        assertEquals(0L, empty.income); assertEquals(0L, empty.expense); assertEquals(0L, empty.net)
        assertTrue(empty.categories.isEmpty())
        val income = StatisticsCalculator.calculate(listOf(item("1", amount = 1200, day = "2026-10-01", type = TransactionType.INCOME)), range, zone)
        assertEquals(1200L, income.income); assertEquals(0L, income.expense); assertEquals(1200L, income.net)
    }

    @Test fun expenseOnlyAndMixedPendingLocal() {
        val range = StatisticsDates.month(2026, 10, zone)
        val expense = item("e", amount = 300, day = "2026-10-15", pending = true)
        val only = StatisticsCalculator.calculate(listOf(expense), range, zone)
        assertEquals(300L, only.expense); assertEquals(-300L, only.net)
        val mixed = StatisticsCalculator.calculate(listOf(expense, item("i", amount = 500, day = "2026-10-16", type = TransactionType.INCOME)), range, zone)
        assertEquals(500L, mixed.income); assertEquals(300L, mixed.expense); assertEquals(200L, mixed.net)
    }

    @Test fun changesInSourceUpdateTotals() {
        val range = StatisticsDates.month(2026, 10, zone)
        val original = item("1", amount = 100, day = "2026-10-01")
        assertEquals(100L, StatisticsCalculator.calculate(listOf(original), range, zone).expense)
        assertEquals(250L, StatisticsCalculator.calculate(listOf(item("1", amount = 250, day = "2026-10-01")), range, zone).expense)
        assertEquals(0L, StatisticsCalculator.calculate(emptyList(), range, zone).expense)
    }

    @Test fun periodBoundariesAndCustomEndAreInclusiveByDay() {
        val range = StatisticsDates.custom(
            LocalDate.parse("2026-10-01").atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
            LocalDate.parse("2026-10-02").atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(), zone)
        val data = listOf(
            item("before", amount = 1, day = "2026-09-30"),
            item("start", amount = 2, day = "2026-10-01"),
            item("end", amount = 3, day = "2026-10-02"),
            item("after", amount = 4, day = "2026-10-03")
        )
        assertEquals(5L, StatisticsCalculator.calculate(data, range, zone).expense)
        assertFalse(range.contains(range.endExclusive))
        assertTrue(range.contains(range.endExclusive - 1))
    }

    @Test fun currentPreviousThreeMonthsAndYear() {
        val today = LocalDate.of(2026, 10, 22)
        assertEquals(at("2026-10-01"), StatisticsDates.forPeriod(StatisticsPeriod.THIS_MONTH, today, zone).start)
        assertEquals(at("2026-09-01"), StatisticsDates.forPeriod(StatisticsPeriod.LAST_MONTH, today, zone).start)
        assertEquals(at("2026-08-01"), StatisticsDates.forPeriod(StatisticsPeriod.LAST_THREE_MONTHS, today, zone).start)
        assertEquals(at("2026-11-01"), StatisticsDates.forPeriod(StatisticsPeriod.LAST_THREE_MONTHS, today, zone).endExclusive)
        assertEquals(at("2026-01-01"), StatisticsDates.forPeriod(StatisticsPeriod.THIS_YEAR, today, zone).start)
    }

    @Test fun archivedMissingAndSameCategoryIdRemainSeparateByOwner() {
        val source = listOf(item("a", "A", 100, "2026-10-01", archived = true), item("b", "B", 200, "2026-10-01"),
            item("c", "A", 50, "2026-10-01", categoryId = "gone", missing = true))
        val snap = StatisticsCalculator.calculate(source, StatisticsDates.month(2026, 10, zone), zone)
        assertEquals(350L, snap.expense)
        assertEquals(350L, snap.categories.sumOf { it.total })
        assertEquals(3, snap.categories.size)
        assertTrue(snap.categories.any { it.category.isArchived && it.category.userId == "A" })
        assertTrue(snap.categories.any { it.category.isMissing })
    }

    @Test fun monthlySeriesHasTwelveExactBucketsAndChangesWithYear() {
        val source = listOf(item("jan", amount = 100, day = "2026-01-01"),
            item("dec", amount = 50, day = "2026-12-31"),
            item("income", amount = 250, day = "2026-12-01", type = TransactionType.INCOME))
        val series = StatisticsCalculator.monthly(source, 2026, zone)
        assertEquals(12, series.size)
        assertEquals(0L, series[1].expense)
        assertEquals(150L, series.sumOf { it.expense })
        assertEquals(250L, series.sumOf { it.income })
        assertEquals(0L, StatisticsCalculator.monthly(source, 2025, zone).sumOf { it.expense })
        val annual = StatisticsCalculator.calculate(source, StatisticsDates.year(2026, zone), zone)
        assertEquals(annual.expense, series.sumOf { it.expense })
    }

    @Test fun chartBucketsReconcileWithSummaryAndStayReadableForLongRange() {
        val source = listOf(item("one", amount = 10, day = "2025-01-01"),
            item("two", amount = 20, day = "2026-02-02"),
            item("three", amount = 30, day = "2026-02-03", type = TransactionType.INCOME))
        val range = StatisticsRange(at("2025-01-01"), at("2027-01-01"))
        val snap = StatisticsCalculator.calculate(source, range, zone)
        assertTrue(snap.buckets.size <= 12)
        assertEquals(snap.income, snap.buckets.sumOf { it.income })
        assertEquals(snap.expense, snap.buckets.sumOf { it.expense })
    }

    @Test fun exactStartAndLastMillisecondCountOnce() {
        val range = StatisticsDates.month(2026, 10, zone)
        val first = item("first", amount = 1, day = "2026-10-01")
        val final = item("final", amount = 2, day = "2026-10-01").let {
            it.copy(transaction = it.transaction.copy(createdAt = range.endExclusive - 1))
        }
        val out = item("out", amount = 3, day = "2026-10-01").let {
            it.copy(transaction = it.transaction.copy(createdAt = range.endExclusive))
        }
        val snap = StatisticsCalculator.calculate(listOf(first, final, out), range, zone)
        assertEquals(3L, snap.expense)
        assertEquals(2, snap.transactions.size)
    }

    @Test(expected = ArithmeticException::class) fun overflowIsReportedInsteadOfWrapping() {
        StatisticsCalculator.calculate(listOf(item("a", amount = Long.MAX_VALUE, day = "2026-10-01"),
            item("b", amount = 1, day = "2026-10-02")), StatisticsDates.month(2026, 10, zone), zone)
    }
}
