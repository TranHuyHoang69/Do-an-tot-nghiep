package com.example.moneymatev2.data.local.entity

data class RecurringTransactionEntity(
    val localId: String,
    val remoteId: String?,
    val userId: String,
    val type: TransactionType,
    val amountMinor: Long,
    val categoryStableId: String ,
    val note: String?,
    val repeatRule: RepeatRule,
    val startAt: Long,
    val nextRunAt: Long,
    val isActive: Boolean,
    val syncStatus: SyncStatus,
    val createdAt: Long,
    val updatedAt: Long
)

enum class RepeatRule{
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY
}
