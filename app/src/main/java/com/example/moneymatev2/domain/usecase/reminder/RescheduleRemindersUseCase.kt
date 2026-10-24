package com.example.moneymatev2.domain.usecase.reminder

import com.example.moneymatev2.domain.reminder.ReminderRepeatCalculator
import com.example.moneymatev2.domain.reminder.ReminderScheduler
import com.example.moneymatev2.domain.repository.ReminderRepository
import com.example.moneymatev2.domain.repository.AuthRepository
import javax.inject.Inject

class RescheduleRemindersUseCase @Inject constructor(
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val repeatCalculator: ReminderRepeatCalculator,
    private val authRepository: AuthRepository
) {

    suspend operator fun invoke(
        userId: String
    ) {
        if (userId.isBlank() || authRepository.getCurrentUserId() != userId) {
            return
        }

        val now =
            System.currentTimeMillis()

        val reminders =
            repository.getActiveRemindersOnce(
                userId = userId
            )

        reminders.forEach { reminder ->
            if (authRepository.getCurrentUserId() != userId) return

            if (!reminder.isActive) {
                return@forEach
            }

            /*
             * Reminder vẫn còn ở tương lai:
             * schedule lại trực tiếp.
             */
            if (reminder.triggerAt > now) {
                scheduler.schedule(
                    reminder
                )

                return@forEach
            }

            /*
             * Reminder đã quá hạn.
             *
             * NONE:
             *   không còn lần tiếp theo; giữ lời nhắc để người dùng đặt lại.
             *
             * DAILY/WEEKLY/MONTHLY/YEARLY:
             *   tính lần tiếp theo.
             */
            val nextTrigger =
                repeatCalculator
                    .calculateNextTrigger(
                        currentTriggerAt =
                            reminder.triggerAt,
                        repeatRule =
                            reminder.repeatRule,
                        now = now,
                        anchorAt = reminder.anchorAt
                    )

            if (nextTrigger == null) {

                /*
                 * Reminder một lần đã quá hạn.
                 */
                // A past one-time reminder stays visible for manual rescheduling.
                // Rescheduling does not send missed notifications in bulk.
                scheduler.cancel(
                    reminder.id
                )

            } else {

                /*
                 * Reminder lặp:
                 * cập nhật occurrence tiếp theo.
                 */
                repository.updateReminderTrigger(
                    id = reminder.id,
                    triggerAt = nextTrigger
                )

                if (authRepository.getCurrentUserId() != userId) return
                scheduler.schedule(
                    reminder.copy(
                        triggerAt = nextTrigger
                    )
                )
            }
        }
    }
}
