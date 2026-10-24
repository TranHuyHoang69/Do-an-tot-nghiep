package com.example.moneymatev2.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.moneymatev2.MainActivity
import com.example.moneymatev2.R
import com.example.moneymatev2.domain.model.ReminderModel
import com.example.moneymatev2.domain.repository.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository
) {
    private val notificationManager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)
    private val notificationLock = Any()

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
                ReminderLocalizedResources.getString(context, R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description =
                    ReminderLocalizedResources.getString(context, R.string.reminder_channel_description)
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
        ownerUid: String,
        reminder: ReminderModel
    ): Boolean = synchronized(notificationLock) {
            // The receiver may have loaded A's reminder just before A signed out.
            if (ownerUid.isBlank() || authRepository.getCurrentUserId() != ownerUid) return@synchronized false
            createNotificationChannel()
            val tag = ReminderNotificationIdentity.tagFor(ownerUid, reminder.id)

            if (ReminderNotificationAvailability.status(context) != ReminderNotificationStatus.AVAILABLE) {
                return@synchronized false
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
                    putExtra(EXTRA_OWNER_UID, ownerUid)
                    data = Uri.parse("moneymate://notification/reminder/${Uri.encode(tag)}")
                }

            /*
             * Mỗi reminder có PendingIntent riêng.
             */
            val contentPendingIntent =
                PendingIntent.getActivity(
                    context,
                    0,
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
                            ?: ReminderLocalizedResources.getString(context, R.string.reminder_notification_fallback)
                    )
                    .setPriority(
                        NotificationCompat.PRIORITY_HIGH
                    )
                    .setAutoCancel(true)
                    .setContentIntent(
                        contentPendingIntent
                    )
                    .build()

            // Recheck while holding the same lock used by cleanup. If logout wins,
            // the notification is never posted; if show wins, cleanup removes it.
            if (authRepository.getCurrentUserId() != ownerUid) return@synchronized false
            try {
                NotificationManagerCompat.from(context).notify(
                    tag,
                    ReminderNotificationIdentity.NOTIFICATION_ID,
                    notification
                )
                true
            } catch (_: SecurityException) {
                false
            }
    }

    /** Cancels only notifications posted for this UID by the tagged scheme. */
    fun cancelForUser(userId: String) = synchronized(notificationLock) {
        if (userId.isBlank()) return@synchronized
        activeReminders().filter {
            ReminderNotificationIdentity.belongsTo(it.tag, userId)
        }.forEach { notificationManager.cancel(it.tag, it.id) }
    }

    /** On process start, remove tagged reminders from accounts other than the current one. */
    fun reconcileOnStart(currentUid: String?) = synchronized(notificationLock) {
        activeReminders().filter {
            ReminderNotificationIdentity.isTaggedReminder(it.tag) &&
                (currentUid == null || !ReminderNotificationIdentity.belongsTo(it.tag, currentUid))
        }.forEach { notificationManager.cancel(it.tag, it.id) }
    }

    private fun activeReminders(): Array<StatusBarNotification> =
        notificationManager.activeNotifications.filter {
            it.packageName == context.packageName &&
                it.notification.channelId == CHANNEL_ID
        }.toTypedArray()

    companion object {

        const val CHANNEL_ID =
            "money_mate_reminders"

        const val EXTRA_REMINDER_ID =
            "notification_reminder_id"

        const val EXTRA_OWNER_UID = "notification_reminder_owner_uid"

    }
}

/** A full reminder ID is kept in the tag, while the UID is represented by a stable digest. */
internal object ReminderNotificationIdentity {
    const val NOTIFICATION_ID = 0
    private const val PREFIX = "moneymate.reminder.uid."

    fun tagFor(uid: String, reminderId: String): String = ownerPrefix(uid) + reminderId

    fun belongsTo(tag: String?, uid: String): Boolean =
        tag?.startsWith(ownerPrefix(uid)) == true

    fun isTaggedReminder(tag: String?): Boolean = tag?.startsWith(PREFIX) == true

    private fun ownerPrefix(uid: String): String =
        PREFIX + MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) } + "."
}
