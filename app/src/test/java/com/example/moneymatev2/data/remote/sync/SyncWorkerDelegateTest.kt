package com.example.moneymatev2.data.remote.sync

import com.example.moneymatev2.domain.model.AppResult
import com.example.moneymatev2.domain.model.UserModel
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.usecase.SyncOrchestrator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SyncWorkerDelegateTest {

    @Test
    fun validUid_callsSyncAllOnceAndReturnsSuccess() = runBlocking {
        val auth = FakeAuthRepository("uid-1")
        val sync = FakeSyncOrchestrator()
        val delegate = delegate(sync, auth)

        val result = delegate.run(attempt = 1)

        assertTrue(result is SyncWorkerRunResult.Success)
        assertEquals(listOf("uid-1"), sync.calls)
    }

    @Test
    fun noAuthenticatedUser_doesNotSyncOtherAccount() = runBlocking {
        val auth = FakeAuthRepository(null)
        val sync = FakeSyncOrchestrator()
        val delegate = delegate(sync, auth)

        val result = delegate.run(attempt = 1)

        assertTrue(result is SyncWorkerRunResult.Success)
        assertTrue(sync.calls.isEmpty())
        assertEquals(false, (result as SyncWorkerRunResult.Success).signedIn)
    }

    @Test
    fun successfulSync_returnsSuccess() = runBlocking {
        val delegate = delegate(FakeSyncOrchestrator(), FakeAuthRepository("uid-1"))

        val result = delegate.run(attempt = 1)

        assertTrue(result is SyncWorkerRunResult.Success)
        assertEquals(true, (result as SyncWorkerRunResult.Success).synced)
    }

    @Test
    fun transientNetworkError_returnsRetry() = runBlocking {
        val error = IOException("network unavailable")
        val delegate = delegate(
            FakeSyncOrchestrator(error),
            FakeAuthRepository("uid-1")
        )

        val result = delegate.run(attempt = 1)

        assertTrue(result is SyncWorkerRunResult.Retry)
        assertSame(error, (result as SyncWorkerRunResult.Retry).error)
    }

    @Test
    fun cancellation_propagates() = runBlocking {
        val delegate = delegate(
            FakeSyncOrchestrator(CancellationException("cancelled")),
            FakeAuthRepository("uid-1")
        )

        try {
            delegate.run(attempt = 1)
            throw AssertionError("Expected CancellationException")
        } catch (e: CancellationException) {
            assertEquals("cancelled", e.message)
        }
    }

    @Test
    fun permissionDeniedLikeCloudPullError_returnsPermanentFailure() = runBlocking {
        val error = CloudPullService.CloudPullException(
            listOf(
                CloudPullService.CloudPullFailure(
                    collection = "recurring_transactions",
                    type = "FirebaseFirestoreException",
                    message = "PERMISSION_DENIED",
                    permanent = true
                )
            )
        )
        val delegate = delegate(
            FakeSyncOrchestrator(error),
            FakeAuthRepository("uid-1")
        )

        val result = delegate.run(attempt = 1)

        assertTrue(result is SyncWorkerRunResult.Failure)
        assertEquals(true, (result as SyncWorkerRunResult.Failure).permanent)
        assertSame(error, result.error)
    }

    @Test
    fun transientErrorAtMaxAttempts_returnsFailure() = runBlocking {
        val error = IOException("network unavailable")
        val delegate = delegate(
            FakeSyncOrchestrator(error),
            FakeAuthRepository("uid-1")
        )

        val result = delegate.run(attempt = SyncWorkerDelegate.MAX_ATTEMPTS)

        assertTrue(result is SyncWorkerRunResult.Failure)
        assertEquals(false, (result as SyncWorkerRunResult.Failure).permanent)
    }

    @Test
    fun accountChangedDuringSync_returnsRetry() = runBlocking {
        val auth = MutableAuthRepository("uid-1")
        val sync = FakeSyncOrchestrator(onSync = { auth.userId = "uid-2" })
        val delegate = delegate(sync, auth)

        val result = delegate.run(attempt = 1)

        assertTrue(result is SyncWorkerRunResult.Retry)
        assertEquals(listOf("uid-1"), sync.calls)
    }

    @Test
    fun logoutAndLoginSameUidDuringSync_doesNotReportSuccess() = runBlocking {
        val auth = FakeAuthRepository("uid-1")
        val version = AuthSessionVersion()
        val sync = FakeSyncOrchestrator(onSync = { version.invalidate() })
        val delegate = SyncWorkerDelegate(sync, auth, SyncSessionGuard(auth, version))
        try {
            delegate.run(attempt = 1)
            throw AssertionError("Expected session cancellation")
        } catch (e: CancellationException) {
            assertEquals("SYNC_AUTH_SESSION_CHANGED", e.message)
        }
    }

    private fun delegate(sync: SyncOrchestrator, auth: AuthRepository): SyncWorkerDelegate =
        SyncWorkerDelegate(sync, auth, SyncSessionGuard(auth, AuthSessionVersion()))

    private class FakeSyncOrchestrator(
        private val error: Exception? = null,
        private val onSync: () -> Unit = {}
    ) : SyncOrchestrator {
        val calls = mutableListOf<String>()

        override suspend fun syncAll(userId: String) {
            calls += userId
            onSync()
            error?.let { throw it }
        }
    }

    private class FakeAuthRepository(
        private val userId: String?
    ) : AuthRepository {
        override fun observeAuthState(): Flow<UserModel?> =
            flowOf(userId?.let { UserModel(userId = it, email = null, displayName = null, photoUrl = null) })

        override fun getCurrentUserId(): String? = userId

        override suspend fun signIn(email: String, password: String): AppResult<UserModel> =
            throw UnsupportedOperationException()

        override suspend fun signUp(
            email: String,
            password: String,
            displayName: String
        ): AppResult<UserModel> = throw UnsupportedOperationException()

        override suspend fun signInWithGoogle(idToken: String): AppResult<UserModel> =
            throw UnsupportedOperationException()

        override suspend fun signOut() {
            throw UnsupportedOperationException()
        }
    }

    private class MutableAuthRepository(
        var userId: String?
    ) : AuthRepository {
        override fun observeAuthState(): Flow<UserModel?> =
            flowOf(userId?.let { UserModel(userId = it, email = null, displayName = null, photoUrl = null) })

        override fun getCurrentUserId(): String? = userId

        override suspend fun signIn(email: String, password: String): AppResult<UserModel> =
            throw UnsupportedOperationException()

        override suspend fun signUp(
            email: String,
            password: String,
            displayName: String
        ): AppResult<UserModel> = throw UnsupportedOperationException()

        override suspend fun signInWithGoogle(idToken: String): AppResult<UserModel> =
            throw UnsupportedOperationException()

        override suspend fun signOut() {
            userId = null
        }
    }
}
