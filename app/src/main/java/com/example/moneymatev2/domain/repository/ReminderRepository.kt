package com.example.moneymatev2.domain.repository

import com.example.moneymatev2.data.local.entity.RepeatRule
import com.example.moneymatev2.domain.model.ReminderModel
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {

    fun getActiveReminders(
        userId: String
    ): Flow<List<ReminderModel>>

    suspend fun getActiveRemindersOnce(
        userId: String
    ): List<ReminderModel>

    suspend fun createReminder(
        userId: String,
        title: String,
        note: String?,
        triggerAt: Long,
        repeatRule: RepeatRule
    ): ReminderModel

    suspend fun updateReminder(
        reminder: ReminderModel
    )

    suspend fun setReminderActive(
        id: String,
        isActive: Boolean
    )

    suspend fun deleteReminder(
        id: String
    )

    fun getReminders(
        userId: String
    ): Flow<List<ReminderModel>>

    suspend fun syncPendingReminders(
        userId: String
    )

    suspend fun getReminder(
        id: String
    ): ReminderModel?

    suspend fun updateReminderTrigger(
        id: String,
        triggerAt: Long
    )
}