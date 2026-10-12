package com.example.moneymatev2.core.reminder

import com.example.moneymatev2.domain.reminder.ReminderScheduler
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.ReminderRepository
import com.example.moneymatev2.domain.usecase.reminder.RescheduleRemindersUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderAuthSessionManager @Inject constructor(
    private val authRepository: AuthRepository,
    private val reminderRepository: ReminderRepository,
    private val reminderScheduler: ReminderScheduler,
    private val rescheduleRemindersUseCase: RescheduleRemindersUseCase
) {

    private val scope =
        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.IO
        )

    private val started =
        AtomicBoolean(false)

    fun start() {

        /*
         * Application.onCreate() có thể vô tình gọi nhiều lần
         * trong quá trình refactor/test.
         *
         * Chỉ cho observer chạy một lần.
         */
        if (!started.compareAndSet(false, true)) {
            return
        }

        scope.launch {

            var previousUserId: String? = null

            authRepository
                .observeAuthState()
                .map { user ->
                    user?.userId
                }
                .distinctUntilChanged()
                .collect { currentUserId ->

                    /*
                     * User thay đổi:
                     *
                     * A -> null
                     * A -> B
                     *
                     * Hủy toàn bộ alarm thuộc A.
                     */
                    if (
                        previousUserId != null &&
                        previousUserId != currentUserId
                    ) {
                        cancelUserAlarms(
                            userId = previousUserId!!
                        )
                    }

                    /*
                     * null -> A
                     * A -> B
                     *
                     * Schedule reminder của user mới.
                     */
                    if (
                        currentUserId != null &&
                        currentUserId != previousUserId
                    ) {
                        rescheduleRemindersUseCase(
                            userId = currentUserId
                        )
                    }

                    previousUserId =
                        currentUserId
                }
        }
    }

    private suspend fun cancelUserAlarms(
        userId: String
    ) {
        val reminders =
            reminderRepository
                .getActiveRemindersOnce(
                    userId = userId
                )

        reminders.forEach { reminder ->
            reminderScheduler.cancel(
                reminder.id
            )
        }
    }
}