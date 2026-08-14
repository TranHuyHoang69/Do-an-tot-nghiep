package com.example.moneymatev2.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import com.example.moneymatev2.data.local.entity.TransactionEntity

@Dao
interface TransactionDao{
    @Transaction
    @Query(
        """
            SELECT * FROM transactions
            WHERE userId = :userId AND isDeleted = 0
            ORDER BY createdAt DESC
        """)
    fun getAllTransactions(userId: String): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions
        WHERE userId = :userId AND isDeleted = 0 AND createdAt BETWEEN :start AND :end
        ORDER BY createdAt DESC
    """)
    fun getTransactionByPeriod(userId: String, start: Long, end: Long): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions
        WHERE userId = :userId AND isDeleted = 0 AND categoryLocalId = :categoryLocalId
        ORDER BY createdAt DESC
    """)
    fun getTransactionByCategory(userId: String, categoryLocalId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE localId = :localId AND isDeleted = 0")
    fun getTransactionByLocalId(localId: String): Flow<TransactionEntity?>

    //sync
    @Query("""
        SELECT * FROM transactions
        WHERE userId = :userId AND pendingOperation != 'NONE'
        ORDER BY createdAt DESC
    """)
    suspend fun getPendingTransactionsForSync(userId: String): List<TransactionEntity>

//    @Query("SELECT * FROM transactions WHERE localId = :remoteId LIMIT 1")
//    suspend fun getTransactionByRemoteId(remoteId: String): Flow<TransactionEntity?>

    //
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    //soft delete
    @Query("""
        UPDATE transactions
        SET isDeleted = 1, pendingOperation = 'DELETE', updatedAt = :updatedAt
        WHERE localId = :localId
    """)
    suspend fun softDeleteTransaction(localId: String, updatedAt: Long)

    @Query("DELETE FROM transactions WHERE localId = :localId")
    suspend fun hardDeleteTransaction(localId: String)

    //update sync
    @Query("""
        UPDATE transactions
        SET syncStatus = "SYNCED", pendingOperation = "NONE",
        remoteUpdatedAt = :remoteUpdatedAt,
        lastSyncError = NULL, retryCount = 0
        WHERE localId = :localId
    """)
    suspend fun markSynced(localId: String, remoteUpdatedAt: Long)

    @Query("""
        UPDATE transactions
        SET syncStatus = "FAILED", lastSyncError = :error,
        retryCount = retryCount + 1, lastSyncAttemptAt = :attemptAt
        WHERE localId = :localId
    """)
    suspend fun markSyncFailed(localId: String, error: String, attemptAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFromRemote(transaction: List<TransactionEntity>)

    @Query("SELECT COUNT(*) FROM transactions WHERE userId = :userId AND categoryLocalId = :categoryStableId AND isDeleted = 0")
    suspend fun countTransactionsByCategory(userId: String, categoryStableId: String): Int

    @Query("DELETE FROM transactions WHERE userId = :userId")
    suspend fun clearAllForUser(userId: String)
}