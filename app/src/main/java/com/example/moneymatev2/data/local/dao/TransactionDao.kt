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

    @Query("SELECT * FROM transactions WHERE userId = :userId AND localId = :localId AND isDeleted = 0")
    fun getTransactionByLocalId(userId: String, localId: String): Flow<TransactionEntity?>

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

    @Query("""UPDATE transactions SET type=:type, amountMinor=:amountMinor, categoryLocalId=:categoryId,
        note=:note, createdAt=:createdAt, updatedAt=:updatedAt, syncStatus='PENDING',
        pendingOperation=CASE WHEN pendingOperation='CREATE' THEN 'CREATE' ELSE 'UPDATE' END,
        lastSyncError=NULL, retryCount=0
        WHERE userId=:userId AND localId=:localId AND isDeleted=0 AND pendingOperation!='DELETE'
        AND updatedAt=:expectedUpdatedAt""")
    suspend fun updateEditableFields(userId: String, localId: String, type: com.example.moneymatev2.data.local.entity.TransactionType,
        amountMinor: Long, categoryId: String, note: String?, createdAt: Long,
        updatedAt: Long, expectedUpdatedAt: Long): Int

    //soft delete
    @Query("""
        UPDATE transactions
        SET isDeleted = 1, syncStatus = 'PENDING', pendingOperation = 'DELETE', updatedAt = :updatedAt
        WHERE userId = :userId AND localId = :localId AND isDeleted = 0 AND pendingOperation != 'DELETE'
    """)
    suspend fun softDeleteTransaction(userId: String, localId: String, updatedAt: Long): Int

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

    @Query("SELECT COUNT(*) FROM transactions WHERE userId = :userId AND categoryLocalId = :categoryStableId AND isDeleted = 0 AND pendingOperation != 'DELETE'")
    suspend fun countTransactionsByCategory(userId: String, categoryStableId: String): Int

    @Query("DELETE FROM transactions WHERE userId = :userId")
    suspend fun clearAllForUser(userId: String)

    @Query("""
    UPDATE transactions
    SET categoryLocalId = :newCategoryId,
        syncStatus = 'PENDING',
        pendingOperation = CASE WHEN pendingOperation = 'NONE' THEN 'UPDATE' ELSE pendingOperation END,
        updatedAt = CASE WHEN updatedAt >= :updatedAt THEN updatedAt + 1 ELSE :updatedAt END
    WHERE categoryLocalId = :oldCategoryId AND userId = :userId
      AND isDeleted = 0 AND pendingOperation != 'DELETE'
""")
    suspend fun reassignCategory(userId: String, oldCategoryId: String, newCategoryId: String, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGeneratedTransaction(
        transaction: TransactionEntity
    ): Long

    @Query("SELECT * FROM transactions WHERE localId = :localId LIMIT 1")
    suspend fun getRowForCloudMerge(localId: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE userId = :userId AND pendingOperation = 'NONE'")
    suspend fun getCleanRowsForCloudMerge(userId: String): List<TransactionEntity>

    @Query("DELETE FROM transactions WHERE localId = :id AND userId = :userId AND pendingOperation = 'NONE'")
    suspend fun deleteCleanRowForCloudMerge(id: String, userId: String)

    @Query("""UPDATE transactions SET syncStatus='SYNCED',pendingOperation='NONE',remoteUpdatedAt=:remoteUpdatedAt,lastSyncError=NULL,retryCount=0 WHERE localId=:localId AND userId=:userId AND updatedAt=:expectedUpdatedAt AND pendingOperation!='NONE'""")
    suspend fun markSyncedIfUnchanged(localId: String, userId: String, expectedUpdatedAt: Long, remoteUpdatedAt: Long): Int

    @Query("DELETE FROM transactions WHERE localId=:id AND userId=:userId AND pendingOperation='DELETE' AND updatedAt=:expectedUpdatedAt")
    suspend fun hardDeleteIfUnchanged(id:String,userId:String,expectedUpdatedAt:Long):Int

    @Query("""
    UPDATE transactions
    SET syncStatus = 'FAILED',
        lastSyncError = :error,
        retryCount = retryCount + 1,
        lastSyncAttemptAt = :attemptAt
    WHERE localId = :localId
      AND userId = :userId
      AND updatedAt = :expectedUpdatedAt
      AND pendingOperation != 'NONE'
""")
    suspend fun markSyncFailedIfUnchanged(
        localId: String,
        userId: String,
        expectedUpdatedAt: Long,
        error: String,
        attemptAt: Long
    ): Int
}
