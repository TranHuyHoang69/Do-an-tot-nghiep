package com.example.moneymatev2.domain.usecase.reminder

import com.example.moneymatev2.data.local.entity.RepeatRule
import com.example.moneymatev2.domain.model.ReminderModel
import com.example.moneymatev2.domain.reminder.ReminderScheduler
import com.example.moneymatev2.domain.repository.ReminderRepository
import javax.inject.Inject

class CreateReminderUseCase @Inject constructor(
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler
) {

    suspend operator fun invoke(
        userId: String,
        title: String,
        note: String?,
        triggerAt: Long,
        repeatRule: RepeatRule
    ): ReminderModel {

        require(userId.isNotBlank()) {
            "User id must not be empty"
        }

        require(title.isNotBlank()) {
            "Reminder title must not be empty"
        }

        require(triggerAt > System.currentTimeMillis()) {
            "Reminder time must be in the future"
        }

        val reminder = repository.createReminder(
            userId = userId,
            title = title,
            note = note,
            triggerAt = triggerAt,
            repeatRule = repeatRule
        )

        scheduler.schedule(reminder)

        return reminder
    }
}