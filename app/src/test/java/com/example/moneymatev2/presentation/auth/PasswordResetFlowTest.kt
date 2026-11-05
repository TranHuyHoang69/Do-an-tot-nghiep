package com.example.moneymatev2.presentation.auth

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.example.moneymatev2.domain.model.AppResult
import com.example.moneymatev2.domain.model.PasswordResetError
import com.example.moneymatev2.domain.model.UserModel
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.usecase.auth.SendPasswordResetEmailUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PasswordResetFlowTest {
    private val dispatcher = StandardTestDispatcher()
    private val auth = FakeAuth()
    private val useCase = SendPasswordResetEmailUseCase(auth)

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun blankAndMalformedEmailNeverCallRepository() = runTest(dispatcher) {
        for (email in listOf("", "   ")) {
            assertEquals(PasswordResetError.EmptyEmail,
                (useCase(email) as AppResult.Failure).error)
        }
        for (email in listOf("not-an-email", "a @example.com")) {
            assertEquals(PasswordResetError.InvalidEmail,
                (useCase(email) as AppResult.Failure).error)
        }
        assertEquals(0, auth.requests)
    }

    @Test fun validEmailIsTrimmedAndCallsRepositoryOnceWithoutChangingSession() = runTest(dispatcher) {
        assertTrue(useCase("  alice@example.com  ") is AppResult.Success)
        assertEquals(1, auth.requests)
        assertEquals("alice@example.com", auth.lastEmail)
        assertEquals("original-uid", auth.uid)
        assertEquals(0, auth.signOutCalls)
    }

    @Test fun repeatedSubmitDuringLoadingSendsOnlyOneRequestAndAcceptsOnce() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        auth.gate = gate
        val vm = PasswordResetViewModel(useCase)
        try {
            vm.onEmailChanged("alice@example.com")
            vm.submit()
            vm.submit()
            assertEquals(PasswordResetStatus.Loading, vm.uiState.value.status)
            runCurrent()
            assertEquals(1, auth.requests)
            vm.submit()
            assertEquals(1, auth.requests)
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(PasswordResetStatus.Accepted, vm.uiState.value.status)
            vm.submit()
            assertEquals(1, auth.requests)
        } finally { vm.viewModelScope.cancel() }
    }

    @Test fun errorStopsLoadingKeepsEmailAndAllowsRetry() = runTest(dispatcher) {
        auth.result = AppResult.Failure(PasswordResetError.Network)
        val vm = PasswordResetViewModel(useCase)
        try {
            vm.onEmailChanged("alice@example.com")
            vm.submit()
            advanceUntilIdle()
            assertEquals(PasswordResetStatus.Error(PasswordResetError.Network), vm.uiState.value.status)
            assertEquals("alice@example.com", vm.uiState.value.email)
            auth.result = AppResult.Success(Unit)
            vm.submit()
            advanceUntilIdle()
            assertEquals(PasswordResetStatus.Accepted, vm.uiState.value.status)
            assertEquals(2, auth.requests)
        } finally { vm.viewModelScope.cancel() }
    }

    @Test fun openingAndLeavingScreenDoesNotSendRequest() = runTest(dispatcher) {
        val vm = PasswordResetViewModel(useCase)
        vm.onEmailChanged("alice@example.com")
        vm.viewModelScope.cancel()
        assertEquals(0, auth.requests)
    }

    private class FakeAuth : AuthRepository {
        var uid = "original-uid"
        var signOutCalls = 0
        var requests = 0
        var lastEmail: String? = null
        var gate: CompletableDeferred<Unit>? = null
        var result: AppResult<Unit> = AppResult.Success(Unit)

        override fun observeAuthState(): Flow<UserModel?> = flowOf(null)
        override fun getCurrentUserId(): String = uid
        override suspend fun signIn(email: String, password: String): AppResult<UserModel> = error("unused")
        override suspend fun signUp(email: String, password: String, displayName: String): AppResult<UserModel> = error("unused")
        override suspend fun signInWithGoogle(idToken: String): AppResult<UserModel> = error("unused")
        override suspend fun signOut() { signOutCalls++ }
        override suspend fun sendPasswordResetEmail(email: String): AppResult<Unit> {
            requests++
            lastEmail = email
            gate?.await()
            return result
        }
    }
}
