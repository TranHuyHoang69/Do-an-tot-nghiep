package com.example.moneymatev2.domain.usecase.category

import com.example.moneymatev2.domain.model.AppResult
import com.example.moneymatev2.domain.model.TransactionError
import com.example.moneymatev2.domain.repository.CategoryRepository
import com.example.moneymatev2.domain.repository.TransactionRepository
import com.example.moneymatev2.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Cần cả 2 Repository (Category + Transaction) -> đặt ở UseCase, không đặt riêng
 * trong 1 Repository nào, đúng theo nguyên tắc đã áp dụng cho GetMonthlyBudgetOverviewUseCase.
 */
class DeleteCategoryUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        userId: String,
        categoryId: String,
        replacementCategoryId: String
    ): AppResult<Unit> {
        if (authRepository.getCurrentUserId() != userId) {
            return AppResult.Failure(TransactionError.NotAuthenticated)
        }
        val source = categoryRepository.getCategory(userId, categoryId)
        val replacement = categoryRepository.getCategory(userId, replacementCategoryId)
        if (source == null || replacement == null || replacement.isArchived || source.type != replacement.type) {
            return AppResult.Failure(TransactionError.CategoryNotSelected)
        }
        if (categoryId == replacementCategoryId) {
            return AppResult.Failure(
                TransactionError.UnknownError("Không thể chọn chính danh mục đang xóa làm danh mục thay thế")
            )
        }
        transactionRepository.reassignCategory(userId, categoryId, replacementCategoryId)
        categoryRepository.archiveCategory(userId, categoryId)
        return AppResult.Success(Unit)
    }
}
