package com.example.moneymatev2.data.remote.sync

import android.app.Application
import androidx.room.Room
import com.example.moneymatev2.data.local.AppDatabase
import com.example.moneymatev2.data.local.entity.*
import com.example.moneymatev2.data.repository.CategoryRepositoryImpl
import com.example.moneymatev2.data.repository.DefaultCategoryIds
import com.example.moneymatev2.data.repository.TransactionRepositoryImpl
import com.example.moneymatev2.data.repository.BudgetRepositoryImpl
import com.example.moneymatev2.data.repository.ReminderRepositoryImpl
import com.example.moneymatev2.data.repository.RecurringTransactionRepositoryImpl
import com.example.moneymatev2.domain.usecase.recurring.RecurringScheduleCalculator
import com.example.moneymatev2.domain.repository.*
import com.example.moneymatev2.domain.model.CloudPullReport
import com.example.moneymatev2.domain.reminder.ReminderScheduler
import com.example.moneymatev2.domain.usecase.SyncManager
import com.example.moneymatev2.domain.usecase.reminder.RescheduleRemindersUseCase
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
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
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RoomSyncSafetyTest {
    private lateinit var db: AppDatabase
    private lateinit var auth: AuthRepository
    private lateinit var firestore: FirebaseFirestore
    private lateinit var trigger: SyncTrigger
    private lateinit var versions: AuthSessionVersion
    private lateinit var sessions: SyncSessionGuard
    private lateinit var runner: PendingUploadRunner
    private var uid: String? = "account-a"

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        auth = mock(AuthRepository::class.java)
        `when`(auth.getCurrentUserId()).thenAnswer { uid }
        firestore = mock(FirebaseFirestore::class.java, RETURNS_DEEP_STUBS)
        trigger = mock(SyncTrigger::class.java)
        versions = AuthSessionVersion()
        sessions = SyncSessionGuard(auth, versions)
        runner = PendingUploadRunner(db, sessions)
    }

    @After fun tearDown() { db.close() }

    @Test fun seedAcrossAccountsAndRepeatedLogin_preservesMappings() = runBlocking {
        val repository = CategoryRepositoryImpl(db.categoryDao(), firestore, trigger, runner, db)
        repository.seedDefaultCategoriesIfNeeded("account-a")
        val a = db.categoryDao().getActiveCategories("account-a").first()
        val tx = transaction().copy(categoryLocalId = a.first().localId)
        db.transactionDao().insertTransaction(tx)
        uid = "account-b"
        repository.seedDefaultCategoriesIfNeeded("account-b")
        val b = db.categoryDao().getActiveCategories("account-b").first()
        uid = "account-a"
        repeat(3) { repository.seedDefaultCategoriesIfNeeded("account-a") }
        assertEquals(4, a.size)
        assertEquals(4, b.size)
        assertTrue(a.map { it.localId }.intersect(b.map { it.localId }.toSet()).isEmpty())
        assertEquals(a, db.categoryDao().getActiveCategories("account-a").first())
        assertEquals(tx, db.transactionDao().getRowForCloudMerge(tx.localId))
    }

    @Test fun existingLegacyDefault_isNotRenamedOrDuplicated() = runBlocking {
        val legacy = category(DefaultCategoryIds.SPEND_FOOD)
        db.categoryDao().insertCategory(legacy)
        val tx = transaction().copy(categoryLocalId = legacy.localId)
        db.transactionDao().insertTransaction(tx)
        val repository = CategoryRepositoryImpl(db.categoryDao(), firestore, trigger, runner, db)
        repeat(2) { repository.seedDefaultCategoriesIfNeeded("account-a") }
        assertEquals(4, db.categoryDao().getActiveCategories("account-a").first().size)
        assertEquals(legacy, db.categoryDao().getByStableId("account-a", "spend_food"))
        assertEquals(tx, db.transactionDao().getRowForCloudMerge(tx.localId))
    }

    @Test fun sameLegacyIdAcrossAccounts_doesNotReplaceOtherAccount() = runBlocking {
        val original = category(DefaultCategoryIds.SPEND_FOOD)
        db.categoryDao().insertCategory(original)
        val other = original.copy(userId = "account-b")
        db.categoryDao().insertCategory(other)
        assertEquals(original, db.categoryDao().getCategoryByLocalIdOnce("account-a", original.localId))
        assertEquals(other, db.categoryDao().getCategoryByLocalIdOnce("account-b", original.localId))
    }

    @Test fun cloudPullInsertsRemoteButPreservesPendingAndFailedRows() = runBlocking {
        val pending = transaction()
        val failed = transaction("failed").copy(syncStatus = SyncStatus.FAILED)
        val pendingDefault = category("default")
        db.transactionDao().insertTransaction(pending)
        db.transactionDao().insertTransaction(failed)
        db.categoryDao().insertCategory(pendingDefault)
        snapshots(mapOf(
            "transactions" to listOf(remoteTransaction("new"), remoteTransaction(pending.localId), remoteTransaction(failed.localId)),
            "categories" to listOf(document("default", mapOf(
                "name" to "remote", "stableId" to "spend_food", "isDefault" to true,
                "type" to "EXPENSE", "updatedAt" to 200L
            )))
        ))
        pull().pull("account-a")
        assertNotNull(db.transactionDao().getRowForCloudMerge("new"))
        assertEquals(PendingOperation.NONE, db.transactionDao().getRowForCloudMerge("new")!!.pendingOperation)
        assertEquals(pending, db.transactionDao().getRowForCloudMerge(pending.localId))
        assertEquals(failed, db.transactionDao().getRowForCloudMerge(failed.localId))
        assertEquals(pendingDefault, db.categoryDao().getCategoryByLocalIdOnce("account-a", "default"))
    }

    @Test fun cloudPullDoesNotRestorePendingTransactionDelete() = runBlocking {
        val tombstone = transaction().copy(isDeleted = true, pendingOperation = PendingOperation.DELETE)
        db.transactionDao().insertTransaction(tombstone)
        snapshots(mapOf("transactions" to listOf(remoteTransaction(tombstone.localId))))
        pull().pull("account-a")
        assertEquals(tombstone, db.transactionDao().getRowForCloudMerge(tombstone.localId))
        assertTrue(db.transactionDao().getAllTransactions("account-a").first().isEmpty())
    }

    @Test fun failedCollectionDoesNotPruneLocal_andOtherCollectionsMerge() = runBlocking {
        val clean = transaction().copy(pendingOperation = PendingOperation.NONE, syncStatus = SyncStatus.SYNCED)
        db.transactionDao().insertTransaction(clean)
        snapshots(mapOf("categories" to listOf(document("cloud-category", mapOf("name" to "category", "updatedAt" to 200L)))))
        `when`(collection("transactions").get(Source.SERVER)).thenReturn(Tasks.forException(IOException("offline")))
        try {
            pull().pull("account-a")
            fail("Expected incomplete pull")
        } catch (e: CloudPullService.CloudPullException) {
            assertFalse(e.isPermanent)
        }
        assertEquals(clean, db.transactionDao().getRowForCloudMerge(clean.localId))
        assertNotNull(db.categoryDao().getCategoryByLocalIdOnce("account-a", "cloud-category"))
    }

    @Test fun uploadOldSnapshot_doesNotAcknowledgeSameMillisecondEdit() = runBlocking {
        val original = transaction().copy(pendingOperation = PendingOperation.UPDATE)
        val changed = original.copy(note = "edited")
        db.transactionDao().insertTransaction(original)
        upload(original) { db.transactionDao().updateTransaction(changed) }
        assertEquals(changed, db.transactionDao().getRowForCloudMerge(original.localId))
    }

    @Test fun failureOfOldSnapshot_doesNotMarkSameMillisecondEditFailed() = runBlocking {
        val original = transaction()
        val changed = original.copy(note = "edited")
        db.transactionDao().insertTransaction(original)
        try {
            upload(original) {
                db.transactionDao().updateTransaction(changed)
                throw IOException("offline")
            }
            fail("Expected upload failure")
        } catch (_: IOException) { }
        assertEquals(changed, db.transactionDao().getRowForCloudMerge(original.localId))
    }

    @Test fun uploadFailureRetainsPending_andRetryAcknowledgesIt() = runBlocking {
        val original = transaction()
        db.transactionDao().insertTransaction(original)
        try {
            upload(original) { throw IOException("offline") }
            fail("Expected upload failure")
        } catch (_: IOException) { }
        val failed = db.transactionDao().getRowForCloudMerge(original.localId)!!
        assertEquals(SyncStatus.FAILED, failed.syncStatus)
        assertEquals(PendingOperation.CREATE, failed.pendingOperation)
        assertEquals(1, db.transactionDao().getPendingTransactionsForSync("account-a").size)
        upload(failed) { }
        assertEquals(PendingOperation.NONE, db.transactionDao().getRowForCloudMerge(original.localId)!!.pendingOperation)
    }

    @Test fun deleteAcknowledgement_doesNotDeleteNewerOrSameMillisecondEdit() = runBlocking {
        val original = transaction().copy(pendingOperation = PendingOperation.DELETE, isDeleted = true)
        val changed = original.copy(pendingOperation = PendingOperation.UPDATE, isDeleted = false)
        db.transactionDao().insertTransaction(original)
        upload(original) { db.transactionDao().updateTransaction(changed) }
        assertEquals(changed, db.transactionDao().getRowForCloudMerge(original.localId))
    }

    @Test fun cancellationLeavesPendingUnchanged() = runBlocking {
        val original = transaction()
        db.transactionDao().insertTransaction(original)
        try {
            upload(original) { throw CancellationException("cancelled") }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertEquals(original, db.transactionDao().getRowForCloudMerge(original.localId))
    }

    @Test fun accountChangedDuringUpload_doesNotAcknowledgeOldSession() = runBlocking {
        val original = transaction()
        db.transactionDao().insertTransaction(original)
        try {
            upload(original) { uid = "account-b" }
            fail("Expected session cancellation")
        } catch (_: CancellationException) { }
        assertEquals(original, db.transactionDao().getRowForCloudMerge(original.localId))
    }

    @Test fun signOutAndBackToSameAccount_invalidatesCapturedSession() = runBlocking {
        val original = transaction()
        db.transactionDao().insertTransaction(original)
        try {
            upload(original) { versions.invalidate() }
            fail("Expected session cancellation")
        } catch (_: CancellationException) { }
        assertEquals(original, db.transactionDao().getRowForCloudMerge(original.localId))
    }

    @Test fun accountChangesDuringAcknowledgement_rollsBackRoomStatus() = runBlocking {
        val row = transaction()
        db.transactionDao().insertTransaction(row)
        try {
            runner.run(sessions.capture("account-a"), row,
                readCurrent = { db.transactionDao().getRowForCloudMerge(row.localId) },
                upload = {},
                acknowledge = {
                    db.transactionDao().markSyncedIfUnchanged(row.localId, row.userId, row.updatedAt, 200L)
                    uid = "account-b"
                },
                recordFailure = { fail("Session cancellation must not mark FAILED") }
            )
            fail("Expected session cancellation")
        } catch (_: CancellationException) { }
        assertEquals(row, db.transactionDao().getRowForCloudMerge(row.localId))
    }

    @Test fun repositoryWithStaleUid_neverDispatchesFirestoreWrite() = runBlocking {
        db.transactionDao().insertTransaction(transaction())
        uid = "account-b"
        val repository = TransactionRepositoryImpl(db.transactionDao(), db.categoryDao(), firestore, trigger, runner)
        try {
            repository.syncPendingTransactions("account-a")
            fail("Expected session cancellation")
        } catch (_: CancellationException) { }
        verifyNoInteractions(firestore)
    }

    @Test fun permissionErrorIsPermanent_evenWhenSuppressedByNetworkFailure() {
        val error = IOException("offline")
        error.addSuppressed(FirebaseFirestoreException("permission", FirebaseFirestoreException.Code.PERMISSION_DENIED))
        assertTrue(error.isPermanentSyncError())
    }

    @Test fun allFiveRepositories_propagateFailureKeepPendingThenRetrySameDocuments() = runBlocking {
        db.transactionDao().insertTransaction(transaction())
        db.categoryDao().insertCategory(category("category"))
        db.budgetDao().insertBudget(BudgetEntity("budget", "account-a", "category", 100L, "VND", 10, 2026,
            syncStatus = SyncStatus.PENDING, pendingOperation = PendingOperation.CREATE, createdAt = 100L, updatedAt = 100L))
        db.reminderDao().insertReminder(ReminderEntity("reminder", "account-a", "title", null, 100L,
            RepeatRule.NONE, true, SyncStatus.PENDING, PendingOperation.CREATE, createdAt = 100L, updatedAt = 100L))
        db.recurringTransactionDao().insert(RecurringTransactionEntity("recurring", "account-a", TransactionType.EXPENSE,
            100L, "VND", "category", null, "MONTHLY", 100L, "UTC", 0L, 100L, true, createdAt = 100L, updatedAt = 100L))
        val uploads: List<suspend () -> Unit> = listOf(
            { TransactionRepositoryImpl(db.transactionDao(), db.categoryDao(), firestore, trigger, runner).syncPendingTransactions("account-a") },
            { CategoryRepositoryImpl(db.categoryDao(), firestore, trigger, runner, db).syncPendingCategories("account-a") },
            { BudgetRepositoryImpl(db.budgetDao(), db.transactionDao(), db.categoryDao(), firestore, trigger, runner).syncPendingBudgets("account-a") },
            { ReminderRepositoryImpl(db.reminderDao(), firestore, trigger, runner, auth).syncPendingReminders("account-a") },
            { RecurringTransactionRepositoryImpl(db, db.recurringTransactionDao(), db.transactionDao(), db.categoryDao(),
                RecurringScheduleCalculator(), firestore, trigger, runner).syncPending("account-a") }
        )
        val docs = listOf("transactions" to "tx", "categories" to "category", "budgets" to "budget",
            "reminders" to "reminder", "recurring_transactions" to "recurring").map { (name, id) -> collection(name).document(id) }
        docs.forEach { `when`(it.set(any<Any>())).thenReturn(Tasks.forException(IOException("offline"))) }
        for (upload in uploads) {
            try { upload(); fail("Repository swallowed upload failure") } catch (_: IOException) { }
        }
        assertEquals(SyncStatus.FAILED, db.transactionDao().getPendingTransactionsForSync("account-a").single().syncStatus)
        assertEquals(SyncStatus.FAILED, db.categoryDao().getPendingCategoriesForSync("account-a").single().syncStatus)
        assertEquals(SyncStatus.FAILED, db.budgetDao().getPendingBudgetsForSync("account-a").single().syncStatus)
        assertEquals(SyncStatus.FAILED, db.reminderDao().getPendingRemindersForSync("account-a").single().syncStatus)
        assertEquals(SyncStatus.FAILED, db.recurringTransactionDao().getPending("account-a").single().syncStatus)
        docs.forEach { `when`(it.set(any<Any>())).thenReturn(Tasks.forResult(null)) }
        uploads.forEach { it() }
        assertTrue(db.transactionDao().getPendingTransactionsForSync("account-a").isEmpty())
        assertTrue(db.categoryDao().getPendingCategoriesForSync("account-a").isEmpty())
        assertTrue(db.budgetDao().getPendingBudgetsForSync("account-a").isEmpty())
        assertTrue(db.reminderDao().getPendingRemindersForSync("account-a").isEmpty())
        assertTrue(db.recurringTransactionDao().getPending("account-a").isEmpty())
        docs.forEach { verify(it, times(2)).set(any<Any>()) }
    }

    @Test fun deleteAcknowledgementOfUnchangedSnapshot_removesOnlyItsOwnerRow() = runBlocking {
        val row = transaction().copy(pendingOperation = PendingOperation.DELETE, isDeleted = true)
        db.transactionDao().insertTransaction(row)
        assertEquals(0, db.transactionDao().hardDeleteIfUnchanged(row.localId, "account-b", row.updatedAt))
        upload(row) { }
        assertNull(db.transactionDao().getRowForCloudMerge(row.localId))
    }

    @Test fun queuedAlarmLookup_rejectsOtherAccountAndDeletedReminder() = runBlocking {
        val row = ReminderEntity("reminder", "account-a", "title", null, 100L, RepeatRule.NONE, true,
            SyncStatus.PENDING, PendingOperation.CREATE, createdAt = 100L, updatedAt = 100L)
        db.reminderDao().insertReminder(row)
        val repository = ReminderRepositoryImpl(db.reminderDao(), firestore, trigger, runner, auth)
        assertNotNull(repository.getReminder(row.localId))
        uid = "account-b"
        assertNull(repository.getReminder(row.localId))
        repository.setReminderActive(row.localId, false)
        assertEquals(row, db.reminderDao().getReminderByLocalIdOnce(row.localId))
        uid = "account-a"
        repository.deleteReminder(row.localId)
        assertNull(repository.getReminder(row.localId))
    }

    @Test fun accountChangesDuringPull_managerStopsBeforeSeedOrUpload() = runBlocking {
        val cloud = mock(CloudPullRepository::class.java)
        val categories = mock(CategoryRepository::class.java)
        val transactions = mock(TransactionRepository::class.java)
        val budgets = mock(BudgetRepository::class.java)
        val reminders = mock(ReminderRepository::class.java)
        val recurring = mock(RecurringTransactionRepository::class.java)
        val alarms = mock(ReminderScheduler::class.java)
        val reschedule = mock(RescheduleRemindersUseCase::class.java)
        `when`(reminders.getActiveRemindersOnce("account-a")).thenReturn(emptyList())
        `when`(cloud.pull("account-a")).thenAnswer {
            uid = "account-b"
            CloudPullReport(0, 0, 0, 0, 0)
        }
        val manager = SyncManager(cloud, categories, transactions, budgets, reminders, recurring, alarms, reschedule, sessions)
        try {
            manager.syncAll("account-a")
            fail("Expected session cancellation")
        } catch (_: CancellationException) { }
        verifyNoInteractions(categories, transactions, budgets, recurring, alarms, reschedule)
    }

    @Test fun cloudPull_sameLegacyIdAcrossAccounts_preservesBothOwnersAndPending() = runBlocking {
        val id = DefaultCategoryIds.SPEND_FOOD
        val pendingA = category(id)
        db.categoryDao().insertCategory(pendingA)
        val txA = transaction().copy(categoryLocalId = id)
        db.transactionDao().insertTransaction(txA)
        uid = "account-b"
        snapshots(mapOf("categories" to listOf(document(id, mapOf(
            "name" to "Owner B", "stableId" to "spend_food", "type" to "EXPENSE", "updatedAt" to 200L
        ))), "transactions" to listOf(document("tx-b", mapOf(
            "categoryLocalId" to id, "type" to "EXPENSE", "amountMinor" to 200L, "updatedAt" to 200L
        )))), "account-b")
        repeat(2) { pull().pull("account-b") }
        assertEquals("Owner B", db.categoryDao().getCategoryByLocalIdOnce("account-b", id)!!.name)
        assertEquals(id, db.transactionDao().getRowForCloudMerge("tx-b")!!.categoryLocalId)
        assertEquals(pendingA, db.categoryDao().getCategoryByLocalIdOnce("account-a", id))
        assertEquals(txA, db.transactionDao().getRowForCloudMerge(txA.localId))
        uid = "account-a"
        snapshots(mapOf("categories" to listOf(document(id, mapOf("name" to "Old cloud A", "updatedAt" to 300L)))))
        pull().pull("account-a")
        assertEquals(pendingA, db.categoryDao().getCategoryByLocalIdOnce("account-a", id))
        assertEquals(1, db.categoryDao().getAllCategories("account-b").first().size)
    }

    @Test fun categoryUpload_sameIdOtherOwner_andSameMillisecondEdit_arePreserved() = runBlocking {
        val original = category(DefaultCategoryIds.SPEND_FOOD)
        val other = original.copy(userId = "account-b")
        val changed = original.copy(name = "new local edit")
        db.categoryDao().insertCategory(original)
        db.categoryDao().insertCategory(other)
        val doc = collection("categories").document(original.localId)
        val task = com.google.android.gms.tasks.TaskCompletionSource<Void>()
        val started = CompletableDeferred<Unit>()
        `when`(doc.set(any<Any>())).thenAnswer { started.complete(Unit); task.task }
        val repository = CategoryRepositoryImpl(db.categoryDao(), firestore, trigger, runner, db)
        val upload = async {
            repository.syncPendingCategories("account-a")
        }
        kotlinx.coroutines.withTimeout(5000) { started.await() }
        db.categoryDao().updateCategory(changed)
        task.setResult(null)
        upload.await()
        assertEquals(changed, db.categoryDao().getCategoryByLocalIdOnce("account-a", original.localId))
        assertEquals(other, db.categoryDao().getCategoryByLocalIdOnce("account-b", original.localId))
    }

    private suspend fun upload(row: TransactionEntity, write: suspend () -> Unit) {
        val dao = db.transactionDao()
        runner.run(sessions.capture("account-a"), row,
            readCurrent = { dao.getRowForCloudMerge(row.localId) },
            upload = write,
            acknowledge = {
                if (row.pendingOperation == PendingOperation.DELETE) {
                    dao.hardDeleteIfUnchanged(row.localId, row.userId, row.updatedAt)
                } else {
                    dao.markSyncedIfUnchanged(row.localId, row.userId, row.updatedAt, 200L)
                }
            },
            recordFailure = { dao.markSyncFailedIfUnchanged(row.localId, row.userId, row.updatedAt, "network", 200L) }
        )
    }

    private fun transaction(id: String = "tx") = TransactionEntity(
        id, "account-a", TransactionType.EXPENSE, 100L, "VND", "category", null,
        SyncStatus.PENDING, PendingOperation.CREATE, createdAt = 100L, updatedAt = 100L
    )

    private fun category(id: String) = CategoryEntity(
        id, "spend_food", "account-a", "spend_food", TransactionType.EXPENSE, "ic_food", "#9E9E9E",
        true, syncStatus = SyncStatus.PENDING, pendingOperation = PendingOperation.CREATE,
        createdAt = 100L, updatedAt = 100L
    )

    private fun pull() = CloudPullService(firestore, auth, db, db.categoryDao(), db.transactionDao(),
        db.budgetDao(), db.reminderDao(), db.recurringTransactionDao(), sessions)

    private fun collection(name: String, owner: String = "account-a") = firestore.collection("users").document(owner).collection(name)

    private fun snapshots(rows: Map<String, List<DocumentSnapshot>>, owner: String = "account-a") {
        for (name in listOf("categories", "transactions", "budgets", "reminders", "recurring_transactions")) {
            val snapshot = mock(QuerySnapshot::class.java)
            `when`(snapshot.documents).thenReturn(rows[name] ?: emptyList())
            `when`(collection(name, owner).get(Source.SERVER)).thenReturn(Tasks.forResult(snapshot))
        }
    }

    private fun remoteTransaction(id: String) = document(id, mapOf(
        "type" to "EXPENSE", "amountMinor" to 200L, "currency" to "VND",
        "categoryLocalId" to "category", "updatedAt" to 200L
    ))

    private fun document(id: String, data: Map<String, Any>): DocumentSnapshot {
        val doc = mock(DocumentSnapshot::class.java, RETURNS_DEEP_STUBS)
        `when`(doc.id).thenReturn(id)
        `when`(doc.data).thenReturn(data)
        `when`(doc.reference.path).thenReturn("test/$id")
        return doc
    }
}
