package com.example.moneymatev2.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.moneymatev2.data.local.entity.BudgetConflictEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetConflictDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(conflict: BudgetConflictEntity)

    @Query("SELECT * FROM budget_conflicts WHERE userId = :userId ORDER BY capturedAt DESC")
    fun observeForUser(userId: String): Flow<List<BudgetConflictEntity>>

    @Query("SELECT * FROM budget_conflicts WHERE userId = :userId AND budgetLocalId = :budgetId ORDER BY capturedAt DESC")
    suspend fun getForBudget(userId: String, budgetId: String): List<BudgetConflictEntity>
}
