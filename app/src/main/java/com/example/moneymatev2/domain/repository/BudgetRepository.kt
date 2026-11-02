package com.example.moneymatev2.domain.repository

import com.example.moneymatev2.domain.model.BudgetModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface BudgetRepository {

    fun getBudgetForMonth(userId: String, month: Int, year: Int): Flow<List<BudgetModel>>

    /** Counts durable local edits rejected by cloud revision checks for this account. */
    fun observeConflictCount(userId: String): Flow<Int> = flowOf(0)

    suspend fun setBudget(userId: String, categoryId: String, amountMinor: Long, currency: String)

    suspend fun deleteBudget(userId: String, categoryId: String, expectedBudgetId: String? = null)

    suspend fun syncPendingBudgets(userId: String)
}
