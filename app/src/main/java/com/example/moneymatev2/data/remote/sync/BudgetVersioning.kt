package com.example.moneymatev2.data.remote.sync

import com.example.moneymatev2.data.local.entity.BudgetEntity
import com.example.moneymatev2.data.local.entity.PendingOperation
import com.example.moneymatev2.data.local.entity.SyncStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

internal data class BudgetCloudRecord(
    val localId: String,
    val categoryLocalId: String,
    val amountMinor: Long,
    val currency: String,
    val effectiveMonth: Int,
    val effectiveYear: Int,
    val isArchived: Boolean,
    val createdAt: Long,
    val revision: Long,
    val lastOperationId: String?,
    val updatedAt: Long?
) {
    fun asLocal(userId: String) = BudgetEntity(
        localId = localId, userId = userId, categoryLocalId = categoryLocalId,
        amountMinor = amountMinor, currency = currency,
        effectiveMonth = effectiveMonth, effectiveYear = effectiveYear,
        isArchived = isArchived, syncStatus = SyncStatus.SYNCED,
        pendingOperation = PendingOperation.NONE, createdAt = createdAt,
        updatedAt = updatedAt ?: System.currentTimeMillis(), remoteUpdatedAt = updatedAt,
        cloudRevision = revision
    )

    fun contains(row: BudgetEntity) = categoryLocalId == row.categoryLocalId &&
        amountMinor == row.amountMinor && currency == row.currency &&
        effectiveMonth == row.effectiveMonth && effectiveYear == row.effectiveYear &&
        isArchived == row.isArchived && createdAt == row.createdAt

    companion object {
        fun from(document: DocumentSnapshot): BudgetCloudRecord? {
            if (!document.exists()) return null
            val data = requireNotNull(document.data) { "Missing Budget document data" }
            fun number(key: String) = (data[key] as? Number)?.toLong()
                ?: throw IllegalStateException("Invalid Budget $key at ${document.reference.path}")
            val revision = (data["revision"] as? Number)?.toLong() ?: 0L
            require(revision >= 0) { "Negative Budget revision" }
            val time = when (val value = data["updatedAt"]) {
                is Timestamp -> value.toDate().time
                is Date -> value.time
                is Number -> value.toLong()
                else -> null
            }
            return BudgetCloudRecord(
                localId = document.id,
                categoryLocalId = data["categoryLocalId"] as? String ?: error("Missing Budget category"),
                amountMinor = number("amountMinor"),
                currency = data["currency"] as? String ?: "VND",
                effectiveMonth = number("effectiveMonth").toInt(),
                effectiveYear = number("effectiveYear").toInt(),
                isArchived = data["isArchived"] as? Boolean ?: false,
                createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
                revision = revision,
                lastOperationId = data["lastOperationId"] as? String,
                updatedAt = time
            )
        }
    }
}

internal sealed interface BudgetWriteDecision {
    data class Commit(val revision: Long) : BudgetWriteDecision
    data class AlreadyCommitted(val revision: Long) : BudgetWriteDecision
    data class Conflict(val reason: String) : BudgetWriteDecision
}

internal object BudgetVersionPolicy {
    fun decide(row: BudgetEntity, current: BudgetCloudRecord?): BudgetWriteDecision {
        val operationId = row.pendingUploadId
            ?: return BudgetWriteDecision.Conflict("MISSING_OPERATION_ID")
        if (current?.lastOperationId == operationId) {
            return if (current.contains(row)) BudgetWriteDecision.AlreadyCommitted(current.revision)
            else BudgetWriteDecision.Conflict("OPERATION_ID_REUSED")
        }
        if (row.pendingOperation == PendingOperation.CREATE) {
            return if (current == null) BudgetWriteDecision.Commit(1)
            else if (current.contains(row)) BudgetWriteDecision.AlreadyCommitted(current.revision)
            else BudgetWriteDecision.Conflict("CREATE_EXISTS")
        }
        if (current == null) return BudgetWriteDecision.Conflict("REMOTE_MISSING")
        val base = row.baseRevision ?: run {
            if (current.contains(row)) return BudgetWriteDecision.AlreadyCommitted(current.revision)
            // v4 did not store revisions. The server timestamp of the exact document
            // observed before the local edit is the only available proof of its base.
            // This recovery is restricted to an untouched legacy cloud document.
            if (current.revision == 0L && current.lastOperationId == null &&
                row.remoteUpdatedAt != null && row.remoteUpdatedAt == current.updatedAt &&
                current.localId == row.localId && current.categoryLocalId == row.categoryLocalId &&
                current.effectiveMonth == row.effectiveMonth && current.effectiveYear == row.effectiveYear &&
                current.createdAt == row.createdAt && !current.isArchived
            ) 0L else return BudgetWriteDecision.Conflict("UNKNOWN_BASE_REVISION")
        }
        if (base != current.revision) return if (current.contains(row))
            BudgetWriteDecision.AlreadyCommitted(current.revision)
        else BudgetWriteDecision.Conflict("REVISION_MISMATCH")
        if (current.isArchived && !row.isArchived && !row.pendingRecreate)
            return BudgetWriteDecision.Conflict("STALE_UPDATE_AFTER_DELETE")
        if (current.revision == Long.MAX_VALUE)
            return BudgetWriteDecision.Conflict("REVISION_EXHAUSTED")
        return BudgetWriteDecision.Commit(current.revision + 1)
    }
}

internal class BudgetVersionConflictException(
    val reason: String,
    val remote: BudgetCloudRecord?
) : IllegalStateException("Budget sync conflict: $reason")
