package com.example.moneymatev2.domain.usecase.transaction

import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.TransactionError
import com.example.moneymatev2.domain.model.AppResult
import com.example.moneymatev2.domain.model.Money
import com.example.moneymatev2.domain.model.TransactionModel
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.TransactionRepository
import javax.inject.Inject

class CreateTransactionUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        type: TransactionType,
        amountMinor: Long,
        categoryId: String?,
        note: String?,
        date: Long,
        currency: String = "VND"
    ): AppResult<Unit>{
        val userId = authRepository.getCurrentUserId()
            ?: return AppResult.Failure(TransactionError.NotAuthenticated)

        if(amountMinor <= 0){
            return AppResult.Failure(TransactionError.InvalidAmount)
        }

        if(categoryId.isNullOrBlank()){
            return AppResult.Failure(TransactionError.CategoryNotSelected)
        }

        return try {
            transactionRepository.createTransaction(
                TransactionModel(
                    id = "",
                    userId = userId,
                    type = type,
                    money = Money(amountMinor, currency),
                    categoryId = categoryId,
                    note = note?.ifBlank { null },
                    createdAt = date,
                    updatedAt = System.currentTimeMillis(),
                    isPendingSync = true
                )
            )
            AppResult.Success(Unit)
        }catch (e : Exception){
            AppResult.Failure(TransactionError.UnknownError(e.message ?: "Unknown error"))
        }
    }
}