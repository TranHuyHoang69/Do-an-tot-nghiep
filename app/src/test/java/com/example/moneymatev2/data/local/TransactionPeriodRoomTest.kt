package com.example.moneymatev2.data.local

import android.app.Application
import androidx.room.Room
import com.example.moneymatev2.core.util.TimeRangeCalculator
import com.example.moneymatev2.data.local.entity.BudgetEntity
import com.example.moneymatev2.data.local.entity.PendingOperation
import com.example.moneymatev2.data.local.entity.SyncStatus
import com.example.moneymatev2.data.local.entity.TransactionEntity
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.BudgetModel
import com.example.moneymatev2.domain.model.Money
import com.example.moneymatev2.domain.model.TransactionModel
import com.example.moneymatev2.domain.repository.BudgetRepository
import com.example.moneymatev2.domain.repository.TransactionRepository
import com.example.moneymatev2.domain.usecase.GetMonthlyBudgetOverviewUseCase
import com.example.moneymatev2.presentation.home.HomePeriod
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone
import java.math.BigInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TransactionPeriodRoomTest {
    private val zone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    private lateinit var previousZone: TimeZone
    private lateinit var room: AppDatabase

    @Before fun setUp() {
        previousZone = TimeZone.getDefault()
        TimeZone.setDefault(zone)
        room = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), AppDatabase::class.java
        ).build()
    }

    @After fun tearDown() {
        room.close()
        TimeZone.setDefault(previousZone)
    }

    @Test fun adjacentMonthsIncludeStartExcludeEndAndPreserveRows() = runBlocking {
        val (previousStart, previousEnd) = month(2026, Calendar.SEPTEMBER)
        val (start, end) = month(2026, Calendar.OCTOBER)
        val (nextStart, nextEnd) = month(2026, Calendar.NOVEMBER)
        assertEquals(start, previousEnd)
        assertEquals(end, nextStart)
        val rows = listOf(
            transaction("t1", start - 1), transaction("t2", start),
            transaction("t3", start + 1), transaction("t4", end - 1),
            transaction("t5", end), transaction("t6", end + 1)
        )
        rows.forEach { room.transactionDao().insertTransaction(it) }

        val september = room.transactionDao().getTransactionByPeriod("A", previousStart, previousEnd).first()
        val october = room.transactionDao().getTransactionByPeriod("A", start, end).first()
        val november = room.transactionDao().getTransactionByPeriod("A", nextStart, nextEnd).first()
        assertEquals(listOf("t1"), september.map { it.localId })
        assertEquals(setOf("t2", "t3", "t4"), october.map { it.localId }.toSet())
        assertEquals(setOf("t5", "t6"), november.map { it.localId }.toSet())
        assertFalse(october.any { it.localId == "t1" || it.localId == "t5" })
        assertTrue(november.any { it.localId == "t5" })
        assertTrue(october.map { it.localId }.intersect(november.map { it.localId }.toSet()).isEmpty())
        assertEquals(rows.map { it.localId }.toSet(),
            (september + october + november).map { it.localId }.toSet())
        assertEquals(rows, rows.map { room.transactionDao().getRowForCloudMerge(it.localId) })
        assertEquals(rows.map { it.localId }.toSet(),
            room.transactionDao().getAllTransactions("A").first().map { it.localId }.toSet())
    }

    @Test fun periodQueryKeepsOwnerAndSoftDeleteFiltersAndBothTypes() = runBlocking {
        val (start, end) = month(2026, Calendar.OCTOBER)
        val dao = room.transactionDao()
        dao.insertTransaction(transaction("expense", start, operation = PendingOperation.CREATE))
        dao.insertTransaction(transaction("income", start + 1, type = TransactionType.INCOME))
        dao.insertTransaction(transaction("other-owner", start, userId = "B"))
        dao.insertTransaction(transaction("deleted", start + 2, deleted = true))
        dao.insertTransaction(transaction("pending-delete", end - 1))
        assertEquals(1, dao.softDeleteTransaction("A", "pending-delete", end + 10))

        assertEquals(setOf("expense", "income"),
            dao.getTransactionByPeriod("A", start, end).first().map { it.localId }.toSet())
        assertEquals(listOf("other-owner"),
            dao.getTransactionByPeriod("B", start, end).first().map { it.localId })
        val removed = dao.getRowForCloudMerge("pending-delete")!!
        assertTrue(removed.isDeleted)
        assertEquals(PendingOperation.DELETE, removed.pendingOperation)
    }

    @Test fun budgetOverviewUsesRoomBoundaryAndReactsToOfflineAndPulledRows() = runBlocking {
        val dao = room.transactionDao()
        val budgetDao = room.budgetDao()
        val (octoberStart, novemberStart) = month(2026, Calendar.OCTOBER)
        val (_, decemberStart) = month(2026, Calendar.NOVEMBER)
        budgetDao.insertBudget(budget("october", 10))
        budgetDao.insertBudget(budget("november", 11))
        val useCase = GetMonthlyBudgetOverviewUseCase(RoomBudgets(), RoomTransactions())

        val october = useCase("A", 10, 2026, octoberStart, novemberStart).first()
        assertEquals(0L, october.totalSpent.amountMinor)
        assertEquals(2_000_000L, october.totalRemaining.amountMinor)

        val updates = Channel<Long>(Channel.UNLIMITED)
        val collector = launch {
            useCase("A", 11, 2026, novemberStart, decemberStart).collect {
                updates.send(it.totalSpent.amountMinor)
            }
        }
        try {
            assertEquals(0L, withTimeout(5_000) { updates.receive() })
            val offline = transaction("offline", novemberStart, amount = 500_000,
                operation = PendingOperation.CREATE)
            dao.insertTransaction(offline)
            assertEquals(500_000L, withTimeout(5_000) { updates.receive() })
            assertEquals(offline, dao.getRowForCloudMerge("offline"))

            dao.insertTransaction(transaction("other-category", novemberStart, category = "travel", amount = 300_000))
            dao.insertTransaction(transaction("income", novemberStart, type = TransactionType.INCOME, amount = 700_000))
            dao.insertTransaction(transaction("deleted", novemberStart, amount = 400_000, deleted = true))
            dao.insertTransaction(transaction("pending-delete", novemberStart, amount = 600_000))
            dao.softDeleteTransaction("A", "pending-delete", novemberStart + 1)
            dao.insertTransaction(transaction("other-owner", novemberStart, userId = "B", amount = 800_000))

            val november = useCase("A", 11, 2026, novemberStart, decemberStart).first()
            assertEquals(500_000L, november.totalSpent.amountMinor)
            assertEquals(1_500_000L, november.totalRemaining.amountMinor)
            assertEquals(500_000L, november.budgets.single().spentAmount.amountMinor)
            assertEquals(0.25f, november.budgets.single().spentAmount.amountMinor.toFloat() /
                november.budgets.single().budgetAmount.amountMinor.toFloat(), 0f)
            assertEquals(0L, useCase("A", 10, 2026, octoberStart, novemberStart).first().totalSpent.amountMinor)
            assertEquals(0L, useCase("B", 11, 2026, novemberStart, decemberStart).first().totalSpent.amountMinor)

            // A cloud pull writes into Room; its invalidation must refresh the same Flow.
            dao.upsertFromRemote(listOf(transaction("pulled", novemberStart + 1, amount = 250_000)))
            assertEquals(750_000L, withTimeout(5_000) {
                var spent: Long
                do { spent = updates.receive() } while (spent != 750_000L)
                spent
            })
            assertEquals(1_250_000L,
                useCase("A", 11, 2026, novemberStart, decemberStart).first().totalRemaining.amountMinor)
        } finally {
            collector.cancel()
        }
    }

    @Test fun calculatorKeepsFebruaryAndYearBoundaryHalfOpen() {
        val (february, march) = month(2024, Calendar.FEBRUARY)
        assertEquals(localMidnight(2024, Calendar.MARCH, 1), march)
        assertTrue(february <= localMidnight(2024, Calendar.FEBRUARY, 29))
        val (_, january) = month(2026, Calendar.DECEMBER)
        assertEquals(localMidnight(2027, Calendar.JANUARY, 1), january)
    }

    @Test fun roomOverviewKeepsExactAmountsBeyondLongAndReadsLegacyZero() = runBlocking {
        val (start, end) = month(2026, Calendar.OCTOBER)
        room.budgetDao().insertBudget(budget("legacy-zero", 10).copy(amountMinor = 0))
        val useCase = GetMonthlyBudgetOverviewUseCase(RoomBudgets(), RoomTransactions())
        val zero = useCase("A", 10, 2026, start, end).first()
        assertEquals(BigInteger.ZERO, zero.totalBudgetMinor)
        assertEquals(BigInteger.ZERO, zero.totalSpentMinor)
        assertEquals(0L, room.budgetDao().getRowForCloudMerge("legacy-zero")!!.amountMinor)

        // Cloud pull uses this Room merge path and must accept historical zero limits.
        room.budgetDao().upsertFromRemote(listOf(budget("cloud-zero", 9).copy(amountMinor = 0)))
        assertEquals(0L, room.budgetDao().getRowForCloudMerge("cloud-zero")!!.amountMinor)
        assertEquals(0L, GetMonthlyBudgetOverviewUseCase(RoomBudgets(), RoomTransactions())
            .invoke("A", 9, 2026, month(2026, Calendar.SEPTEMBER).first, start)
            .first().totalBudget.amountMinor)

        room.transactionDao().insertTransaction(transaction("max", start, amount = Long.MAX_VALUE))
        room.transactionDao().insertTransaction(transaction("one", start + 1, amount = 1))
        room.transactionDao().insertTransaction(transaction("income", start, amount = Long.MAX_VALUE,
            type = TransactionType.INCOME))
        room.transactionDao().insertTransaction(transaction("other-owner", start, userId = "B",
            amount = Long.MAX_VALUE))
        val result = useCase("A", 10, 2026, start, end).first()
        val exact = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)
        assertEquals(exact, result.budgets.single().spentMinor)
        assertEquals(exact, result.totalSpentMinor)
        assertEquals(exact.negate(), result.totalRemainingMinor)
        assertEquals(0L, room.budgetDao().getRowForCloudMerge("legacy-zero")!!.amountMinor)
        assertEquals(Long.MAX_VALUE, room.transactionDao().getRowForCloudMerge("max")!!.amountMinor)
    }

    @Test fun roomOverviewSumsSeveralBudgetsBeyondLongWithoutChangingRows() = runBlocking {
        val (start, end) = month(2026, Calendar.OCTOBER)
        val first = budget("food-limit", 10).copy(amountMinor = Long.MAX_VALUE)
        val second = budget("travel-limit", 10).copy(categoryLocalId = "travel", amountMinor = Long.MAX_VALUE)
        room.budgetDao().insertBudget(first)
        room.budgetDao().insertBudget(second)
        room.transactionDao().insertTransaction(transaction("food-spent", start, amount = Long.MAX_VALUE))
        room.transactionDao().insertTransaction(transaction("travel-spent", start, category = "travel",
            amount = Long.MAX_VALUE))
        val result = GetMonthlyBudgetOverviewUseCase(RoomBudgets(), RoomTransactions())
            .invoke("A", 10, 2026, start, end).first()
        val twiceMax = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(2))
        assertEquals(twiceMax, result.totalBudgetMinor)
        assertEquals(twiceMax, result.totalSpentMinor)
        assertEquals(BigInteger.ZERO, result.totalRemainingMinor)
        assertEquals(first, room.budgetDao().getRowForCloudMerge("food-limit"))
        assertEquals(second, room.budgetDao().getRowForCloudMerge("travel-limit"))
    }

    @Test fun mixedLegacyCurrenciesAreKeptSeparateInsteadOfShowingFalseTotals() = runBlocking {
        val (start, end) = month(2026, Calendar.OCTOBER)
        room.budgetDao().insertBudget(budget("food-limit", 10))
        room.transactionDao().insertTransaction(transaction("vnd", start, amount = 100))
        room.transactionDao().insertTransaction(transaction("usd", start + 1, amount = 50).copy(currency = "USD"))
        val result = GetMonthlyBudgetOverviewUseCase(RoomBudgets(), RoomTransactions())
            .invoke("A", 10, 2026, start, end).first()
        assertEquals(BigInteger.valueOf(100), result.budgets.single().spentMinor)
        assertEquals(BigInteger.valueOf(50), result.budgets.single().spentByCurrency["USD"])
        assertEquals(null, result.totalSpentMinor)
        assertEquals(null, result.totalRemainingMinor)
    }

    private fun month(year: Int, calendarMonth: Int): Pair<Long, Long> =
        TimeRangeCalculator.getTimeRange(HomePeriod.MONTH, localMidnight(year, calendarMonth, 15))

    private fun localMidnight(year: Int, month: Int, day: Int): Long = Calendar.getInstance(zone).apply {
        clear()
        set(year, month, day, 0, 0, 0)
    }.timeInMillis

    private fun transaction(
        id: String, at: Long, userId: String = "A", category: String = "food",
        type: TransactionType = TransactionType.EXPENSE, amount: Long = 1,
        deleted: Boolean = false, operation: PendingOperation = PendingOperation.NONE
    ) = TransactionEntity(id, userId, type, amount, "VND", category, null,
        if (operation == PendingOperation.NONE) SyncStatus.SYNCED else SyncStatus.PENDING,
        operation, deleted, at, at)

    private fun budget(id: String, month: Int) = BudgetEntity(
        id, "A", "food", 2_000_000, "VND", month, 2026,
        syncStatus = SyncStatus.SYNCED, pendingOperation = PendingOperation.NONE,
        createdAt = 1, updatedAt = 1
    )

    // Read adapters retain the production repository mappings while exercising both Room queries
    // and the real budget use case; cloud dependencies are unnecessary for these local reads.
    private inner class RoomBudgets : BudgetRepository {
        override fun getBudgetForMonth(userId: String, month: Int, year: Int): Flow<List<BudgetModel>> =
            room.budgetDao().getActiveBudgetsForMonth(userId, year, month).map { rows ->
                rows.map { BudgetModel(it.localId, it.categoryLocalId, Money(it.amountMinor, it.currency),
                    Money.zero(it.currency), it.effectiveMonth, it.effectiveYear) }
            }
        override suspend fun setBudget(userId: String, categoryId: String, amountMinor: Long, currency: String): Unit = error("read only")
        override suspend fun deleteBudget(userId: String, categoryId: String, expectedBudgetId: String?): Unit = error("read only")
        override suspend fun syncPendingBudgets(userId: String): Unit = error("read only")
    }

    private inner class RoomTransactions : TransactionRepository {
        private fun TransactionEntity.model() = TransactionModel(localId, userId, type,
            Money(amountMinor, currency), categoryLocalId, note, createdAt, updatedAt,
            pendingOperation != PendingOperation.NONE)
        override fun getTransactionByPeriod(userId: String, start: Long, end: Long): Flow<List<TransactionModel>> =
            room.transactionDao().getTransactionByPeriod(userId, start, end).map { rows -> rows.map { it.model() } }
        override fun getAllTransactions(userId: String): Flow<List<TransactionModel>> = error("unused")
        override fun getTransactionByCategory(userId: String, categoryId: String): Flow<List<TransactionModel>> = error("unused")
        override fun getTransactionById(userId: String, localId: String): Flow<TransactionModel?> = error("unused")
        override suspend fun createTransaction(transaction: TransactionModel): Unit = error("read only")
        override suspend fun updateTransaction(transaction: TransactionModel): Unit = error("read only")
        override suspend fun deleteTransaction(userId: String, id: String): Unit = error("read only")
        override suspend fun syncPendingTransactions(userId: String): Unit = error("read only")
        override suspend fun reassignCategory(userId: String, oldCategoryId: String, newCategoryId: String): Unit = error("read only")
    }
}
