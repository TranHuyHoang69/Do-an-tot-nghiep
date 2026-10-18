package com.example.moneymatev2.domain.usecase.transaction

import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class DeleteTransactionUseCase @Inject constructor(
    private val transactions: TransactionRepository,
    private val auth: AuthRepository
) {
    suspend operator fun invoke(userId: String, id: String) {
        require(auth.getCurrentUserId() == userId) { "Session changed" }
        require(transactions.getTransactionById(userId, id).first() != null) { "Transaction unavailable" }
        require(auth.getCurrentUserId() == userId) { "Session changed" }
        transactions.deleteTransaction(userId, id)
    }
}
