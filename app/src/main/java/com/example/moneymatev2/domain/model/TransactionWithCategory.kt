package com.example.moneymatev2.domain.model

data class TransactionWithCategory(
    val transaction: TransactionModel,
    val category: CategoryModel?
)