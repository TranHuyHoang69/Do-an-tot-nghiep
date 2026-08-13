package com.example.moneymatev2.domain.usecase

import com.example.moneymatev2.domain.repository.BudgetRepository
import com.example.moneymatev2.domain.repository.CategoryRepository
import com.example.moneymatev2.domain.repository.ReminderRepository
import com.example.moneymatev2.domain.repository.TransactionRepository
import javax.inject.Inject

class SyncManager @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository
) {
    suspend fun syncAll(userId: String) {
        categoryRepository.syncPendingCategories(userId)
        transactionRepository.syncPendingTransactions(userId)
        budgetRepository.syncPendingBudgets(userId)
        reminderRepository.syncPendingReminders(userId)
    }
}