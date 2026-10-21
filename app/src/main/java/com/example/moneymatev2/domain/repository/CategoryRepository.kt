package com.example.moneymatev2.domain.repository

import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.CategoryModel
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {

    data class DeletionUsage(
        val transactionCount: Int,
        val hasActiveBudget: Boolean,
        val hasRecurringTransaction: Boolean
    ) {
        val isBlocked: Boolean get() = hasActiveBudget || hasRecurringTransaction
    }

    enum class ArchiveResult { ARCHIVED, INVALID, IN_USE, REPLACEMENT_REQUIRED }

    fun getAllCategories(userId: String): Flow<List<CategoryModel>>

    suspend fun getCategory(userId: String, id: String): CategoryModel?

    fun getActiveCategories(userId: String): Flow<List<CategoryModel>>

    fun getActiveCategoriesByType(userId: String, type: TransactionType): Flow<List<CategoryModel>>

    suspend fun createCategory(category: CategoryModel, userId: String)

    suspend fun updateCategory(category: CategoryModel)

    suspend fun archiveCategory(userId: String, id: String)

    suspend fun getDeletionUsage(userId: String, id: String): DeletionUsage

    suspend fun archiveWithReplacement(userId: String, id: String, replacementId: String?): ArchiveResult

    suspend fun seedDefaultCategoriesIfNeeded(userId: String)

    suspend fun syncPendingCategories(userId: String)
}
