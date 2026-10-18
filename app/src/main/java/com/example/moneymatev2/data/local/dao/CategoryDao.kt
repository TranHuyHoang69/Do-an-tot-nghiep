    package com.example.moneymatev2.data.local.dao

    import androidx.room.Dao
    import androidx.room.Insert
    import androidx.room.OnConflictStrategy
    import androidx.room.Query
    import androidx.room.Update
    import com.example.moneymatev2.data.local.entity.CategoryEntity
    import com.example.moneymatev2.data.local.entity.TransactionType
    import kotlinx.coroutines.flow.Flow

    @Dao
    interface CategoryDao {

        @Query("SELECT * FROM categories WHERE userId = :userId ORDER BY name ASC")
        fun getAllCategories(userId: String): Flow<List<CategoryEntity>>

        @Query("""
            SELECT * FROM categories
            WHERE userId = :userId AND isArchived = 0
            ORDER BY name ASC
        """)
        fun getActiveCategories(userId: String): Flow<List<CategoryEntity>>

        @Query("""
            SELECT * FROM categories
            WHERE userId = :userId AND type = :type AND isArchived = 0
            ORDER BY name ASC
        """)
        fun getActiveCategoriesByType(userId: String, type: TransactionType): Flow<List<CategoryEntity>>

        @Query("SELECT * FROM categories WHERE userId = :userId AND localId = :localId")
        fun getCategoryByLocalId(userId: String, localId: String): Flow<CategoryEntity?>


        @Query("SELECT * FROM categories WHERE userId = :userId AND localId = :localId")
        suspend fun getCategoryByLocalIdOnce(userId: String, localId: String): CategoryEntity?

        @Query("SELECT * FROM categories WHERE userId = :userId AND stableId = :stableId LIMIT 1")
        suspend fun getByStableId(userId: String, stableId: String): CategoryEntity?

        @Query("""
            SELECT * FROM categories
            WHERE userId = :userId AND pendingOperation != 'NONE'
            ORDER BY updatedAt ASC
        """)
        suspend fun getPendingCategoriesForSync(userId: String): List<CategoryEntity>

        @Insert(onConflict = OnConflictStrategy.ABORT)
        suspend fun insertCategory(category: CategoryEntity)

        @Update
        suspend fun updateCategory(category: CategoryEntity)

        // Archive thay vì xóa cứng — bảo toàn hiển thị đúng cho các transaction cũ
        @Query("""
            UPDATE categories
            SET isArchived = 1, syncStatus = 'PENDING', pendingOperation = 'UPDATE', updatedAt = :updatedAt
            WHERE userId = :userId AND localId = :localId
        """)
        suspend fun archiveCategory(userId: String, localId: String, updatedAt: Long)

        @Query("""
            UPDATE categories
            SET syncStatus = 'SYNCED', pendingOperation = 'NONE',
                 remoteUpdatedAt = :remoteUpdatedAt,
                lastSyncError = NULL, retryCount = 0
            WHERE userId = :userId AND localId = :localId
        """)
        suspend fun markSynced(userId: String, localId: String, remoteUpdatedAt: Long)

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun upsertFromRemote(categories: List<CategoryEntity>)

        @Query("DELETE FROM categories WHERE userId = :userId")
        suspend fun clearAllForUser(userId: String)

        @Query("""UPDATE categories SET syncStatus='SYNCED',pendingOperation='NONE',remoteUpdatedAt=:remoteUpdatedAt,lastSyncError=NULL,retryCount=0 WHERE localId=:localId AND userId=:userId AND updatedAt=:expectedUpdatedAt AND pendingOperation!='NONE'""")
        suspend fun markSyncedIfUnchanged(localId: String, userId: String, expectedUpdatedAt: Long, remoteUpdatedAt: Long): Int

        @Query("""
            UPDATE categories
            SET syncStatus = 'FAILED',
                lastSyncError = :error,
                retryCount = retryCount + 1,
                lastSyncAttemptAt = :attemptAt
            WHERE localId = :localId
              AND userId = :userId
              AND updatedAt = :expectedUpdatedAt
              AND pendingOperation != 'NONE'
        """)
        suspend fun markSyncFailedIfUnchanged(
            localId: String,
            userId: String,
            expectedUpdatedAt: Long,
            error: String,
            attemptAt: Long
        ): Int
    }
