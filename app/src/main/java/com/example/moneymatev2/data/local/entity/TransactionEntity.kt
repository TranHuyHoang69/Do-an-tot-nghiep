package com.example.moneymatev2.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "transactions",
    primaryKeys = ["localId"],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["categoryLocalId"]),
        Index(value = ["pendingOperation"]),
        Index(value = ["syncStatus"]),
        Index(value = ["isDeleted"])
    ]
)
data class TransactionEntity(
    val localId: String,
    val userId: String,
    val type: TransactionType,
    val amountMinor: Long,
    val currency: String,
    val categoryLocalId: String,
    val note: String?,
    val syncStatus: SyncStatus,
    val pendingOperation: PendingOperation,
    val isDeleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val remoteUpdatedAt: Long? = null,
    val lastSyncAttemptAt: Long? = null,
    val lastSyncError: String? = null,
    val retryCount: Int = 0
)

enum class TransactionType {
    INCOME,
    EXPENSE
}
enum class SyncStatus {
    SYNCED,
    PENDING,
    FAILED
}
enum class PendingOperation {
    NONE,
    CREATE,
    UPDATE,
    DELETE
}
