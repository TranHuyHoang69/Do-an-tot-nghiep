package com.example.moneymatev2.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.moneymatev2.domain.model.ReminderModel
import com.example.moneymatev2.domain.reminder.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : ReminderScheduler {

    private val alarmManager: AlarmManager =
        context.getSystemService(AlarmManager::class.java)

    override fun schedule(reminder: ReminderModel) {
        if (!reminder.isActive) {
            cancel(reminder.id)
            return
        }

        // Không schedule thời điểm đã nằm trong quá khứ.
        // Reminder lặp sẽ được tính lần tiếp theo ở bước xử lý repeat sau.
        if (reminder.triggerAt <= System.currentTimeMillis()) {
            return
        }

        val pendingIntent =
            createPendingIntent(reminder.id)

        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminder.triggerAt,
                pendingIntent
            )
        } else {
            // Không có quyền exact alarm:
            // vẫn schedule reminder thay vì crash/mất hoàn toàn lời nhắc.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminder.triggerAt,
                pendingIntent
            )
        }
    }

    override fun cancel(reminderId: String) {
        val pendingIntent =
            findPendingIntent(reminderId)
                ?: return

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    override fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun createPendingIntent(
        reminderId: String
    ): PendingIntent {

        return PendingIntent.getBroadcast(
            context,
            0,
            createIntent(reminderId),
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun findPendingIntent(
        reminderId: String
    ): PendingIntent? {

        return PendingIntent.getBroadcast(
            context,
            0,
            createIntent(reminderId),
            PendingIntent.FLAG_NO_CREATE or
                    PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createIntent(
        reminderId: String
    ): Intent {

        return Intent(
            context,
            ReminderReceiver::class.java
        ).apply {

            action = ACTION_REMINDER

            data = Uri.parse(
                "moneymate://reminder/${Uri.encode(reminderId)}"
            )

            putExtra(
                EXTRA_REMINDER_ID,
                reminderId
            )
        }
    }

    companion object {
        const val ACTION_REMINDER =
            "com.example.moneymatev2.ACTION_REMINDER"

        const val EXTRA_REMINDER_ID =
            "extra_reminder_id"
    }
}