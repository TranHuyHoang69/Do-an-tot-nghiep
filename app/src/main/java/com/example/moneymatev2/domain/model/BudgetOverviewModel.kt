package com.example.moneymatev2.domain.model

import java.math.BigInteger

data class BudgetOverviewModel(
    val budgets: List<BudgetModel>,
    val totalBudgetMinor: BigInteger?,
    val totalSpentMinor: BigInteger?,
    val currency: String
) {
    constructor(budgets: List<BudgetModel>, totalBudget: Money, totalSpent: Money,
                totalRemaining: Money) : this(
        budgets, BigInteger.valueOf(totalBudget.amountMinor), BigInteger.valueOf(totalSpent.amountMinor),
        totalBudget.currency
    ) {
        require(BigInteger.valueOf(totalRemaining.amountMinor) ==
            requireNotNull(totalBudgetMinor).subtract(requireNotNull(totalSpentMinor)))
    }

    val totalRemainingMinor: BigInteger?
        get() = if (totalBudgetMinor != null && totalSpentMinor != null)
            totalBudgetMinor.subtract(totalSpentMinor) else null

    val totalBudget: Money get() = Money(requireNotNull(totalBudgetMinor).longValueExact(), currency)
    val totalSpent: Money get() = Money(requireNotNull(totalSpentMinor).longValueExact(), currency)
    val totalRemaining: Money get() = Money(requireNotNull(totalRemainingMinor).longValueExact(), currency)
}
