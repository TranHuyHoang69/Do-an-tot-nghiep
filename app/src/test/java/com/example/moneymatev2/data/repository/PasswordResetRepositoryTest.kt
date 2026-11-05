package com.example.moneymatev2.data.repository

import android.app.Application
import com.example.moneymatev2.data.remote.sync.AuthSessionVersion
import com.example.moneymatev2.data.remote.sync.SyncTrigger
import com.example.moneymatev2.domain.model.AppResult
import com.example.moneymatev2.domain.model.PasswordResetError
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PasswordResetRepositoryTest {
    private val firebase = mock(FirebaseAuth::class.java)
    private val sessionVersion = mock(AuthSessionVersion::class.java)
    private val syncTrigger = mock(SyncTrigger::class.java)
    private val repository = AuthRepositoryImpl(firebase, sessionVersion, syncTrigger,
        RuntimeEnvironment.getApplication())

    @Test fun acceptedRequestCallsInjectedFirebaseOnlyAndLeavesSessionUntouched() = runBlocking {
        `when`(firebase.sendPasswordResetEmail("alice@example.com"))
            .thenReturn(Tasks.forResult<Void>(null))
        assertTrue(repository.sendPasswordResetEmail("alice@example.com") is AppResult.Success)
        verify(firebase, times(1)).sendPasswordResetEmail("alice@example.com")
        verify(firebase, never()).signOut()
        verifyNoInteractions(sessionVersion, syncTrigger)
    }

    @Test fun unknownAddressFromLegacyFirebaseConfigurationGetsNeutralResult() = runBlocking {
        `when`(firebase.sendPasswordResetEmail("unknown@example.com"))
            .thenReturn(Tasks.forException(
                FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "Unknown user")))
        assertTrue(repository.sendPasswordResetEmail("unknown@example.com") is AppResult.Success)
        verifyNoInteractions(sessionVersion, syncTrigger)
    }

    @Test fun networkThrottlingAndOtherFirebaseFailuresReturnTypedErrors() = runBlocking {
        val cases = listOf(
            FirebaseNetworkException("offline") to PasswordResetError.Network,
            java.net.SocketTimeoutException("timeout") to PasswordResetError.Network,
            FirebaseTooManyRequestsException("throttled") to PasswordResetError.TooManyRequests,
            IllegalStateException("backend unavailable") to PasswordResetError.Unavailable
        )
        cases.forEach { (exception, expected) ->
            `when`(firebase.sendPasswordResetEmail("alice@example.com"))
                .thenReturn(Tasks.forException(exception))
            val result = repository.sendPasswordResetEmail("alice@example.com") as AppResult.Failure
            assertEquals(expected, result.error)
        }
        verify(firebase, times(4)).sendPasswordResetEmail("alice@example.com")
        verifyNoInteractions(sessionVersion, syncTrigger)
    }
}
