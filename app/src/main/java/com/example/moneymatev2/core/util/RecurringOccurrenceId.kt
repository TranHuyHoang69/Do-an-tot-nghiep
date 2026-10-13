package com.example.moneymatev2.core.util

import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Stable for the same template + scheduled period across retries and devices.
 * TransactionEntity.localId and Firestore transaction document ID are identical.
 */
object RecurringOccurrenceId {
    fun from(
        userId: String,
        recurringId: String,
        dueAt: Long
    ): String {
        require(userId.isNotBlank() && recurringId.isNotBlank())
        val key = "moneymate:recurring:v1:$userId:$recurringId:$dueAt"
        return UUID.nameUUIDFromBytes(key.toByteArray(StandardCharsets.UTF_8)).toString()
    }
}
