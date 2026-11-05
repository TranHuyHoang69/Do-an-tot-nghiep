package com.example.moneymatev2.data.repository

import android.app.Application
import androidx.room.Room
import com.example.moneymatev2.data.local.AppDatabase
import com.example.moneymatev2.data.local.ProtectedDefaultCategory
import com.example.moneymatev2.data.local.entity.*
import com.example.moneymatev2.data.remote.dto.CategoryDto
import com.example.moneymatev2.data.remote.sync.*
import com.example.moneymatev2.domain.model.AppResult
import com.example.moneymatev2.domain.model.TransactionError
import com.example.moneymatev2.domain.model.UserModel
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.usecase.category.EditCategoryUseCase
import com.example.moneymatev2.domain.usecase.transaction.GetTransactionWithCategoryUseCase
import com.google.android.gms.tasks.Tasks
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
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CategoryEditingTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: CategoryRepositoryImpl
    private lateinit var edit: EditCategoryUseCase
    private lateinit var auth: AuthRepository
    private lateinit var firestore: FirebaseFirestore
    private lateinit var trigger: SyncTrigger
    private lateinit var runner: PendingUploadRunner
    private var uid = "a"
    private val original = CategoryEntity("personal", null, "a", "Food", TransactionType.EXPENSE,
        "ic_food", "#F44336", false, syncStatus = SyncStatus.SYNCED,
        pendingOperation = PendingOperation.NONE, createdAt = 1, updatedAt = 2)

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        auth = mock(AuthRepository::class.java)
        `when`(auth.getCurrentUserId()).thenAnswer { uid }
        `when`(auth.observeAuthState()).thenReturn(flowOf(UserModel("a", null, null, null)))
        firestore = mock(FirebaseFirestore::class.java, RETURNS_DEEP_STUBS)
        trigger = mock(SyncTrigger::class.java)
        runner = PendingUploadRunner(db, SyncSessionGuard(auth, AuthSessionVersion()))
        repository = CategoryRepositoryImpl(db.categoryDao(), firestore, trigger, runner, db)
        edit = EditCategoryUseCase(repository, auth)
        db.categoryDao().insertCategory(original)
        db.categoryDao().insertCategory(original.copy(userId = "b"))
    }

    @After fun cleanup() { db.close() }

    @Test fun editingEachVisualFieldOrAllFieldsUpdatesOneRowAndKeepsIdentity() = runBlocking {
        val changes = listOf(Triple("New name", original.iconKey, original.colorHex),
            Triple(original.name, "ic_money", original.colorHex),
            Triple(original.name, original.iconKey, "#2196F3"),
            Triple("  All fields  ", "ic_car", "#4CAF50"))
        changes.forEach { (name, icon, color) ->
            db.categoryDao().updateCategory(original)
            assertTrue(edit("a", original.localId, name, icon, color) is AppResult.Success)
            val updated = db.categoryDao().getCategoryByLocalIdOnce("a", original.localId)!!
            assertEquals(original.copy(name = name.trim(), iconKey = icon, colorHex = color,
                syncStatus = SyncStatus.PENDING, pendingOperation = PendingOperation.UPDATE,
                updatedAt = updated.updatedAt), updated)
            assertTrue(updated.updatedAt > original.updatedAt)
            assertEquals(listOf(updated), db.categoryDao().getAllCategoriesOnce("a"))
            assertEquals(original.copy(userId = "b"), db.categoryDao().getCategoryByLocalIdOnce("b", original.localId))
        }
        verify(trigger, times(4)).requestImmediateSync()
        verifyNoInteractions(firestore)
    }

    @Test fun blankNameMissingCategoryAndWrongOwnerDoNotWrite() = runBlocking {
        assertEquals(TransactionError.InvalidCategoryName,
            (edit("a", original.localId, " \n ", "ic_car", "#2196F3") as AppResult.Failure).error)
        assertEquals(TransactionError.CategoryNotSelected,
            (edit("a", "missing", "New", "ic_car", "#2196F3") as AppResult.Failure).error)
        uid = "b"
        assertEquals(TransactionError.NotAuthenticated,
            (edit("a", original.localId, "New", "ic_car", "#2196F3") as AppResult.Failure).error)
        assertEquals(original, db.categoryDao().getCategoryByLocalIdOnce("a", original.localId))
        verifyNoInteractions(trigger, firestore)
    }

    @Test fun defaultEditsKeepStableIdFlagsAndProtectionWithoutReseedingDuplicates() = runBlocking {
        repository.seedDefaultCategoriesIfNeeded("a")
        val defaults = db.categoryDao().getAllCategoriesOnce("a").filter { it.isDefault }
        defaults.forEach { row ->
            assertTrue(edit("a", row.localId, "Renamed", "ic_money", "#2196F3") is AppResult.Success)
            val updated = db.categoryDao().getCategoryByLocalIdOnce("a", row.localId)!!
            assertEquals(row.localId, updated.localId)
            assertEquals(row.stableId, updated.stableId)
            assertEquals(row.type, updated.type)
            assertTrue(updated.isDefault)
            assertTrue(ProtectedDefaultCategory.matches(updated))
            try {
                repository.archiveCategory("a", row.localId)
                fail("Default must remain protected")
            } catch (_: DefaultCategoryDeletionException) { }
        }
        repository.seedDefaultCategoriesIfNeeded("a")
        assertEquals(8, db.categoryDao().getAllCategoriesOnce("a").count { it.isDefault })
        assertFalse(ProtectedDefaultCategory.matches(db.categoryDao().getCategoryByLocalIdOnce("a", "personal")!!))
    }

    @Test fun legacyDefaultWithNoStableIdRemainsProtectedAfterChangingIcon() = runBlocking {
        val legacy = original.copy(localId = "legacy-unmapped", stableId = null, isDefault = true)
        db.categoryDao().insertCategory(legacy)
        assertTrue(edit("a", legacy.localId, "Legacy edited", "custom_icon", "#2196F3") is AppResult.Success)
        val updated = db.categoryDao().getCategoryByLocalIdOnce("a", legacy.localId)!!
        assertNull(updated.stableId)
        assertTrue(ProtectedDefaultCategory.matches(updated))
        try {
            repository.archiveCategory("a", legacy.localId)
            fail("Legacy default must remain protected")
        } catch (_: DefaultCategoryDeletionException) { }
    }

    @Test fun linkedTransactionBudgetAndRecurringRowsStayExactlyUnchangedAndFlowReadsNewDetails() = runBlocking {
        val transaction = TransactionEntity("tx", "a", TransactionType.EXPENSE, 100, "VND", original.localId,
            null, SyncStatus.SYNCED, PendingOperation.NONE, createdAt = 1, updatedAt = 2)
        val budget = BudgetEntity("budget", "a", original.localId, 500, "VND", 10, 2026,
            syncStatus = SyncStatus.SYNCED, pendingOperation = PendingOperation.NONE, createdAt = 1, updatedAt = 2)
        val recurring = RecurringTransactionEntity("recurring", "a", TransactionType.EXPENSE,
            100, "VND", original.localId, null, "MONTHLY", 100, "UTC", 0, 100, true,
            syncStatus = SyncStatus.SYNCED, pendingOperation = PendingOperation.NONE, createdAt = 1, updatedAt = 2)
        db.transactionDao().insertTransaction(transaction)
        db.budgetDao().insertBudget(budget)
        db.recurringTransactionDao().insert(recurring)
        val transactions = TransactionRepositoryImpl(db.transactionDao(), db.categoryDao(), firestore, trigger, runner)
        val history = GetTransactionWithCategoryUseCase(transactions, repository, auth)()
        assertEquals(original.name, history.first().single().category!!.name)
        assertTrue(edit("a", original.localId, "Updated", "ic_car", "#2196F3") is AppResult.Success)
        assertEquals(transaction, db.transactionDao().getRowForCloudMerge("tx"))
        assertEquals(budget, db.budgetDao().getRowForCloudMerge("budget"))
        assertEquals(recurring, db.recurringTransactionDao().getById("recurring", "a"))
        val category = history.first().single().category!!
        assertEquals("Updated", category.name)
        assertEquals("ic_car", category.iconKey)
        assertEquals("#2196F3", category.colorHex)
    }

    @Test fun pendingCreateSurvivesEditAndTimestampAlwaysAdvances() = runBlocking {
        val future = original.copy(pendingOperation = PendingOperation.CREATE, syncStatus = SyncStatus.PENDING,
            updatedAt = System.currentTimeMillis() + 60_000)
        db.categoryDao().updateCategory(future)
        assertTrue(edit("a", original.localId, "New", original.iconKey, original.colorHex) is AppResult.Success)
        val updated = db.categoryDao().getCategoryByLocalIdOnce("a", original.localId)!!
        assertEquals(PendingOperation.CREATE, updated.pendingOperation)
        assertEquals(future.updatedAt + 1, updated.updatedAt)
    }

    @Test fun failedRoomUpdateReturnsFailureWithoutPartialWriteOrSyncRequest() = runBlocking {
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_category_edit BEFORE UPDATE ON categories
            BEGIN SELECT RAISE(ABORT, 'injected write failure'); END
        """.trimIndent())
        assertTrue(edit("a", original.localId, "New", "ic_money", "#2196F3") is AppResult.Failure)
        assertEquals(original, db.categoryDao().getCategoryByLocalIdOnce("a", original.localId))
        assertEquals(1, db.categoryDao().getAllCategoriesOnce("a").size)
        verifyNoInteractions(trigger, firestore)
    }

    @Test fun offlineEditRemainsPendingThenUploadsToSameDocumentOnRetry(): Unit = runBlocking {
        val doc = firestore.collection("users").document("a").collection("categories").document(original.localId)
        `when`(doc.set(any(CategoryDto::class.java))).thenReturn(Tasks.forException(IOException("offline")))
        assertTrue(edit("a", original.localId, "Offline name", "ic_money", "#2196F3") is AppResult.Success)
        try { repository.syncPendingCategories("a"); fail("Upload must fail offline") } catch (_: IOException) { }
        val pending = db.categoryDao().getPendingCategoriesForSync("a").single()
        assertEquals("Offline name", pending.name)
        assertEquals(PendingOperation.UPDATE, pending.pendingOperation)
        assertEquals(SyncStatus.FAILED, pending.syncStatus)
        `when`(doc.set(any(CategoryDto::class.java))).thenReturn(Tasks.forResult(null))
        repository.syncPendingCategories("a")
        val synced = db.categoryDao().getCategoryByLocalIdOnce("a", original.localId)!!
        assertEquals(SyncStatus.SYNCED, synced.syncStatus)
        assertEquals(PendingOperation.NONE, synced.pendingOperation)
        assertEquals(1, db.categoryDao().getAllCategoriesOnce("a").size)
        verify(doc, times(2)).set(any(CategoryDto::class.java))
    }
}
