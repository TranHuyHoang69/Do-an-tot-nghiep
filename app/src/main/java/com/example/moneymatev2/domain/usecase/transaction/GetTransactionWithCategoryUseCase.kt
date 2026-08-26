package com.example.moneymatev2.domain.usecase.transaction

import com.example.moneymatev2.domain.model.TransactionWithCategory
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.CategoryRepository
import com.example.moneymatev2.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class GetTransactionWithCategoryUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val authRepository: AuthRepository
) {
    operator fun invoke(): Flow<List<TransactionWithCategory>>{
        val userId = authRepository.getCurrentUserId() ?: return flowOf(emptyList())

        return combine(
            transactionRepository.getAllTransactions(userId),
            categoryRepository.getActiveCategories(userId)
        ){transaction, catogory ->
            val categoryId = catogory.associateBy { it.id }
            transaction.sortedByDescending { it.createdAt }.map { tx -> TransactionWithCategory(tx, categoryId[tx.categoryId]) }
        }
    }
}