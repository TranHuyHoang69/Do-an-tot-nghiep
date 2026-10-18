package com.example.moneymatev2.data.remote.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.UUID
import kotlinx.coroutines.CancellationException

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val delegate: SyncWorkerDelegate
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val runId = UUID.randomUUID().toString().take(8)
        val attempt = runAttemptCount + 1
        Log.i(TAG, "SYNC_WORKER_RUNNING runId=$runId attempt=$attempt")
        Log.i(TAG, "SYNC_START runId=$runId")

        val result = try {
            delegate.run(attempt)
        } catch (e: CancellationException) {
            val reason = if (e.message == "SYNC_AUTH_SESSION_CHANGED") "auth_session_changed" else "cancelled"
            Log.i(TAG, "SYNC_FAILED runId=$runId result=cancelled reason=$reason")
            throw e
        }
        return when (result) {
            is SyncWorkerRunResult.Success -> {
                Log.i(
                    TAG,
                    "SYNC_AUTH_UID_CHECK runId=$runId signedIn=${result.signedIn} uidHash=${result.uidHash}"
                )
                if (result.synced) {
                    Log.i(TAG, "SYNC_COMPLETE runId=$runId")
                } else {
                    Log.i(TAG, "SYNC_COMPLETE runId=$runId result=no_authenticated_user")
                }
                Result.success()
            }

            is SyncWorkerRunResult.Retry -> {
                Log.e(
                    TAG,
                    "SYNC_FAILED runId=$runId result=retry_scheduled nextAttempt=${attempt + 1} ${result.error?.syncFailureSummary().orEmpty()}"
                )
                Result.retry()
            }

            is SyncWorkerRunResult.Failure -> {
                Log.e(
                    TAG,
                    "SYNC_FAILED runId=$runId result=failure permanent=${result.permanent} ${result.error?.syncFailureSummary().orEmpty()}"
                )
                Result.failure()
            }
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        const val MAX_ATTEMPTS = SyncWorkerDelegate.MAX_ATTEMPTS
    }
}
