package com.example.moneymatev2.presentation.profile

import android.app.Application
import com.example.moneymatev2.R
import com.example.moneymatev2.domain.model.UserModel
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import androidx.lifecycle.viewModelScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val auth = mock(AuthRepository::class.java)
    private val users = mock(UserRepository::class.java)
    private val account = MutableStateFlow<UserModel?>(null)
    private val localA = MutableStateFlow<UserModel?>(null)
    private val localB = MutableStateFlow<UserModel?>(null)
    private var currentUid: String? = null
    private var providers: Set<String> = emptySet()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        `when`(auth.observeAuthState()).thenReturn(account)
        `when`(auth.getCurrentUserId()).thenAnswer { currentUid }
        `when`(auth.getCurrentProviderIds()).thenAnswer { providers }
        `when`(users.getCurrentUser("a")).thenReturn(localA)
        `when`(users.getCurrentUser("b")).thenReturn(localB)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun signedInShowsFirebaseValuesWithSameUidRoomFallback() = runTest(dispatcher) {
        currentUid = "a"
        providers = setOf("password")
        localA.value = UserModel("a", "room@example.com", "Room name", null)
        account.value = UserModel("a", "auth@example.com", null, null)
        val vm = ProfileViewModel(auth, users)
        try {
            advanceUntilIdle()
            val state = vm.uiState.value as ProfileUiState.SignedIn
            assertEquals("a", state.userId)
            assertEquals("Room name", state.displayName)
            assertEquals("auth@example.com", state.email)
            assertTrue(state.hasPasswordProvider)
            assertFalse(state.hasGoogleProvider)
        } finally { vm.viewModelScope.cancel() }
    }

    @Test fun logoutAndAccountSwitchNeverExposePreviousOwnersFields() = runTest(dispatcher) {
        currentUid = "a"
        localA.value = UserModel("a", "a-local@example.com", "A local", null)
        account.value = UserModel("a", "a@example.com", "Alice", null)
        val vm = ProfileViewModel(auth, users)
        try {
            advanceUntilIdle()
            assertEquals("Alice", (vm.uiState.value as ProfileUiState.SignedIn).displayName)
            vm.openDeleteConfirmation("a")

            currentUid = null
            account.value = null
            advanceUntilIdle()
            assertEquals(ProfileUiState.Guest, vm.uiState.value)
            assertEquals(ProfileDeleteState(), vm.deleteState.value)

            currentUid = "b"
            providers = setOf("google.com")
            localB.value = UserModel("b", "b-local@example.com", "B local", null)
            account.value = UserModel("b", "b@example.com", "Bob", null)
            advanceUntilIdle()
            val state = vm.uiState.value as ProfileUiState.SignedIn
            assertEquals("b", state.userId)
            assertEquals("Bob", state.displayName)
            assertEquals("b@example.com", state.email)
            assertFalse(state.hasPasswordProvider)
            assertTrue(state.hasGoogleProvider)
            assertEquals(ProfileDeleteState(), vm.deleteState.value)
        } finally { vm.viewModelScope.cancel() }
    }

    @Test fun deleteDialogsRequireOwnerAndNeverDeleteAuth() = runTest(dispatcher) {
        currentUid = "a"
        account.value = UserModel("a", "a@example.com", "Alice", null)
        val vm = ProfileViewModel(auth, users)
        try {
            advanceUntilIdle()
            vm.openDeleteConfirmation("b")
            assertEquals(ProfileDeleteStage.NONE, vm.deleteState.value.stage)
            vm.openDeleteConfirmation("a")
            vm.openDeleteConfirmation("a")
            assertEquals(ProfileDeleteStage.CONFIRMATION, vm.deleteState.value.stage)
            vm.continueToReauthentication("b")
            assertEquals(ProfileDeleteStage.CONFIRMATION, vm.deleteState.value.stage)
            vm.continueToReauthentication("a")
            assertEquals(ProfileDeleteStage.REAUTHENTICATION, vm.deleteState.value.stage)
            vm.dismissDelete()
            assertEquals(ProfileDeleteState(), vm.deleteState.value)
            verify(auth, never()).signOut()
        } finally { vm.viewModelScope.cancel() }
    }

    @Test fun profileStringsExistInVietnameseAndEnglish() {
        val app = RuntimeEnvironment.getApplication()
        val vi = app.createConfigurationContext(app.resources.configuration.apply { setLocale(Locale("vi")) })
        val enConfig = android.content.res.Configuration(app.resources.configuration).apply { setLocale(Locale.ENGLISH) }
        val en = app.createConfigurationContext(enConfig)
        assertEquals("Thông tin người dùng", vi.getString(R.string.profile_title))
        assertEquals("User information", en.getString(R.string.profile_title))
        assertEquals("Xóa tài khoản", vi.getString(R.string.profile_delete))
        assertEquals("Delete account", en.getString(R.string.profile_delete))
        assertTrue(en.getString(R.string.profile_deletion_unavailable).isNotBlank())
    }
}
