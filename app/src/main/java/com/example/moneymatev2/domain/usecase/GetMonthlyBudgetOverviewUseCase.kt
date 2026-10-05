package com.example.moneymatev2.domain.usecase

import com.example.moneymatev2.core.util.DefaultCurrency
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.BudgetOverviewModel
import com.example.moneymatev2.domain.model.Money
import com.example.moneymatev2.domain.repository.BudgetRepository
import com.example.moneymatev2.domain.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class GetMonthlyBudgetOverviewUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository
) {
    /**
     * periodStart/periodEnd: khoảng [start, end) của tháng cần xem, do tầng gọi (ViewModel)
     * tự tính qua TimeRangeCalculator.getTimeRange(HomePeriod.MONTH, anchor) -- UseCase ở
     * tầng Domain không được biết tới HomePeriod (thuộc Presentation), tránh phụ thuộc ngược tầng.
     */
    operator fun invoke(
        userId: String,
        month: Int,
        year: Int,
        periodStart: Long,
        periodEnd: Long
    ): Flow<BudgetOverviewModel> {
        val budgetsFlow = budgetRepository.getBudgetForMonth(userId, month, year)
        val transactionFlow = transactionRepository.getTransactionByPeriod(userId, periodStart, periodEnd)

        return combine(budgetsFlow, transactionFlow) { budgets, transactions ->
            val spentByCategory = transactions
                .filter { it.type == TransactionType.EXPENSE }
                .groupBy { it.categoryId }
                .mapValues { (_, txs) -> txs.sumOf { it.money.amountMinor } }

            val enrichedBudgets = budgets.map { budget ->
                val spent = spentByCategory[budget.categoryId] ?: 0L
                // Lấy currency từ chính budget đã lưu, không hardcode -> đúng dữ liệu thật của record đó.
                budget.copy(spentAmount = Money(spent, budget.budgetAmount.currency))
            }

            // Fallback duy nhất khi KHÔNG có budget nào (list rỗng) -> tham chiếu về nơi khai báo chung,
            // không lặp lại chuỗi "VND"/"VNĐ" ở đây.
            val currency = enrichedBudgets.firstOrNull()?.budgetAmount?.currency
                ?: DefaultCurrency.CODE

            val totalBudget = enrichedBudgets.sumOf { it.budgetAmount.amountMinor }
            val totalSpent = enrichedBudgets.sumOf { it.spentAmount.amountMinor }

            BudgetOverviewModel(
                budgets = enrichedBudgets,
                totalBudget = Money(totalBudget, currency),
                totalSpent = Money(totalSpent, currency),
                totalRemaining = Money(totalBudget - totalSpent, currency)
            )
        }.flowOn(Dispatchers.Default)
    }
}