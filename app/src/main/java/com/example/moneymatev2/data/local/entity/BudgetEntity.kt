package com.example.moneymatev2.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "budgets",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["categoryLocalId"]),
        Index(value = ["pendingOperation"]),
        Index(value = ["userId","categoryLocalId","effectiveMonth","effectiveYear"], unique = true),
    ]
)
data class BudgetEntity(
    @PrimaryKey
    val localId: String,
    val userId: String,
    val categoryLocalId: String,
    val amountMinor: Long,
    val currency: String,
    val effectiveMonth: Int,
    val effectiveYear: Int,
    val isArchived: Boolean = false,
    val syncStatus: SyncStatus,
    val pendingOperation: PendingOperation,
    val lastSyncError: String? = null,
    val retryCount: Int = 0,
    val lastSyncAttemptAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val remoteUpdatedAt: Long? = null

)
