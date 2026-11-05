package com.example.moneymatev2.presentation.home

import com.example.moneymatev2.core.util.TimeRangeCalculator
import com.example.moneymatev2.core.util.DefaultCurrency
import com.example.moneymatev2.core.util.groupByCategory
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.CategoryModel
import com.example.moneymatev2.domain.model.GroupedTransaction
import com.example.moneymatev2.domain.model.TransactionWithCategory
import com.example.moneymatev2.ui.item.defaultCategoryNameResId
import java.text.Normalizer
import java.util.Locale

sealed interface HistorySearchQuery {
    data object Empty : HistorySearchQuery
    data class Text(val normalizedText: String) : HistorySearchQuery
    data class ExactAmount(val amount: Long) : HistorySearchQuery
    data class AmountRange(val min: Long, val max: Long) : HistorySearchQuery
    data object InvalidAmount : HistorySearchQuery
    data object InvalidAmountRange : HistorySearchQuery
}

object HistorySearchQueryParser {
    private val amountPattern = Regex("(?:[0-9]+|[0-9]{1,3}(?:[.,][0-9]{3})+)")
    private val combiningMarks = Regex("\\p{M}+")
    private val whitespace = Regex("\\s+")

    fun parse(input: String): HistorySearchQuery {
        val query = input.trim()
        if (query.isEmpty()) return HistorySearchQuery.Empty

        // Text containing letters is always text, even when it contains a hyphen.
        val amountLike = query.any(Char::isDigit) && query.all {
            it.isDigit() || it == '.' || it == ',' || it == '-' || it.isWhitespace()
        }
        if (!amountLike) return HistorySearchQuery.Text(normalizeText(query))

        val hyphen = query.indexOf('-')
        if (hyphen < 0) {
            return parseAmount(query)?.let(HistorySearchQuery::ExactAmount)
                ?: HistorySearchQuery.InvalidAmount
        }
        if (query.indexOf('-', hyphen + 1) >= 0) return HistorySearchQuery.InvalidAmountRange
        val min = parseAmount(query.substring(0, hyphen).trim())
        val max = parseAmount(query.substring(hyphen + 1).trim())
        return if (min == null || max == null || min > max) HistorySearchQuery.InvalidAmountRange
        else HistorySearchQuery.AmountRange(min, max)
    }

    fun normalizeText(input: String): String = combiningMarks.replace(
        Normalizer.normalize(input.trim().lowercase(Locale.ROOT).replace('đ', 'd'),
            Normalizer.Form.NFD), ""
    ).replace(whitespace, " ")

    private fun parseAmount(input: String): Long? {
        if (input.contains('.') && input.contains(',')) return null
        if (!amountPattern.matches(input)) return null
        return input.replace(".", "").replace(",", "").toLongOrNull()
    }
}

object HistorySearchMatcher {
    fun matches(
        item: TransactionWithCategory,
        query: HistorySearchQuery,
        localizedDefaultNames: Map<Int, String>
    ): Boolean = when (query) {
        HistorySearchQuery.Empty -> true
        is HistorySearchQuery.Text -> {
            val noteMatches = item.transaction.note?.let {
                HistorySearchQueryParser.normalizeText(it).contains(query.normalizedText)
            } ?: false
            val category = item.category?.takeIf { it.userId == item.transaction.userId }
            noteMatches || (category != null && HistorySearchQueryParser.normalizeText(
                displayName(category, localizedDefaultNames)).contains(query.normalizedText))
        }
        is HistorySearchQuery.ExactAmount ->
            item.transaction.money.currency == DefaultCurrency.CODE &&
                item.transaction.money.amountMinor == query.amount
        is HistorySearchQuery.AmountRange ->
            item.transaction.money.currency == DefaultCurrency.CODE &&
                item.transaction.money.amountMinor in query.min..query.max
        HistorySearchQuery.InvalidAmount, HistorySearchQuery.InvalidAmountRange -> false
    }

    private fun displayName(category: CategoryModel, localizedDefaultNames: Map<Int, String>): String {
        val resourceId = defaultCategoryNameResId(category)
        return if (resourceId == null) category.name else localizedDefaultNames[resourceId] ?: category.name
    }
}

data class HistorySearchFilters(
    val period: HomePeriod, val anchor: Long, val type: TransactionType,
    val customStart: Long?, val customEnd: Long?, val sort: DetailSortType,
    val query: String, val categoryNames: Map<Int, String>
)

data class HistorySearchResult(
    val transactions: List<TransactionWithCategory>,
    val groups: List<GroupedTransaction>,
    val hasPeriodTransactions: Boolean,
    val error: HistorySearchQuery?
)

/** Runs after the existing UID-scoped Flow has joined transactions and categories. */
object HistorySearchEngine {
    fun search(source: List<TransactionWithCategory>, filters: HistorySearchFilters): HistorySearchResult {
        val (start, end) = if (filters.period == HomePeriod.CUSTOM) {
            (filters.customStart ?: filters.anchor) to
                (filters.customEnd ?: (filters.anchor + 24 * 60 * 60 * 1000L))
        } else TimeRangeCalculator.getTimeRange(filters.period, filters.anchor)
        val inPeriod = source.filter {
            it.transaction.type == filters.type && it.transaction.createdAt in start until end
        }
        val parsed = HistorySearchQueryParser.parse(filters.query)
        val error = parsed.takeIf { it == HistorySearchQuery.InvalidAmount ||
            it == HistorySearchQuery.InvalidAmountRange }
        val visible = if (error != null) emptyList() else inPeriod.filter {
            HistorySearchMatcher.matches(it, parsed, filters.categoryNames)
        }
        val grouped = visible.groupByCategory(filters.type)
        return HistorySearchResult(visible, when (filters.sort) {
            DetailSortType.DATE_DESC -> grouped.sortedByDescending { it.latestTransactionAt }
            DetailSortType.AMOUNT_DESC -> grouped.sortedByDescending { it.totalAmount }
        }, inPeriod.isNotEmpty(), error)
    }
}
