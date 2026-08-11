package com.example.moneymatev2.data.remote.dto

import com.example.moneymatev2.data.local.entity.PendingOperation
import com.example.moneymatev2.data.local.entity.ReminderEntity
import com.example.moneymatev2.data.local.entity.RepeatRule
import com.example.moneymatev2.data.local.entity.SyncStatus
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class ReminderDto(
    val title: String = "",
    val message: String? = null,
    val triggerAt: Long = 0,
    val repeatRule: String? = RepeatRule.NONE.name,
    val isActive: Boolean = true,
    val createdAt: Long = 0,

    @get:PropertyName("updatedAt")
    @set:PropertyName("updatedAt")
    @ServerTimestamp
    var updatedAt: Date? = null
)

fun ReminderEntity.toDto() = ReminderDto(
    title = title,
    message = message,
    triggerAt = triggerAt,
    repeatRule = repeatRule.name,
    isActive = isActive,
    createdAt = createdAt
)

fun ReminderDto.toEntity(
    localId: String,
    userId: String,
    syncStatus: SyncStatus,
    pendingOperation: PendingOperation
) = ReminderEntity(
    localId = localId,
    userId = userId,
    title = title,
    message = message,
    triggerAt = triggerAt,
    repeatRule = RepeatRule.valueOf(repeatRule ?: RepeatRule.NONE.name),
    isActive = isActive,
    syncStatus = syncStatus,
    pendingOperation = pendingOperation,
    createdAt = createdAt,
    updatedAt = updatedAt?.time ?: System.currentTimeMillis(),
    remoteUpdatedAt = updatedAt?.time
)