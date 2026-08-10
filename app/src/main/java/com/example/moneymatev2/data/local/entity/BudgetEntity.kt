package com.example.moneymatev2.data.local.entity

data class BudgetEntity(
    val localId: String,
    val remoteId: String?,
    val userId: String,
    val categoryStableId: String,
    val amountMinor: Long,
    val currency: String,
    val month: Int,
    val year: Int,
    val syncStatus: SyncStatus,
    val createdAt: Long,
    val updatedAt: Long
)
