package com.example.moneymatev2.data.remote.dto

import com.example.moneymatev2.data.local.entity.CategoryEntity
import com.example.moneymatev2.data.local.entity.PendingOperation
import com.example.moneymatev2.data.local.entity.SyncStatus
import com.example.moneymatev2.data.local.entity.TransactionType
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class CategoryDto(
    val stableId: String? = null,
    val name: String = "",
    val type: String = TransactionType.EXPENSE.name,
    val iconKey: String = "",
    val colorHex: String = "",
    val isDefault: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long = 0,

    @get:PropertyName("updatedAt")
    @set:PropertyName("updatedAt")
    @ServerTimestamp
    var updatedAt: Date? = null
)

fun CategoryEntity.toDto() = CategoryDto(
    stableId = stableId,
    name = name,
    type = type.name,
    iconKey = iconKey,
    colorHex = colorHex,
    isDefault = isDefault,
    isArchived = isArchived,
    createdAt = createdAt
)

fun CategoryDto.toEntity(
    localId: String,
    userId: String,
    syncStatus: SyncStatus,
    pendingOperation: PendingOperation
) = CategoryEntity(
    localId = localId,
    stableId = stableId,
    userId = userId,
    name = name,
    type = TransactionType.valueOf(type),
    iconKey = iconKey,
    colorHex = colorHex,
    isDefault = isDefault,
    isArchived = isArchived,
    syncStatus = syncStatus,
    pendingOperation = pendingOperation,
    createdAt = createdAt,
    updatedAt = updatedAt?.time ?: System.currentTimeMillis(),
    remoteUpdatedAt = updatedAt?.time
)