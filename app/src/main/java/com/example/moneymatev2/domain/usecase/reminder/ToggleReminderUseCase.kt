package com.example.moneymatev2.domain.usecase.reminder

import com.example.moneymatev2.domain.model.ReminderModel
import com.example.moneymatev2.domain.reminder.ReminderRepeatCalculator
import com.example.moneymatev2.domain.reminder.ReminderScheduler
import com.example.moneymatev2.domain.repository.ReminderRepository
import javax.inject.Inject

class ToggleReminderUseCase @Inject constructor(
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val repeatCalculator: ReminderRepeatCalculator
) {

    suspend operator fun invoke(
        reminderId: String,
        isActive: Boolean
    ): ReminderModel {

        val reminder =
            repository.getReminder(reminderId)
                ?: throw IllegalArgumentException(
                    "Reminder with id $reminderId does not exist"
                )

        return if (isActive) {
            enable(reminder)
        } else {
            disable(reminder)
        }
    }

    private suspend fun enable(
        reminder: ReminderModel
    ): ReminderModel {

        val now = System.currentTimeMillis()

        val triggerAt =
            if (reminder.triggerAt > now) {
                reminder.triggerAt
            } else {
                repeatCalculator.calculateNextTrigger(
                    currentTriggerAt =
                        reminder.triggerAt,
                    repeatRule =
                        reminder.repeatRule,
                    now = now
                ) ?: throw IllegalStateException(
                    "One-time reminder has expired. Please select a new time."
                )
            }

        val updatedReminder =
            reminder.copy(
                triggerAt = triggerAt,
                isActive = true
            )

        repository.updateReminder(
            updatedReminder
        )

        scheduler.schedule(
            updatedReminder
        )

        return updatedReminder
    }

    private suspend fun disable(
        reminder: ReminderModel
    ): ReminderModel {

        repository.setReminderActive(
            id = reminder.id,
            isActive = false
        )

        scheduler.cancel(
            reminder.id
        )

        return reminder.copy(
            isActive = false
        )
    }
}