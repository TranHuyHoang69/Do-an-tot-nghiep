package com.example.moneymatev2.domain.usecase

import com.example.moneymatev2.core.util.DefaultCurrency
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.BudgetOverviewModel
import com.example.moneymatev2.domain.repository.BudgetRepository
import com.example.moneymatev2.domain.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import java.math.BigInteger

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
                .mapValues { (_, txs) -> txs.groupBy { it.money.currency }.mapValues { (_, currencyRows) ->
                    currencyRows.fold(BigInteger.ZERO) { sum, tx ->
                        sum.add(BigInteger.valueOf(tx.money.amountMinor))
                    }
                } }

            val enrichedBudgets = budgets.map { budget ->
                val byCurrency = spentByCategory[budget.categoryId].orEmpty()
                val spent = byCurrency[budget.budgetAmount.currency] ?: BigInteger.ZERO
                // Lấy currency từ chính budget đã lưu, không hardcode -> đúng dữ liệu thật của record đó.
                budget.copy(spentMinor = spent, spentByCurrency = byCurrency)
            }

            // Fallback duy nhất khi KHÔNG có budget nào (list rỗng) -> tham chiếu về nơi khai báo chung,
            // không lặp lại chuỗi "VND"/"VNĐ" ở đây.
            val currency = enrichedBudgets.firstOrNull()?.budgetAmount?.currency
                ?: DefaultCurrency.CODE

            val singleCurrency = enrichedBudgets.all { budget ->
                budget.budgetAmount.currency == currency &&
                    budget.spentByCurrency.keys.all { it == currency }
            }
            val totalBudget = if (singleCurrency) enrichedBudgets.fold(BigInteger.ZERO) { sum, budget ->
                sum.add(BigInteger.valueOf(budget.budgetAmount.amountMinor))
            } else null
            val totalSpent = if (singleCurrency) enrichedBudgets.fold(BigInteger.ZERO) { sum, budget ->
                sum.add(budget.spentMinor)
            } else null

            BudgetOverviewModel(
                budgets = enrichedBudgets,
                totalBudgetMinor = totalBudget,
                totalSpentMinor = totalSpent,
                currency = currency
            )
        }.flowOn(Dispatchers.Default)
    }
}
