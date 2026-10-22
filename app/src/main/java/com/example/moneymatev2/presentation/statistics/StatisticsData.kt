package com.example.moneymatev2.presentation.statistics

import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.CategoryModel
import com.example.moneymatev2.domain.model.TransactionWithCategory
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class StatisticsRange(val start: Long, val endExclusive: Long) {
    init { require(start < endExclusive) }
    fun contains(time: Long) = time >= start && time < endExclusive
}

enum class StatisticsPeriod { THIS_MONTH, LAST_MONTH, LAST_THREE_MONTHS, THIS_YEAR, CUSTOM }

object StatisticsDates {
    fun forPeriod(period: StatisticsPeriod, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): StatisticsRange {
        val month = today.withDayOfMonth(1)
        val (first, last) = when (period) {
            StatisticsPeriod.THIS_MONTH -> month to month.plusMonths(1)
            StatisticsPeriod.LAST_MONTH -> month.minusMonths(1) to month
            StatisticsPeriod.LAST_THREE_MONTHS -> month.minusMonths(2) to month.plusMonths(1)
            StatisticsPeriod.THIS_YEAR -> today.withDayOfYear(1) to today.withDayOfYear(1).plusYears(1)
            StatisticsPeriod.CUSTOM -> error("Custom dates required")
        }
        return StatisticsRange(first.atStartOfDay(zone).toInstant().toEpochMilli(), last.atStartOfDay(zone).toInstant().toEpochMilli())
    }

    fun custom(startUtc: Long, endUtc: Long, zone: ZoneId = ZoneId.systemDefault()): StatisticsRange {
        val first = Instant.ofEpochMilli(startUtc).atZone(ZoneOffset.UTC).toLocalDate()
        val last = Instant.ofEpochMilli(endUtc).atZone(ZoneOffset.UTC).toLocalDate()
        require(!first.isAfter(last))
        return StatisticsRange(first.atStartOfDay(zone).toInstant().toEpochMilli(), last.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
    }

    fun year(year: Int, zone: ZoneId = ZoneId.systemDefault()) = StatisticsRange(
        LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli(),
        LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
    )

    fun month(year: Int, month: Int, zone: ZoneId = ZoneId.systemDefault()): StatisticsRange {
        val first = YearMonth.of(year, month).atDay(1)
        return StatisticsRange(first.atStartOfDay(zone).toInstant().toEpochMilli(), first.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli())
    }

    fun label(range: StatisticsRange, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String {
        val first = Instant.ofEpochMilli(range.start).atZone(zone).toLocalDate()
        val last = Instant.ofEpochMilli(range.endExclusive - 1).atZone(zone).toLocalDate()
        return if (first.dayOfMonth == 1 && first.plusMonths(1).minusDays(1) == last) {
            YearMonth.from(first).format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
        } else "${first.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", locale))} – ${last.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", locale))}"
    }
}

data class StatisticsCategory(val category: CategoryModel, val total: Long, val transactions: List<TransactionWithCategory>)
data class StatisticsBucket(val label: String, val income: Long, val expense: Long)
data class StatisticsSnapshot(
    val income: Long, val expense: Long, val categories: List<StatisticsCategory>,
    val buckets: List<StatisticsBucket>, val transactions: List<TransactionWithCategory>
) { val net: Long get() = Math.subtractExact(income, expense) }

object StatisticsCalculator {
    fun calculate(source: List<TransactionWithCategory>, range: StatisticsRange, zone: ZoneId = ZoneId.systemDefault()): StatisticsSnapshot {
        val transactions = source.filter { range.contains(it.transaction.createdAt) }.sortedByDescending { it.transaction.createdAt }
        val income = sum(transactions.filter { it.transaction.type == TransactionType.INCOME })
        val expenses = transactions.filter { it.transaction.type == TransactionType.EXPENSE }
        val expense = sum(expenses)
        val categories = expenses.groupBy { it.transaction.userId to it.transaction.categoryId }
            .values.map { group -> StatisticsCategory(requireNotNull(group.first().category), sum(group), group) }
            .sortedWith(compareByDescending<StatisticsCategory> { it.total }.thenBy { it.category.id })
        val first = Instant.ofEpochMilli(range.start).atZone(zone).toLocalDate()
        val last = Instant.ofEpochMilli(range.endExclusive - 1).atZone(zone).toLocalDate()
        val monthly = first.plusDays(45).isBefore(last)
        val monthCount = ChronoUnit.MONTHS.between(YearMonth.from(first), YearMonth.from(last)) + 1
        val monthStep = ((monthCount + 11) / 12).coerceAtLeast(1)
        val starts = buildList {
            var date = first
            while (!date.isAfter(last)) {
                add(date)
                date = if (monthly) date.withDayOfMonth(1).plusMonths(monthStep) else date.plusDays(7)
            }
        }
        val buckets = starts.mapIndexed { index, date ->
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = if (index + 1 < starts.size) starts[index + 1].atStartOfDay(zone).toInstant().toEpochMilli() else range.endExclusive
            val items = transactions.filter { it.transaction.createdAt >= start && it.transaction.createdAt < end }
            StatisticsBucket(if (monthly) "${date.monthValue}/${date.year % 100}" else "${date.dayOfMonth}/${date.monthValue}",
                sum(items.filter { it.transaction.type == TransactionType.INCOME }),
                sum(items.filter { it.transaction.type == TransactionType.EXPENSE }))
        }
        return StatisticsSnapshot(income, expense, categories, buckets, transactions)
    }

    fun monthly(source: List<TransactionWithCategory>, year: Int, zone: ZoneId = ZoneId.systemDefault()): List<StatisticsBucket> =
        (1..12).map { month ->
            val range = StatisticsDates.month(year, month, zone)
            val items = source.filter { range.contains(it.transaction.createdAt) }
            StatisticsBucket(month.toString(), sum(items.filter { it.transaction.type == TransactionType.INCOME }),
                sum(items.filter { it.transaction.type == TransactionType.EXPENSE }))
        }

    private fun sum(items: List<TransactionWithCategory>): Long = items.fold(0L) { total, item ->
        Math.addExact(total, item.transaction.money.amountMinor)
    }
}
