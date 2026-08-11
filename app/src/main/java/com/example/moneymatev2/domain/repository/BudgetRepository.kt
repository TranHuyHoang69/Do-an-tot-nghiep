package com.example.moneymatev2.domain.repository

import com.example.moneymatev2.domain.model.BudgetModel
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {

    fun getBudgetForMonth(userId: String, month: Int, year: Int): Flow<List<BudgetModel>>

    suspend fun setBudget(userId: String, categoryId: String, amountMinor: Long, currency: String)

    suspend fun deleteBudget(userId: String, categoryId: String)

    suspend fun syncPendingBudgets(userId: String)
}