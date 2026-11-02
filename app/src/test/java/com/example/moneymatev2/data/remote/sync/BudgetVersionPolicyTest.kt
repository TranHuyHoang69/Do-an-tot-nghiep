package com.example.moneymatev2.data.remote.sync

import com.example.moneymatev2.data.local.entity.BudgetEntity
import com.example.moneymatev2.data.local.entity.PendingOperation
import com.example.moneymatev2.data.local.entity.SyncStatus
import org.junit.Assert.*
import org.junit.Test

class BudgetVersionPolicyTest {
    private fun row(operation: PendingOperation = PendingOperation.UPDATE, base: Long? = 5,
                    archived: Boolean = false, recreate: Boolean = false) = BudgetEntity(
        localId = "budget", userId = "A", categoryLocalId = "food", amountMinor = 100,
        currency = "VND", effectiveMonth = 10, effectiveYear = 2026,
        isArchived = archived, syncStatus = SyncStatus.PENDING,
        pendingOperation = operation, createdAt = 1, updatedAt = 2,
        baseRevision = base, pendingUploadId = "operation", pendingRecreate = recreate
    )

    private fun cloud(revision: Long = 5, archived: Boolean = false,
                      operationId: String? = "older") = BudgetCloudRecord(
        localId = "budget", categoryLocalId = "food", amountMinor = 100,
        currency = "VND", effectiveMonth = 10, effectiveYear = 2026,
        isArchived = archived, createdAt = 1, revision = revision,
        lastOperationId = operationId, updatedAt = 3
    )

    @Test fun matchingRevisionAdvancesExactlyOnce() {
        assertEquals(BudgetWriteDecision.Commit(6), BudgetVersionPolicy.decide(row(), cloud()))
        assertEquals(BudgetWriteDecision.Commit(6),
            BudgetVersionPolicy.decide(row(archived = true), cloud()))
    }

    @Test fun staleUpdateCannotOverwriteNewerTombstone() {
        assertEquals(BudgetWriteDecision.Conflict("REVISION_MISMATCH"),
            BudgetVersionPolicy.decide(row(), cloud(revision = 6, archived = true)))
        assertEquals(BudgetWriteDecision.Conflict("STALE_UPDATE_AFTER_DELETE"),
            BudgetVersionPolicy.decide(row(), cloud(archived = true)))
    }

    @Test fun createRequiresAbsentDocumentAndRecreateRequiresExplicitIntent() {
        assertEquals(BudgetWriteDecision.Commit(1),
            BudgetVersionPolicy.decide(row(PendingOperation.CREATE, null), null))
        assertEquals(BudgetWriteDecision.Conflict("CREATE_EXISTS"),
            BudgetVersionPolicy.decide(row(PendingOperation.CREATE, null).copy(amountMinor = 200), cloud()))
        assertEquals(BudgetWriteDecision.Commit(6),
            BudgetVersionPolicy.decide(row(recreate = true), cloud(archived = true)))
    }

    @Test fun legacyRevisionZeroIsReadableButUnknownLocalBaseCannotBlindWrite() {
        assertEquals(BudgetWriteDecision.Commit(1),
            BudgetVersionPolicy.decide(row(base = 0), cloud(revision = 0, operationId = null)))
        assertEquals(BudgetWriteDecision.Conflict("UNKNOWN_BASE_REVISION"),
            BudgetVersionPolicy.decide(row(base = null).copy(amountMinor = 200), cloud(revision = 0, operationId = null)))
    }

    @Test fun unchangedLegacyCloudTimestampRecoversUnknownBase() {
        val pending = row(base = null).copy(remoteUpdatedAt = 3)
        assertEquals(BudgetWriteDecision.Commit(1),
            BudgetVersionPolicy.decide(pending.copy(amountMinor = 200),
                cloud(revision = 0, operationId = null)))
        assertEquals(BudgetWriteDecision.Conflict("UNKNOWN_BASE_REVISION"),
            BudgetVersionPolicy.decide(pending.copy(amountMinor = 200, remoteUpdatedAt = 4),
                cloud(revision = 0, operationId = null)))
    }

    @Test fun identicalRemoteContentCanBeAcknowledgedWithoutOverwrite() {
        assertEquals(BudgetWriteDecision.AlreadyCommitted(8),
            BudgetVersionPolicy.decide(row(), cloud(revision = 8)))
    }

    @Test fun lostAckRetryRecognizesSameOperationWithoutNewRevision() {
        assertEquals(BudgetWriteDecision.AlreadyCommitted(6),
            BudgetVersionPolicy.decide(row(), cloud(revision = 6, operationId = "operation")))
        assertEquals(BudgetWriteDecision.Conflict("OPERATION_ID_REUSED"),
            BudgetVersionPolicy.decide(row(archived = true), cloud(revision = 6,
                operationId = "operation")))
    }

    @Test fun revisionCannotWrapLong() {
        assertEquals(BudgetWriteDecision.Conflict("REVISION_EXHAUSTED"),
            BudgetVersionPolicy.decide(row(base = Long.MAX_VALUE),
                cloud(revision = Long.MAX_VALUE)))
    }
}
