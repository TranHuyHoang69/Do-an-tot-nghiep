package com.example.moneymatev2.core.reminder

import android.app.Application
import com.example.moneymatev2.data.local.entity.RepeatRule
import com.example.moneymatev2.data.reminder.ReminderNotificationManager
import com.example.moneymatev2.domain.model.ReminderModel
import com.example.moneymatev2.domain.model.UserModel
import com.example.moneymatev2.domain.reminder.ReminderScheduler
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.ReminderRepository
import com.example.moneymatev2.domain.usecase.reminder.RescheduleRemindersUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class ReminderAuthSessionManagerTest {
    private val users = MutableStateFlow<UserModel?>(user("account-a"))
    private val auth = mock(AuthRepository::class.java)
    private val repository = mock(ReminderRepository::class.java)
    private val scheduler = mock(ReminderScheduler::class.java)
    private val reschedule = mock(RescheduleRemindersUseCase::class.java)
    private val notifications = mock(ReminderNotificationManager::class.java)

    @Before fun setUp() = runBlocking {
        `when`(auth.observeAuthState()).thenReturn(users)
        `when`(repository.getActiveRemindersOnce("account-a")).thenReturn(
            listOf(ReminderModel("alarm-a", "A", null, 1L, RepeatRule.NONE, true))
        )
        Unit
    }

    @Test fun logoutCleansOldNotificationsAndPreservesExistingAlarmCancellation() = runBlocking {
        manager().start()
        verify(notifications, timeout(3000)).reconcileOnStart("account-a")

        users.value = null
        verify(notifications, timeout(3000)).cancelForUser("account-a")
        verify(scheduler, timeout(3000)).cancel("alarm-a")
        verify(repository, never()).deleteReminder("alarm-a")
    }

    @Test fun switchingAccountsCleansAAndSchedulesBWithoutCleaningB() = runBlocking {
        manager().start()
        verify(notifications, timeout(3000)).reconcileOnStart("account-a")

        users.value = user("account-b")
        verify(notifications, timeout(3000)).cancelForUser("account-a")
        verify(scheduler, timeout(3000)).cancel("alarm-a")
        verify(reschedule, timeout(3000)).invoke("account-b")
        verify(notifications, never()).cancelForUser("account-b")
    }

    @Test fun unchangedUidDoesNotCancelNotificationsOrAlarms() = runBlocking {
        manager().start()
        verify(notifications, timeout(3000)).reconcileOnStart("account-a")

        users.value = user("account-a")
        verify(notifications, never()).cancelForUser("account-a")
        verify(scheduler, never()).cancel("alarm-a")
    }

    @Test fun cleanupFailureDoesNotBlockAlarmCancellationOrNewOwner() = runBlocking {
        doThrow(IllegalStateException("notification service unavailable"))
            .`when`(notifications).cancelForUser("account-a")
        manager().start()
        verify(notifications, timeout(3000)).reconcileOnStart("account-a")

        users.value = user("account-b")
        verify(notifications, timeout(3000)).cancelForUser("account-a")
        verify(scheduler, timeout(3000)).cancel("alarm-a")
        verify(reschedule, timeout(3000)).invoke("account-b")
    }

    private fun manager() = ReminderAuthSessionManager(
        auth, repository, scheduler, reschedule, notifications
    )

    private fun user(uid: String) = UserModel(uid, null, null, null)
}
