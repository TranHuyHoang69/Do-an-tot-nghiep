package com.example.moneymatev2.domain.usecase.transaction

import com.example.moneymatev2.domain.model.TransactionWithCategory
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.CategoryRepository
import com.example.moneymatev2.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class GetTransactionWithCategoryUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val authRepository: AuthRepository
) {
    operator fun invoke(): Flow<List<TransactionWithCategory>> {
        // Thay getCurrentUserId() (snapshot 1 lần) bằng observeAuthState() + flatMapLatest --
        // mỗi khi auth state đổi (đăng xuất, đăng nhập lại bằng tài khoản khác), toàn bộ
        // combine() bên trong được HỦY và CHẠY LẠI với userId mới, không cần tạo lại ViewModel.
        return authRepository.observeAuthState().flatMapLatest { user ->
            val userId = user?.userId ?: return@flatMapLatest flowOf(emptyList())

            combine(
                transactionRepository.getAllTransactions(userId),
                categoryRepository.getActiveCategories(userId)
            ) { transactions, categories ->
                val categoryById = categories.associateBy { it.id }
                transactions
                    .sortedByDescending { it.createdAt }
                    .map { tx -> TransactionWithCategory(tx, categoryById[tx.categoryId]) }
            }
        }
    }
}