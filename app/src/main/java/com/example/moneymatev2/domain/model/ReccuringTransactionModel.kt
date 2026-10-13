package com.example.moneymatev2.domain.model

import com.example.moneymatev2.data.local.entity.TransactionType

/** Template, NOT a posted transaction. nextOccurrenceIndex is zero-based. */
data class RecurringTransactionModel(
    val id: String,
    val userId: String,
    val type: TransactionType,
    val amountMinor: Long,
    val currency: String = "VND",
    val categoryId: String,
    val note: String?,
    val frequency: RecurringFrequency,
    val startAt: Long,
    val timeZoneId: String,
    val nextOccurrenceIndex: Long,
    val nextRunAt: Long,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
