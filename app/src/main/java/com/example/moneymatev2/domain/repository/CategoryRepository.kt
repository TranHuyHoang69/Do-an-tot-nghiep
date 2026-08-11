package com.example.moneymatev2.domain.repository

import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.CategoryModel
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {

    fun getActiveCategories(userId: String): Flow<List<CategoryModel>>

    fun getActiveCategoriesByType(userId: String, type: TransactionType): Flow<List<CategoryModel>>

    suspend fun createCategory(category: CategoryModel, userId: String)

    suspend fun updateCategory(category: CategoryModel)

    suspend fun archiveCategory(id: String)

    suspend fun seedDefaultCategoriesIfNeeded(userId: String)

    suspend fun syncPendingCategories(userId: String)
}