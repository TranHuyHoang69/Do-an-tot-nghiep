package com.example.moneymatev2.data.remote.sync

import android.util.Log
import androidx.room.withTransaction
import com.example.moneymatev2.data.local.AppDatabase
import com.example.moneymatev2.data.local.dao.BudgetDao
import com.example.moneymatev2.data.local.dao.CategoryDao
import com.example.moneymatev2.data.local.dao.ReminderDao
import com.example.moneymatev2.data.local.dao.TransactionDao
import com.example.moneymatev2.data.local.dao.RecurringTransactionDao
import com.example.moneymatev2.data.local.entity.*
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.CloudPullRepository
import com.example.moneymatev2.domain.model.CloudPullReport
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.firebase.Timestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A server-confirmed full pull for one authenticated account.
 *
 * Policy: unsynced local changes win; server is authoritative for clean local rows.
 * Never use an offline-cache snapshot for deletion reconciliation.
 * All downloaded data are parsed BEFORE touching Room.
 *
 * V1: full-collection pulls; suitable for small/medium personal data sets.
 */
@Singleton
class CloudPullService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val db: AppDatabase,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val reminderDao: ReminderDao,
    private val recurringDao: RecurringTransactionDao,
    private val sessions: SyncSessionGuard
) : CloudPullRepository {
    override suspend fun pull(userId: String): CloudPullReport {
        require(userId.isNotBlank())
        val session = sessions.capture(userId)
        assertAccount(userId)

        val categories = pullCollection(userId, "categories") { it.toCategoryEntity(userId) }
        val transactions = pullCollection(userId, "transactions") { it.toTransactionEntity(userId) }
        val budgets = pullCollection(userId, "budgets") { it.toBudgetEntity(userId) }
        val reminders = pullCollection(userId, "reminders") { it.toReminderEntity(userId) }
        val recurring = pullCollection(userId, "recurring_transactions") { it.toRecurringEntity(userId) }
        val failures = listOf(categories, transactions, budgets, reminders, recurring).mapNotNull { it.failure }

        assertAccount(userId)
        // Failed collections are not merged or pruned. Successful SERVER snapshots may
        // still refresh Room so the UI can recover visible data while WorkManager retries.
        db.withTransaction {
            sessions.check(session)

            var merged = 0
            var skipped = 0
            if (categories.isComplete) for (row in categories.rows) {
                val old = categoryDao.getCategoryByLocalIdOnce(userId, row.localId)
                if (shouldMerge(old?.userId, old?.pendingOperation, old?.remoteUpdatedAt, row.remoteUpdatedAt, userId)) {
                    check(old == null || old.userId == userId) { "Category ID belongs to another account" }
                    categoryDao.upsertFromRemote(listOf(row))
                    merged++
                } else {
                    skipped++
                }
            }
            logMerge("categories", categories, merged, skipped, 0)

            merged = 0
            skipped = 0
            var pruned = 0
            if (transactions.isComplete) for (row in transactions.rows) {
                val old = transactionDao.getRowForCloudMerge(row.localId)
                if (shouldMerge(old?.userId, old?.pendingOperation, old?.remoteUpdatedAt, row.remoteUpdatedAt, userId)) {
                    transactionDao.upsertFromRemote(listOf(row))
                    merged++
                } else {
                    skipped++
                }
            }
            val transactionIds = transactions.rows.mapTo(hashSetOf()) { it.localId }
            if (transactions.isComplete) for (row in transactionDao.getCleanRowsForCloudMerge(userId)) {
                if (row.localId !in transactionIds) {
                    transactionDao.deleteCleanRowForCloudMerge(row.localId, userId)
                    pruned++
                }
            }
            logMerge("transactions", transactions, merged, skipped, pruned)

            merged = 0
            skipped = 0
            if (budgets.isComplete) for (row in budgets.rows) {
                val old = budgetDao.getRowForCloudMerge(row.localId)
                if (!shouldMerge(old?.userId, old?.pendingOperation, old?.remoteUpdatedAt, row.remoteUpdatedAt, userId)) {
                    skipped++
                    continue
                }

                // budgets has UNIQUE(userId,categoryLocalId,effectiveMonth,effectiveYear).
                // If the other device created the same monthly budget with a different ID,
                // never overwrite that device's unsynced local edit.
                val sameMonth = budgetDao.getBudgetRowForExactMonth(userId, row.categoryLocalId, row.effectiveYear, row.effectiveMonth)
                if (sameMonth != null && sameMonth.localId != row.localId) {
                    if (sameMonth.pendingOperation != PendingOperation.NONE) {
                        skipped++
                        continue
                    }
                    budgetDao.deleteCleanRowForCloudMerge(sameMonth.localId, userId)
                }
                budgetDao.upsertFromRemote(listOf(row))
                merged++
            }
            logMerge("budgets", budgets, merged, skipped, 0)

            merged = 0
            skipped = 0
            pruned = 0
            if (reminders.isComplete) for (row in reminders.rows) {
                val old = reminderDao.getReminderByLocalIdOnce(row.localId)
                if (shouldMerge(old?.userId, old?.pendingOperation, old?.remoteUpdatedAt, row.remoteUpdatedAt, userId)) {
                    reminderDao.upsertFromRemote(listOf(row))
                    merged++
                } else {
                    skipped++
                }
            }
            val reminderIds = reminders.rows.mapTo(hashSetOf()) { it.localId }
            if (reminders.isComplete) for (row in reminderDao.getCleanRowsForCloudMerge(userId)) {
                if (row.localId !in reminderIds) {
                    reminderDao.deleteCleanRowForCloudMerge(row.localId, userId)
                    pruned++
                }
            }
            logMerge("reminders", reminders, merged, skipped, pruned)

            merged = 0
            skipped = 0
            pruned = 0
            if (recurring.isComplete) for (row in recurring.rows) {
                val old = recurringDao.getRowForCloudMerge(row.localId)
                if (shouldMerge(old?.userId, old?.pendingOperation, old?.remoteUpdatedAt, row.remoteUpdatedAt, userId)) {
                    recurringDao.upsertFromRemote(row)
                    merged++
                } else {
                    skipped++
                }
            }
            val recurringIds = recurring.rows.mapTo(hashSetOf()) { it.localId }
            if (recurring.isComplete) for (row in recurringDao.getCleanRowsForCloudMerge(userId)) {
                if (row.localId !in recurringIds) {
                    recurringDao.deleteCleanRowForCloudMerge(row.localId, userId)
                    pruned++
                }
            }
            logMerge("recurring_transactions", recurring, merged, skipped, pruned)
            sessions.check(session)
        }

        if (failures.isNotEmpty()) {
            throw CloudPullException(failures)
        }

        return CloudPullReport(
            categories.rows.size,
            transactions.rows.size,
            budgets.rows.size,
            reminders.rows.size,
            recurring.rows.size
        )
    }

    private suspend fun fetch(userId: String, collection: String): QuerySnapshot {
        assertAccount(userId)
        return firestore.collection("users").document(userId)
            .collection(collection).get(Source.SERVER).await()
    }

    private suspend fun <T> pullCollection(
        userId: String,
        collection: String,
        mapper: (DocumentSnapshot) -> T
    ): CollectionPull<T> {
        Log.i(TAG, "SYNC_PULL_START collection=$collection")
        return try {
            val rows = fetch(userId, collection).documents.map { doc ->
                validateOwner(doc, userId)
                mapper(doc)
            }
            Log.i(TAG, "SYNC_PULL_RESULT collection=$collection remoteCount=${rows.size}")
            CollectionPull(collection = collection, rows = rows)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(
                TAG,
                "SYNC_FAILED stage=pull collection=$collection ${e.syncFailureSummary()}"
            )
            CollectionPull(
                collection = collection,
                rows = emptyList(),
                failure = CloudPullFailure(
                    collection = collection,
                    type = e.javaClass.simpleName,
                    message = e.syncFailureSummary(),
                    permanent = e.isPermanentPullError()
                )
            )
        }
    }

    private fun <T> logMerge(
        collection: String,
        pull: CollectionPull<T>,
        merged: Int,
        skipped: Int,
        pruned: Int
    ) {
        if (!pull.isComplete) {
            Log.w(TAG, "SYNC_MERGE_RESULT collection=$collection skippedBecausePullFailed=true")
            return
        }
        Log.i(
            TAG,
            "SYNC_MERGE_RESULT collection=$collection remoteCount=${pull.rows.size} merged=$merged skipped=$skipped pruned=$pruned"
        )
    }

    private fun assertAccount(userId: String) {
        if (authRepository.getCurrentUserId() != userId) {
            throw CancellationException("SYNC_AUTH_SESSION_CHANGED")
        }
    }

    private fun validateOwner(doc: DocumentSnapshot, userId: String) {
        // Existing DTOs omit userId; if present, it MUST match the authenticated path.
        val storedOwner = doc.getString("userId")
        require(storedOwner == null || storedOwner == userId) { "Mismatching userId at ${doc.reference.path}" }
    }

    private fun shouldMerge(
        localUserId: String?, localOperation: PendingOperation?,
        localRemoteUpdatedAt: Long?, incomingRemoteUpdatedAt: Long?, userId: String
    ): Boolean {
        if (localUserId == null) return true
        check(localUserId == userId) { "A local ID is shared by different accounts" }
        if (localOperation != PendingOperation.NONE) return false // includes CREATE,UPDATE,DELETE
        // Reject objectively older remote snapshots, but not equal timestamps.
        return localRemoteUpdatedAt == null || incomingRemoteUpdatedAt == null || incomingRemoteUpdatedAt >= localRemoteUpdatedAt
    }

    private fun DocumentSnapshot.toTransactionEntity(userId: String): TransactionEntity {
        val data = requireData()
        val time = parseRemoteTime(data, reference.path)
        return TransactionEntity(
            localId = id,
            userId = userId,
            type = TransactionType.valueOf(text(data, "type") ?: TransactionType.EXPENSE.name),
            amountMinor = requiredLong(data, "amountMinor", reference.path),
            currency = text(data, "currency") ?: "VND",
            categoryLocalId = requiredText(data, "categoryLocalId", reference.path),
            note = text(data, "note"),
            isDeleted = bool(data, "isDeleted") ?: false,
            createdAt = long(data, "createdAt") ?: 0L,
            updatedAt = time.takeIf { it > 0 } ?: System.currentTimeMillis(),
            syncStatus = SyncStatus.SYNCED,
            pendingOperation = PendingOperation.NONE,
            remoteUpdatedAt = time.takeIf { it > 0 }
        )
    }

    private fun DocumentSnapshot.toCategoryEntity(userId: String): CategoryEntity {
        val data = requireData()
        val time = parseRemoteTime(data, reference.path)
        return CategoryEntity(
            localId = id,
            stableId = text(data, "stableId"),
            userId = userId,
            name = requiredText(data, "name", reference.path),
            type = TransactionType.valueOf(text(data, "type") ?: TransactionType.EXPENSE.name),
            iconKey = text(data, "iconKey") ?: "",
            colorHex = text(data, "colorHex") ?: "",
            isDefault = bool(data, "isDefault") ?: false,
            isArchived = bool(data, "isArchived") ?: false,
            syncStatus = SyncStatus.SYNCED,
            pendingOperation = PendingOperation.NONE,
            createdAt = long(data, "createdAt") ?: 0L,
            updatedAt = time.takeIf { it > 0 } ?: System.currentTimeMillis(),
            remoteUpdatedAt = time.takeIf { it > 0 }
        )
    }

    private fun DocumentSnapshot.toBudgetEntity(userId: String): BudgetEntity {
        val data = requireData()
        val time = parseRemoteTime(data, reference.path)
        return BudgetEntity(
            localId = id,
            userId = userId,
            categoryLocalId = requiredText(data, "categoryLocalId", reference.path),
            amountMinor = requiredLong(data, "amountMinor", reference.path),
            currency = text(data, "currency") ?: "VND",
            effectiveMonth = requiredLong(data, "effectiveMonth", reference.path).toInt(),
            effectiveYear = requiredLong(data, "effectiveYear", reference.path).toInt(),
            isArchived = bool(data, "isArchived") ?: false,
            syncStatus = SyncStatus.SYNCED,
            pendingOperation = PendingOperation.NONE,
            createdAt = long(data, "createdAt") ?: 0L,
            updatedAt = time.takeIf { it > 0 } ?: System.currentTimeMillis(),
            remoteUpdatedAt = time.takeIf { it > 0 }
        )
    }

    private fun DocumentSnapshot.toReminderEntity(userId: String): ReminderEntity {
        val data = requireData()
        val time = parseRemoteTime(data, reference.path)
        return ReminderEntity(
            localId = id,
            userId = userId,
            title = requiredText(data, "title", reference.path),
            note = text(data, "note"),
            triggerAt = requiredLong(data, "triggerAt", reference.path),
            repeatRule = runCatching {
                RepeatRule.valueOf(text(data, "repeatRule") ?: RepeatRule.NONE.name)
            }.getOrDefault(RepeatRule.NONE),
            isActive = bool(data, "isActive") ?: true,
            syncStatus = SyncStatus.SYNCED,
            pendingOperation = PendingOperation.NONE,
            createdAt = long(data, "createdAt") ?: 0L,
            updatedAt = time.takeIf { it > 0 } ?: System.currentTimeMillis(),
            remoteUpdatedAt = time.takeIf { it > 0 }
        )
    }

    private fun DocumentSnapshot.toRecurringEntity(userId: String): RecurringTransactionEntity {
        val data = data ?: error("Missing document data at ${reference.path}")
        fun requiredText(key: String) = (data[key] as? String)?.takeIf { it.isNotBlank() }
            ?: error("Missing recurring $key at ${reference.path}")
        fun requiredLong(key: String) = (data[key] as? Number)?.toLong()
            ?: error("Missing recurring $key at ${reference.path}")
        fun requiredBoolean(key: String) = data[key] as? Boolean
            ?: error("Missing recurring $key at ${reference.path}")
        val time = parseRemoteTime(data, reference.path)
        require((data["localId"] as? String ?: id) == id) { "Recurring ID mismatch" }
        return RecurringTransactionEntity(
            localId = id,
            userId = userId,
            type = TransactionType.valueOf(requiredText("type")),
            amountMinor = requiredLong("amountMinor"),
            currency = requiredText("currency"),
            categoryLocalId = requiredText("categoryLocalId"),
            note = data["note"] as? String,
            frequency = requiredText("frequency"),
            startAt = requiredLong("startAt"),
            timeZoneId = requiredText("timeZoneId"),
            nextOccurrenceIndex = requiredLong("nextOccurrenceIndex"),
            nextRunAt = requiredLong("nextRunAt"),
            isActive = requiredBoolean("isActive"),
            isDeleted = (data["isDeleted"] as? Boolean) ?: false,
            syncStatus = SyncStatus.SYNCED,
            pendingOperation = PendingOperation.NONE,
            createdAt = requiredLong("createdAt"),
            updatedAt = time,
            remoteUpdatedAt = time.takeIf { it > 0 }
        )
    }

    private fun DocumentSnapshot.requireData(): Map<String, Any?> =
        data ?: error("Missing document data at ${reference.path}")

    private fun parseRemoteTime(data: Map<String, Any?>, path: String): Long {
        return when (val raw = data["updatedAt"]) {
            null -> 0L
            is Number -> raw.toLong()
            is java.util.Date -> raw.time
            is Timestamp -> raw.toDate().time
            else -> error("Invalid updatedAt type ${raw.javaClass.simpleName} at $path")
        }
    }

    private fun text(data: Map<String, Any?>, key: String): String? =
        (data[key] as? String)?.takeIf { it.isNotBlank() }

    private fun requiredText(data: Map<String, Any?>, key: String, path: String): String =
        text(data, key) ?: error("Missing $key at $path")

    private fun long(data: Map<String, Any?>, key: String): Long? =
        (data[key] as? Number)?.toLong()

    private fun requiredLong(data: Map<String, Any?>, key: String, path: String): Long =
        long(data, key) ?: error("Missing $key at $path")

    private fun bool(data: Map<String, Any?>, key: String): Boolean? =
        data[key] as? Boolean

    private fun Exception.isPermanentPullError(): Boolean {
        return when (this) {
            is FirebaseFirestoreException -> when (code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                FirebaseFirestoreException.Code.UNAUTHENTICATED,
                FirebaseFirestoreException.Code.INVALID_ARGUMENT,
                FirebaseFirestoreException.Code.FAILED_PRECONDITION -> true
                else -> false
            }
            is IllegalArgumentException,
            is IllegalStateException -> true
            else -> false
        }
    }

    private data class CollectionPull<T>(
        val collection: String,
        val rows: List<T>,
        val failure: CloudPullFailure? = null
    ) {
        val isComplete: Boolean = failure == null
    }

    data class CloudPullFailure(
        val collection: String,
        val type: String,
        val message: String,
        val permanent: Boolean
    )

    class CloudPullException(
        val failures: List<CloudPullFailure>
    ) : Exception(
        failures.joinToString(
            prefix = "Cloud pull incomplete: ",
            separator = "; "
        ) { "${it.collection}:${it.type}:${it.message}" }
    ) {
        val isPermanent: Boolean = failures.any { it.permanent }
    }

    companion object {
        private const val TAG = "CloudPullService"
    }
}
