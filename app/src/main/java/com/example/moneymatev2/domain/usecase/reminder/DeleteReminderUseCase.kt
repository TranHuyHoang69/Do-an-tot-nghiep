package com.example.moneymatev2.domain.usecase.reminder

import com.example.moneymatev2.domain.reminder.ReminderScheduler
import com.example.moneymatev2.domain.repository.ReminderRepository
import javax.inject.Inject

class DeleteReminderUseCase @Inject constructor(
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler
) {

    suspend operator fun invoke(
        reminderId: String
    ) {
        repository.deleteReminder(
            reminderId
        )

        scheduler.cancel(
            reminderId
        )
    }
}