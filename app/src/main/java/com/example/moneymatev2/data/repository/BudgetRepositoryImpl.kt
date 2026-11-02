package com.example.moneymatev2.data.repository

import com.example.moneymatev2.data.local.dao.BudgetDao
import com.example.moneymatev2.data.local.dao.BudgetConflictDao
import com.example.moneymatev2.data.local.dao.CategoryDao
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.data.local.dao.TransactionDao
import com.example.moneymatev2.data.local.entity.BudgetEntity
import com.example.moneymatev2.data.local.entity.BudgetConflictEntity
import com.example.moneymatev2.data.local.entity.PendingOperation
import com.example.moneymatev2.data.local.entity.SyncStatus
import com.example.moneymatev2.data.remote.dto.toDto
import com.example.moneymatev2.data.remote.sync.SyncTrigger
import com.example.moneymatev2.data.remote.sync.PendingUploadRunner
import com.example.moneymatev2.data.remote.sync.syncFailureSummary
import com.example.moneymatev2.data.remote.sync.BudgetCloudRecord
import com.example.moneymatev2.data.remote.sync.BudgetVersionPolicy
import com.example.moneymatev2.data.remote.sync.BudgetWriteDecision
import com.example.moneymatev2.data.remote.sync.BudgetVersionConflictException
import com.example.moneymatev2.domain.model.BudgetModel
import com.example.moneymatev2.domain.model.Money
import com.example.moneymatev2.domain.repository.BudgetRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

