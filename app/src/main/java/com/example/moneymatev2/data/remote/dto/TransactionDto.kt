package com.example.moneymatev2.data.remote.dto

import com.example.moneymatev2.data.local.entity.PendingOperation
import com.example.moneymatev2.data.local.entity.SyncStatus
import com.example.moneymatev2.data.local.entity.TransactionEntity
import com.example.moneymatev2.data.local.entity.TransactionType
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class TransactionDto(
    val type: String = TransactionType.EXPENSE.name,
    val amountMinor: Long = 0,
    val currency: String = "VNĐ",
    val categoryLocalId: String = "",
    val note: String? = null,
    val isDeleted: Boolean = false,
    val createdAt: Long = 0,

    @get:PropertyName("updatedAt")
    @set:PropertyName("updatedAt")
    @ServerTimestamp
    var updatedAt: Date? = null
)

fun TransactionEntity.toDto() = TransactionDto(
    type = type.name,
    amountMinor = amountMinor,
    currency = currency,
    categoryLocalId = categoryLocalId,
    note = note,
    isDeleted = isDeleted,
    createdAt = createdAt
)

fun TransactionDto.toEntity(
    localId: String,
    userId: String,
    syncStatus: SyncStatus,
    pendingOperation: PendingOperation
) = TransactionEntity(
    localId = localId,
    userId = userId,
    type = TransactionType.valueOf(type),
    amountMinor = amountMinor,
    currency = currency,
    categoryLocalId = categoryLocalId,
    note = note,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt?.time ?: 0L,
    syncStatus = syncStatus,
    pendingOperation = pendingOperation,
    remoteUpdatedAt = updatedAt?.time ?: 0L
)