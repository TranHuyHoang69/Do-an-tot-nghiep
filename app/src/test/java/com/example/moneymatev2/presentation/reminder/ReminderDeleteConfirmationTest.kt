package com.example.moneymatev2.presentation.reminder

import android.app.Application
import com.example.moneymatev2.data.local.entity.RepeatRule
import com.example.moneymatev2.domain.model.ReminderModel
import com.example.moneymatev2.domain.model.UserModel
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.ReminderRepository
import com.example.moneymatev2.domain.usecase.reminder.CreateReminderUseCase
import com.example.moneymatev2.domain.usecase.reminder.DeleteReminderUseCase
import com.example.moneymatev2.domain.usecase.reminder.ToggleReminderUseCase
import com.example.moneymatev2.domain.usecase.reminder.UpdateReminderUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class ReminderDeleteConfirmationTest {
    private val dispatcher = StandardTestDispatcher()
    private val auth = mock(AuthRepository::class.java)
    private val repository = mock(ReminderRepository::class.java)
    private val delete = mock(DeleteReminderUseCase::class.java)

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        `when`(auth.observeAuthState()).thenReturn(flowOf(UserModel("a", null, null, null)))
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun viewModel() = ReminderViewModel(
        auth, repository, mock(CreateReminderUseCase::class.java),
        mock(UpdateReminderUseCase::class.java), mock(ToggleReminderUseCase::class.java), delete
    )

    @Test fun selectionSnapshotExcludesStaleIdsAndDoesNotDeleteBeforeConfirmation() = runTest(dispatcher) {
        val visible = listOf(ReminderModel("one", "One", null, 10, RepeatRule.NONE, true),
            ReminderModel("two", "Two", null, 20, RepeatRule.NONE, true))
        val snapshot = snapshotReminderDeleteIds(listOf("one", "foreign", "one"), visible)
        assertEquals(listOf("one"), snapshot)
        val vm = viewModel()
        advanceUntilIdle()
        verify(delete, never()).invoke("one")
        // Dismissing the dialog does not call the ViewModel at all.
        assertEquals(listOf("one"), snapshot)
        verify(delete, never()).invoke("one")
        assertTrue(vm.deleteReminders(snapshot))
        advanceUntilIdle()
        verify(delete, times(1)).invoke("one")
    }

    @Test fun emptySelectionAndRepeatedConfirmCannotStartAnotherDelete() = runTest(dispatcher) {
        val vm = viewModel()
        assertFalse(vm.deleteReminders(emptyList()))
        assertTrue(vm.deleteReminders(listOf("one", "one")))
        assertFalse(vm.deleteReminders(listOf("two")))
        advanceUntilIdle()
        verify(delete, times(1)).invoke("one")
        verify(delete, never()).invoke("two")
        assertEquals(ReminderEvent.Deleted, vm.events.first())
    }

    @Test fun failedDeleteEmitsErrorAndNeverReportsSuccess() = runTest(dispatcher) {
        `when`(delete.invoke("bad")).thenThrow(IllegalStateException("private database detail"))
        val vm = viewModel()
        assertTrue(vm.deleteReminders(listOf("bad")))
        advanceUntilIdle()
        assertEquals(ReminderEvent.Error(ReminderErrorKind.DELETE), vm.events.first())
        assertFalse(vm.isDeleting.value)
    }
}
