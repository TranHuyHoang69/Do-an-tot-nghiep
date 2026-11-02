package com.example.moneymatev2.data.remote.sync

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import com.example.moneymatev2.data.local.AppDatabase
import com.example.moneymatev2.data.local.entity.BudgetEntity
import com.example.moneymatev2.data.local.entity.PendingOperation
import com.example.moneymatev2.data.local.entity.SyncStatus
import com.example.moneymatev2.data.remote.dto.BudgetDto
import com.example.moneymatev2.data.repository.BudgetRepositoryImpl
import com.example.moneymatev2.domain.repository.AuthRepository
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
import java.util.UUID

/** Two independent Room databases drive the real Budget upload and CloudPullService merge.
 * Only Firestore document I/O is simulated. Defect assertions are labelled explicitly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BudgetMultiDeviceConflictTest {
    private companion object {
        const val UID = "user-test-01"
        const val CATEGORY = "food-category"
        const val YEAR = 2026
        const val MONTH = 10
        const val BASE = "budget-01"
        const val A_ID = "A1"
        const val B_ID = "B1"
    }

    private val firestore = mock(FirebaseFirestore::class.java, RETURNS_DEEP_STUBS)
    private val trigger = mock(SyncTrigger::class.java)
    private val cloud = mutableMapOf<String, LinkedHashMap<String, Map<String, Any>>>()
    private val failAfterCommitOnce = mutableSetOf<Pair<String, String>>()
    private val failBeforeCommitOnce = mutableSetOf<Pair<String, String>>()
    private val failPullOnce = mutableSetOf<String>()
    private var pauseAfterCommit: Pair<CompletableDeferred<Unit>, TaskCompletionSource<Long>>? = null
    private var pauseBudgetPull: Pair<CompletableDeferred<Unit>, TaskCompletionSource<QuerySnapshot>>? = null
    private var heldBudgetSnapshot: QuerySnapshot? = null
    private var serverTime = 2_000_000_000_000L
    private lateinit var a: Device
    private lateinit var b: Device

    @Before fun setUp() {
        configureCollection(UID)
        configureBudgetTransactions()
        a = Device("A")
        b = Device("B")
    }

    @After fun tearDown() {
        a.db.close()
        b.db.close()
    }

    @Test fun ordinaryCreateUpdateDeleteNeverCreatesConflict() = runBlocking {
        a.insert(row(A_ID, 2_000_000, PendingOperation.CREATE))
        a.upload()
        assertEquals(1L, cloudRevision(A_ID))
        a.update(A_ID, 3_000_000)
        a.upload()
        assertEquals(2L, cloudRevision(A_ID))
        a.archive(A_ID)
        assertTrue(a.active().isEmpty())
        a.upload()
        b.pull()
        assertTrue(cloudArchived(A_ID))
        assertTrue(b.active().isEmpty())
        assertTrue(a.db.budgetConflictDao().getForBudget(UID, A_ID).isEmpty())
        assertEquals(0, a.db.budgetDao().observeUnresolvedCount(UID).first())
    }

    @Test fun inheritedDeleteCreatesCurrentMonthTombstoneAndRemainsHiddenAfterPull() = runBlocking {
        val now = java.util.Calendar.getInstance()
        val currentYear = now.get(java.util.Calendar.YEAR)
        val currentMonth = now.get(java.util.Calendar.MONTH) + 1
        now.add(java.util.Calendar.MONTH, -1)
        val inherited = row(A_ID, 2_000_000, PendingOperation.NONE).copy(
            effectiveYear = now.get(java.util.Calendar.YEAR),
            effectiveMonth = now.get(java.util.Calendar.MONTH) + 1)
        a.insert(inherited)
        cloud[UID]!![A_ID] = cloudData(inherited)
        assertEquals(A_ID, a.db.budgetDao().getActiveBudgetsForMonth(UID, currentYear, currentMonth).first().single().localId)
        a.deleteVisible(A_ID)
        val tombstone = a.db.budgetDao().getBudgetRowForExactMonth(UID, CATEGORY, currentYear, currentMonth)!!
        val tombstoneRef = firestore.collection("users").document(UID).collection("budgets").document(tombstone.localId)
        `when`(tombstoneRef.path).thenReturn("users/$UID/budgets/${tombstone.localId}")
        assertTrue(tombstone.isArchived)
        assertTrue(a.db.budgetDao().getActiveBudgetsForMonth(UID, currentYear, currentMonth).first().isEmpty())
        a.pull()
        a.upload()
        a.pull()
        assertTrue(cloudArchived(tombstone.localId))
        assertTrue(a.db.budgetDao().getActiveBudgetsForMonth(UID, currentYear, currentMonth).first().isEmpty())
        assertEquals(0, a.db.budgetDao().observeUnresolvedCount(UID).first())
    }

    @Test fun oldConflictRecordAloneDoesNotRaiseNotice() = runBlocking {
        seedSharedBase()
        a.db.budgetConflictDao().insert(com.example.moneymatev2.data.local.entity.BudgetConflictEntity(
            conflictId = "old", userId = UID, budgetLocalId = BASE,
            categoryLocalId = CATEGORY, effectiveMonth = MONTH, effectiveYear = YEAR,
            amountMinor = 1_000_000, currency = "VND", isArchived = false,
            expectedRevision = 1, actualRevision = 2, reason = "REVISION_MISMATCH", capturedAt = 1
        ))
        assertEquals(0, a.db.budgetDao().observeUnresolvedCount(UID).first())
        assertEquals(0, a.db.budgetDao().observeUnresolvedCount("other-user").first())
    }

    @Test fun cloudPullCannotReplaceCurrentMonthTombstoneWithAnotherActiveId() = runBlocking {
        val deleted = row(A_ID, 0, PendingOperation.NONE, archived = true)
        a.insert(deleted)
        cloud[UID]!![B_ID] = cloudData(row(B_ID, 2_000_000, PendingOperation.NONE))
        a.pull()
        a.pull()
        assertTrue(a.active().isEmpty())
        assertTrue(a.get(A_ID)!!.isArchived)
        assertNull(a.get(B_ID))
        assertEquals(2_000_000L, cloudAmount(B_ID))
        assertEquals(1, a.db.budgetDao().observeUnresolvedCount(UID).first())
        assertEquals(1, a.db.budgetConflictDao().getForBudget(UID, A_ID).size)
    }

    @Test fun confirmedBud05A_createCreateUploadAThenBLeavesTwoCloudDocuments() = runBlocking {
        createCreate(first = a, second = b)
    }

    @Test fun confirmedBud05A_createCreateUploadBThenALeavesTwoCloudDocuments() = runBlocking {
        createCreate(first = b, second = a)
    }

    @Test fun confirmedBud05A_pullBeforePendingCreatesStillLeavesTwoCloudDocuments() = runBlocking {
        a.insert(row(A_ID, 2_000_000, PendingOperation.CREATE))
        b.insert(row(B_ID, 3_000_000, PendingOperation.CREATE))
        a.pull()
        b.pull()
        assertEquals(PendingOperation.CREATE, a.get(A_ID)!!.pendingOperation)
        assertEquals(PendingOperation.CREATE, b.get(B_ID)!!.pendingOperation)
        a.upload()
        b.upload()
        settle()
        assertEquals("CONFIRMED BUD-05-A: two cloud documents share one business key", 2, cloudRows().size)
        assertEquals(b.active().single(), a.active().single())
    }

    @Test fun roomUniqueKeyRejectsTwoIdsForSameOwnerCategoryAndMonth() = runBlocking {
        a.insert(row(A_ID, 2_000_000, PendingOperation.CREATE))
        try {
            a.insert(row(B_ID, 3_000_000, PendingOperation.CREATE))
            fail("Expected Room unique constraint")
        } catch (_: SQLiteConstraintException) { }
        assertEquals(A_ID, a.db.budgetDao().getBudgetRowForExactMonth(UID, CATEGORY, YEAR, MONTH)!!.localId)
        a.insert(row(B_ID, 3_000_000, PendingOperation.CREATE).copy(userId = "other-user"))
        assertEquals(B_ID,
            a.db.budgetDao().getBudgetRowForExactMonth("other-user", CATEGORY, YEAR, MONTH)!!.localId)
        assertTrue(cloudRows().isEmpty())
    }

    @Test fun pendingLocalCreateSurvivesPullOfCompetingCloudIdThenDuplicatesOnUpload() = runBlocking {
        a.insert(row(A_ID, 2_000_000, PendingOperation.CREATE))
        b.insert(row(B_ID, 3_000_000, PendingOperation.CREATE))
        b.upload()
        a.pull()
        assertEquals(A_ID, a.db.budgetDao().getBudgetRowForExactMonth(UID, CATEGORY, YEAR, MONTH)!!.localId)
        assertEquals(PendingOperation.CREATE, a.get(A_ID)!!.pendingOperation)
        assertNull(a.get(B_ID))
        a.upload()
        assertEquals("CONFIRMED BUD-05-A: pending guard avoids local loss but cannot prevent cloud duplicate",
            2, cloudRows().size)
    }

    private suspend fun createCreate(first: Device, second: Device) {
        a.insert(row(A_ID, 2_000_000, PendingOperation.CREATE))
        b.insert(row(B_ID, 3_000_000, PendingOperation.CREATE))
        first.upload()
        second.upload()
        assertEquals(2, cloudRows().size)
        settle()
        val aVisible = a.active().single()
        val bVisible = b.active().single()
        assertEquals(bVisible, aVisible)
        assertEquals(3_000_000L, aVisible.amountMinor)
        assertEquals(PendingOperation.NONE, aVisible.pendingOperation)
        assertEquals(2, cloudRows().size)
        // A second complete pull is idempotent in the visible result but cannot clean cloud duplicates.
        settle()
        assertEquals(aVisible, a.active().single())
        assertEquals(bVisible, b.active().single())
        assertEquals("CONFIRMED BUD-05-A: repeated sync retains duplicate documents", 2, cloudRows().size)
    }

    @Test fun updateUpdateAThenBRejectsStaleSecondWrite() = runBlocking {
        updateUpdate(a, b, 2_000_000)
    }

    @Test fun updateUpdateBThenARejectsStaleSecondWrite() = runBlocking {
        updateUpdate(b, a, 3_000_000)
    }

    private suspend fun updateUpdate(first: Device, second: Device, finalAmount: Long) {
        seedSharedBase()
        a.update(BASE, 2_000_000)
        b.update(BASE, 3_000_000)
        a.pull()
        b.pull()
        assertEquals(2_000_000L, a.get(BASE)!!.amountMinor)
        assertEquals(3_000_000L, b.get(BASE)!!.amountMinor)
        assertEquals(PendingOperation.UPDATE, a.get(BASE)!!.pendingOperation)
        assertEquals(PendingOperation.UPDATE, b.get(BASE)!!.pendingOperation)
        first.upload()
        second.upload()
        settle()
        assertEquals(finalAmount, cloudAmount(BASE))
        assertEquals(2_000_000L, a.get(BASE)!!.amountMinor)
        assertEquals(3_000_000L, b.get(BASE)!!.amountMinor)
        assertEquals(PendingOperation.NONE, a.get(BASE)!!.pendingOperation)
        assertEquals(PendingOperation.NONE, b.get(BASE)!!.pendingOperation)
        assertEquals(1, cloudRows().size)
        assertEquals(1, second.db.budgetConflictDao().getForBudget(UID, BASE).size)
    }

    @Test fun staleUpdateCannotResurrectArchivedBudget() = runBlocking {
        seedSharedBase()
        a.archive(BASE)
        b.update(BASE, 2_000_000)
        a.pull()
        b.pull()
        assertTrue(a.get(BASE)!!.isArchived)
        assertEquals(PendingOperation.UPDATE, b.get(BASE)!!.pendingOperation)
        a.upload()
        assertTrue(cloudArchived(BASE))
        b.upload()
        settle()
        assertTrue(cloudArchived(BASE))
        assertTrue(a.active().isEmpty())
        assertEquals(2_000_000L, b.active().single().amountMinor)
        assertEquals(2L, cloudRevision(BASE))
        assertEquals(1, cloudRows().size)
        assertTrue(b.db.budgetDao().getPendingBudgetsForSync(UID).isEmpty())
        val conflict = b.db.budgetConflictDao().getForBudget(UID, BASE).single()
        assertEquals(2_000_000L, conflict.amountMinor)
        assertEquals(1L, conflict.expectedRevision)
        assertEquals(2L, conflict.actualRevision)
        assertTrue(b.db.budgetConflictDao().observeForUser("other-user").first().isEmpty())
        settle()
        assertTrue(cloudArchived(BASE))
        assertTrue(a.active().isEmpty())
        assertEquals(2_000_000L, b.active().single().amountMinor)
        assertEquals(2L, cloudRevision(BASE))
    }

    @Test fun legacyCloudWithoutRevisionUpgradesOnDeleteAndRejectsStaleUpdate() = runBlocking {
        val legacy = row(BASE, 1_000_000, PendingOperation.NONE).copy(cloudRevision = 0)
        cloud[UID]!![BASE] = cloudData(legacy).toMutableMap().apply {
            remove("revision")
            remove("lastOperationId")
        }
        a.insert(legacy)
        b.insert(legacy)
        a.archive(BASE)
        b.update(BASE, 2_000_000)
        a.upload()
        assertEquals(1L, cloudRevision(BASE))
        assertTrue(cloudArchived(BASE))
        b.upload()
        settle()
        assertTrue(a.active().isEmpty())
        assertEquals(2_000_000L, b.active().single().amountMinor)
        assertEquals(1L, cloudRevision(BASE))
        assertEquals(2_000_000L, b.db.budgetConflictDao().getForBudget(UID, BASE).single().amountMinor)
    }

    @Test fun legacyPendingUpdateWithUnknownBaseIsPreservedAsConflict() = runBlocking {
        seedSharedBase()
        val pending = b.get(BASE)!!.copy(amountMinor = 2_000_000,
            syncStatus = SyncStatus.PENDING, pendingOperation = PendingOperation.UPDATE,
            baseRevision = null, cloudRevision = null, pendingUploadId = "migrated-op",
            updatedAt = 101)
        assertEquals(1, b.db.budgetDao().updateBudget(pending))
        b.upload()
        assertEquals(1_000_000L, cloudAmount(BASE))
        assertEquals(2_000_000L, b.get(BASE)!!.amountMinor)
        assertEquals(PendingOperation.NONE, b.get(BASE)!!.pendingOperation)
        val saved = b.db.budgetConflictDao().getForBudget(UID, BASE).single()
        assertEquals(2_000_000L, saved.amountMinor)
        assertEquals("UNKNOWN_BASE_REVISION", saved.reason)
        b.upload()
        assertEquals(1, b.db.budgetConflictDao().getForBudget(UID, BASE).size)
    }

    @Test fun legacyPendingEditWithVerifiedServerTimestampUpgradesRevision() = runBlocking {
        val original = row(BASE, 1_000_000, PendingOperation.NONE)
        val legacyCloud = cloudData(original).toMutableMap().apply {
            remove("revision")
            remove("lastOperationId")
        }
        cloud[UID]!![BASE] = legacyCloud
        a.insert(original.copy(amountMinor = 2_000_000, updatedAt = 101,
            remoteUpdatedAt = legacyCloud["updatedAt"] as Long,
            syncStatus = SyncStatus.PENDING, pendingOperation = PendingOperation.UPDATE,
            cloudRevision = null, baseRevision = null, pendingUploadId = "legacy-edit"))
        a.upload()
        assertEquals(1L, cloudRevision(BASE))
        assertEquals(2_000_000L, cloudAmount(BASE))
        assertEquals(PendingOperation.NONE, a.get(BASE)!!.pendingOperation)
        assertTrue(a.db.budgetConflictDao().getForBudget(UID, BASE).isEmpty())
    }

    @Test fun terminalConflictKeepsDeleteIntentAndResolvesOnlyAfterExactCloudMatch() = runBlocking {
        seedSharedBase()
        a.archive(BASE)
        b.update(BASE, 2_000_000)
        b.upload()
        a.upload()
        repeat(2) { a.pull(); a.upload() }
        assertTrue(a.get(BASE)!!.isArchived)
        assertTrue(a.active().isEmpty())
        assertEquals(1, a.db.budgetDao().observeUnresolvedCount(UID).first())
        assertEquals(1, a.db.budgetConflictDao().getForBudget(UID, BASE).size)
        assertFalse(cloudArchived(BASE))
        cloud[UID]!![BASE] = cloud[UID]!![BASE]!!.toMutableMap().apply {
            put("isArchived", true)
            put("amountMinor", 1_000_000L)
            put("revision", 3L)
            put("updatedAt", ++serverTime)
        }
        a.pull()
        assertEquals(0, a.db.budgetDao().observeUnresolvedCount(UID).first())
        assertTrue(a.active().isEmpty())
        assertEquals(1, a.db.budgetConflictDao().getForBudget(UID, BASE).size)
    }

    @Test fun deleteAfterCommittedUpdateBecomesConflictWithoutOverwritingCloud() = runBlocking {
        seedSharedBase()
        a.archive(BASE)
        b.update(BASE, 2_000_000)
        b.upload()
        a.upload()
        settle()
        assertFalse(cloudArchived(BASE))
        assertTrue(a.get(BASE)!!.isArchived)
        assertTrue(a.active().isEmpty())
        assertEquals(1, a.db.budgetDao().observeUnresolvedCount(UID).first())
        assertFalse(b.get(BASE)!!.isArchived)
        assertEquals(2L, cloudRevision(BASE))
        assertTrue(a.db.budgetConflictDao().getForBudget(UID, BASE).single().isArchived)
    }

    @Test fun deleteDeleteIsIdempotent() = runBlocking {
        seedSharedBase()
        a.archive(BASE)
        b.archive(BASE)
        a.upload()
        b.upload()
        settle()
        assertEquals(1, cloudRows().size)
        assertTrue(cloudArchived(BASE))
        assertTrue(a.active().isEmpty())
        assertTrue(b.active().isEmpty())
        assertTrue(a.db.budgetDao().getPendingBudgetsForSync(UID).isEmpty())
        assertTrue(b.db.budgetDao().getPendingBudgetsForSync(UID).isEmpty())
    }

    @Test fun createThenDeleteBeforeUploadSendsOnlyTombstone() = runBlocking {
        a.insert(row(A_ID, 2_000_000, PendingOperation.CREATE))
        a.archive(A_ID)
        a.upload()
        b.pull()
        assertEquals(1, cloudRows().size)
        assertTrue(cloudArchived(A_ID))
        assertTrue(a.active().isEmpty())
        assertTrue(b.active().isEmpty())
    }

    @Test fun failedUploadBeforeCommitRetainsPendingAndRetriesSameDocumentId() = runBlocking {
        a.insert(row(A_ID, 2_000_000, PendingOperation.CREATE))
        failBeforeCommitOnce += UID to A_ID
        try { a.upload(); fail("Expected network failure") } catch (_: IOException) { }
        assertTrue(cloudRows().isEmpty())
        assertEquals(PendingOperation.CREATE, a.get(A_ID)!!.pendingOperation)
        assertEquals(SyncStatus.FAILED, a.get(A_ID)!!.syncStatus)
        a.upload()
        assertEquals(1, cloudRows().size)
        assertEquals(PendingOperation.NONE, a.get(A_ID)!!.pendingOperation)
    }

    @Test fun lostAckRetryDoesNotApplyTheSameOperationTwice() = runBlocking {
        seedSharedBase()
        a.update(BASE, 2_000_000)
        b.update(BASE, 3_000_000)
        failAfterCommitOnce += UID to BASE
        try { a.upload(); fail("Expected lost acknowledgement") } catch (_: IOException) { }
        assertEquals(2_000_000L, cloudAmount(BASE))
        assertEquals(PendingOperation.UPDATE, a.get(BASE)!!.pendingOperation)
        assertEquals(SyncStatus.FAILED, a.get(BASE)!!.syncStatus)
        b.upload()
        assertEquals(2_000_000L, cloudAmount(BASE))
        a.upload()
        settle()
        assertEquals(2_000_000L, cloudAmount(BASE))
        assertEquals(2L, cloudRevision(BASE))
        assertEquals(2_000_000L, a.get(BASE)!!.amountMinor)
        assertEquals(3_000_000L, b.get(BASE)!!.amountMinor)
        assertEquals(3_000_000L, b.db.budgetConflictDao().getForBudget(UID, BASE).single().amountMinor)
        assertEquals(1, cloudRows().size)
    }

    @Test fun lostAckImmediateRetryAcknowledgesOneCommitOnly() = runBlocking {
        seedSharedBase()
        a.update(BASE, 2_000_000)
        failAfterCommitOnce += UID to BASE
        try { a.upload(); fail("Expected lost ACK") } catch (_: IOException) { }
        assertEquals(2L, cloudRevision(BASE))
        assertEquals(PendingOperation.UPDATE, a.get(BASE)!!.pendingOperation)
        a.upload()
        assertEquals(2L, cloudRevision(BASE))
        assertEquals(PendingOperation.NONE, a.get(BASE)!!.pendingOperation)
        assertNull(a.get(BASE)!!.remoteUpdatedAt)
        settle()
        assertEquals(2L, a.get(BASE)!!.cloudRevision)
        assertEquals(2L, b.get(BASE)!!.cloudRevision)
        assertEquals(2_000_000L, b.get(BASE)!!.amountMinor)
    }

    @Test fun lostAckThenNewerCommitRejectsStaleRetryAndConverges() = runBlocking {
        seedSharedBase()
        a.update(BASE, 2_000_000)
        failAfterCommitOnce += UID to BASE
        try { a.upload(); fail("Expected lost ACK") } catch (_: IOException) { }
        assertEquals(2L, cloudRevision(BASE))
        b.pull()
        b.update(BASE, 3_000_000)
        b.upload()
        assertEquals(3L, cloudRevision(BASE))
        a.upload()
        settle()
        assertEquals(3_000_000L, cloudAmount(BASE))
        assertEquals(2_000_000L, a.get(BASE)!!.amountMinor)
        assertEquals(3_000_000L, b.get(BASE)!!.amountMinor)
        assertEquals(1L, a.get(BASE)!!.cloudRevision)
        assertEquals(3L, b.get(BASE)!!.cloudRevision)
        assertTrue(a.db.budgetDao().getPendingBudgetsForSync(UID).isEmpty())
        assertEquals(2_000_000L, a.db.budgetConflictDao().getForBudget(UID, BASE).single().amountMinor)
        settle()
        assertEquals(3L, cloudRevision(BASE))
        assertEquals(1, cloudRows().size)
    }

    @Test fun failedBudgetPullLeavesLocalCleanRowUntouchedForRetry() = runBlocking {
        seedSharedBase()
        cloud[UID]!![BASE] = cloud[UID]!![BASE]!!.toMutableMap().apply {
            put("amountMinor", 2_000_000L)
            put("revision", 2L)
            put("updatedAt", ++serverTime)
        }
        failPullOnce += UID
        try { a.pull(); fail("Expected incomplete cloud pull") }
        catch (_: CloudPullService.CloudPullException) { }
        assertEquals(1_000_000L, a.get(BASE)!!.amountMinor)
        a.pull()
        assertEquals(2_000_000L, a.get(BASE)!!.amountMinor)
    }

    @Test fun accountSwitchStopsOldUidUploadAndLeavesPendingForLoginAgain() = runBlocking {
        a.insert(row(A_ID, 2_000_000, PendingOperation.CREATE))
        a.currentUid = "other-user"
        try { a.upload(); fail("Expected session rejection") } catch (_: CancellationException) { }
        assertTrue(cloudRows().isEmpty())
        assertEquals(PendingOperation.CREATE, a.get(A_ID)!!.pendingOperation)
        a.currentUid = UID
        a.upload()
        assertEquals(2_000_000L, cloudAmount(A_ID))
        assertTrue(cloud["other-user"].isNullOrEmpty())
    }

    @Test fun uidSwitchAfterCloudCommitCannotAcknowledgeOldSession() = runBlocking {
        a.insert(row(A_ID, 2_000_000, PendingOperation.CREATE))
        val started = CompletableDeferred<Unit>()
        val response = TaskCompletionSource<Long>()
        pauseAfterCommit = started to response
        val upload = async {
            try { a.upload(); fail("Expected session rejection") }
            catch (_: CancellationException) { }
        }
        withTimeout(5_000) { started.await() }
        a.currentUid = "other-user"
        response.setResult(1L)
        upload.await()
        assertEquals(1L, cloudRevision(A_ID))
        assertEquals(PendingOperation.CREATE, a.get(A_ID)!!.pendingOperation)
        assertTrue(cloud["other-user"].isNullOrEmpty())
        a.currentUid = UID
        a.upload()
        assertEquals(PendingOperation.NONE, a.get(A_ID)!!.pendingOperation)
        assertEquals(1L, cloudRevision(A_ID))
    }

    @Test fun revisionPullIgnoresClientClockAheadOfLaterServerCommit() = runBlocking {
        seedSharedBase()
        a.update(BASE, 2_000_000)
        a.upload()
        // Simulate metadata left by an older client with a clock ahead of the server.
        val clean = a.get(BASE)!!
        val ahead = clean.copy(remoteUpdatedAt = 2_100_000_000_000L)
        assertEquals(1, a.db.budgetDao().updateBudget(ahead))
        b.pull()
        b.update(BASE, 3_000_000)
        b.upload()
        repeat(3) { a.pull(); b.pull() }
        assertEquals(3_000_000L, cloudAmount(BASE))
        assertEquals(3_000_000L, a.get(BASE)!!.amountMinor)
        assertEquals(3_000_000L, b.get(BASE)!!.amountMinor)
        assertEquals(PendingOperation.NONE, a.get(BASE)!!.pendingOperation)
    }

    @Test fun revisionPullIgnoresClientClockBehindAndTimezoneChange() = runBlocking {
        seedSharedBase()
        a.update(BASE, 2_000_000)
        a.upload()
        b.pull()
        b.update(BASE, 3_000_000)
        b.upload()
        assertEquals(1, a.db.budgetDao().updateBudget(a.get(BASE)!!.copy(remoteUpdatedAt = 1L)))
        val originalZone = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Pacific/Honolulu"))
            a.pull()
        } finally {
            java.util.TimeZone.setDefault(originalZone)
        }
        settle()
        assertEquals(3L, cloudRevision(BASE))
        assertEquals(3L, a.get(BASE)!!.cloudRevision)
        assertEquals(3L, b.get(BASE)!!.cloudRevision)
        assertEquals(3_000_000L, a.get(BASE)!!.amountMinor)
        assertEquals(3_000_000L, b.get(BASE)!!.amountMinor)
    }

    @Test fun newerRevisionWinsEvenWhenServerTimestampEqualsLocalTimestamp() = runBlocking {
        seedSharedBase()
        val equalTime = a.get(BASE)!!.remoteUpdatedAt!!
        cloud[UID]!![BASE] = cloud[UID]!![BASE]!!.toMutableMap().apply {
            put("amountMinor", 2_000_000L)
            put("revision", 2L)
            put("updatedAt", equalTime)
        }
        a.pull()
        b.pull()
        assertEquals(2L, a.get(BASE)!!.cloudRevision)
        assertEquals(2L, b.get(BASE)!!.cloudRevision)
        assertEquals(2_000_000L, a.get(BASE)!!.amountMinor)
        assertEquals(2_000_000L, b.get(BASE)!!.amountMinor)
    }

    @Test fun olderCloudRevisionCannotRollbackCleanLocalBudget() = runBlocking {
        seedSharedBase()
        cloud[UID]!![BASE] = cloud[UID]!![BASE]!!.toMutableMap().apply {
            put("amountMinor", 9_000_000L)
            put("revision", 0L)
            put("updatedAt", ++serverTime)
        }
        a.pull()
        assertEquals(1L, a.get(BASE)!!.cloudRevision)
        assertEquals(1_000_000L, a.get(BASE)!!.amountMinor)
        assertTrue(a.db.budgetDao().getPendingBudgetsForSync(UID).isEmpty())
        assertEquals("CLOUD_REVISION_BEHIND_LOCAL",
            a.db.budgetConflictDao().getForBudget(UID, BASE).single().reason)
    }

    @Test fun equalRevisionDifferentContentIsRecordedWithoutChoosingByTimestamp() = runBlocking {
        seedSharedBase()
        cloud[UID]!![BASE] = cloud[UID]!![BASE]!!.toMutableMap().apply {
            put("amountMinor", 9_000_000L)
            put("updatedAt", ++serverTime)
        }
        repeat(2) { a.pull() }
        assertEquals(1_000_000L, a.get(BASE)!!.amountMinor)
        assertEquals(1L, a.get(BASE)!!.cloudRevision)
        val conflict = a.db.budgetConflictDao().getForBudget(UID, BASE).single()
        assertEquals("SAME_REVISION_DIVERGENCE", conflict.reason)
        assertEquals(1L, conflict.actualRevision)
        assertEquals(1_000_000L, conflict.amountMinor)
    }

    @Test fun newerCloudRevisionDoesNotOverwritePendingBudgetBeforeConflictHandling() = runBlocking {
        seedSharedBase()
        a.update(BASE, 2_000_000)
        cloud[UID]!![BASE] = cloud[UID]!![BASE]!!.toMutableMap().apply {
            put("amountMinor", 3_000_000L)
            put("revision", 2L)
            put("updatedAt", ++serverTime)
        }
        a.pull()
        assertEquals(2_000_000L, a.get(BASE)!!.amountMinor)
        assertEquals(PendingOperation.UPDATE, a.get(BASE)!!.pendingOperation)
        a.upload()
        assertEquals(3_000_000L, cloudAmount(BASE))
        assertEquals(2_000_000L, a.get(BASE)!!.amountMinor)
        assertEquals(2_000_000L, a.db.budgetConflictDao().getForBudget(UID, BASE).single().amountMinor)
    }

    @Test fun pullFetchedBeforeLocalEditCannotOverwriteTheNewPendingRow() = runBlocking {
        seedSharedBase()
        cloud[UID]!![BASE] = cloud[UID]!![BASE]!!.toMutableMap().apply {
            put("amountMinor", 3_000_000L)
            put("revision", 2L)
            put("updatedAt", ++serverTime)
        }
        val fetched = CompletableDeferred<Unit>()
        val response = TaskCompletionSource<QuerySnapshot>()
        pauseBudgetPull = fetched to response
        val pull = async { a.pull() }
        withTimeout(5_000) { fetched.await() }
        a.update(BASE, 4_000_000)
        response.setResult(requireNotNull(heldBudgetSnapshot))
        pull.await()
        assertEquals(4_000_000L, a.get(BASE)!!.amountMinor)
        assertEquals(PendingOperation.UPDATE, a.get(BASE)!!.pendingOperation)
        a.upload()
        assertEquals(3_000_000L, cloudAmount(BASE))
        assertEquals(4_000_000L, a.db.budgetConflictDao().getForBudget(UID, BASE).single().amountMinor)
    }

    @Test fun cleanLegacyRowWithUnknownRevisionReceivesCloudWithoutClockGuess() = runBlocking {
        seedSharedBase()
        assertEquals(1, a.db.budgetDao().updateBudget(a.get(BASE)!!.copy(
            cloudRevision = null, remoteUpdatedAt = 2_100_000_000_000L)))
        a.pull()
        assertEquals(1L, a.get(BASE)!!.cloudRevision)
        assertEquals(1_000_000L, a.get(BASE)!!.amountMinor)
        assertTrue(a.db.budgetDao().getPendingBudgetsForSync(UID).isEmpty())
    }

    @Test fun conflictSnapshotRemainsAfterMainRowReceivesLaterCloudRevision() = runBlocking {
        seedSharedBase()
        a.update(BASE, 2_000_000)
        b.update(BASE, 3_000_000)
        a.upload()
        b.upload()
        assertEquals(3_000_000L, b.db.budgetConflictDao().getForBudget(UID, BASE).single().amountMinor)
        a.update(BASE, 4_000_000)
        a.upload()
        b.pull()
        assertEquals(3L, cloudRevision(BASE))
        assertEquals(1L, b.get(BASE)!!.cloudRevision)
        assertEquals(3_000_000L, b.get(BASE)!!.amountMinor)
        assertEquals(3_000_000L, b.db.budgetConflictDao().getForBudget(UID, BASE).single().amountMinor)
    }

    @Test fun newerLocalEditKeepsPendingAndInheritsConfirmedBaseAfterOlderUpload() = runBlocking {
        seedSharedBase()
        a.update(BASE, 2_000_000)
        val started = CompletableDeferred<Unit>()
        val response = TaskCompletionSource<Long>()
        pauseAfterCommit = started to response
        val upload = async { a.upload() }
        withTimeout(5_000) { started.await() }
        a.update(BASE, 4_000_000)
        response.setResult(2L)
        upload.await()
        assertEquals(2_000_000L, cloudAmount(BASE))
        assertEquals(4_000_000L, a.get(BASE)!!.amountMinor)
        assertEquals(PendingOperation.UPDATE, a.get(BASE)!!.pendingOperation)
        assertEquals(2L, a.get(BASE)!!.baseRevision)
        assertEquals(2L, a.get(BASE)!!.cloudRevision)
        a.upload()
        assertEquals(4_000_000L, cloudAmount(BASE))
        assertEquals(3L, cloudRevision(BASE))
        assertEquals(PendingOperation.NONE, a.get(BASE)!!.pendingOperation)
        assertTrue(a.db.budgetConflictDao().getForBudget(UID, BASE).isEmpty())
    }

    @Test fun editDuringFirstCreateBecomesVersionedUpdateAfterCreateCommits() = runBlocking {
        a.insert(row(A_ID, 2_000_000, PendingOperation.CREATE))
        val started = CompletableDeferred<Unit>()
        val response = TaskCompletionSource<Long>()
        pauseAfterCommit = started to response
        val upload = async { a.upload() }
        withTimeout(5_000) { started.await() }
        a.update(A_ID, 3_000_000)
        response.setResult(1L)
        upload.await()
        val pending = a.get(A_ID)!!
        assertEquals(PendingOperation.UPDATE, pending.pendingOperation)
        assertEquals(1L, pending.baseRevision)
        a.upload()
        b.pull()
        assertEquals(2L, cloudRevision(A_ID))
        assertEquals(3_000_000L, cloudAmount(A_ID))
        assertEquals(3_000_000L, b.get(A_ID)!!.amountMinor)
        assertTrue(a.db.budgetConflictDao().getForBudget(UID, A_ID).isEmpty())
    }

    @Test fun sameBudgetDocumentIdAcrossUidCannotCrossOverwriteButBlocksOtherUidPull() = runBlocking {
        seedSharedBase()
        configureCollection("other-user")
        val other = row(BASE, 9_000_000, PendingOperation.NONE).copy(userId = "other-user")
        cloud["other-user"]!![BASE] = cloudData(other)
        a.currentUid = "other-user"
        try { a.pull("other-user"); fail("Expected global localId collision") }
        catch (_: IllegalStateException) { }
        assertEquals(UID, a.get(BASE)!!.userId)
        assertEquals(1_000_000L, a.get(BASE)!!.amountMinor)
        assertNull(a.db.budgetDao().getBudgetRowForExactMonth("other-user", CATEGORY, YEAR, MONTH))
    }

    private suspend fun seedSharedBase() {
        val base = row(BASE, 1_000_000, PendingOperation.NONE)
        cloud[UID]!![BASE] = cloudData(base)
        a.insert(base)
        b.insert(base)
    }

    private suspend fun settle() {
        repeat(2) {
            a.pull()
            b.pull()
            a.upload()
            b.upload()
        }
    }

    private fun row(id: String, amount: Long, operation: PendingOperation,
                    archived: Boolean = false) = BudgetEntity(
        localId = id, userId = UID, categoryLocalId = CATEGORY, amountMinor = amount,
        currency = "VND", effectiveMonth = MONTH, effectiveYear = YEAR, isArchived = archived,
        syncStatus = if (operation == PendingOperation.NONE) SyncStatus.SYNCED else SyncStatus.PENDING,
        pendingOperation = operation, createdAt = 100, updatedAt = 100,
        remoteUpdatedAt = if (operation == PendingOperation.NONE) serverTime else null,
        cloudRevision = if (operation == PendingOperation.NONE) 1L else null,
        pendingUploadId = if (operation == PendingOperation.NONE) null else "$id-create"
    )

    private fun cloudData(row: BudgetEntity): Map<String, Any> = mapOf(
        "categoryLocalId" to row.categoryLocalId, "amountMinor" to row.amountMinor,
        "currency" to row.currency, "effectiveMonth" to row.effectiveMonth,
        "effectiveYear" to row.effectiveYear, "isArchived" to row.isArchived,
        "createdAt" to row.createdAt, "updatedAt" to ++serverTime,
        "revision" to 1L, "lastOperationId" to "seed-${row.localId}"
    )

    private fun cloudRows(owner: String = UID) = cloud[owner].orEmpty()
    private fun cloudAmount(id: String) = cloudRows()[id]!!["amountMinor"] as Long
    private fun cloudArchived(id: String) = cloudRows()[id]!!["isArchived"] as Boolean
    private fun cloudRevision(id: String) = cloudRows()[id]!!["revision"] as Long

    private fun configureCollection(owner: String) {
        cloud.getOrPut(owner) { linkedMapOf() }
        for (id in listOf(A_ID, B_ID, BASE)) {
            val ref = firestore.collection("users").document(owner).collection("budgets").document(id)
            `when`(ref.path).thenReturn("users/$owner/budgets/$id")
        }
        for (name in listOf("categories", "transactions", "budgets", "reminders", "recurring_transactions")) {
            val collection = firestore.collection("users").document(owner).collection(name)
            `when`(collection.get(Source.SERVER)).thenAnswer {
                if (name == "budgets" && failPullOnce.remove(owner)) {
                    Tasks.forException<QuerySnapshot>(IOException("budget pull unavailable"))
                } else {
                    val snapshot = mock(QuerySnapshot::class.java)
                    val docs = if (name == "budgets") cloudRows(owner).toSortedMap().map { (id, data) ->
                        val doc = mock(DocumentSnapshot::class.java, RETURNS_DEEP_STUBS)
                        `when`(doc.id).thenReturn(id)
                        `when`(doc.data).thenReturn(data)
                        `when`(doc.reference.path).thenReturn("users/$owner/budgets/$id")
                        doc
                    } else emptyList()
                    `when`(snapshot.documents).thenReturn(docs)
                    val gate = if (name == "budgets") pauseBudgetPull else null
                    if (gate != null) {
                        pauseBudgetPull = null
                        heldBudgetSnapshot = snapshot
                        gate.first.complete(Unit)
                        gate.second.task
                    } else Tasks.forResult(snapshot)
                }
            }
        }
    }

    private fun configureBudgetTransactions() {
        `when`(firestore.runTransaction(any<Transaction.Function<Long>>())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val action = invocation.arguments[0] as Transaction.Function<Long>
            val transaction = mock(Transaction::class.java)
            var target: Pair<String, String>? = null
            var staged: BudgetDto? = null
            `when`(transaction.get(any())).thenAnswer { call ->
                val path = (call.arguments[0] as com.google.firebase.firestore.DocumentReference).path
                val parts = path.split('/')
                val owner = parts[1]
                val id = parts[3]
                target = owner to id
                val data = cloud[owner]?.get(id)
                val snapshot = mock(DocumentSnapshot::class.java, RETURNS_DEEP_STUBS)
                `when`(snapshot.exists()).thenReturn(data != null)
                `when`(snapshot.data).thenReturn(data)
                `when`(snapshot.id).thenReturn(id)
                `when`(snapshot.reference.path).thenReturn(path)
                snapshot
            }
            `when`(transaction.set(any(), any<Any>())).thenAnswer { call ->
                staged = call.arguments[1] as BudgetDto
                transaction
            }
            try {
                val result = action.apply(transaction)
                val key = requireNotNull(target)
                if (failBeforeCommitOnce.remove(key)) {
                    Tasks.forException<Long>(IOException("offline before commit"))
                } else {
                    staged?.let { dto ->
                        cloud.getOrPut(key.first) { linkedMapOf() }[key.second] = mapOf(
                            "categoryLocalId" to dto.categoryLocalId,
                            "amountMinor" to dto.amountMinor, "currency" to dto.currency,
                            "effectiveMonth" to dto.effectiveMonth,
                            "effectiveYear" to dto.effectiveYear,
                            "isArchived" to dto.isArchived, "createdAt" to dto.createdAt,
                            "revision" to requireNotNull(dto.revision),
                            "lastOperationId" to requireNotNull(dto.lastOperationId),
                            "recreatedFromTombstone" to dto.recreatedFromTombstone,
                            "updatedAt" to ++serverTime
                        )
                    }
                    if (failAfterCommitOnce.remove(key)) Tasks.forException<Long>(IOException("ACK lost"))
                    else pauseAfterCommit?.let { (started, response) ->
                        pauseAfterCommit = null
                        started.complete(Unit)
                        response.task
                    } ?: Tasks.forResult(result)
                }
            } catch (error: Exception) {
                Tasks.forException<Long>(error)
            }
        }
    }

    private inner class Device(val name: String) {
        var currentUid = UID
        val db: AppDatabase = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), AppDatabase::class.java
        ).build()
        val auth: AuthRepository = mock(AuthRepository::class.java)
        private val sessions = SyncSessionGuard(auth, AuthSessionVersion())
        private val runner = PendingUploadRunner(db, sessions)
        private val repository = BudgetRepositoryImpl(db.budgetDao(), db.transactionDao(),
            db.categoryDao(), firestore, trigger, runner, db.budgetConflictDao())
        private val cloudPull = CloudPullService(firestore, auth, db, db.categoryDao(),
            db.transactionDao(), db.budgetDao(), db.reminderDao(), db.recurringTransactionDao(), sessions)

        init { `when`(auth.getCurrentUserId()).thenAnswer { currentUid } }

        suspend fun insert(row: BudgetEntity) = db.budgetDao().insertBudget(row)
        suspend fun get(id: String) = db.budgetDao().getRowForCloudMerge(id)
        suspend fun active() = db.budgetDao().getActiveBudgetsForMonth(UID, YEAR, MONTH).first()
        suspend fun upload() = repository.syncPendingBudgets(UID)
        suspend fun pull(userId: String = UID) = cloudPull.pull(userId)
        suspend fun deleteVisible(expectedId: String) = repository.deleteBudget(UID, CATEGORY, expectedId)

        suspend fun update(id: String, amount: Long) {
            val old = requireNotNull(get(id))
            assertEquals(1, db.budgetDao().updateBudget(old.copy(amountMinor = amount,
                isArchived = false, syncStatus = SyncStatus.PENDING,
                pendingOperation = if (old.pendingOperation == PendingOperation.CREATE)
                    PendingOperation.CREATE else PendingOperation.UPDATE,
                updatedAt = old.updatedAt + 1,
                baseRevision = old.baseRevision ?: old.cloudRevision,
                pendingUploadId = UUID.randomUUID().toString(),
                pendingRecreate = old.isArchived)))
        }

        suspend fun archive(id: String) {
            val old = requireNotNull(get(id))
            assertEquals(1, db.budgetDao().updateBudget(old.copy(isArchived = true,
                syncStatus = SyncStatus.PENDING,
                pendingOperation = if (old.pendingOperation == PendingOperation.CREATE)
                    PendingOperation.CREATE else PendingOperation.UPDATE,
                updatedAt = old.updatedAt + 1,
                baseRevision = old.baseRevision ?: old.cloudRevision,
                pendingUploadId = UUID.randomUUID().toString(), pendingRecreate = false)))
        }
    }
}
