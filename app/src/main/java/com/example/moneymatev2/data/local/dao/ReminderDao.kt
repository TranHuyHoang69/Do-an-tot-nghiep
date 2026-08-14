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
    @Query("SELECT * FROM reminders WHERE userId = :userId AND isActive = 1 ORDER BY triggerAt ASC")
    fun getActiveReminders(userId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE isActive = 1")
    fun getAllActiveRemindersOnce(): List<ReminderEntity>


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
        SET isActive = 0, pendingOperation = 'UPDATE', updatedAt = :updatedAt
        WHERE localId = :localId
    """)
    suspend fun deactivateReminder(localId: String, updatedAt: Long)

    @Query("""
        UPDATE reminders
        SET syncStatus = 'SYNCED', pendingOperation = 'NONE',
        remoteUpdatedAt = :remoteUpdatedAt, lastSyncError = NULL, retryCount = 0
        WHERE localId = :localId
    """)
    suspend fun markSynced(localId: Long, remoteUpdatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFromRemote(reminders: List<ReminderEntity>)
}