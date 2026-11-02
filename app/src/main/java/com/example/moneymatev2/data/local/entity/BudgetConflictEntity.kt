package com.example.moneymatev2.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Durable copy of a rejected offline edit, kept apart from the cloud-authoritative Budget row. */
@Entity(tableName = "budget_conflicts", indices = [Index(value = ["userId"])])
data class BudgetConflictEntity(
    @PrimaryKey val conflictId: String,
    val userId: String,
    val budgetLocalId: String,
    val categoryLocalId: String,
    val effectiveMonth: Int,
    val effectiveYear: Int,
    val amountMinor: Long,
    val currency: String,
    val isArchived: Boolean,
    val expectedRevision: Long?,
    val actualRevision: Long?,
    val reason: String,
    val capturedAt: Long
)
