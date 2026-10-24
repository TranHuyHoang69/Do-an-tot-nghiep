package com.example.moneymatev2.data.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.moneymatev2.domain.reminder.ReminderScheduler
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.usecase.reminder.RescheduleRemindersUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ReminderBootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var rescheduleRemindersUseCase:
            RescheduleRemindersUseCase

    @Inject
    lateinit var reminderScheduler:
            ReminderScheduler

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val action =
            intent.action ?: return

        val isExactAlarmPermissionChanged =
            Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.S &&
                    action ==
                    AlarmManager
                        .ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED

        if (!ReminderSystemBroadcasts.isRescheduleAction(action)) return

        /*
         * BroadcastReceiver.onReceive() phải kết thúc nhanh.
         * goAsync() cho phép chạy coroutine ngắn sau đó.
         */
        val pendingResult =
            goAsync()

        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.IO
        ).launch {

            try {

                /*
                 * Khi nhận broadcast do vừa cấp quyền
                 * exact alarm, kiểm tra lại quyền trước.
                 */
                if (
                    isExactAlarmPermissionChanged &&
                    !reminderScheduler
                        .canScheduleExactAlarms()
                ) {
                    return@launch
                }

                /*
                 * Firebase Auth giữ session local,
                 * nên lấy user hiện tại rồi chỉ
                 * schedule reminder của user đó.
                 */
                val user =
                    authRepository
                        .observeAuthState()
                        .first()

                val userId =
                    user?.userId
                        ?: return@launch

                rescheduleRemindersUseCase(
                    userId = userId
                )

            } catch (error: Exception) {
                Log.e("ReminderBootReceiver", "Unable to reschedule reminders after $action", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

internal object ReminderSystemBroadcasts {
    fun isRescheduleAction(action: String?): Boolean = action in setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
    )
}
