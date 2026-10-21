package com.example.moneymatev2.data.repository

import android.app.Application
import androidx.room.Room
import com.example.moneymatev2.data.local.AppDatabase
import com.example.moneymatev2.data.local.entity.*
import com.example.moneymatev2.data.remote.sync.AuthSessionVersion
import com.example.moneymatev2.data.remote.sync.PendingUploadRunner
import com.example.moneymatev2.data.remote.sync.SyncSessionGuard
import com.example.moneymatev2.data.remote.sync.SyncTrigger
import com.example.moneymatev2.domain.model.AppResult
import com.example.moneymatev2.domain.model.TransactionError
import com.example.moneymatev2.domain.model.UserModel
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.CategoryRepository
import com.example.moneymatev2.domain.usecase.category.DeleteCategoryUseCase
import com.example.moneymatev2.domain.usecase.transaction.GetTransactionWithCategoryUseCase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
class CategoryDeletionTest {
    private lateinit var db: AppDatabase
    private lateinit var categories: CategoryRepositoryImpl
    private lateinit var auth: AuthRepository
    private var uid: String? = "a"

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        auth = mock(AuthRepository::class.java)
        `when`(auth.getCurrentUserId()).thenAnswer { uid }
        `when`(auth.observeAuthState()).thenReturn(flowOf(UserModel("a", null, null, null)))
        val runner = PendingUploadRunner(db, SyncSessionGuard(auth, AuthSessionVersion()))
        val trigger = mock(SyncTrigger::class.java)
        val firestore = mock(FirebaseFirestore::class.java)
        categories = CategoryRepositoryImpl(db.categoryDao(), firestore, trigger, runner, db)
        db.categoryDao().insertCategory(category("a", "old"))
        db.categoryDao().insertCategory(category("a", "new"))
        db.categoryDao().insertCategory(category("b", "old"))
    }

    @After fun tearDown() { db.close() }

    private fun category(owner: String, id: String) = CategoryEntity(
        id, null, owner, "$owner-$id", TransactionType.EXPENSE, "ic_food", "#123456", false,
        syncStatus = SyncStatus.SYNCED, pendingOperation = PendingOperation.NONE,
        createdAt = 1, updatedAt = 1
    )

    private fun transaction(owner: String, id: String, categoryId: String,
                            operation: PendingOperation = PendingOperation.NONE,
                            deleted: Boolean = false) = TransactionEntity(
        id, owner, TransactionType.EXPENSE, 100, "VND", categoryId, null,
        SyncStatus.SYNCED, operation, isDeleted = deleted, createdAt = 10, updatedAt = 20
    )

    @Test fun unusedCategoryArchivesWithoutReplacementOrAnotherCategoryOfSameType() = runBlocking {
        db.categoryDao().archiveCategory("a", "new", 2)
        assertEquals(0, categories.getDeletionUsage("a", "old").transactionCount)
        assertTrue(DeleteCategoryUseCase(categories, auth)("a", "old") is AppResult.Success)
        assertTrue(db.categoryDao().getCategoryByLocalIdOnce("a", "old")!!.isArchived)
        assertFalse(db.categoryDao().getCategoryByLocalIdOnce("b", "old")!!.isArchived)
        assertTrue(categories.getActiveCategories("a").first().isEmpty())
    }

    @Test fun archiveTimestampAdvancesPastAnInFlightCategorySnapshot() = runBlocking {
        val original = db.categoryDao().getCategoryByLocalIdOnce("a", "old")!!
        db.categoryDao().archiveCategory("a", "old", original.updatedAt)
        val archived = db.categoryDao().getCategoryByLocalIdOnce("a", "old")!!
        assertTrue(archived.updatedAt > original.updatedAt)
        assertEquals(PendingOperation.UPDATE, archived.pendingOperation)
        assertEquals(0, db.categoryDao().markSyncedIfUnchanged(
            "old", "a", original.updatedAt, original.updatedAt))
        assertEquals(PendingOperation.UPDATE,
            db.categoryDao().getCategoryByLocalIdOnce("a", "old")!!.pendingOperation)
    }

    @Test fun lateTransactionRequiresReplacementAndCannotBeOrphaned() = runBlocking {
        val preview = categories.getDeletionUsage("a", "old")
        assertEquals(0, preview.transactionCount)
        db.transactionDao().insertTransaction(transaction("a", "late", "old"))
        val result = DeleteCategoryUseCase(categories, auth)("a", "old")
        assertEquals(TransactionError.CategoryReplacementRequired, (result as AppResult.Failure).error)
        assertFalse(db.categoryDao().getCategoryByLocalIdOnce("a", "old")!!.isArchived)
        assertEquals("old", db.transactionDao().getRowForCloudMerge("late")!!.categoryLocalId)
    }

    @Test fun replacementIsOnlyValidatedWhenTransactionsNeedMoving() = runBlocking {
        db.transactionDao().insertTransaction(transaction("a", "live", "old"))
        assertEquals(CategoryRepository.ArchiveResult.REPLACEMENT_REQUIRED,
            categories.archiveWithReplacement("a", "old", null))
        assertEquals(CategoryRepository.ArchiveResult.INVALID,
            categories.archiveWithReplacement("a", "old", "old"))
        assertEquals(CategoryRepository.ArchiveResult.INVALID,
            categories.archiveWithReplacement("a", "old", "missing"))
        db.categoryDao().archiveCategory("a", "new", 2)
        assertEquals(CategoryRepository.ArchiveResult.INVALID,
            categories.archiveWithReplacement("a", "old", "new"))
        assertEquals("old", db.transactionDao().getRowForCloudMerge("live")!!.categoryLocalId)
        assertFalse(db.categoryDao().getCategoryByLocalIdOnce("a", "old")!!.isArchived)
    }

    @Test fun confirmedDeleteReassignsOnlyOwnersLiveTransactionsAndArchivesSource() = runBlocking {
        db.transactionDao().insertTransaction(transaction("a", "live", "old", PendingOperation.CREATE))
        db.transactionDao().insertTransaction(transaction("a", "deleted", "old", PendingOperation.DELETE, true))
        db.transactionDao().insertTransaction(transaction("b", "other", "old"))

        val result = DeleteCategoryUseCase(categories, auth)("a", "old", "new")
        assertTrue(result is AppResult.Success)
        assertTrue(db.categoryDao().getCategoryByLocalIdOnce("a", "old")!!.isArchived)
        assertFalse(db.categoryDao().getCategoryByLocalIdOnce("b", "old")!!.isArchived)
        val live = db.transactionDao().getRowForCloudMerge("live")!!
        assertEquals("new", live.categoryLocalId)
        assertEquals(PendingOperation.CREATE, live.pendingOperation)
        assertEquals("old", db.transactionDao().getRowForCloudMerge("deleted")!!.categoryLocalId)
        assertEquals("old", db.transactionDao().getRowForCloudMerge("other")!!.categoryLocalId)
        assertEquals(1, db.transactionDao().getAllTransactions("a").first().size)
        val runner = PendingUploadRunner(db, SyncSessionGuard(auth, AuthSessionVersion()))
        val transactions = TransactionRepositoryImpl(db.transactionDao(), db.categoryDao(),
            mock(FirebaseFirestore::class.java), mock(SyncTrigger::class.java), runner)
        val history = GetTransactionWithCategoryUseCase(transactions, categories, auth)().first()
        assertEquals("a-new", history.single().category!!.name)
    }

    @Test fun transactionMoveFailureRollsBackCategoryArchive() = runBlocking {
        db.transactionDao().insertTransaction(transaction("a", "live", "old"))
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER prevent_category_move BEFORE UPDATE OF categoryLocalId ON transactions
            BEGIN SELECT RAISE(ABORT, 'injected move failure'); END
        """.trimIndent())
        try {
            categories.archiveWithReplacement("a", "old", "new")
            fail("move must fail")
        } catch (_: Exception) { }
        assertFalse(db.categoryDao().getCategoryByLocalIdOnce("a", "old")!!.isArchived)
        assertEquals("old", db.transactionDao().getRowForCloudMerge("live")!!.categoryLocalId)
    }

    @Test fun blockerAppearingAfterPreviewPreventsArchive() = runBlocking {
        assertFalse(categories.getDeletionUsage("a", "old").isBlocked)
        db.budgetDao().insertBudget(BudgetEntity("budget-late", "a", "old", 500, "VND", 10, 2026,
            syncStatus = SyncStatus.SYNCED, pendingOperation = PendingOperation.NONE,
            createdAt = 1, updatedAt = 1))
        assertEquals(CategoryRepository.ArchiveResult.IN_USE,
            categories.archiveWithReplacement("a", "old", null))
        assertFalse(db.categoryDao().getCategoryByLocalIdOnce("a", "old")!!.isArchived)
    }

    @Test fun activeBudgetOrRecurringTemplateBlocksDeleteWithoutPartialReassignment() = runBlocking {
        db.transactionDao().insertTransaction(transaction("a", "live", "old"))
        db.budgetDao().insertBudget(BudgetEntity("budget", "a", "old", 500, "VND", 10, 2026,
            syncStatus = SyncStatus.SYNCED, pendingOperation = PendingOperation.NONE,
            createdAt = 1, updatedAt = 1))
        assertEquals(CategoryRepository.ArchiveResult.IN_USE,
            categories.archiveWithReplacement("a", "old", "new"))
        assertEquals("old", db.transactionDao().getRowForCloudMerge("live")!!.categoryLocalId)
        assertFalse(db.categoryDao().getCategoryByLocalIdOnce("a", "old")!!.isArchived)

        db.budgetDao().updateBudget(db.budgetDao().getRowForCloudMerge("budget")!!.copy(isArchived = true))
        db.recurringTransactionDao().insert(RecurringTransactionEntity(
            "recurring", "a", TransactionType.EXPENSE, 100, "VND", "old", null,
            "MONTHLY", 1, "UTC", 0, 1, false,
            createdAt = 1, updatedAt = 1
        ))
        val result = DeleteCategoryUseCase(categories, auth)("a", "old", "new")
        assertTrue(result is AppResult.Failure)
        assertEquals(TransactionError.CategoryInUse, (result as AppResult.Failure).error)
        assertEquals("old", db.transactionDao().getRowForCloudMerge("live")!!.categoryLocalId)
        assertFalse(db.categoryDao().getCategoryByLocalIdOnce("a", "old")!!.isArchived)
    }

    @Test fun wrongOwnerCannotDeleteAndArchivedOrMissingCategoriesRemainInHistory() = runBlocking {
        db.categoryDao().archiveCategory("a", "old", 30)
        db.transactionDao().insertTransaction(transaction("a", "archived", "old"))
        db.transactionDao().insertTransaction(transaction("a", "missing", "gone"))
        val runner = PendingUploadRunner(db, SyncSessionGuard(auth, AuthSessionVersion()))
        val transactions = TransactionRepositoryImpl(db.transactionDao(), db.categoryDao(),
            mock(FirebaseFirestore::class.java), mock(SyncTrigger::class.java), runner)
        val history = GetTransactionWithCategoryUseCase(transactions, categories, auth)().first()
        assertEquals(2, history.size)
        assertTrue(history.single { it.transaction.id == "archived" }.category!!.isArchived)
        assertTrue(history.single { it.transaction.id == "missing" }.category!!.isMissing)

        uid = "b"
        assertTrue(DeleteCategoryUseCase(categories, auth)("a", "old", "new") is AppResult.Failure)
        assertFalse(db.categoryDao().getCategoryByLocalIdOnce("b", "old")!!.isArchived)
    }
}
