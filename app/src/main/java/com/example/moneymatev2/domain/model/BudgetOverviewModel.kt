package com.example.moneymatev2.domain.model

data class BudgetOverviewModel(
    val budgets: List<BudgetModel>,
    val totalBudget: Money,
    val totalSpent: Money,
    val totalRemaining: Money
)