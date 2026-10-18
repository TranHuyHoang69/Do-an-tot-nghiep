package com.example.moneymatev2.data.remote.sync

import com.example.moneymatev2.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Re-sync on app startup and each account change (including login on a second device). */
@Singleton
class AuthSyncSessionManager @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncTrigger: SyncTrigger
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch {
            authRepository.observeAuthState()
                .map { it?.userId }
                .distinctUntilChanged()
                .collect { uid ->
                    if (!uid.isNullOrBlank()) syncTrigger.requestImmediateSync()
                }
        }
    }
}
