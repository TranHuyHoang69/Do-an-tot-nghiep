package com.example.moneymatev2.data.local.entity

data class ReminderEntity(
    val localId: String,
    val remoteId: String?,
    val userId: String,
    val title: String,
    val message: String?,
    val triggerAt: Long,
    val repeatRule: RepeatRule,
    val isActive: Boolean,
    val syncStatus: SyncStatus,
    val createdAt: Long,
    val updatedAt: Long
)
