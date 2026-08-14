package com.example.moneymatev2.data.repository

import com.example.moneymatev2.data.local.dao.BudgetDao
import com.example.moneymatev2.data.local.dao.TransactionDao
import com.example.moneymatev2.data.local.entity.BudgetEntity
import com.example.moneymatev2.data.local.entity.PendingOperation
import com.example.moneymatev2.data.local.entity.SyncStatus
import com.example.moneymatev2.data.remote.dto.toDto
import com.example.moneymatev2.data.remote.sync.SyncTrigger
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
    private val firestore: FirebaseFirestore,
    private val syncTrigger: SyncTrigger
): BudgetRepository {
    override fun getBudgetForMonth(userId: String, month: Int, year: Int): Flow<List<BudgetModel>> =
        budgetDao.getActiveBudgetsForMonth(userId, month, year).map { budgets ->
            budgets.map { it.toModel() }
        }

    override suspend fun setBudget(
        userId: String,
        categoryId: String,
        amountMinor: Long,
        currency: String
    ) {
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
        val pending = budgetDao.getPendingBudgetsForSync(userId)
        for (b in pending) {
            try {
                firestore.collection("users").document(userId)
                    .collection("budgets").document(b.localId)
                    .set(b.toDto()).await()
                budgetDao.markSynced(b.localId, System.currentTimeMillis())
            }catch (e: Exception) {
                // Handle error, maybe log it or update the budget with the error message
                // For simplicity, we will just print the error here
                println("Error syncing budget ${b.localId}: ${e.message}")
            }
        }
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