package com.example.moneymatev2.data.reminder

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.example.moneymatev2.R
import com.example.moneymatev2.data.local.entity.RepeatRule
import com.example.moneymatev2.domain.model.ReminderModel
import com.example.moneymatev2.domain.repository.AuthRepository
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class ReminderNotificationManagerTest {
    private lateinit var context: Application
    private lateinit var system: NotificationManager
    private lateinit var notifications: ReminderNotificationManager
    private lateinit var auth: AuthRepository
    private var currentUid: String? = "account-a"

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        system = context.getSystemService(NotificationManager::class.java)
        auth = mock(AuthRepository::class.java)
        `when`(auth.getCurrentUserId()).thenAnswer { currentUid }
        notifications = ReminderNotificationManager(context, auth)
    }

    @After fun tearDown() {
        system.activeNotifications.forEach { system.cancel(it.tag, it.id) }
    }

    private fun reminder(id: String) = ReminderModel(
        id, "Reminder $id", null, System.currentTimeMillis() + 60_000,
        RepeatRule.NONE, true
    )

    private fun activeTags() = system.activeNotifications.mapNotNull { it.tag }.toSet()

    @Test fun identifiersAreUniqueAcrossOwnersAndReminders() {
        val a1 = ReminderNotificationIdentity.tagFor("account-a", "one")
        val a2 = ReminderNotificationIdentity.tagFor("account-a", "two")
        val b1 = ReminderNotificationIdentity.tagFor("account-b", "one")
        assertNotEquals(a1, a2)
        assertNotEquals(a1, b1)
        assertTrue(ReminderNotificationIdentity.belongsTo(a1, "account-a"))
        assertFalse(ReminderNotificationIdentity.belongsTo(a1, "account-b"))
    }

    @Test fun eachPendingIntentKeepsItsOwnReminderAndOwner() {
        notifications.show("account-a", reminder("one"))
        notifications.show("account-a", reminder("two"))
        val posted = system.activeNotifications.associateBy { it.notification.extras.getString("android.title") }
        val one = shadowOf(posted.getValue("Reminder one").notification.contentIntent).savedIntent
        val two = shadowOf(posted.getValue("Reminder two").notification.contentIntent).savedIntent
        assertEquals("one", one.getStringExtra(ReminderNotificationManager.EXTRA_REMINDER_ID))
        assertEquals("two", two.getStringExtra(ReminderNotificationManager.EXTRA_REMINDER_ID))
        assertEquals("account-a", one.getStringExtra(ReminderNotificationManager.EXTRA_OWNER_UID))
        assertEquals("account-a", two.getStringExtra(ReminderNotificationManager.EXTRA_OWNER_UID))
        assertNotEquals(one.data, two.data)
        assertFalse(one.filterEquals(two))
    }

    @Test fun logoutCancelsOnlyOldOwnersRemindersAndRepeatedCleanupIsSafe() {
        notifications.show("account-a", reminder("one"))
        notifications.show("account-a", reminder("two"))
        currentUid = "account-b"
        notifications.show("account-b", reminder("one"))
        val bTag = ReminderNotificationIdentity.tagFor("account-b", "one")
        assertEquals(3, activeTags().size)

        notifications.cancelForUser("account-a")
        notifications.cancelForUser("account-a")
        assertEquals(setOf(bTag), activeTags())
    }

    @Test fun noNotificationAndUnchangedOwnerDoNotRemoveAnything() {
        notifications.cancelForUser("account-a")
        notifications.show("account-a", reminder("one"))
        notifications.reconcileOnStart("account-a")
        assertEquals(
            setOf(ReminderNotificationIdentity.tagFor("account-a", "one")),
            activeTags()
        )
    }

    @Test fun processRestartReconciliationRemovesOnlyOtherOwnersTaggedReminders() {
        notifications.show("account-a", reminder("one"))
        currentUid = "account-b"
        notifications.show("account-b", reminder("two"))
        notifications.reconcileOnStart("account-b")
        assertEquals(
            setOf(ReminderNotificationIdentity.tagFor("account-b", "two")),
            activeTags()
        )
    }

    @Test fun staleReceiverOwnerCannotPostAfterAccountChange() {
        currentUid = "account-b"
        notifications.show("account-a", reminder("old"))
        assertTrue(activeTags().isEmpty())
    }

    @Test fun cleanupDoesNotTouchOtherChannelsOrAmbiguousLegacyNotifications() {
        notifications.createNotificationChannel()
        system.createNotificationChannel(
            NotificationChannel("other", "Other", NotificationManager.IMPORTANCE_DEFAULT)
        )
        system.notify(7, NotificationCompat.Builder(context, "other")
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("Other").build())
        system.notify(8, NotificationCompat.Builder(context, ReminderNotificationManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("Legacy").build())
        notifications.show("account-a", reminder("one"))

        notifications.cancelForUser("account-a")
        assertEquals(2, system.activeNotifications.size)
        assertTrue(system.activeNotifications.any { it.id == 7 })
        assertTrue(system.activeNotifications.any { it.id == 8 })
    }
}
