package com.example.moneymatev2.data.remote.sync

import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.usecase.SyncOrchestrator
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class SyncWorkerDelegate @Inject constructor(
    private val syncManager: SyncOrchestrator,
    private val authRepository: AuthRepository,
    private val sessions: SyncSessionGuard
) {
    suspend fun run(attempt: Int): SyncWorkerRunResult {
        return try {
            val userId = authRepository.getCurrentUserId()
            if (userId.isNullOrBlank()) {
                return SyncWorkerRunResult.Success(
                    signedIn = false,
                    uidHash = "none",
                    synced = false
                )
            }

            val session = sessions.capture(userId)
            syncManager.syncAll(userId)

            val currentUserId = authRepository.getCurrentUserId()
            if (currentUserId != userId) {
                return retryOrFailure(attempt, null)
            }
            sessions.check(session)

            SyncWorkerRunResult.Success(
                signedIn = true,
                uidHash = userId.safeHash(),
                synced = true
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.isPermanentSyncError()) {
                SyncWorkerRunResult.Failure(e, permanent = true)
            } else {
                retryOrFailure(attempt, e)
            }
        }
    }

    private fun retryOrFailure(attempt: Int, error: Exception?): SyncWorkerRunResult =
        if (attempt >= MAX_ATTEMPTS) {
            SyncWorkerRunResult.Failure(error, permanent = false)
        } else {
            SyncWorkerRunResult.Retry(error)
        }

    companion object {
        const val MAX_ATTEMPTS = 5
    }
}

sealed class SyncWorkerRunResult {
    data class Success(
        val signedIn: Boolean,
        val uidHash: String,
        val synced: Boolean
    ) : SyncWorkerRunResult()

    data class Retry(val error: Exception?) : SyncWorkerRunResult()

    data class Failure(
        val error: Exception?,
        val permanent: Boolean
    ) : SyncWorkerRunResult()
}
