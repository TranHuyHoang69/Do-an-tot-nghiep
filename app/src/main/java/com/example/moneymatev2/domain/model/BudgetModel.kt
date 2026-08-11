package com.example.moneymatev2.domain.model

data class BudgetModel(
    val id: String,
    val categoryId: String,
    val budgetAmount: Money,
    val spentAmount: Money,
    val effectiveMonth: Int,
    val effectiveYear: Int
){
    val remainingAmount: Money
        get() = budgetAmount - spentAmount
}

