package com.example.moneymatev2.domain.model

import com.example.moneymatev2.data.local.entity.TransactionType

data class TransactionModel(
    val id: String,
    val userId: String,
    val type: TransactionType,
    val money: Money,
    val categoryId: String,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val isPendingSync: Boolean
)