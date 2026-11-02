package com.example.moneymatev2.domain.model

import java.math.BigInteger

data class BudgetModel(
    val id: String,
    val categoryId: String,
    val budgetAmount: Money,
    val spentMinor: BigInteger,
    val effectiveMonth: Int,
    val effectiveYear: Int,
    val spentByCurrency: Map<String, BigInteger> = emptyMap()
){
    constructor(id: String, categoryId: String, budgetAmount: Money, spentAmount: Money,
                effectiveMonth: Int, effectiveYear: Int) : this(
        id, categoryId, budgetAmount, BigInteger.valueOf(spentAmount.amountMinor), effectiveMonth, effectiveYear
    )

    val remainingMinor: BigInteger
        get() = BigInteger.valueOf(budgetAmount.amountMinor).subtract(spentMinor)

    // Existing Long accessors remain checked; an exact total is never truncated.
    val spentAmount: Money
        get() = Money(spentMinor.longValueExact(), budgetAmount.currency)
    val remainingAmount: Money
        get() = Money(remainingMinor.longValueExact(), budgetAmount.currency)
}

