package com.example.moneymatev2.data.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.moneymatev2.MainActivity
import com.example.moneymatev2.R
import com.example.moneymatev2.domain.model.ReminderModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun createNotificationChannel() {
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description =
                    CHANNEL_DESCRIPTION
            }

        val notificationManager =
            context.getSystemService(
                NotificationManager::class.java
            )

        notificationManager
            .createNotificationChannel(
                channel
            )
    }

    fun show(
        reminder: ReminderModel
    ) {
        createNotificationChannel()

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        /*
         * Khi user chạm notification:
         *
         * - App đóng:
         *      MainActivity.onCreate()
         *
         * - App đang mở:
         *      MainActivity.onNewIntent()
         */
        val contentIntent =
            Intent(
                context,
                MainActivity::class.java
            ).apply {

                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP

                putExtra(
                    EXTRA_REMINDER_ID,
                    reminder.id
                )
            }

        /*
         * Mỗi reminder có PendingIntent riêng.
         */
        val contentPendingIntent =
            PendingIntent.getActivity(
                context,
                reminder.id.hashCode(),
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        val notification =
            NotificationCompat.Builder(
                context,
                CHANNEL_ID
            )
                .setSmallIcon(
                    R.drawable.ic_notification
                )
                .setContentTitle(
                    reminder.title
                )
                .setContentText(
                    reminder.note
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: DEFAULT_MESSAGE
                )
                .setPriority(
                    NotificationCompat.PRIORITY_HIGH
                )
                .setAutoCancel(true)
                .setContentIntent(
                    contentPendingIntent
                )
                .build()

        NotificationManagerCompat
            .from(context)
            .notify(
                reminder.id.hashCode(),
                notification
            )
    }

    companion object {

        const val CHANNEL_ID =
            "money_mate_reminders"

        const val CHANNEL_NAME =
            "Reminders"

        const val CHANNEL_DESCRIPTION =
            "MoneyMate reminder notifications"

        const val EXTRA_REMINDER_ID =
            "notification_reminder_id"

        private const val DEFAULT_MESSAGE =
            "You have a MoneyMate reminder."
    }
}