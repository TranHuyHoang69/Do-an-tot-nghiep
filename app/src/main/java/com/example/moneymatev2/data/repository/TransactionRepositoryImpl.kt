package com.example.moneymatev2.data.repository

import com.example.moneymatev2.data.local.dao.TransactionDao
import com.example.moneymatev2.data.local.entity.PendingOperation
import com.example.moneymatev2.data.local.entity.SyncStatus
import com.example.moneymatev2.data.local.entity.TransactionEntity
import com.example.moneymatev2.data.remote.dto.toDto
import com.example.moneymatev2.data.remote.sync.SyncTrigger
import com.example.moneymatev2.domain.model.Money
import com.example.moneymatev2.domain.model.TransactionModel
import com.example.moneymatev2.domain.repository.TransactionRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

class TransactionRepositoryImpl @Inject constructor(
    private val dao: TransactionDao,
    private val firestore: FirebaseFirestore,
    private val syncTrigger: SyncTrigger
): TransactionRepository{
    override fun getAllTransactions(userId: String): Flow<List<TransactionModel>> =
        dao.getAllTransactions(userId).map { list -> list.map { it.toModel() } }

    override fun getTransactionByPeriod(
        userId: String,
        start: Long,
        end: Long): Flow<List<TransactionModel>> =
        dao.getTransactionByPeriod(userId, start, end).map { list -> list.map { it.toModel() } }

    override fun getTransactionById(localId: String): Flow<TransactionModel?> =
        dao.getTransactionByLocalId(localId).map { it?.toModel() }


    override fun getTransactionByCategory(
        userId: String,
        categoryId: String
    ): Flow<List<TransactionModel>> =
        dao.getTransactionByCategory(userId, categoryId).map { list -> list.map { it.toModel() } }

    override suspend fun createTransaction(transaction: TransactionModel) {
        val now = System.currentTimeMillis()
        val entity = TransactionEntity(
            localId = transaction.id.ifEmpty { UUID.randomUUID().toString() },
            userId = transaction.userId,
            type = transaction.type,
            amountMinor = transaction.money.amountMinor,
            currency = transaction.money.currency,
            categoryLocalId = transaction.categoryId,
            note = transaction.note,
            syncStatus = SyncStatus.PENDING,
            pendingOperation = PendingOperation.CREATE,
            createdAt = now,
            updatedAt = now
        )
        dao.insertTransaction(entity)
        syncTrigger.requestImmediateSync()
    }

    override suspend fun updateTransaction(transaction: TransactionModel) {
        val existing = dao.getTransactionByLocalId(transaction.id)
        val entity = TransactionEntity(
            localId = transaction.id,
            userId = transaction.userId,
            type = transaction.type,
            amountMinor = transaction.money.amountMinor,
            currency = transaction.money.currency,
            categoryLocalId = transaction.categoryId,
            note = transaction.note,
            syncStatus = SyncStatus.PENDING,
            pendingOperation = PendingOperation.UPDATE,
            createdAt = transaction.updatedAt,
            updatedAt = System.currentTimeMillis()
        )
        dao.updateTransaction(entity)
        syncTrigger.requestImmediateSync()
    }

    override suspend fun deleteTransaction(id: String) {
        dao.softDeleteTransaction(id, System.currentTimeMillis())
        syncTrigger.requestImmediateSync()
    }

    override suspend fun syncPendingTransactions(userId: String) {
        val pending = dao.getPendingTransactionsForSync(userId)
        for (tx in pending) {
            try {
                val docRef = firestore.collection("users").document(userId)
                    .collection("transactions").document(tx.localId)

                when (tx.pendingOperation) {
                    PendingOperation.CREATE, PendingOperation.UPDATE -> {
                        docRef.set(tx.toDto()).await()
                        dao.markSynced(tx.localId, System.currentTimeMillis())
                    }
                    PendingOperation.DELETE -> {
                        docRef.delete().await()
                        dao.hardDeleteTransaction(tx.localId)
                    }
                    PendingOperation.NONE -> Unit
                }
            } catch (e: Exception) {
                dao.markSyncFailed(tx.localId, e.message ?: "unknown error", System.currentTimeMillis())
            }
        }
    }

    private fun TransactionEntity.toModel() = TransactionModel(
        id = localId,
        userId = userId,
        type = type,
        money = Money(amountMinor, currency),
        categoryId = categoryLocalId,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isPendingSync = pendingOperation != PendingOperation.NONE
    )
}