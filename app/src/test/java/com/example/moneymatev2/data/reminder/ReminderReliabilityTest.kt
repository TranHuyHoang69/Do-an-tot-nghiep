package com.example.moneymatev2.data.reminder

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import com.example.moneymatev2.data.local.entity.RepeatRule
import com.example.moneymatev2.domain.model.ReminderModel
import com.example.moneymatev2.domain.reminder.ReminderRepeatCalculator
import com.example.moneymatev2.domain.reminder.ReminderScheduler
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.ReminderRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.mockingDetails
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class ReminderReliabilityTest {
    @Test fun appDisabledAndChannelBlockedAreDetected() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals(ReminderNotificationStatus.AVAILABLE, ReminderNotificationAvailability.status(context))
        shadowOf(manager).setNotificationsEnabled(false)
        assertEquals(ReminderNotificationStatus.APP_DISABLED, ReminderNotificationAvailability.status(context))
        shadowOf(manager).setNotificationsEnabled(true)
        manager.createNotificationChannel(NotificationChannel(
            ReminderNotificationManager.CHANNEL_ID, "Blocked", NotificationManager.IMPORTANCE_NONE
        ))
        assertEquals(ReminderNotificationStatus.CHANNEL_DISABLED, ReminderNotificationAvailability.status(context))
        val auth = mock(AuthRepository::class.java)
        `when`(auth.getCurrentUserId()).thenReturn("a")
        val reminder = ReminderModel("one", "One", null, 10L, RepeatRule.NONE, true)
        assertFalse(ReminderNotificationManager(context, auth).show("a", reminder))
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test fun systemActionsThatNeedRescheduleAreRecognized() {
        assertTrue(ReminderSystemBroadcasts.isRescheduleAction(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(ReminderSystemBroadcasts.isRescheduleAction(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertTrue(ReminderSystemBroadcasts.isRescheduleAction(Intent.ACTION_TIME_CHANGED))
        assertTrue(ReminderSystemBroadcasts.isRescheduleAction(Intent.ACTION_TIMEZONE_CHANGED))
        assertFalse(ReminderSystemBroadcasts.isRescheduleAction(Intent.ACTION_VIEW))
    }

    @Test fun blockedOneTimeReminderRemainsActiveAndRepeatingReminderContinues() = runTest {
        val repository = mock(ReminderRepository::class.java)
        val scheduler = mock(ReminderScheduler::class.java)
        val notifications = mock(ReminderNotificationManager::class.java)
        val auth = mock(AuthRepository::class.java)
        `when`(auth.getCurrentUserId()).thenReturn("a")
        val once = ReminderModel("once", "Once", null, 1L, RepeatRule.NONE, true)
        val daily = ReminderModel("daily", "Daily", null, 1L, RepeatRule.DAILY, true, 1L)
        `when`(repository.getReminder("once")).thenReturn(once)
        `when`(repository.getReminder("daily")).thenReturn(daily)
        `when`(notifications.show("a", once)).thenReturn(false)
        `when`(notifications.show("a", daily)).thenReturn(false)
        val receiver = ReminderReceiver().apply {
            reminderRepository = repository
            this.scheduler = scheduler
            notificationManager = notifications
            repeatCalculator = ReminderRepeatCalculator()
            authRepository = auth
        }
        receiver.handleReminder("once")
        verify(repository, never()).setReminderActive("once", false)
        receiver.handleReminder("daily")
        val updates = mockingDetails(repository).invocations.filter { it.method.name == "updateReminderTrigger" }
        assertEquals(1, updates.size)
        assertEquals("daily", updates.single().arguments[0])
        assertTrue((updates.single().arguments[1] as Long) > System.currentTimeMillis())
        val schedules = mockingDetails(scheduler).invocations.filter { it.method.name == "schedule" }
        assertEquals("daily", (schedules.single().arguments[0] as ReminderModel).id)
    }

    @Test fun deliveredOneTimeReminderStillCompletesNormally() = runTest {
        val repository = mock(ReminderRepository::class.java)
        val scheduler = mock(ReminderScheduler::class.java)
        val notifications = mock(ReminderNotificationManager::class.java)
        val auth = mock(AuthRepository::class.java)
        val once = ReminderModel("once", "Once", null, 1L, RepeatRule.NONE, true)
        `when`(auth.getCurrentUserId()).thenReturn("a")
        `when`(repository.getReminder("once")).thenReturn(once)
        `when`(notifications.show("a", once)).thenReturn(true)
        val receiver = ReminderReceiver().apply {
            reminderRepository = repository
            this.scheduler = scheduler
            notificationManager = notifications
            repeatCalculator = ReminderRepeatCalculator()
            authRepository = auth
        }
        receiver.handleReminder("once")
        verify(repository).setReminderActive("once", false)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ReminderRuntimePermissionTest {
    @Test fun deniedPostNotificationsIsBlocked() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(ReminderNotificationStatus.RUNTIME_PERMISSION,
            ReminderNotificationAvailability.status(context))
    }
}
