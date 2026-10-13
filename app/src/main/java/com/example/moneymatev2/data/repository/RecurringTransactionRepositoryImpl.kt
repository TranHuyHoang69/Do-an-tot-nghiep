package com.example.moneymatev2.data.repository

import androidx.room.withTransaction
import com.example.moneymatev2.core.util.RecurringOccurrenceId
import com.example.moneymatev2.data.local.AppDatabase
import com.example.moneymatev2.data.local.dao.CategoryDao
import com.example.moneymatev2.data.local.dao.RecurringTransactionDao
import com.example.moneymatev2.data.local.dao.TransactionDao
import com.example.moneymatev2.data.local.entity.PendingOperation
import com.example.moneymatev2.data.local.entity.RecurringTransactionEntity
import com.example.moneymatev2.data.local.entity.SyncStatus
import com.example.moneymatev2.data.local.entity.TransactionEntity
import com.example.moneymatev2.data.remote.sync.SyncTrigger
import com.example.moneymatev2.domain.model.RecurringFrequency
import com.example.moneymatev2.domain.model.RecurringTransactionModel
import com.example.moneymatev2.domain.repository.RecurringTransactionRepository
import com.example.moneymatev2.domain.usecase.recurring.RecurringScheduleCalculator
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

class RecurringTransactionRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val recurringDao: RecurringTransactionDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val calculator: RecurringScheduleCalculator,
    private val firestore: FirebaseFirestore,
    private val syncTrigger: SyncTrigger
) : RecurringTransactionRepository {

    override fun observe(userId: String): Flow<List<RecurringTransactionModel>> =
        recurringDao.observeAll(userId).map { rows -> rows.map { it.toModel() } }

    override suspend fun getById(userId: String, id: String): RecurringTransactionModel? =
        recurringDao.getById(id, userId)?.toModel()

    override suspend fun save(userId: String, model: RecurringTransactionModel) {
        require(userId.isNotBlank() && model.userId == userId)
        require(model.amountMinor > 0L && model.currency == "VND")
        val category = categoryDao.getCategoryByLocalIdOnce(model.categoryId)
            ?: throw IllegalArgumentException("Category not found")
        require(category.userId == userId && !category.isArchived && category.type == model.type) {
            "Invalid category for transaction type"
        }

        val now = System.currentTimeMillis()
        db.withTransaction {
            val existing = model.id.takeIf { it.isNotBlank() }
                ?.let { recurringDao.getById(it, userId) }

            if (model.id.isNotBlank() && existing == null) {
                throw IllegalArgumentException("Recurring transaction not found")
            }

            // Edits do not regenerate historic periods with the new template settings.
            // The original user-selected day is preserved as anchor for all new occurrences.
            val scheduleChanged = existing == null ||
                    existing.startAt != model.startAt ||
                    existing.frequency != model.frequency.name ||
                    existing.timeZoneId != model.timeZoneId

            val (cursor, nextRunAt) = when {
                existing == null -> firstOccurrenceForNewTemplate(model, now)
                scheduleChanged || (!existing.isActive && model.isActive) || existing.nextRunAt <= now -> firstFutureOccurrence(model, now)
                else -> existing.nextOccurrenceIndex to existing.nextRunAt
            }

            val row = RecurringTransactionEntity(
                localId = existing?.localId ?: UUID.randomUUID().toString(),
                userId = userId,
                type = model.type,
                amountMinor = model.amountMinor,
                currency = model.currency,
                categoryLocalId = model.categoryId,
                note = model.note?.trim()?.takeIf { it.isNotEmpty() },
                frequency = model.frequency.name,
                startAt = model.startAt,
                timeZoneId = model.timeZoneId,
                nextOccurrenceIndex = cursor,
                nextRunAt = nextRunAt,
                isActive = model.isActive,
                syncStatus = SyncStatus.PENDING,
                pendingOperation = if (existing == null || existing.pendingOperation == PendingOperation.CREATE) PendingOperation.CREATE else PendingOperation.UPDATE,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                remoteUpdatedAt = existing?.remoteUpdatedAt
            )
            if (existing == null) recurringDao.insert(row) else recurringDao.update(row)
        }
        syncTrigger.requestImmediateSync()
    }

    override suspend fun setActive(userId: String, id: String, active: Boolean) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            val existing = recurringDao.getById(id, userId) ?: return@withTransaction
            val (cursor, nextRunAt) = if (active && !existing.isActive) {
                firstFutureOccurrence(existing.toModel(), now)
            } else existing.nextOccurrenceIndex to existing.nextRunAt
            recurringDao.update(existing.copy(
                isActive = active,
                nextOccurrenceIndex = cursor,
                nextRunAt = nextRunAt,
                updatedAt = now,
                syncStatus = SyncStatus.PENDING,
                pendingOperation = existing.nextWriteOperation()
            ))
        }
        syncTrigger.requestImmediateSync()
    }

    override suspend fun delete(userId: String, id: String) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            val old = recurringDao.getById(id, userId) ?: return@withTransaction
            recurringDao.update(old.copy(
                isActive = false,
                isDeleted = true,
                updatedAt = now,
                syncStatus = SyncStatus.PENDING,
                pendingOperation = PendingOperation.DELETE
            ))
        }
        syncTrigger.requestImmediateSync()
    }

    /** Atomic cursor advance + deterministic transaction INSERT IGNORE => safe WorkManager retries. */
    override suspend fun postDue(userId: String, maxOccurrences: Int): Int {
        require(maxOccurrences in 1..500)
        var inserted = 0
        for (attempt in 0 until maxOccurrences) {
            val now = System.currentTimeMillis()
            val found = db.withTransaction {
                val due = recurringDao.nextDue(userId, now) ?: return@withTransaction null
                val category = categoryDao.getCategoryByLocalIdOnce(due.categoryLocalId)
                if (category == null || category.isArchived || category.userId != userId || category.type != due.type) {
                    // Pauses broken templates rather than silently miscategorizing money.
                    recurringDao.update(due.copy(
                        isActive = false,
                        updatedAt = now,
                        syncStatus = SyncStatus.PENDING,
                        pendingOperation = due.nextWriteOperation()
                    ))
                    return@withTransaction 0
                }
                val transactionId = RecurringOccurrenceId.from(userId, due.localId, due.nextRunAt)
                val row = TransactionEntity(
                    localId = transactionId,
                    userId = userId,
                    type = due.type,
                    amountMinor = due.amountMinor,
                    currency = due.currency,
                    categoryLocalId = due.categoryLocalId,
                    note = due.note,
                    syncStatus = SyncStatus.PENDING,
                    pendingOperation = PendingOperation.CREATE,
                    createdAt = due.nextRunAt, // actual period, not processing time
                    updatedAt = now
                )
                val rowId = transactionDao.insertGeneratedTransaction(row)
                val index = Math.addExact(due.nextOccurrenceIndex, 1L)
                val next = calculator.occurrenceAt(
                    due.startAt, due.timeZoneId, RecurringFrequency.valueOf(due.frequency), index
                )
                recurringDao.update(due.copy(
                    nextOccurrenceIndex = index,
                    nextRunAt = next,
                    updatedAt = now,
                    syncStatus = SyncStatus.PENDING,
                    pendingOperation = due.nextWriteOperation()
                ))
                if (rowId == -1L) 0 else 1
            } ?: break // No more due; do not query Room up to 100 unnecessary times.
            inserted += found
        }
        if (inserted > 0) syncTrigger.requestImmediateSync()
        return inserted
    }

    override suspend fun syncPending(userId: String) {
        for (item in recurringDao.getPending(userId)) {
            try {
                val doc = firestore.collection("users").document(userId)
                    .collection("recurring_transactions").document(item.localId)
                if (item.pendingOperation == PendingOperation.DELETE) {
                    doc.delete().await()
                    recurringDao.hardDeleteIfUnchanged(item.localId, userId, item.updatedAt)
                } else if (item.pendingOperation != PendingOperation.NONE) {
                    doc.set(mapOf(
                        "localId" to item.localId,
                        "userId" to item.userId,
                        "type" to item.type.name,
                        "amountMinor" to item.amountMinor,
                        "currency" to item.currency,
                        "categoryLocalId" to item.categoryLocalId,
                        "note" to item.note,
                        "frequency" to item.frequency,
                        "startAt" to item.startAt,
                        "timeZoneId" to item.timeZoneId,
                        "nextOccurrenceIndex" to item.nextOccurrenceIndex,
                        "nextRunAt" to item.nextRunAt,
                        "isActive" to item.isActive,
                        "updatedAt" to item.updatedAt,
                        "createdAt" to item.createdAt
                    )).await()
                    recurringDao.markSynced(item.localId, userId, item.updatedAt, System.currentTimeMillis())
                }
            } catch (e: Exception) {
                recurringDao.markSyncFailed(item.localId, userId, e.message ?: "Sync failed", System.currentTimeMillis())
            }
        }
    }

    private fun RecurringTransactionEntity.nextWriteOperation(): PendingOperation =
        if (pendingOperation == PendingOperation.CREATE) PendingOperation.CREATE else PendingOperation.UPDATE

    private fun RecurringTransactionEntity.toModel(): RecurringTransactionModel = RecurringTransactionModel(
        id = localId,
        userId = userId,
        type = type,
        amountMinor = amountMinor,
        currency = currency,
        categoryId = categoryLocalId,
        note = note,
        frequency = RecurringFrequency.valueOf(frequency),
        startAt = startAt,
        timeZoneId = timeZoneId,
        nextOccurrenceIndex = nextOccurrenceIndex,
        nextRunAt = nextRunAt,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun firstOccurrenceForNewTemplate(model: RecurringTransactionModel, now: Long): Pair<Long, Long> {
        // A new template starting TODAY should post today's occurrence; do not backfill
        // all pre-creation historical periods if the user selects an old start date.
        val zone = java.time.ZoneId.of(model.timeZoneId)
        val anchorDate = java.time.Instant.ofEpochMilli(model.startAt).atZone(zone).toLocalDate()
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return if (!anchorDate.isBefore(today)) {
            0L to calculator.occurrenceAt(model.startAt, model.timeZoneId, model.frequency, 0L)
        } else {
            firstFutureOccurrence(model, now)
        }
    }

    private fun firstFutureOccurrence(model: RecurringTransactionModel, now: Long): Pair<Long, Long> {
        // Binary search avoids hundreds of thousands of loops for very old anchor dates.
        val c = { index: Long -> calculator.occurrenceAt(model.startAt, model.timeZoneId, model.frequency, index) }
        if (c(0) > now) return 0L to c(0)
        var high = 1L
        while (c(high) <= now) high = Math.multiplyExact(high, 2L)
        var low = 0L
        while (low + 1 < high) {
            val mid = low + (high - low) / 2
            if (c(mid) <= now) low = mid else high = mid
        }
        return high to c(high)
    }
}
