package com.example.moneymatev2.domain.usecase.category

import com.example.moneymatev2.domain.model.AppResult
import com.example.moneymatev2.domain.model.TransactionError
import com.example.moneymatev2.domain.repository.CategoryRepository
import com.example.moneymatev2.domain.repository.TransactionRepository
import javax.inject.Inject

/**
 * Cần cả 2 Repository (Category + Transaction) -> đặt ở UseCase, không đặt riêng
 * trong 1 Repository nào, đúng theo nguyên tắc đã áp dụng cho GetMonthlyBudgetOverviewUseCase.
 */
class DeleteCategoryUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository
) {
    suspend operator fun invoke(
        userId: String,
        categoryId: String,
        replacementCategoryId: String
    ): AppResult<Unit> {
        if (categoryId == replacementCategoryId) {
            return AppResult.Failure(
                TransactionError.UnknownError("Không thể chọn chính danh mục đang xóa làm danh mục thay thế")
            )
        }
        transactionRepository.reassignCategory(userId, categoryId, replacementCategoryId)
        categoryRepository.archiveCategory(categoryId) // tái dùng archiveCategory() có sẵn, không hard-delete
        return AppResult.Success(Unit)
    }
}