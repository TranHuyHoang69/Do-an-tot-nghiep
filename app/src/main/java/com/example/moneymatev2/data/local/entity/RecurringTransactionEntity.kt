package com.example.moneymatev2.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Not a posted transaction: recurring template only. Never share entity IDs across users. */
@Entity(
    tableName = "recurring_transactions",
    indices = [Index(value = ["userId", "isActive", "nextRunAt"])]
)
data class RecurringTransactionEntity(
    @PrimaryKey val localId: String,
    val userId: String,
    val type: TransactionType,
    val amountMinor: Long,
    val currency: String,
    val categoryLocalId: String,
    val note: String?,
    val frequency: String,
    val startAt: Long,
    val timeZoneId: String,
    val nextOccurrenceIndex: Long,
    val nextRunAt: Long,
    val isActive: Boolean,
    val isDeleted: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val pendingOperation: PendingOperation = PendingOperation.CREATE,
    val createdAt: Long,
    val updatedAt: Long,
    val remoteUpdatedAt: Long? = null,
    val lastSyncError: String? = null,
    val lastSyncAttemptAt: Long? = null,
    val retryCount: Int = 0
)
