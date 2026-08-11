package com.example.moneymatev2.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminders",
    indices = [
        Index("userId"),
        Index("triggerAt"),
        Index("isActive"),
        Index("pendingOperation")
    ]
)
data class ReminderEntity(
    @PrimaryKey
    val localId: String,
    val userId: String,
    val title: String,
    val message: String?,
    val triggerAt: Long,
    val repeatRule: RepeatRule,
    val isActive: Boolean,
    val syncStatus: SyncStatus,
    val pendingOperation: PendingOperation,
    val lastSyncError: String? = null,
    val retryCount: Int = 0,
    val lastSyncAttemptAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val remoteUpdatedAt: Long? = null
)

