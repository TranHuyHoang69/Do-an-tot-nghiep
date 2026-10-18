package com.example.moneymatev2.data.repository

import com.example.moneymatev2.data.local.dao.BudgetDao
import com.example.moneymatev2.data.local.dao.CategoryDao
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.data.local.dao.TransactionDao
import com.example.moneymatev2.data.local.entity.BudgetEntity
import com.example.moneymatev2.data.local.entity.PendingOperation
import com.example.moneymatev2.data.local.entity.SyncStatus
import com.example.moneymatev2.data.remote.dto.toDto
import com.example.moneymatev2.data.remote.sync.SyncTrigger
import com.example.moneymatev2.data.remote.sync.PendingUploadRunner
import com.example.moneymatev2.data.remote.sync.syncFailureSummary
import com.example.moneymatev2.domain.model.BudgetModel
import com.example.moneymatev2.domain.model.Money
import com.example.moneymatev2.domain.repository.BudgetRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
    private val uploadRunner: PendingUploadRunner
): BudgetRepository {
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
        uploadRunner.capture(userId)
        val category = categoryDao.getCategoryByLocalIdOnce(userId, categoryId)
        require(category != null && !category.isArchived && category.type == TransactionType.EXPENSE) { "Invalid budget category" }
        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)
        val now = System.currentTimeMillis()

        val existing = budgetDao.getBudgetRowForExactMonth(userId, categoryId, year, month)
        if (existing != null) {
            budgetDao.updateBudget(
                existing.copy(
                    amountMinor = amountMinor,
                    currency = currency,
                    isArchived = false,
                    updatedAt = now,
                    syncStatus = SyncStatus.PENDING,
                    pendingOperation = PendingOperation.UPDATE
                )
            )
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
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        syncTrigger.requestImmediateSync()
    }

    override suspend fun deleteBudget(userId: String, categoryId: String) {
        uploadRunner.capture(userId)
        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)
        val now = System.currentTimeMillis()

        val existing = budgetDao.getBudgetRowForExactMonth(userId, categoryId, year, month)
        if (existing != null) {
            budgetDao.updateBudget(
                existing.copy(
                    isArchived = true,
                    updatedAt = now,
                    syncStatus = SyncStatus.PENDING,
                    pendingOperation = PendingOperation.UPDATE
                )
            )
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
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        syncTrigger.requestImmediateSync()
    }


    override suspend fun syncPendingBudgets(userId: String) {
        val session = uploadRunner.capture(userId)
        val pending = budgetDao.getPendingBudgetsForSync(userId)
        var firstError: Exception? = null
        for (row in pending) {
            try {
                uploadRunner.run(
                    session = session,
                    snapshot = row,
                    readCurrent = { budgetDao.getRowForCloudMerge(row.localId) },
                    upload = {
                        val doc = firestore.collection("users").document(userId)
                            .collection("budgets").document(row.localId)
                        doc.set(row.toDto()).await()
                    },
                    acknowledge = {
                        budgetDao.markSyncedIfUnchanged(row.localId, userId, row.updatedAt, System.currentTimeMillis())
                    },
                    recordFailure = { error ->
                        budgetDao.markSyncFailedIfUnchanged(
                            row.localId, userId, row.updatedAt,
                            error.syncFailureSummary(), System.currentTimeMillis()
                        )
                    }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
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

}
