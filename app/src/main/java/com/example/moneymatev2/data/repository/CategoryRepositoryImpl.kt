package com.example.moneymatev2.data.repository

import com.example.moneymatev2.data.local.dao.CategoryDao
import com.example.moneymatev2.data.local.entity.CategoryEntity
import com.example.moneymatev2.data.local.entity.PendingOperation
import com.example.moneymatev2.data.local.entity.SyncStatus
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.data.remote.dto.toDto
import com.example.moneymatev2.data.remote.sync.SyncTrigger
import com.example.moneymatev2.data.remote.sync.PendingUploadRunner
import com.example.moneymatev2.data.remote.sync.syncFailureSummary
import com.example.moneymatev2.domain.model.CategoryModel
import com.example.moneymatev2.domain.repository.CategoryRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject

object DefaultCategoryIds {
    // Legacy fixed IDs are kept for reading existing local/cloud data. New seeded
    // defaults use account-scoped IDs to avoid cross-account Room primary-key collisions.
    const val SPEND_FOOD = "8f14e45f-0000-0000-0000-000000000001"
    const val SPEND_TRANSPORT = "8f14e45f-0000-0000-0000-000000000002"
    const val SPEND_SHOPPING = "8f14e45f-0000-0000-0000-000000000003"
    const val INCOME_SALARY = "8f14e45f-0000-0000-0000-000000000004"

    fun forUser(userId: String, stableId: String): String {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest("$userId:$stableId".toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it) }
        return "default_${stableId}_$hash"
    }
}

class CategoryRepositoryImpl @Inject constructor(
    private val dao: CategoryDao,
    private val firestore: FirebaseFirestore,
    private val syncTrigger: SyncTrigger,
    private val uploadRunner: PendingUploadRunner
): CategoryRepository {

    // Đảm bảo seedDefaultCategoriesIfNeeded không bị 2 coroutine chạy chồng lên nhau
    // (VD SignInWithGoogleUseCase + ObserveAuthStateUseCase cùng trigger lúc đăng nhập),
    // nguyên nhân gây SQLiteConstraintException: UNIQUE constraint failed: categories.localId.
    private val seedMutex = Mutex()

    override fun getAllCategories(userId: String): Flow<List<CategoryModel>> =
        dao.getAllCategories(userId).map { rows -> rows.map { it.toModel() } }

    override suspend fun getCategory(userId: String, id: String): CategoryModel? =
        dao.getCategoryByLocalIdOnce(userId, id)?.toModel()

    override fun getActiveCategories(userId: String): Flow<List<CategoryModel>> =
        dao.getActiveCategories(userId).map { list -> list.map { it.toModel() } }

    override fun getActiveCategoriesByType(
        userId: String,
        type: TransactionType
    ): Flow<List<CategoryModel>> =
        dao.getActiveCategoriesByType(userId, type).map { list -> list.map { it.toModel() } }

    override suspend fun createCategory(category: CategoryModel, userId: String) {
        require(category.userId == userId && !category.isMissing)
        uploadRunner.capture(userId)
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
        uploadRunner.capture(category.userId)
        require(!category.isMissing)
        val existing = dao.getCategoryByLocalIdOnce(category.userId, category.id)
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

    override suspend fun archiveCategory(userId: String, id: String) {
        uploadRunner.capture(userId)
        dao.archiveCategory(userId, id, System.currentTimeMillis())
        syncTrigger.requestImmediateSync()
    }

    override suspend fun seedDefaultCategoriesIfNeeded(userId: String) {
        seedMutex.withLock {
            val now = System.currentTimeMillis()
            val defaults = listOf(
                Triple(TransactionType.EXPENSE, "spend_food", "ic_food"),
                Triple(TransactionType.EXPENSE, "spend_transport", "ic_car"),
                Triple(TransactionType.EXPENSE, "spend_shopping", "ic_shop"),
                Triple(TransactionType.INCOME, "income_salary", "ic_money")
            )
            var inserted = false
            defaults.forEach { (type, stableId, iconKey) ->
                if (dao.getByStableId(userId, stableId) != null) return@forEach

                dao.insertCategory(
                    CategoryEntity(
                        localId = DefaultCategoryIds.forUser(userId, stableId),
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
                inserted = true
            }
            if (inserted) syncTrigger.requestImmediateSync()
        }
    }


    override suspend fun syncPendingCategories(userId: String) {
        val session = uploadRunner.capture(userId)
        val pending = dao.getPendingCategoriesForSync(userId)
        var firstError: Exception? = null
        for (row in pending) {
            try {
                uploadRunner.run(
                    session = session,
                    snapshot = row,
                    readCurrent = { dao.getCategoryByLocalIdOnce(userId, row.localId) },
                    upload = {
                        val doc = firestore.collection("users").document(userId)
                            .collection("categories").document(row.localId)
                        doc.set(row.toDto()).await()
                    },
                    acknowledge = {
                        dao.markSyncedIfUnchanged(row.localId, userId, row.updatedAt, System.currentTimeMillis())
                    },
                    recordFailure = { error ->
                        dao.markSyncFailedIfUnchanged(
                            row.localId, userId, row.updatedAt,
                            error.syncFailureSummary(), System.currentTimeMillis()
                        )
                    }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("SyncUpload", "SYNC_FAILED collection=categories ${e.syncFailureSummary()}")
                if (firstError == null) firstError = e else if (firstError !== e) firstError.addSuppressed(e)
            }
        }
        firstError?.let { throw it }
    }


    private fun CategoryEntity.toModel() = CategoryModel(
        userId = userId,
        id = localId,
        stableId = stableId,
        name = name,
        type = type,
        iconKey = iconKey,
        colorHex = colorHex,
        isDefault = isDefault,
        isArchived = isArchived
    )
}
