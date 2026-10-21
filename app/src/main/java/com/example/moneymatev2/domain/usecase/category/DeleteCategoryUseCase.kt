package com.example.moneymatev2.domain.usecase.category

import com.example.moneymatev2.domain.model.AppResult
import com.example.moneymatev2.domain.model.TransactionError
import com.example.moneymatev2.domain.repository.CategoryRepository
import com.example.moneymatev2.domain.repository.AuthRepository
import javax.inject.Inject

/** Validates the session before the repository's atomic reassignment and archive. */
class DeleteCategoryUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        userId: String,
        categoryId: String,
        replacementCategoryId: String? = null
    ): AppResult<Unit> {
        if (authRepository.getCurrentUserId() != userId) {
            return AppResult.Failure(TransactionError.NotAuthenticated)
        }
        return when (categoryRepository.archiveWithReplacement(userId, categoryId, replacementCategoryId)) {
            CategoryRepository.ArchiveResult.ARCHIVED -> AppResult.Success(Unit)
            CategoryRepository.ArchiveResult.IN_USE -> AppResult.Failure(TransactionError.CategoryInUse)
            CategoryRepository.ArchiveResult.INVALID -> AppResult.Failure(TransactionError.CategoryNotSelected)
            CategoryRepository.ArchiveResult.REPLACEMENT_REQUIRED ->
                AppResult.Failure(TransactionError.CategoryReplacementRequired)
        }
    }
}
