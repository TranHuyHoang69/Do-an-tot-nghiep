package com.example.moneymatev2.presentation.root

import androidx.lifecycle.SavedStateHandle
import com.example.moneymatev2.core.util.AppPreferences
import com.example.moneymatev2.domain.model.UserModel
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.presentation.security.LocalSecurityManager
import com.example.moneymatev2.presentation.security.LocalSecurityState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class PendingReminderNavigationTest {
    private val dispatcher = StandardTestDispatcher()
    private val account = MutableStateFlow<UserModel?>(null)
    private val auth = mock(AuthRepository::class.java)
    private val preferences = mock(AppPreferences::class.java)
    private val security = mock(LocalSecurityManager::class.java)

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        `when`(auth.observeAuthState()).thenReturn(account)
        `when`(preferences.themeMode).thenReturn(flowOf("system"))
        `when`(security.state).thenReturn(MutableStateFlow(LocalSecurityState.Guest()))
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun user(uid: String) = UserModel(uid, null, null, null)
    private fun create(handle: SavedStateHandle = SavedStateHandle()) =
        AppRootViewModel(auth, preferences, security, handle)

    @Test fun latestRequestWinsAndOldCompletionCannotClearIt() = runTest(dispatcher) {
        val vm = create()
        vm.offerReminderNavigation("first", "a")
        val first = vm.pendingReminderNavigation.value!!
        vm.offerReminderNavigation("second", "a")
        val second = vm.pendingReminderNavigation.value!!
        vm.completeReminderNavigation(first.requestId)
        assertEquals("second", vm.pendingReminderNavigation.value?.reminderId)
        assertTrue(second.requestId > first.requestId)
        vm.completeReminderNavigation(second.requestId)
        assertNull(vm.pendingReminderNavigation.value)
    }

    @Test fun pendingRequestSurvivesRecreationAndConsumedOneDoesNotReplay() = runTest(dispatcher) {
        account.value = user("a")
        val handle = SavedStateHandle()
        val vm = create(handle)
        advanceUntilIdle()
        vm.offerReminderNavigation("one", "a")
        val restored = create(handle)
        advanceUntilIdle()
        assertEquals(vm.pendingReminderNavigation.value, restored.pendingReminderNavigation.value)
        restored.completeReminderNavigation(restored.pendingReminderNavigation.value!!.requestId)
        assertNull(create(handle).pendingReminderNavigation.value)
    }

    @Test fun unauthenticatedStartupDiscardsAnOldSavedRequest() = runTest(dispatcher) {
        account.value = null
        val handle = SavedStateHandle(
            mapOf(PendingReminderNavigation.NAV_KEY to arrayListOf("old", "a", "1"))
        )
        val vm = create(handle)
        advanceUntilIdle()
        assertNull(vm.pendingReminderNavigation.value)
    }

    @Test fun logoutAndAccountSwitchDropOldOwnerButSameUidKeepsRequest() = runTest(dispatcher) {
        account.value = user("a")
        val vm = create()
        advanceUntilIdle()
        vm.offerReminderNavigation("one", "a")
        account.value = user("a")
        advanceUntilIdle()
        assertNotNull(vm.pendingReminderNavigation.value)
        account.value = user("b")
        advanceUntilIdle()
        assertNull(vm.pendingReminderNavigation.value)
        vm.offerReminderNavigation("two", "b")
        account.value = null
        advanceUntilIdle()
        assertNull(vm.pendingReminderNavigation.value)
    }

    @Test fun mismatchedOwnerAndMalformedSavedStateAreIgnored() = runTest(dispatcher) {
        val vm = create()
        vm.offerReminderNavigation("one", "a")
        vm.discardReminderNavigationForDifferentUser("b")
        assertNull(vm.pendingReminderNavigation.value)
        assertNull(PendingReminderNavigation.fromSavedState(arrayListOf("one", "a", "bad")))
        vm.offerReminderNavigation("", "a")
        assertNull(vm.pendingReminderNavigation.value)
    }

    @Test fun lockAndAccountMustBothAllowNavigation() {
        val request = PendingReminderNavigation("one", "a", 1)
        val locked = LocalSecurityState.Active("a", true, true, false)
        val unlocked = locked.copy(unlocked = true)
        assertFalse(request.isAllowedFor(AuthStartupState.LoggedIn("a"), locked))
        assertTrue(request.isAllowedFor(AuthStartupState.LoggedIn("a"), unlocked))
        assertFalse(request.isAllowedFor(AuthStartupState.LoggedIn("b"), unlocked))
        assertFalse(request.isAllowedFor(AuthStartupState.Guest, unlocked))
        assertFalse(request.isAllowedFor(AuthStartupState.LoggedIn("a"), unlocked.copy(uid = "b")))
    }
}
