package com.example.moneymatev2.domain.usecase

import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.BudgetOverviewModel
import com.example.moneymatev2.domain.model.Money
import com.example.moneymatev2.domain.repository.BudgetRepository
import com.example.moneymatev2.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.Calendar
import javax.inject.Inject

class GetMonthlyBudgetOverviewUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository
) {
    operator fun invoke(
        userId: String,
        month: Int,
        year: Int,
        currency: String = "VNĐ"
    ): Flow<BudgetOverviewModel>{
        val (start, end) = monthRange(month, year)

        val budgetsFlow = budgetRepository.getBudgetForMonth(userId, month, year)
        val transactionFlow = transactionRepository.getTransactionByPeriod(userId, start, end)


        return combine(budgetsFlow, transactionFlow){budgets, transactions ->
            val spentByCategory = transactions
                .filter { it.type == TransactionType.EXPENSE }
                .groupBy { it.categoryId }
                .mapValues { (_, txs) -> txs.sumOf { it.money.amountMinor } }

            val enrichedBudgets = budgets.map { budgets ->
                val spent = spentByCategory[budgets.categoryId] ?: 0L
                budgets.copy(
                    spentAmount = Money(spent, currency)
                )
            }

            val totalBudget = enrichedBudgets.sumOf { it.budgetAmount.amountMinor }
            val totalSpent = enrichedBudgets.sumOf { it.spentAmount.amountMinor }

            BudgetOverviewModel(
                budgets = enrichedBudgets,
                totalBudget = Money(totalBudget, currency),
                totalSpent = Money(totalSpent, currency),
                totalRemaining = Money(totalBudget - totalSpent, currency)
            )
        }
    }

    private fun monthRange(month: Int, year: Int): Pair<Long, Long>{
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        cal.add(Calendar.MONTH, 1)
        cal.add(Calendar.MILLISECOND, -1)
        val end = cal.timeInMillis

        return start to end

    }

}