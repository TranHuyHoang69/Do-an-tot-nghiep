package com.example.moneymatev2.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.moneymatev2.data.local.entity.RecurringTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringTransactionDao {
    @Query("SELECT COUNT(*) FROM recurring_transactions WHERE userId = :userId AND categoryLocalId = :categoryId AND isDeleted = 0")
    suspend fun countNotDeletedByCategory(userId: String, categoryId: String): Int

    @Query("SELECT * FROM recurring_transactions WHERE userId = :userId AND isDeleted = 0 ORDER BY nextRunAt ASC")
    fun observeAll(userId: String): Flow<List<RecurringTransactionEntity>>

    @Query("SELECT * FROM recurring_transactions WHERE localId = :id AND userId = :userId AND isDeleted = 0 LIMIT 1")
    suspend fun getById(id: String, userId: String): RecurringTransactionEntity?

    @Query("SELECT * FROM recurring_transactions WHERE userId = :userId AND isActive = 1 AND isDeleted = 0 AND nextRunAt <= :now ORDER BY nextRunAt ASC LIMIT 1")
    suspend fun nextDue(userId: String, now: Long): RecurringTransactionEntity?

    @Query("SELECT * FROM recurring_transactions WHERE userId = :userId AND pendingOperation != 'NONE' ORDER BY updatedAt ASC")
    suspend fun getPending(userId: String): List<RecurringTransactionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: RecurringTransactionEntity)

    @Update
    suspend fun update(item: RecurringTransactionEntity)

    @Query("""
    UPDATE recurring_transactions
    SET syncStatus = 'SYNCED',
        pendingOperation = 'NONE',
        remoteUpdatedAt = :remoteTime,
        lastSyncError = NULL,
        retryCount = 0
    WHERE localId = :id
      AND userId = :userId
      AND updatedAt = :expectedUpdatedAt
      AND pendingOperation IN ('CREATE', 'UPDATE')
""")
    suspend fun markSynced(
        id: String,
        userId: String,
        expectedUpdatedAt: Long,
        remoteTime: Long
    ): Int

    @Query("DELETE FROM recurring_transactions WHERE localId = :id AND userId = :userId AND updatedAt = :expectedUpdatedAt AND pendingOperation = 'DELETE'")
    suspend fun hardDeleteIfUnchanged(id: String, userId: String, expectedUpdatedAt: Long): Int

    @Query("""
    UPDATE recurring_transactions
    SET syncStatus = 'FAILED',
        lastSyncError = :error,
        retryCount = retryCount + 1,
        lastSyncAttemptAt = :at
    WHERE localId = :id
      AND userId = :userId
      AND updatedAt = :expectedUpdatedAt
      AND pendingOperation != 'NONE'
""")
    suspend fun markSyncFailedIfUnchanged(
        id: String,
        userId: String,
        expectedUpdatedAt: Long,
        error: String,
        at: Long
    ): Int

    @Query("SELECT * FROM recurring_transactions WHERE localId = :localId LIMIT 1")
    suspend fun getRowForCloudMerge(localId: String): RecurringTransactionEntity?

    @Query("SELECT * FROM recurring_transactions WHERE userId = :userId AND pendingOperation = 'NONE'")
    suspend fun getCleanRowsForCloudMerge(userId: String): List<RecurringTransactionEntity>

    @Query("DELETE FROM recurring_transactions WHERE localId = :id AND userId = :userId AND pendingOperation = 'NONE'")
    suspend fun deleteCleanRowForCloudMerge(id: String, userId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFromRemote(row: RecurringTransactionEntity)
}
