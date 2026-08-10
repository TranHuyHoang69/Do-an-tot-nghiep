package com.example.moneymatev2.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.moneymatev2.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("""
        SELECT * FROM categories
        WHERE userId = :userId AND isArchived = 0
        ORDER BY name DESC
    """)
    fun getActiveCategories(userId: String): Flow<List<CategoryEntity>>


}