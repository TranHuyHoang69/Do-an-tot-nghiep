package com.example.moneymatev2.domain.usecase.transaction

import com.example.moneymatev2.domain.model.TransactionModel
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class UpdateTransactionUseCase @Inject constructor(
    private val transactions: TransactionRepository,
    private val auth: AuthRepository
) {
    suspend operator fun invoke(transaction: TransactionModel) {
        require(auth.getCurrentUserId() == transaction.userId) { "Session changed" }
        require(transaction.money.amountMinor > 0) { "Invalid amount" }
        val current = transactions.getTransactionById(transaction.userId, transaction.id).first()
        require(current != null) { "Transaction unavailable" }
        require(auth.getCurrentUserId() == transaction.userId) { "Session changed" }
        transactions.updateTransaction(transaction)
    }
}
