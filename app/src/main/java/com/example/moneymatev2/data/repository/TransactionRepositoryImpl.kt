package com.example.moneymatev2.data.repository

import com.example.moneymatev2.data.local.dao.TransactionDao
import com.example.moneymatev2.data.local.dao.CategoryDao
import com.example.moneymatev2.data.local.entity.PendingOperation
import com.example.moneymatev2.data.local.entity.SyncStatus
import com.example.moneymatev2.data.local.entity.TransactionEntity
import com.example.moneymatev2.data.remote.dto.toDto
import com.example.moneymatev2.data.remote.sync.SyncTrigger
import com.example.moneymatev2.data.remote.sync.PendingUploadRunner
import com.example.moneymatev2.data.remote.sync.syncFailureSummary
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
    private val categoryDao: CategoryDao,
    private val firestore: FirebaseFirestore,
    private val syncTrigger: SyncTrigger,
    private val uploadRunner: PendingUploadRunner
): TransactionRepository{
    override fun getAllTransactions(userId: String): Flow<List<TransactionModel>> =
        dao.getAllTransactions(userId).map { list -> list.map { it.toModel() } }

    override fun getTransactionByPeriod(
        userId: String,
        start: Long,
        end: Long): Flow<List<TransactionModel>> =
        dao.getTransactionByPeriod(userId, start, end).map { list -> list.map { it.toModel() } }

    override fun getTransactionById(userId: String, localId: String): Flow<TransactionModel?> =
        dao.getTransactionByLocalId(userId, localId).map { it?.toModel() }


    override fun getTransactionByCategory(
        userId: String,
        categoryId: String
    ): Flow<List<TransactionModel>> =
        dao.getTransactionByCategory(userId, categoryId).map { list -> list.map { it.toModel() } }

    override suspend fun createTransaction(transaction: TransactionModel) {
        uploadRunner.capture(transaction.userId)
        val category = categoryDao.getCategoryByLocalIdOnce(transaction.userId, transaction.categoryId)
        require(category != null && !category.isArchived && category.type == transaction.type) { "Invalid category" }
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
        uploadRunner.capture(transaction.userId)
        val existing = dao.getRowForCloudMerge(transaction.id)
        require(existing?.userId == transaction.userId) { "Transaction owner mismatch" }
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
            createdAt = transaction.createdAt,
            updatedAt = System.currentTimeMillis()
        )
        dao.updateTransaction(entity)
        syncTrigger.requestImmediateSync()
    }

    override suspend fun deleteTransaction(userId: String, id: String) {
        uploadRunner.capture(userId)
        dao.softDeleteTransaction(userId, id, System.currentTimeMillis())
        syncTrigger.requestImmediateSync()
    }


    override suspend fun syncPendingTransactions(userId: String) {
        val session = uploadRunner.capture(userId)
        val pending = dao.getPendingTransactionsForSync(userId)
        var firstError: Exception? = null
        for (row in pending) {
            try {
                uploadRunner.run(
                    session = session,
                    snapshot = row,
                    readCurrent = { dao.getRowForCloudMerge(row.localId) },
                    upload = {
                        val doc = firestore.collection("users").document(userId)
                            .collection("transactions").document(row.localId)
                        if (row.pendingOperation == PendingOperation.DELETE) doc.delete().await()
                        else doc.set(row.toDto()).await()
                    },
                    acknowledge = {
                        if (row.pendingOperation == PendingOperation.DELETE) {
                            dao.hardDeleteIfUnchanged(row.localId, userId, row.updatedAt)
                        } else {
                            dao.markSyncedIfUnchanged(row.localId, userId, row.updatedAt, System.currentTimeMillis())
                        }
                    },
                    recordFailure = { error ->
                        dao.markSyncFailedIfUnchanged(
                            row.localId, userId, row.updatedAt,
                            error.syncFailureSummary(), System.currentTimeMillis()
                        )
                    }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("SyncUpload", "SYNC_FAILED collection=transactions ${e.syncFailureSummary()}")
                if (firstError == null) firstError = e else if (firstError !== e) firstError.addSuppressed(e)
            }
        }
        firstError?.let { throw it }
    }


    override suspend fun reassignCategory(userId: String, oldCategoryId: String, newCategoryId: String) {
        uploadRunner.capture(userId)
        val source = categoryDao.getCategoryByLocalIdOnce(userId, oldCategoryId)
        val replacement = categoryDao.getCategoryByLocalIdOnce(userId, newCategoryId)
        require(source != null && replacement != null && !replacement.isArchived && source.type == replacement.type) { "Invalid category reassignment" }
        dao.reassignCategory(userId, oldCategoryId, newCategoryId, System.currentTimeMillis())
        syncTrigger.requestImmediateSync()
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
