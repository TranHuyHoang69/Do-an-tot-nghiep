package com.example.moneymatev2.domain.usecase.reminder

import com.example.moneymatev2.domain.model.ReminderModel
import com.example.moneymatev2.domain.reminder.ReminderRepeatCalculator
import com.example.moneymatev2.domain.reminder.ReminderScheduler
import com.example.moneymatev2.domain.repository.ReminderRepository
import javax.inject.Inject

class UpdateReminderUseCase @Inject constructor(
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val repeatCalculator: ReminderRepeatCalculator
) {

    suspend operator fun invoke(
        reminder: ReminderModel
    ): ReminderModel {

        require(reminder.title.isNotBlank()) {
            "Reminder title must not be empty"
        }

        val normalizedReminder =
            normalizeTrigger(reminder)

        repository.updateReminder(
            normalizedReminder
        )

        if (normalizedReminder.isActive) {
            scheduler.schedule(
                normalizedReminder
            )
        } else {
            scheduler.cancel(
                normalizedReminder.id
            )
        }

        return normalizedReminder
    }

    private fun normalizeTrigger(
        reminder: ReminderModel
    ): ReminderModel {

        if (!reminder.isActive) {
            return reminder
        }

        if (
            reminder.triggerAt >
            System.currentTimeMillis()
        ) {
            return reminder
        }

        val nextTrigger =
            repeatCalculator.calculateNextTrigger(
                currentTriggerAt = reminder.triggerAt,
                repeatRule = reminder.repeatRule
            )

        requireNotNull(nextTrigger) {
            "One-time reminder must have a future time"
        }

        return reminder.copy(
            triggerAt = nextTrigger
        )
    }
}