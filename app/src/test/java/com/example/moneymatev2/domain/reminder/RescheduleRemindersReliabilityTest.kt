package com.example.moneymatev2.domain.reminder

import com.example.moneymatev2.data.local.entity.RepeatRule
import com.example.moneymatev2.domain.model.ReminderModel
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.ReminderRepository
import com.example.moneymatev2.domain.usecase.reminder.RescheduleRemindersUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class RescheduleRemindersReliabilityTest {
    private val repository = mock(ReminderRepository::class.java)
    private val scheduler = mock(ReminderScheduler::class.java)
    private val auth = mock(AuthRepository::class.java)
    private val useCase = RescheduleRemindersUseCase(repository, scheduler, ReminderRepeatCalculator(), auth)

    @Test fun onlyCurrentUsersActiveFutureRemindersAreScheduled() = runTest {
        val future = ReminderModel("future", "Future", null,
            System.currentTimeMillis() + 600_000L, RepeatRule.NONE, true)
        `when`(auth.getCurrentUserId()).thenReturn("a")
        `when`(repository.getActiveRemindersOnce("a")).thenReturn(listOf(future, future.copy(id = "off", isActive = false)))
        useCase("b")
        verify(repository, never()).getActiveRemindersOnce("b")
        useCase("a")
        verify(scheduler).schedule(future)
        verify(scheduler, never()).schedule(future.copy(id = "off", isActive = false))
    }

    @Test fun overdueOneTimeReminderStaysEditableWithoutCatchUpNotification() = runTest {
        val overdue = ReminderModel("old", "Old", null,
            System.currentTimeMillis() - 60_000L, RepeatRule.NONE, true)
        `when`(auth.getCurrentUserId()).thenReturn("a")
        `when`(repository.getActiveRemindersOnce("a")).thenReturn(listOf(overdue))
        useCase("a")
        verify(scheduler).cancel("old")
        verify(repository, never()).setReminderActive("old", false)
        verify(scheduler, never()).schedule(overdue)
    }

    @Test fun repeatedRescheduleKeepsAnchorAndUsesSameReminderIdentity() = runTest {
        val now = System.currentTimeMillis()
        val anchor = now - 60L * 24 * 60 * 60 * 1000
        val monthly = ReminderModel("monthly", "Monthly", null, anchor,
            RepeatRule.MONTHLY, true, anchor)
        `when`(auth.getCurrentUserId()).thenReturn("a")
        `when`(repository.getActiveRemindersOnce("a")).thenReturn(listOf(monthly))
        useCase("a")
        useCase("a")
        val scheduled = mockingDetails(scheduler).invocations
            .filter { it.method.name == "schedule" }
            .map { it.arguments[0] as ReminderModel }
        assertEquals(2, scheduled.size)
        assertEquals(1, scheduled.map { it.id }.toSet().size)
        assertTrue(scheduled.all { it.anchorAt == anchor && it.triggerAt > now })
    }
}
