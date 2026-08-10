package com.example.moneymatev2.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    indices = [
        Index(value =  ["userId"]),
        Index(value = ["stableId"]),
        Index(value = ["type"]),
        Index(value = ["pendingOperation"])
    ]
)
data class CategoryEntity(
    @PrimaryKey
    val localId: String,
    val stableId: String?,
    val userId: String,
    val name: String,
    val type: TransactionType,
    val iconKey: String,
    val colorHex: String,
    val isDefault: Boolean,
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
