package com.example.moneymatev2.data.remote.dto

import com.example.moneymatev2.data.local.entity.BudgetEntity
import com.example.moneymatev2.data.local.entity.PendingOperation
import com.example.moneymatev2.data.local.entity.SyncStatus
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class BudgetDto(
    val categoryLocalId: String = "",
    val amountMinor: Long = 0,
    val currency: String = "VNĐ",
    val name: String = "",
    val effectiveMonth: Int = 0,
    val effectiveYear: Int = 0,
    val isArchived: Boolean = false,
    val createdAt: Long = 0,

    @get:PropertyName("updatedAt")
    @set:PropertyName("updatedAt")
    @ServerTimestamp
    var updatedAt: Date? = null
)

fun BudgetEntity.toDto() = BudgetDto(
    categoryLocalId = categoryLocalId,
    amountMinor = amountMinor,
    currency = currency,
    effectiveMonth = effectiveMonth,
    effectiveYear = effectiveYear,
    isArchived = isArchived,
    createdAt = createdAt
)

fun BudgetDto.toEntity(
    localId: String,
    userId: String,
    syncStatus: SyncStatus,
    pendingOperation: PendingOperation
) = BudgetEntity(
    localId = localId,
    userId = userId,
    categoryLocalId = categoryLocalId,
    amountMinor = amountMinor,
    currency = currency,
    effectiveMonth = effectiveMonth,
    effectiveYear = effectiveYear,
    isArchived = isArchived,
    syncStatus = syncStatus,
    pendingOperation = pendingOperation,
    createdAt = createdAt,
    updatedAt = updatedAt?.time ?: System.currentTimeMillis(),
    remoteUpdatedAt = updatedAt?.time
)