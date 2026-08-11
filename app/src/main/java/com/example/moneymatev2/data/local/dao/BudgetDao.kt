package com.example.moneymatev2.data.local.dao

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.moneymatev2.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

interface BudgetDao {
    @Query("""
        SELECT b.* FROM budgets b
        INNER JOIN (categoryLocalId, MAX(effectiveYear * 100 + effectiveMonth) AS maxKey
        FROM budgets
        WHERE userId = :userId
            AND (effectiveYear * 100 + effectiveMonth) <= (:year * 100 + :month)
        GROUP BY categoryLocalId
        ) latest ON b.categoryLocalId = latest.categoryLocalId
            AND (b.effectiveYear * 100 + b.effectiveMonth) = latest.maxKey
        WHERE b.userId = :userId AND b.isArchived = 0
    """)
    fun getActiveBudgetsForMonth(userId: String, year: Int, month: Int): Flow<List<BudgetEntity>>

    @Query("""
        SELECT * FROM budgets
        WHERE userId = :userId AND categoryLocalId = :categoryLocalId
            AND effectiveMonth = :month AND effectiveYear = :year
        LIMIT 1
    """)
    suspend fun getBudgetRowForExactMonth(
        userId: String, categoryLocalId: Long, year: Int, month: Int
    ): BudgetEntity?

    @Query("""
        SELECT * FROM budgets
        WHERE userId = :userId AND pendingOperation != 'NONE'
        ORDER BY updatedAt ASC
    """)
    suspend fun getPendingBudgetsForSync(userId: String): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBudget(budget: BudgetEntity)

    @Update
    suspend fun updateBudget(budget: BudgetEntity)

    @Query("""
        UPDATE budgets
        SET syncStatus = 'SYNCED', pendingOperation = 'NONE',
        remoteUpdatedAt = :remoteUpdatedAt, lastSyncError = NULL, retryCount = 0
        WHERE localId = :localId
    """)
    suspend fun markSynced(localId: String, remoteUpdatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFromRemote(budget: List<BudgetEntity>)
}