package com.example.moneymatev2.data.repository

import com.example.moneymatev2.data.local.dao.ReminderDao
import com.example.moneymatev2.data.local.entity.PendingOperation
import com.example.moneymatev2.data.local.entity.ReminderEntity
import com.example.moneymatev2.data.local.entity.RepeatRule
import com.example.moneymatev2.data.local.entity.SyncStatus
import com.example.moneymatev2.data.remote.dto.toDto
import com.example.moneymatev2.data.remote.sync.SyncTrigger
import com.example.moneymatev2.domain.model.ReminderModel
import com.example.moneymatev2.domain.repository.ReminderRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

class ReminderRepositoryImpl @Inject constructor(
    private val dao: ReminderDao,
    private val firestore: FirebaseFirestore,
    private val syncTrigger: SyncTrigger
): ReminderRepository {
    override fun getActiveReminders(userId: String): Flow<List<ReminderModel>> =
        dao.getActiveReminders(userId).map { list -> list.map { it.toModel() } }

    override suspend fun createReminder(
        userId: String,
        title: String,
        note: String?,
        triggerAt: Long,
        repeatRule: RepeatRule
    ): ReminderModel {

        val now = System.currentTimeMillis()

        val entity = ReminderEntity(
            localId = UUID.randomUUID().toString(),
            userId = userId,
            title = title.trim(),
            note = note?.trim()?.takeIf { it.isNotBlank() },
            triggerAt = triggerAt,
            repeatRule = repeatRule,
            isActive = true,
            syncStatus = SyncStatus.PENDING,
            pendingOperation = PendingOperation.CREATE,
            createdAt = now,
            updatedAt = now
        )

        dao.insertReminder(entity)

        syncTrigger.requestImmediateSync()

        return entity.toModel()
    }

    override suspend fun updateReminder(reminder: ReminderModel){
        val existing = dao.getReminderByLocalIdOnce(reminder.id)
            ?: throw IllegalArgumentException("Reminder with id ${reminder.id} does not exist")
        dao.updateReminder(
            existing.copy(
                title = reminder.title.trim(),
                note = reminder.note
                    ?.trim()
                    ?.takeIf { it.isNotBlank() },
                triggerAt = reminder.triggerAt,
                repeatRule = reminder.repeatRule,
                isActive = reminder.isActive,
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING,
                pendingOperation = PendingOperation.UPDATE
            )
        )
        syncTrigger.requestImmediateSync()
    }

    override suspend fun setReminderActive(
        id: String,
        isActive: Boolean
    ) {
        dao.setReminderActive(
            localId = id,
            isActive = isActive,
            updatedAt = System.currentTimeMillis()
        )

        syncTrigger.requestImmediateSync()
    }

    override suspend fun deleteReminder(id: String) {
        dao.markReminderForDeletion(
            localId = id,
            updatedAt = System.currentTimeMillis()
        )

        syncTrigger.requestImmediateSync()
    }

    override suspend fun syncPendingReminders(userId: String) {
        val pending = dao.getPendingRemindersForSync(userId)

        for (r in pending) {
            try {
                when (r.pendingOperation) {
                    PendingOperation.CREATE,
                    PendingOperation.UPDATE -> {
                        val remoteUpdatedAt = System.currentTimeMillis()

                        firestore.collection("users")
                            .document(userId)
                            .collection("reminders")
                            .document(r.localId)
                            .set(r.toDto())
                            .await()

                        dao.markSynced(
                            localId = r.localId,
                            remoteUpdatedAt = remoteUpdatedAt
                        )
                    }

                    PendingOperation.DELETE -> {
                        firestore.collection("users")
                            .document(userId)
                            .collection("reminders")
                            .document(r.localId)
                            .delete()
                            .await()

                        dao.deleteReminderPermanently(r.localId)
                    }

                    PendingOperation.NONE -> Unit
                }
            } catch (e: Exception) {
                // Sau này nên update retryCount / lastSyncError ở đây
            }
        }
    }

    override fun getReminders(
        userId: String
    ): Flow<List<ReminderModel>> =
        dao.getReminders(userId).map { list ->
            list.map { it.toModel() }
        }
    override suspend fun getReminder(
        id: String
    ): ReminderModel? {
        return dao.getReminderByLocalIdOnce(id)?.toModel()
    }
    override suspend fun updateReminderTrigger(
        id: String,
        triggerAt: Long
    ) {
        dao.updateReminderTrigger(
            localId = id,
            triggerAt = triggerAt,
            updatedAt = System.currentTimeMillis()
        )

        syncTrigger.requestImmediateSync()
    }
    override suspend fun getActiveRemindersOnce(
        userId: String
    ): List<ReminderModel> =
        dao.getAllActiveRemindersOnce(userId)
            .map { entity ->
                entity.toModel()
            }

    private fun ReminderEntity.toModel() = ReminderModel(
        id = localId,
        title = title,
        note = note,
        triggerAt = triggerAt,
        repeatRule = repeatRule,
        isActive = isActive
    )
}