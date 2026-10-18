package com.example.moneymatev2.data.repository

import com.example.moneymatev2.data.local.dao.ReminderDao
import com.example.moneymatev2.data.local.entity.PendingOperation
import com.example.moneymatev2.data.local.entity.ReminderEntity
import com.example.moneymatev2.data.local.entity.RepeatRule
import com.example.moneymatev2.data.local.entity.SyncStatus
import com.example.moneymatev2.data.remote.dto.toDto
import com.example.moneymatev2.data.remote.sync.SyncTrigger
import com.example.moneymatev2.data.remote.sync.PendingUploadRunner
import com.example.moneymatev2.data.remote.sync.syncFailureSummary
import com.example.moneymatev2.domain.model.ReminderModel
import com.example.moneymatev2.domain.repository.ReminderRepository
import com.example.moneymatev2.domain.repository.AuthRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

class ReminderRepositoryImpl @Inject constructor(
    private val dao: ReminderDao,
    private val firestore: FirebaseFirestore,
    private val syncTrigger: SyncTrigger,
    private val uploadRunner: PendingUploadRunner,
    private val authRepository: AuthRepository
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
        if (existing.userId != authRepository.getCurrentUserId()) return
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
        if (getReminder(id) == null) return
        dao.setReminderActive(
            localId = id,
            isActive = isActive,
            updatedAt = System.currentTimeMillis()
        )

        syncTrigger.requestImmediateSync()
    }

    override suspend fun deleteReminder(id: String) {
        if (getReminder(id) == null) return
        dao.markReminderForDeletion(
            localId = id,
            updatedAt = System.currentTimeMillis()
        )

        syncTrigger.requestImmediateSync()
    }


    override suspend fun syncPendingReminders(userId: String) {
        val session = uploadRunner.capture(userId)
        val pending = dao.getPendingRemindersForSync(userId)
        var firstError: Exception? = null
        for (row in pending) {
            try {
                uploadRunner.run(
                    session = session,
                    snapshot = row,
                    readCurrent = { dao.getReminderByLocalIdOnce(row.localId) },
                    upload = {
                        val doc = firestore.collection("users").document(userId)
                            .collection("reminders").document(row.localId)
                        if (row.pendingOperation == PendingOperation.DELETE) doc.delete().await()
                        else doc.set(row.toDto()).await()
                    },
                    acknowledge = {
                        if (row.pendingOperation == PendingOperation.DELETE) {
                            dao.deleteReminderIfUnchanged(row.localId, userId, row.updatedAt)
                        } else {
                            dao.markSyncedIfUnchanged(row.localId, userId, row.updatedAt, System.currentTimeMillis())
                        }
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
                android.util.Log.e("SyncUpload", "SYNC_FAILED collection=reminders ${e.syncFailureSummary()}")
                if (firstError == null) firstError = e else if (firstError !== e) firstError.addSuppressed(e)
            }
        }
        firstError?.let { throw it }
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
        val row = dao.getReminderByLocalIdOnce(id) ?: return null
        return row.takeIf {
            it.userId == authRepository.getCurrentUserId() && it.pendingOperation != PendingOperation.DELETE
        }?.toModel()
    }
    override suspend fun updateReminderTrigger(
        id: String,
        triggerAt: Long
    ) {
        if (getReminder(id) == null) return
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
