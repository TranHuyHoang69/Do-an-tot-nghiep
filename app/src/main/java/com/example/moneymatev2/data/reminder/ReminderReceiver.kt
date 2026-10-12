package com.example.moneymatev2.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.moneymatev2.domain.reminder.ReminderRepeatCalculator
import com.example.moneymatev2.domain.reminder.ReminderScheduler
import com.example.moneymatev2.domain.repository.ReminderRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var scheduler: ReminderScheduler

    @Inject
    lateinit var notificationManager:
            ReminderNotificationManager

    @Inject
    lateinit var repeatCalculator:
            ReminderRepeatCalculator

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val reminderId =
            intent.getStringExtra(
                AlarmReminderScheduler.EXTRA_REMINDER_ID
            ) ?: return

        val pendingResult = goAsync()

        CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        ).launch {
            try {
                handleReminder(reminderId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleReminder(
        reminderId: String
    ) {
        val reminder =
            reminderRepository.getReminder(
                reminderId
            ) ?: return

        if (!reminder.isActive) {
            return
        }

        notificationManager.show(reminder)

        val nextTrigger =
            repeatCalculator.calculateNextTrigger(
                currentTriggerAt =
                    reminder.triggerAt,
                repeatRule =
                    reminder.repeatRule
            )

        if (nextTrigger == null) {
            reminderRepository
                .setReminderActive(
                    id = reminder.id,
                    isActive = false
                )

            return
        }

        reminderRepository
            .updateReminderTrigger(
                id = reminder.id,
                triggerAt = nextTrigger
            )

        scheduler.schedule(
            reminder.copy(
                triggerAt = nextTrigger
            )
        )
    }
}