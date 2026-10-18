package com.example.moneymatev2.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.moneymatev2.data.local.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("""
    SELECT * FROM reminders
    WHERE userId = :userId
      AND isActive = 1
      AND pendingOperation != 'DELETE'
    ORDER BY triggerAt ASC
""")
    fun getActiveReminders(
        userId: String
    ): Flow<List<ReminderEntity>>

    @Query("""
    SELECT * FROM reminders
    WHERE userId = :userId
      AND isActive = 1
      AND pendingOperation != 'DELETE'
""")
    suspend fun getAllActiveRemindersOnce(
        userId: String
    ): List<ReminderEntity>


    @Query("SELECT * FROM reminders WHERE localId = :localId LIMIT 1")
    suspend fun getReminderByLocalIdOnce(localId: String): ReminderEntity?

    @Query("""
        SELECT * FROM reminders
        WHERE userId = :userId AND pendingOperation != 'NONE'
        ORDER BY updatedAt ASC
    """)
    suspend fun getPendingRemindersForSync(userId: String): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReminder(reminder: ReminderEntity)

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Query("""
    UPDATE reminders
    SET isActive = :isActive,
        syncStatus = 'PENDING',
        pendingOperation = 'UPDATE',
        updatedAt = :updatedAt
    WHERE localId = :localId
""")
    suspend fun setReminderActive(
        localId: String,
        isActive: Boolean,
        updatedAt: Long
    )

    @Query("""
        UPDATE reminders
        SET syncStatus = 'SYNCED', pendingOperation = 'NONE',
        remoteUpdatedAt = :remoteUpdatedAt, lastSyncError = NULL, retryCount = 0
        WHERE localId = :localId
    """)
    suspend fun markSynced(localId: String, remoteUpdatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFromRemote(reminders: List<ReminderEntity>)

    @Query("""
    SELECT * FROM reminders
    WHERE userId = :userId
      AND pendingOperation != 'DELETE'
    ORDER BY isActive DESC, triggerAt ASC
""")
    fun getReminders(
        userId: String
    ): Flow<List<ReminderEntity>>

    @Query("""
    UPDATE reminders
    SET isActive = 0,
        syncStatus = 'PENDING',
        pendingOperation = 'DELETE',
        updatedAt = :updatedAt
    WHERE localId = :localId
""")
    suspend fun markReminderForDeletion(
        localId: String,
        updatedAt: Long
    )
    @Query("""
    DELETE FROM reminders
    WHERE localId = :localId
""")
    suspend fun deleteReminderPermanently(
        localId: String
    )
    @Query("""
    UPDATE reminders
    SET triggerAt = :triggerAt,
        syncStatus = 'PENDING',
        pendingOperation = 'UPDATE',
        updatedAt = :updatedAt
    WHERE localId = :localId
""")
    suspend fun updateReminderTrigger(
        localId: String,
        triggerAt: Long,
        updatedAt: Long
    )

    @Query("SELECT * FROM reminders WHERE userId = :userId AND pendingOperation = 'NONE'")
    suspend fun getCleanRowsForCloudMerge(userId: String): List<ReminderEntity>

    @Query("DELETE FROM reminders WHERE localId = :id AND userId = :userId AND pendingOperation = 'NONE'")
    suspend fun deleteCleanRowForCloudMerge(id: String, userId: String)

    @Query("""
    UPDATE reminders
    SET syncStatus = 'SYNCED',
        pendingOperation = 'NONE',
        remoteUpdatedAt = :remoteUpdatedAt,
        lastSyncError = NULL,
        retryCount = 0
    WHERE localId = :localId
      AND userId = :userId
      AND updatedAt = :expectedUpdatedAt
      AND pendingOperation != 'NONE'
""")
    suspend fun markSyncedIfUnchanged(
        localId: String,
        userId: String,
        expectedUpdatedAt: Long,
        remoteUpdatedAt: Long
    ): Int

    @Query("""
    DELETE FROM reminders
    WHERE localId = :localId
      AND userId = :userId
      AND pendingOperation = 'DELETE'
      AND updatedAt = :expectedUpdatedAt
""")
    suspend fun deleteReminderIfUnchanged(
        localId: String,
        userId: String,
        expectedUpdatedAt: Long
    ): Int

    @Query("""
    UPDATE reminders
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
