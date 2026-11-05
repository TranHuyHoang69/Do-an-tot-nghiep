package com.example.moneymatev2.domain.usecase.category

import com.example.moneymatev2.domain.model.AppResult
import com.example.moneymatev2.domain.model.TransactionError
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.CategoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import javax.inject.Inject

class EditCategoryUseCase @Inject constructor(
    private val categories: CategoryRepository,
    private val auth: AuthRepository
) {
    suspend operator fun invoke(
        userId: String, categoryId: String, name: String, iconKey: String, colorHex: String
    ): AppResult<Unit> {
        if (userId.isBlank() || userId != auth.getCurrentUserId())
            return AppResult.Failure(TransactionError.NotAuthenticated)
        if (name.isBlank()) return AppResult.Failure(TransactionError.InvalidCategoryName)
        return try {
            val existing = categories.getCategory(userId, categoryId)
                ?.takeUnless { it.isArchived || it.isMissing }
                ?: return AppResult.Failure(TransactionError.CategoryNotSelected)
            if (userId != auth.getCurrentUserId())
                return AppResult.Failure(TransactionError.NotAuthenticated)
            categories.updateCategory(existing.copy(
                name = name.trim(), iconKey = iconKey, colorHex = colorHex
            ))
            AppResult.Success(Unit)
        } catch (e: CancellationException) {
            if (!currentCoroutineContext().isActive) throw e
            AppResult.Failure(TransactionError.NotAuthenticated)
        } catch (e: Exception) {
            AppResult.Failure(TransactionError.UnknownError(e.message.orEmpty()))
        }
    }
}
