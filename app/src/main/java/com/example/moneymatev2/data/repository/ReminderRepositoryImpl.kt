package com.example.moneymatev2.data.repository

import com.example.moneymatev2.data.local.dao.ReminderDao
import com.example.moneymatev2.data.local.entity.PendingOperation
import com.example.moneymatev2.data.local.entity.ReminderEntity
import com.example.moneymatev2.data.local.entity.RepeatRule
import com.example.moneymatev2.data.local.entity.SyncStatus
import com.example.moneymatev2.data.remote.dto.toDto
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
    private val firestore: FirebaseFirestore
): ReminderRepository {
    override fun getActiveReminders(userId: String): Flow<List<ReminderModel>> =
        dao.getActiveReminders(userId).map { list -> list.map { it.toModel() } }

    override suspend fun createReminder(
        userId: String,
        title: String,
        message: String?,
        triggerAt: Long,
        repeatRule: RepeatRule
    ) {
        val now = System.currentTimeMillis()
        dao.insertReminder(
            ReminderEntity(
                localId = UUID.randomUUID().toString(),
                userId = userId,
                title = title,
                message = message,
                triggerAt = triggerAt,
                repeatRule = repeatRule,
                isActive = true,
                syncStatus = SyncStatus.PENDING,
                pendingOperation = PendingOperation.CREATE,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    override suspend fun updateReminder(reminder: ReminderModel){
        dao.updateReminder(
            ReminderEntity(
                localId = reminder.id,
                userId = "",
                title = reminder.title,
                message = reminder.message,
                triggerAt = reminder.triggerAt,
                repeatRule = reminder.repeatRule,
                isActive = reminder.isActive,
                syncStatus = SyncStatus.PENDING,
                pendingOperation = PendingOperation.UPDATE,
                createdAt = 0,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun deactivateReminder(id: String) {
        val now = System.currentTimeMillis()
        dao.deactivateReminder(id, now)
    }

    override suspend fun syncPendingReminders(userId: String) {
        val pending = dao.getPendingRemindersForSync(userId)
        for (r in pending) {
            try {
                firestore.collection("users").document(userId)
                    .collection("reminders").document(r.userId)
                    .set(r.toDto()).await()
            }catch (e: Exception) {
                // Handle error, maybe log it or update the local entity with the error
            }
        }
    }

    private fun ReminderEntity.toModel() = ReminderModel(
        localId, title, message, triggerAt, repeatRule, isActive
    )
}