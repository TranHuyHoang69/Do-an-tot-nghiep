package com.example.moneymatev2.data.repository

import com.example.moneymatev2.data.local.dao.CategoryDao
import com.example.moneymatev2.data.local.entity.CategoryEntity
import com.example.moneymatev2.data.local.entity.PendingOperation
import com.example.moneymatev2.data.local.entity.SyncStatus
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.data.remote.dto.toDto
import com.example.moneymatev2.data.remote.sync.SyncTrigger
import com.example.moneymatev2.domain.model.CategoryModel
import com.example.moneymatev2.domain.repository.CategoryRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

object DefaultCategoryIds {
    const val SPEND_FOOD = "8f14e45f-0000-0000-0000-000000000001"
    const val SPEND_TRANSPORT = "8f14e45f-0000-0000-0000-000000000002"
    const val SPEND_SHOPPING = "8f14e45f-0000-0000-0000-000000000003"
    const val INCOME_SALARY = "8f14e45f-0000-0000-0000-000000000004"
}

class CategoryRepositoryImpl @Inject constructor(
    private val dao: CategoryDao,
    private val firestore: FirebaseFirestore,
    private val syncTrigger: SyncTrigger
): CategoryRepository {

    // Đảm bảo seedDefaultCategoriesIfNeeded không bị 2 coroutine chạy chồng lên nhau
    // (VD SignInWithGoogleUseCase + ObserveAuthStateUseCase cùng trigger lúc đăng nhập),
    // nguyên nhân gây SQLiteConstraintException: UNIQUE constraint failed: categories.localId.
    private val seedMutex = Mutex()

    override fun getActiveCategories(userId: String): Flow<List<CategoryModel>> =
        dao.getActiveCategories(userId).map { list -> list.map { it.toModel() } }

    override fun getActiveCategoriesByType(
        userId: String,
        type: TransactionType
    ): Flow<List<CategoryModel>> =
        dao.getActiveCategoriesByType(userId, type).map { list -> list.map { it.toModel() } }

    override suspend fun createCategory(category: CategoryModel, userId: String) {
        val now = System.currentTimeMillis()
        dao.insertCategory(
            CategoryEntity(
                localId = category.id.ifEmpty { UUID.randomUUID().toString() },
                stableId = null,
                userId = userId,
                name = category.name,
                type = category.type,
                iconKey = category.iconKey,
                colorHex = category.colorHex,
                isDefault = false,
                syncStatus = SyncStatus.PENDING,
                pendingOperation = PendingOperation.CREATE,
                createdAt = now,
                updatedAt = now
            )
        )
        syncTrigger.requestImmediateSync()
    }

    override suspend fun updateCategory(category: CategoryModel) {
        val existing = dao.getCategoryByLocalIdOnce(category.id)
            ?: throw IllegalArgumentException("Category with id ${category.id} does not exist")
        dao.updateCategory(
            existing.copy(
                name = category.name,
                iconKey = category.iconKey,
                colorHex = category.colorHex,
                isArchived = category.isArchived,
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING,
                pendingOperation = PendingOperation.UPDATE
            )
        )
        syncTrigger.requestImmediateSync()
    }

    override suspend fun archiveCategory(id: String) {
        dao.archiveCategory(id, System.currentTimeMillis())
        syncTrigger.requestImmediateSync()
    }

    override suspend fun seedDefaultCategoriesIfNeeded(userId: String) {
        seedMutex.withLock {
            val existing = dao.getByStableId(userId, "spend_food")
            if (existing != null) return@withLock

            val now = System.currentTimeMillis()
            val defaults = listOf(
                Quadruple(DefaultCategoryIds.SPEND_FOOD, TransactionType.EXPENSE, "spend_food", "ic_food"),
                Quadruple(DefaultCategoryIds.SPEND_TRANSPORT, TransactionType.EXPENSE, "spend_transport", "ic_car"),
                Quadruple(DefaultCategoryIds.SPEND_SHOPPING, TransactionType.EXPENSE, "spend_shopping", "ic_shop"),
                Quadruple(DefaultCategoryIds.INCOME_SALARY, TransactionType.INCOME, "income_salary", "ic_money")
            )
            defaults.forEach { (id, type, stableId, iconKey) ->
                dao.insertCategory(
                    CategoryEntity(
                        localId = id,
                        stableId = stableId,
                        userId = userId,
                        name = stableId,
                        type = type,
                        iconKey = iconKey,
                        colorHex = "#9E9E9E",
                        isDefault = true,
                        syncStatus = SyncStatus.PENDING,
                        pendingOperation = PendingOperation.CREATE,
                        createdAt = now, updatedAt = now
                    )
                )
            }
            syncTrigger.requestImmediateSync()
        }
    }

    override suspend fun syncPendingCategories(userId: String) {
        val pending = dao.getPendingCategoriesForSync(userId)
        for (cat in pending) {
            try {
                firestore.collection("users").document(userId)
                    .collection("categories").document(cat.localId)
                    .set(cat.toDto()).await()
            } catch (e: Exception) {
                // Handle error, maybe log it
            }
        }
    }

    private fun CategoryEntity.toModel() = CategoryModel(
        id = localId,
        stableId = stableId,
        name = name,
        type = type,
        iconKey = iconKey,
        colorHex = colorHex,
        isDefault = isDefault,
        isArchived = isArchived
    )

    data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}