package com.example.moneymatev2.domain.usecase.category

import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.AppResult
import com.example.moneymatev2.domain.model.CategoryModel
import com.example.moneymatev2.domain.model.TransactionError
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.CategoryRepository
import javax.inject.Inject
class CreateCategoryUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        name: String,
        type: TransactionType,
        iconKey: String,
        colorHex: String
    ): AppResult<Unit>{
        val userId = authRepository.getCurrentUserId()
            ?: return AppResult.Failure(TransactionError.NotAuthenticated)

        if (name.isBlank()) {
            return AppResult.Failure(TransactionError.InvalidCategoryName)
        }

        val category = CategoryModel(
            id = "",
            stableId = null,
            name = name.trim(),
            type = type,
            iconKey = iconKey,
            colorHex = colorHex,
            isDefault = false,
            isArchived = false
        )
        categoryRepository.createCategory(category, userId)
        return AppResult.Success(Unit)
    }
}