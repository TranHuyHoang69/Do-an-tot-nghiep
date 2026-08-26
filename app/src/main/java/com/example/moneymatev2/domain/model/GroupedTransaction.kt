package com.example.moneymatev2.domain.model

import com.example.moneymatev2.data.local.entity.TransactionType

data class GroupedTransaction(
    val category: CategoryModel,
    val totalAmount: Long,
    val transactionCount: Int,
    val type: TransactionType
)