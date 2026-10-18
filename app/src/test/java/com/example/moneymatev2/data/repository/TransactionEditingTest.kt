package com.example.moneymatev2.data.repository

import android.app.Application
import androidx.room.Room
import com.example.moneymatev2.data.local.AppDatabase
import com.example.moneymatev2.data.local.entity.*
import com.example.moneymatev2.data.remote.sync.*
import com.example.moneymatev2.domain.model.Money
import com.example.moneymatev2.domain.model.TransactionModel
import com.example.moneymatev2.domain.repository.AuthRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TransactionEditingTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: TransactionRepositoryImpl
    private var uid: String? = "a"

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val auth = mock(AuthRepository::class.java)
        `when`(auth.getCurrentUserId()).thenAnswer { uid }
        val runner = PendingUploadRunner(db, SyncSessionGuard(auth, AuthSessionVersion()))
        repository = TransactionRepositoryImpl(db.transactionDao(), db.categoryDao(),
            mock(FirebaseFirestore::class.java), mock(SyncTrigger::class.java), runner)
        db.categoryDao().insertCategory(CategoryEntity("food", null, "a", "Food", TransactionType.EXPENSE,
            "ic_category", "#123456", false, syncStatus = SyncStatus.SYNCED,
            pendingOperation = PendingOperation.NONE, createdAt = 1, updatedAt = 1))
    }

    @After fun tearDown() { db.close() }

    private fun row(operation: PendingOperation = PendingOperation.NONE) = TransactionEntity(
        "tx", "a", TransactionType.EXPENSE, 100, "VND", "food", "old",
        if (operation == PendingOperation.NONE) SyncStatus.SYNCED else SyncStatus.PENDING,
        operation, createdAt = 123456, updatedAt = 200000, remoteUpdatedAt = 150000,
        retryCount = 2, lastSyncError = "offline"
    )

    private fun model() = TransactionModel("tx", "a", TransactionType.EXPENSE,
        Money(250, "VND"), "food", "edited", 123456, 200000, true)

    @Test fun createUsesSelectedTransactionDate() = runBlocking {
        repository.createTransaction(model().copy(id = ""))
        val created = db.transactionDao().getAllTransactions("a").first().single()
        assertEquals(123456L, created.createdAt)
        assertEquals(PendingOperation.CREATE, created.pendingOperation)
    }

    @Test fun updateKeepsIdDateCategoryAndCreateOperation() = runBlocking {
        db.transactionDao().insertTransaction(row(PendingOperation.CREATE))
        repository.updateTransaction(model())
        val changed = db.transactionDao().getRowForCloudMerge("tx")!!
        assertEquals(1, db.transactionDao().getAllTransactions("a").first().size)
        assertEquals("tx", changed.localId)
        assertEquals("food", changed.categoryLocalId)
        assertEquals(123456L, changed.createdAt)
        assertEquals(250L, changed.amountMinor)
        assertEquals(PendingOperation.CREATE, changed.pendingOperation)
        assertEquals(150000L, changed.remoteUpdatedAt)
    }

    @Test fun syncedUpdateBecomesPendingWithoutLosingRemoteMetadata() = runBlocking {
        db.transactionDao().insertTransaction(row())
        repository.updateTransaction(model())
        val changed = db.transactionDao().getRowForCloudMerge("tx")!!
        assertEquals(PendingOperation.UPDATE, changed.pendingOperation)
        assertEquals(SyncStatus.PENDING, changed.syncStatus)
        assertEquals(150000L, changed.remoteUpdatedAt)
        assertEquals("food", changed.categoryLocalId)
    }

    @Test fun otherOwnerCannotReadUpdateOrDelete() = runBlocking {
        db.transactionDao().insertTransaction(row())
        assertNull(repository.getTransactionById("b", "tx").first())
        uid = "b"
        try { repository.updateTransaction(model()); fail("update must reject session") }
        catch (_: CancellationException) { }
        try { repository.deleteTransaction("a", "tx"); fail("delete must reject session") }
        catch (_: CancellationException) { }
        assertEquals(row(), db.transactionDao().getRowForCloudMerge("tx"))
    }

    @Test fun deleteHidesImmediatelyAndKeepsTombstoneForPullGuard() = runBlocking {
        db.transactionDao().insertTransaction(row(PendingOperation.CREATE))
        repository.deleteTransaction("a", "tx")
        assertTrue(db.transactionDao().getAllTransactions("a").first().isEmpty())
        assertNull(repository.getTransactionById("a", "tx").first())
        val tombstone = db.transactionDao().getRowForCloudMerge("tx")!!
        assertTrue(tombstone.isDeleted)
        assertEquals(PendingOperation.DELETE, tombstone.pendingOperation)
        assertEquals(SyncStatus.PENDING, tombstone.syncStatus)
        try { repository.updateTransaction(model()); fail("deleted row cannot update") }
        catch (_: IllegalArgumentException) { }
    }
}
