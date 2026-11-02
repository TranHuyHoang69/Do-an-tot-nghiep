package com.example.moneymatev2.data.repository

import android.app.Application
import androidx.room.Room
import com.example.moneymatev2.data.local.AppDatabase
import com.example.moneymatev2.data.local.entity.CategoryEntity
import com.example.moneymatev2.data.local.entity.BudgetEntity
import com.example.moneymatev2.data.local.entity.PendingOperation
import com.example.moneymatev2.data.local.entity.SyncStatus
import com.example.moneymatev2.data.local.entity.TransactionEntity
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.data.remote.sync.AuthSessionVersion
import com.example.moneymatev2.data.remote.sync.PendingUploadRunner
import com.example.moneymatev2.data.remote.sync.SyncSessionGuard
import com.example.moneymatev2.data.remote.sync.SyncTrigger
import com.example.moneymatev2.domain.repository.AuthRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BudgetLocalWriteTest {
    private lateinit var room: AppDatabase
    private val auth = mock(AuthRepository::class.java)
    private val trigger = mock(SyncTrigger::class.java)
    private val firestore = mock(FirebaseFirestore::class.java)
    private lateinit var repository: BudgetRepositoryImpl

    @Before fun setUp() = runBlocking {
        room = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
        `when`(auth.getCurrentUserId()).thenReturn("A")
        val runner = PendingUploadRunner(room, SyncSessionGuard(auth, AuthSessionVersion()))
        repository = BudgetRepositoryImpl(room.budgetDao(), room.transactionDao(), room.categoryDao(),
            firestore, trigger, runner, room.budgetConflictDao())
        room.categoryDao().insertCategory(CategoryEntity("food", null, "A", "Food",
            TransactionType.EXPENSE, "ic_food", "#123456", false,
            syncStatus = SyncStatus.SYNCED, pendingOperation = PendingOperation.NONE,
            createdAt = 1, updatedAt = 1))
        room.transactionDao().insertTransaction(TransactionEntity("tx", "A", TransactionType.EXPENSE,
            500, "VND", "food", null, SyncStatus.SYNCED, PendingOperation.NONE,
            createdAt = 1, updatedAt = 1))
    }

    @After fun tearDown() { room.close() }

    @Test fun localSaveAndDeleteRemainSuccessfulWhenImmediateSyncSchedulingFails() = runBlocking {
        doThrow(IllegalStateException("WorkManager unavailable")).`when`(trigger).requestImmediateSync()
        val now = Calendar.getInstance()
        val month = now.get(Calendar.MONTH) + 1
        val year = now.get(Calendar.YEAR)

        repository.setBudget("A", "food", 2_000_000, "VND")
        val saved = room.budgetDao().getBudgetRowForExactMonth("A", "food", year, month)!!
        assertEquals(2_000_000L, saved.amountMinor)
        assertFalse(saved.isArchived)
        assertEquals(PendingOperation.CREATE, saved.pendingOperation)
        assertEquals(2_000_000L, repository.getBudgetForMonth("A", month, year).first().single().budgetAmount.amountMinor)

        try {
            repository.deleteBudget("A", "food", "stale-budget")
            fail("Expected stale budget rejection")
        } catch (_: IllegalArgumentException) {
            assertFalse(room.budgetDao().getBudgetRowForExactMonth("A", "food", year, month)!!.isArchived)
        }
        repository.deleteBudget("A", "food", saved.localId)
        val archived = room.budgetDao().getBudgetRowForExactMonth("A", "food", year, month)!!
        assertEquals(saved.localId, archived.localId)
        assertTrue(archived.isArchived)
        assertEquals(PendingOperation.CREATE, archived.pendingOperation)
        assertTrue(repository.getBudgetForMonth("A", month, year).first().isEmpty())
        assertNotNull(room.categoryDao().getCategoryByLocalIdOnce("A", "food"))
        assertNotNull(room.transactionDao().getRowForCloudMerge("tx"))
        verify(trigger, times(2)).requestImmediateSync()
        verifyNoInteractions(firestore)
    }

    @Test fun categoryValidationFailureDoesNotCreateBudgetOrRequestSync() = runBlocking {
        try {
            repository.setBudget("A", "missing", 100, "VND")
            fail("Expected invalid category")
        } catch (_: IllegalArgumentException) {
            assertTrue(room.budgetDao().getActiveBudgetsForMonth("A",
                Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH) + 1).first().isEmpty())
            verifyNoInteractions(trigger)
        }
    }

    @Test fun nonPositiveLimitCannotCreateOrOverwriteAndLegacyZeroCanBeReadAndCorrected() = runBlocking {
        val now = Calendar.getInstance()
        val month = now.get(Calendar.MONTH) + 1
        val year = now.get(Calendar.YEAR)
        for (invalid in listOf(0L, -1L, Long.MIN_VALUE)) {
            try {
                repository.setBudget("A", "food", invalid, "VND")
                fail("Expected invalid limit")
            } catch (_: IllegalArgumentException) { }
        }
        assertNull(room.budgetDao().getBudgetRowForExactMonth("A", "food", year, month))
        verifyNoInteractions(trigger, firestore)

        val legacy = BudgetEntity("legacy", "A", "food", 0, "VND", month, year,
            syncStatus = SyncStatus.SYNCED, pendingOperation = PendingOperation.NONE,
            createdAt = 1, updatedAt = 1)
        room.budgetDao().insertBudget(legacy)
        assertEquals(0L, repository.getBudgetForMonth("A", month, year).first().single().budgetAmount.amountMinor)
        assertEquals(legacy, room.budgetDao().getRowForCloudMerge("legacy"))
        verifyNoInteractions(trigger, firestore)
        repository.setBudget("A", "food", 1, "VND")
        assertEquals(1L, room.budgetDao().getRowForCloudMerge("legacy")!!.amountMinor)
        assertEquals(PendingOperation.UPDATE, room.budgetDao().getRowForCloudMerge("legacy")!!.pendingOperation)
        val saved = room.budgetDao().getRowForCloudMerge("legacy")!!
        try {
            repository.setBudget("A", "food", -5, "VND")
            fail("Expected invalid update")
        } catch (_: IllegalArgumentException) { }
        assertEquals(saved, room.budgetDao().getRowForCloudMerge("legacy"))
        verify(trigger, times(1)).requestImmediateSync()
    }

    @Test fun deletingInheritedBudgetWritesOnlyCurrentMonthTombstone() = runBlocking {
        val previous = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
        val current = Calendar.getInstance()
        val inherited = BudgetEntity("inherited", "A", "food", 2_000_000, "VND",
            previous.get(Calendar.MONTH) + 1, previous.get(Calendar.YEAR),
            syncStatus = SyncStatus.SYNCED, pendingOperation = PendingOperation.NONE,
            createdAt = 1, updatedAt = 1)
        room.budgetDao().insertBudget(inherited)
        repository.deleteBudget("A", "food", inherited.localId)
        val tombstone = room.budgetDao().getBudgetRowForExactMonth("A", "food",
            current.get(Calendar.YEAR), current.get(Calendar.MONTH) + 1)!!
        assertTrue(tombstone.isArchived)
        assertEquals(PendingOperation.CREATE, tombstone.pendingOperation)
        assertEquals(inherited, room.budgetDao().getRowForCloudMerge(inherited.localId))
        assertTrue(repository.getBudgetForMonth("A", current.get(Calendar.MONTH) + 1,
            current.get(Calendar.YEAR)).first().isEmpty())
        assertNotNull(room.transactionDao().getRowForCloudMerge("tx"))
    }
}
