package com.example.moneymatev2.domain.repository

import com.example.moneymatev2.data.local.entity.RepeatRule
import com.example.moneymatev2.domain.model.ReminderModel
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {

    fun getActiveReminders(userId: String): Flow<List<ReminderModel>>

    suspend fun createReminder(userId: String, title: String, message: String?, triggerAt: Long, repeatRule: RepeatRule)

    suspend fun deactivateReminder(id: String)

    suspend fun syncPendingReminders(userId: String)
}