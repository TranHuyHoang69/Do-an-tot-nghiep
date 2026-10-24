package com.example.moneymatev2.data.reminder

import android.app.Application
import android.app.NotificationManager
import com.example.moneymatev2.R
import com.example.moneymatev2.core.util.Localization
import com.example.moneymatev2.data.local.entity.RepeatRule
import com.example.moneymatev2.domain.model.ReminderModel
import com.example.moneymatev2.domain.repository.AuthRepository
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class ReminderLocalizationTest {
    @Test fun newReminderMessagesExistInVietnameseAndEnglish() {
        val base = RuntimeEnvironment.getApplication()
        val vi = Localization.updateLocale(base, "vi")
        val en = Localization.updateLocale(base, "en")
        val resources = listOf(
            R.string.reminder_notification_fallback,
            R.string.reminder_notification_blocked_message,
            R.string.reminder_delete_many_confirm,
            R.string.reminder_save_error,
            R.string.reminder_missed_once_hint
        )
        resources.forEach { id ->
            assertTrue(vi.getString(id).isNotBlank())
            assertTrue(en.getString(id).isNotBlank())
            assertNotEquals(vi.getString(id), en.getString(id))
        }
    }

    @Test fun notificationWithoutNoteUsesLocalizedResourceNotHardcodedText() {
        val context = RuntimeEnvironment.getApplication()
        val auth = mock(AuthRepository::class.java)
        `when`(auth.getCurrentUserId()).thenReturn("a")
        val manager = ReminderNotificationManager(context, auth)
        val reminder = ReminderModel("fallback", "Title", null,
            System.currentTimeMillis() + 60_000, RepeatRule.NONE, true)
        assertTrue(manager.show("a", reminder))
        val system = context.getSystemService(NotificationManager::class.java)
        val notification = system.activeNotifications.single().notification
        assertEquals(ReminderLocalizedResources.getString(context, R.string.reminder_notification_fallback),
            notification.extras.getCharSequence("android.text").toString())
        system.activeNotifications.forEach { system.cancel(it.tag, it.id) }
    }
}
