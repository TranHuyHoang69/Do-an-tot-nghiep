package com.example.moneymatev2.presentation.home

import android.app.Application
import com.example.moneymatev2.R
import com.example.moneymatev2.data.local.CategoryStableId
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.CategoryModel
import com.example.moneymatev2.domain.model.Money
import com.example.moneymatev2.domain.model.TransactionModel
import com.example.moneymatev2.domain.model.TransactionWithCategory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HistorySearchTest {
    private val food = category("food", CategoryStableId.SPEND_FOOD,
        CategoryStableId.SPEND_FOOD, true)
    private val salary = category("salary", CategoryStableId.INCOME_SALARY,
        CategoryStableId.INCOME_SALARY, true, TransactionType.INCOME)

    @Test fun noteSearchIgnoresCaseAccentsSpacesAndMatchesSubstrings() {
        val item = transaction("coffee", food, "Cà phê   Highlands", 50_000)
        for (query in listOf("cà phê", "ca phe", "HIGHLANDS", "high", "  ca   phe  ")) {
            assertTrue(HistorySearchMatcher.matches(item,
                HistorySearchQueryParser.parse(query), emptyMap()))
        }
        assertFalse(HistorySearchMatcher.matches(item,
            HistorySearchQueryParser.parse("grab"), emptyMap()))
    }

    @Test fun categorySearchUsesDisplayedVietnameseEnglishAndArchivedCustomNames() {
        val item = transaction("food-tx", food, null, 50_000)
        assertTrue(HistorySearchMatcher.matches(item,
            HistorySearchQueryParser.parse("an uong"),
            mapOf(R.string.statistics_default_food to "Ăn uống")))
        assertTrue(HistorySearchMatcher.matches(item,
            HistorySearchQueryParser.parse("food"),
            mapOf(R.string.statistics_default_food to "Food & Drinks")))
        assertFalse(HistorySearchMatcher.matches(item,
            HistorySearchQueryParser.parse("spend_food"),
            mapOf(R.string.statistics_default_food to "Food & Drinks")))
        val archived = category("school", null, "Tiền học", false).copy(isArchived = true)
        assertTrue(HistorySearchMatcher.matches(transaction("school-tx", archived, null, 100_000),
            HistorySearchQueryParser.parse("tien hoc"), emptyMap()))
        val health = category("health", CategoryStableId.SPEND_HEALTH,
            CategoryStableId.SPEND_HEALTH, true)
        assertTrue(HistorySearchMatcher.matches(transaction("health-tx", health, null, 30_000),
            HistorySearchQueryParser.parse("suc khoe"),
            mapOf(R.string.statistics_default_health to "Sức khỏe")))
        val transport = category("transport", CategoryStableId.SPEND_TRANSPORT,
            CategoryStableId.SPEND_TRANSPORT, true)
        assertTrue(HistorySearchMatcher.matches(transaction("transport-tx", transport, null, 30_000),
            HistorySearchQueryParser.parse("di lai"),
            mapOf(R.string.statistics_default_transport to "Đi lại")))
    }

    @Test fun categoryWithWrongOwnerNeverMatchesAndSameNamesKeepSeparateGroups() {
        val foreign = food.copy(userId = "other-user")
        assertFalse(HistorySearchMatcher.matches(transaction("foreign", foreign, null, 50_000),
            HistorySearchQueryParser.parse("food"),
            mapOf(R.string.statistics_default_food to "Food & Drinks")))
        val first = category("personal-1", null, "Tiền học", false)
        val second = category("personal-2", null, "Tiền học", false)
        val result = HistorySearchEngine.search(listOf(
            transaction("one", first, null, 10_000),
            transaction("two", second, null, 20_000)), filters("tien hoc"))
        assertEquals(2, result.groups.size)
        assertEquals(setOf("personal-1", "personal-2"), result.groups.map { it.category.id }.toSet())
    }

    @Test fun exactAmountUsesNumericValueNotFormattedText() {
        val item = transaction("exact", food, null, 50_000)
        for (query in listOf("50000", "50.000", "50,000")) {
            val parsed = HistorySearchQueryParser.parse(query)
            assertEquals(HistorySearchQuery.ExactAmount(50_000), parsed)
            assertTrue(HistorySearchMatcher.matches(item, parsed, emptyMap()))
        }
        assertFalse(HistorySearchMatcher.matches(item,
            HistorySearchQueryParser.parse("50001"), emptyMap()))
    }

    @Test fun rangeIncludesBothEndsAndAcceptsGroupedNumbersAndSpaces() {
        for (query in listOf("50000-200000", "50.000 - 200.000", "50,000-200,000")) {
            val parsed = HistorySearchQueryParser.parse(query)
            assertEquals(HistorySearchQuery.AmountRange(50_000, 200_000), parsed)
            assertTrue(HistorySearchMatcher.matches(transaction("min", food, null, 50_000), parsed, emptyMap()))
            assertTrue(HistorySearchMatcher.matches(transaction("max", food, null, 200_000), parsed, emptyMap()))
            assertFalse(HistorySearchMatcher.matches(transaction("outside", food, null, 200_001), parsed, emptyMap()))
        }
    }

    @Test fun invalidNumericQueriesNeverBecomeUnboundedOrTextSearches() {
        for (query in listOf("200000-50000", "50000-", "-50000", "1-2-3")) {
            assertEquals(HistorySearchQuery.InvalidAmountRange, HistorySearchQueryParser.parse(query))
        }
        assertEquals(HistorySearchQuery.InvalidAmount,
            HistorySearchQueryParser.parse("99999999999999999999999"))
        assertEquals(HistorySearchQuery.InvalidAmount,
            HistorySearchQueryParser.parse("50,00"))
        assertTrue(HistorySearchQueryParser.parse("Grab 20-11") is HistorySearchQuery.Text)
        assertTrue(HistorySearchQueryParser.parse("abc-123") is HistorySearchQuery.Text)
        assertTrue(HistorySearchQueryParser.parse("a".repeat(10_000)) is HistorySearchQuery.Text)
    }

    @Test fun monthExpenseSearchRunsAfterTypeAndTimeFiltersAndKeepsDetailsConsistent() {
        val source = listOf(
            transaction("nov-food", food, "Ăn trưa", 50_000, day = 15),
            transaction("nov-other", category("other", null, "Khác", false), "Taxi", 50_000, day = 15),
            transaction("nov-income", salary, "Lương", 50_000, type = TransactionType.INCOME, day = 15),
            transaction("dec-food", food, "Ăn tối", 50_000, month = Calendar.DECEMBER, day = 2)
        )
        val result = HistorySearchEngine.search(source,
            filters("an uong", names = mapOf(R.string.statistics_default_food to "Ăn uống")))
        assertEquals(listOf("nov-food"), result.transactions.map { it.transaction.id })
        assertEquals(1, result.groups.single().transactionCount)
        assertEquals(50_000L, result.groups.single().totalAmount)
        assertTrue(result.hasPeriodTransactions)
        val noMatch = HistorySearchEngine.search(source, filters("unmatched"))
        assertTrue(noMatch.groups.isEmpty())
        assertTrue(noMatch.hasPeriodTransactions)
        val invalid = HistorySearchEngine.search(source, filters("200000-50000"))
        assertEquals(HistorySearchQuery.InvalidAmountRange, invalid.error)
        assertTrue(invalid.transactions.isEmpty())
    }

    @Test fun yearIncomeRangeAndClearQueryPreserveSelectedFilters() {
        val source = listOf(
            transaction("income-in-range", salary, null, 5_000_000,
                type = TransactionType.INCOME, day = 15),
            transaction("income-outside", salary, null, 20_000_000,
                type = TransactionType.INCOME, day = 15),
            transaction("expense-in-range", food, null, 5_000_000, day = 15),
            transaction("prior-year", salary, null, 5_000_000,
                type = TransactionType.INCOME, year = 2025, day = 15)
        )
        val filters = filters("5000000-15000000").copy(period = HomePeriod.YEAR,
            type = TransactionType.INCOME)
        val result = HistorySearchEngine.search(source, filters)
        assertEquals(listOf("income-in-range"), result.transactions.map { it.transaction.id })
        val cleared = HistorySearchEngine.search(source, filters.copy(query = ""))
        assertEquals(setOf("income-in-range", "income-outside"),
            cleared.transactions.map { it.transaction.id }.toSet())
        val switched = HistorySearchEngine.search(source, filters.copy(type = TransactionType.EXPENSE))
        assertEquals(listOf("expense-in-range"), switched.transactions.map { it.transaction.id })
    }

    @Test fun numericSearchDoesNotConvertCurrenciesOrUseDisplaySign() {
        val income = transaction("income", salary, null, 50_000, type = TransactionType.INCOME)
        val expense = transaction("expense", food, null, 50_000)
        val usd = expense.copy(transaction = expense.transaction.copy(id = "usd", money = Money(50_000, "USD")))
        val parsed = HistorySearchQueryParser.parse("50000")
        assertTrue(HistorySearchMatcher.matches(income, parsed, emptyMap()))
        assertTrue(HistorySearchMatcher.matches(expense, parsed, emptyMap()))
        assertFalse(HistorySearchMatcher.matches(usd, parsed, emptyMap()))
    }

    private fun filters(query: String, names: Map<Int, String> = emptyMap()) = HistorySearchFilters(
        HomePeriod.MONTH, date(), TransactionType.EXPENSE, null, null,
        DetailSortType.DATE_DESC, query, names
    )

    private fun date(year: Int = 2026, month: Int = Calendar.NOVEMBER, day: Int = 15): Long =
        Calendar.getInstance().apply {
            set(year, month, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun category(id: String, stableId: String?, name: String, isDefault: Boolean,
                         type: TransactionType = TransactionType.EXPENSE) = CategoryModel(
        "owner", id, stableId, name, type, "ic_food", "#123456", isDefault, false
    )

    private fun transaction(id: String, category: CategoryModel, note: String?, amount: Long,
                            type: TransactionType = TransactionType.EXPENSE,
                            year: Int = 2026, month: Int = Calendar.NOVEMBER,
                            day: Int = 15) = TransactionWithCategory(
        TransactionModel(id, "owner", type, Money(amount, "VND"), category.id, note,
            date(year, month, day), date(year, month, day), false), category
    )
}