class BudgetRepositoryImpl @Inject constructor(
    private val budgetDao: BudgetDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val firestore: FirebaseFirestore,
    private val syncTrigger: SyncTrigger,
    private val uploadRunner: PendingUploadRunner,
    private val conflictDao: BudgetConflictDao
): BudgetRepository {
    override fun observeConflictCount(userId: String): Flow<Int> =
        budgetDao.observeUnresolvedCount(userId)

    override fun getBudgetForMonth(userId: String, month: Int, year: Int): Flow<List<BudgetModel>> =
        budgetDao.getActiveBudgetsForMonth(userId = userId, year = year, month = month).map { budgets ->
            budgets.map { it.toModel() }
        }

    override suspend fun setBudget(
        userId: String,
        categoryId: String,
        amountMinor: Long,
        currency: String
    ) {
        require(amountMinor > 0L) { "Budget limit must be positive" }
        uploadRunner.capture(userId)
        val category = categoryDao.getCategoryByLocalIdOnce(userId, categoryId)
        require(category != null && !category.isArchived && category.type == TransactionType.EXPENSE) { "Invalid budget category" }
        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)
        val now = System.currentTimeMillis()

        val existing = budgetDao.getBudgetRowForExactMonth(userId, categoryId, year, month)
        if (existing != null) {
            check(budgetDao.updateBudget(
                existing.copy(
                    amountMinor = amountMinor,
                    currency = currency,
                    isArchived = false,
                    updatedAt = now,
                    syncStatus = SyncStatus.PENDING,
                    pendingOperation = if (existing.pendingOperation == PendingOperation.CREATE)
                        PendingOperation.CREATE else PendingOperation.UPDATE,
                    baseRevision = existing.baseRevision ?: existing.cloudRevision,
                    pendingUploadId = UUID.randomUUID().toString(),
                    pendingRecreate = existing.isArchived || existing.pendingRecreate
                )
            ) == 1) { "Budget changed before save" }
        } else {
            budgetDao.insertBudget(
                BudgetEntity(
                    localId = UUID.randomUUID().toString(),
                    userId = userId,
                    categoryLocalId = categoryId,
                    amountMinor = amountMinor,
                    currency = currency,
                    effectiveMonth = month,
                    effectiveYear = year,
                    syncStatus = SyncStatus.PENDING,
                    pendingOperation = PendingOperation.CREATE,
                    pendingUploadId = UUID.randomUUID().toString(),
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        requestSyncAfterLocalWrite()
    }

    override suspend fun deleteBudget(userId: String, categoryId: String, expectedBudgetId: String?) {
        uploadRunner.capture(userId)
        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)
        val now = System.currentTimeMillis()

        if (expectedBudgetId != null) {
            val visible = budgetDao.getActiveBudgetsForMonth(userId, year, month).first()
                .firstOrNull { it.categoryLocalId == categoryId }
            require(visible?.localId == expectedBudgetId) { "Budget changed before delete" }
        }

        val existing = budgetDao.getBudgetRowForExactMonth(userId, categoryId, year, month)
        if (existing != null) {
            check(budgetDao.updateBudget(
                existing.copy(
                    isArchived = true,
                    updatedAt = now,
                    syncStatus = SyncStatus.PENDING,
                    pendingOperation = if (existing.pendingOperation == PendingOperation.CREATE)
                        PendingOperation.CREATE else PendingOperation.UPDATE,
                    baseRevision = existing.baseRevision ?: existing.cloudRevision,
                    pendingUploadId = UUID.randomUUID().toString(),
                    pendingRecreate = false
                )
            ) == 1) { "Budget changed before delete" }
        }else{
            budgetDao.insertBudget(
                BudgetEntity(
                    localId = UUID.randomUUID().toString(),
                    userId = userId,
                    categoryLocalId = categoryId,
                    amountMinor = 0,
                    currency = "VNĐ",
                    effectiveMonth = month,
                    effectiveYear = year,
                    isArchived = true,
                    syncStatus = SyncStatus.PENDING,
                    pendingOperation = PendingOperation.CREATE,
                    pendingUploadId = UUID.randomUUID().toString(),
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        requestSyncAfterLocalWrite()
    }


    override suspend fun syncPendingBudgets(userId: String) {
        val session = uploadRunner.capture(userId)
        val pending = budgetDao.getPendingBudgetsForSync(userId)
        var firstError: Exception? = null
        for (original in pending) {
            try {
                if (original.pendingUploadId == null) {
                    budgetDao.assignUploadIdIfMissing(original.localId, userId, original.updatedAt,
                        UUID.randomUUID().toString())
                }
                val row = budgetDao.getRowForCloudMerge(original.localId) ?: continue
                if (row.userId != userId || row.pendingOperation == PendingOperation.NONE) continue
                var committedRevision: Long? = null
                uploadRunner.run(
                    session = session,
                    snapshot = row,
                    readCurrent = { budgetDao.getRowForCloudMerge(row.localId) },
                    upload = {
                        val doc = firestore.collection("users").document(userId)
                            .collection("budgets").document(row.localId)
                        committedRevision = firestore.runTransaction { transaction ->
                            val current = BudgetCloudRecord.from(transaction.get(doc))
                            when (val decision = BudgetVersionPolicy.decide(row, current)) {
                                is BudgetWriteDecision.Commit -> {
                                    transaction.set(doc, row.toDto(decision.revision,
                                        requireNotNull(row.pendingUploadId)))
                                    decision.revision
                                }
                                is BudgetWriteDecision.AlreadyCommitted -> decision.revision
                                is BudgetWriteDecision.Conflict ->
                                    throw BudgetVersionConflictException(decision.reason, current)
                            }
                        }.await()
                    },
                    acknowledge = {
                        budgetDao.acknowledgeVersionedUpload(row.localId, userId, row.updatedAt,
                            requireNotNull(row.pendingUploadId), requireNotNull(committedRevision))
                    },
                    recordFailure = { error ->
                        if (error is BudgetVersionConflictException) {
                            conflictDao.insert(BudgetConflictEntity(
                                conflictId = requireNotNull(row.pendingUploadId), userId = userId,
                                budgetLocalId = row.localId, categoryLocalId = row.categoryLocalId,
                                effectiveMonth = row.effectiveMonth, effectiveYear = row.effectiveYear,
                                amountMinor = row.amountMinor, currency = row.currency,
                                isArchived = row.isArchived, expectedRevision = row.baseRevision,
                                actualRevision = error.remote?.revision, reason = error.reason,
                                capturedAt = System.currentTimeMillis()
                            ))
                            // Stop futile retries, but retain the exact local intent in Room.
                            // In particular, replacing an archived row with an active cloud
                            // row would make a deleted budget reappear immediately.
                            check(budgetDao.updateBudget(row.copy(
                                syncStatus = SyncStatus.FAILED,
                                pendingOperation = PendingOperation.NONE,
                                lastSyncError = error.reason
                            )) == 1)
                        } else {
                            budgetDao.markSyncFailedIfUnchanged(
                                row.localId, userId, row.updatedAt,
                                error.syncFailureSummary(), System.currentTimeMillis()
                            )
                        }
                    }
                )
                // The user may have edited this row while the confirmed snapshot was uploading.
                // Keep that newer edit pending, based on the revision just committed by this device.
                committedRevision?.let { revision ->
                    uploadRunner.check(session)
                    budgetDao.advancePendingBaseAfterCommittedUpload(
                        row.localId, userId, requireNotNull(row.pendingUploadId),
                        row.baseRevision, row.cloudRevision, revision
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: BudgetVersionConflictException) {
                // Terminal conflict is stored locally; the user's edit remains visible.
                // The row is no longer retried by WorkManager.
                android.util.Log.w("BudgetSync", "Conflict saved: ${e.reason}")
            } catch (e: Exception) {
                android.util.Log.e("SyncUpload", "SYNC_FAILED collection=budgets ${e.syncFailureSummary()}")
                if (firstError == null) firstError = e else if (firstError !== e) firstError.addSuppressed(e)
            }
        }
        firstError?.let { throw it }
    }


    private fun BudgetEntity.toModel() = BudgetModel(
        id = localId,
        categoryId = categoryLocalId,
        budgetAmount = Money(amountMinor, currency),
        spentAmount = Money.zero(currency),
        effectiveMonth = effectiveMonth,
        effectiveYear = effectiveYear
    )

    private fun requestSyncAfterLocalWrite() {
        try {
            syncTrigger.requestImmediateSync()
        } catch (error: Exception) {
            // Room has already committed. The periodic worker can retry this pending row.
            android.util.Log.w("BudgetSync", "Immediate sync scheduling failed", error)
        }
    }

}
