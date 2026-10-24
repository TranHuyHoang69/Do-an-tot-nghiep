package com.example.moneymatev2.data.reminder

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

enum class ReminderNotificationStatus { AVAILABLE, RUNTIME_PERMISSION, APP_DISABLED, CHANNEL_DISABLED }

/** Check all Android gates before claiming that a reminder can notify. */
object ReminderNotificationAvailability {
    fun status(context: Context): ReminderNotificationStatus {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return ReminderNotificationStatus.RUNTIME_PERMISSION

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return ReminderNotificationStatus.APP_DISABLED
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(ReminderNotificationManager.CHANNEL_ID)
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) {
                return ReminderNotificationStatus.CHANNEL_DISABLED
            }
        }
        return ReminderNotificationStatus.AVAILABLE
    }
}
