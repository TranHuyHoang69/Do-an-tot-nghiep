package com.example.moneymatev2.data.remote.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.usecase.SyncManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncWorker: SyncManager,
    private val authRepository: AuthRepository
): CoroutineWorker(context, params){
    override suspend fun doWork(): Result {
        val userId = authRepository.getCurrentUserId() ?: return Result.success()

        return try {
            syncWorker.syncAll(userId)
            Result.success()
        }catch (e: Exception){
            if(runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }
    companion object {
        const val MAX_ATTEMPTS = 5
    }
}