package com.example.moneymatev2.domain.usecase

import com.example.moneymatev2.domain.repository.BudgetRepository
import com.example.moneymatev2.domain.repository.CategoryRepository
import com.example.moneymatev2.domain.repository.ReminderRepository
import com.example.moneymatev2.domain.repository.TransactionRepository
import com.example.moneymatev2.domain.repository.RecurringTransactionRepository
import javax.inject.Inject

class SyncManager @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val recurringRepository: RecurringTransactionRepository
) {
    suspend fun syncAll(userId: String) {
        if (userId.isBlank()) return
        // Generate local history FIRST, so the ordinary transaction sync sees new rows.
        recurringRepository.postDue(userId)
        categoryRepository.syncPendingCategories(userId)
        recurringRepository.syncPending(userId)
        transactionRepository.syncPendingTransactions(userId)
        budgetRepository.syncPendingBudgets(userId)
        reminderRepository.syncPendingReminders(userId)
    }
}
