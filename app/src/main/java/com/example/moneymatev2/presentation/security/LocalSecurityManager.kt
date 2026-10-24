package com.example.moneymatev2.presentation.security

import android.os.SystemClock
import com.example.moneymatev2.data.security.PinCheck
import com.example.moneymatev2.data.security.PinCredentialStore
import com.example.moneymatev2.data.security.PinRecordStatus
import com.example.moneymatev2.data.security.CURRENT_PIN_LENGTH
import com.example.moneymatev2.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LocalSecurityState {
    data object Loading : LocalSecurityState
    data class Guest(val recovering: Boolean = false) : LocalSecurityState
    data class Active(
        val uid: String,
        val hasPin: Boolean,
        val biometricEnabled: Boolean,
        val unlocked: Boolean,
        val needsRecovery: Boolean = false,
        val pinLength: Int = CURRENT_PIN_LENGTH
    ) : LocalSecurityState
}

enum class SecurityResult { SUCCESS, INVALID_PIN, MISMATCH, WRONG_PIN, LOCKED, NO_PIN, UNAVAILABLE, FAILED, WRONG_ACCOUNT }

/** Session unlock lives only in memory; every process start and account change begins locked. */
@Singleton
class LocalSecurityManager @Inject constructor(
    private val auth: AuthRepository,
    private val store: PinCredentialStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _state = MutableStateFlow<LocalSecurityState>(LocalSecurityState.Loading)
    val state: StateFlow<LocalSecurityState> = _state
    private var stoppedAt: Long? = null

    init {
        scope.launch {
            auth.observeAuthState().distinctUntilChangedBy { it?.userId }.collect { user ->
                mutex.withLock {
                    _state.value = LocalSecurityState.Loading
                    val uid = user?.userId
                    if (uid == null) {
                        store.markRecoverySignedOut()
                        _state.value = LocalSecurityState.Guest(recovering = store.recoveryPending())
                    } else {
                        store.consumeRecovery(uid)
                        if (auth.getCurrentUserId() == uid) _state.value = when (val status = store.status(uid)) {
                            PinRecordStatus.None -> LocalSecurityState.Active(uid, false, false, true)
                            is PinRecordStatus.Present -> LocalSecurityState.Active(uid, true,
                                status.biometricEnabled, false, pinLength = status.pinLength)
                            PinRecordStatus.Unavailable -> LocalSecurityState.Active(uid, true, false, false, needsRecovery = true)
                        }
                    }
                }
            }
        }
    }

    private fun owner(): String? = (_state.value as? LocalSecurityState.Active)?.uid
        ?.takeIf { it == auth.getCurrentUserId() }

    suspend fun create(pin: String, confirm: String): SecurityResult = withContext(Dispatchers.IO) { mutex.withLock {
        if (!validPin(pin)) return@withLock SecurityResult.INVALID_PIN
        if (pin != confirm) return@withLock SecurityResult.MISMATCH
        val uid = owner() ?: return@withLock SecurityResult.WRONG_ACCOUNT
        if ((_state.value as LocalSecurityState.Active).hasPin) return@withLock SecurityResult.FAILED
        if (!store.create(uid, pin)) return@withLock SecurityResult.FAILED
        _state.value = LocalSecurityState.Active(uid, true, false, true)
        SecurityResult.SUCCESS
    } }

    suspend fun verify(pin: String): SecurityResult = withContext(Dispatchers.IO) { mutex.withLock {
        val uid = owner() ?: return@withLock SecurityResult.WRONG_ACCOUNT
        val result = check(uid, pin)
        if (result == SecurityResult.SUCCESS) {
            val current = _state.value as LocalSecurityState.Active
            _state.value = current.copy(unlocked = true)
        }
        result
    } }

    suspend fun change(old: String, new: String, confirm: String): SecurityResult = withContext(Dispatchers.IO) { mutex.withLock {
        if (!validPin(new)) return@withLock SecurityResult.INVALID_PIN
        if (new != confirm) return@withLock SecurityResult.MISMATCH
        val uid = owner() ?: return@withLock SecurityResult.WRONG_ACCOUNT
        val verified = check(uid, old)
        if (verified != SecurityResult.SUCCESS) return@withLock verified
        if (!store.replace(uid, new)) return@withLock SecurityResult.FAILED
        val current = _state.value as LocalSecurityState.Active
        _state.value = current.copy(pinLength = CURRENT_PIN_LENGTH)
        SecurityResult.SUCCESS
    } }

    suspend fun delete(pin: String): SecurityResult = withContext(Dispatchers.IO) { mutex.withLock {
        val uid = owner() ?: return@withLock SecurityResult.WRONG_ACCOUNT
        val verified = check(uid, pin)
        if (verified != SecurityResult.SUCCESS) return@withLock verified
        if (!store.remove(uid)) return@withLock SecurityResult.FAILED
        _state.value = LocalSecurityState.Active(uid, false, false, true)
        SecurityResult.SUCCESS
    } }

    suspend fun setBiometric(enabled: Boolean): SecurityResult = withContext(Dispatchers.IO) { mutex.withLock {
        val current = _state.value as? LocalSecurityState.Active ?: return@withLock SecurityResult.WRONG_ACCOUNT
        if (owner() != current.uid || !current.hasPin || !current.unlocked || current.needsRecovery) return@withLock SecurityResult.WRONG_ACCOUNT
        if (!store.setBiometric(current.uid, enabled)) return@withLock SecurityResult.FAILED
        _state.value = current.copy(biometricEnabled = enabled)
        SecurityResult.SUCCESS
    } }

    fun unlockWithBiometric(uid: String): Boolean {
        val current = _state.value as? LocalSecurityState.Active ?: return false
        if (current.uid != uid || auth.getCurrentUserId() != uid || !current.hasPin ||
            !current.biometricEnabled || current.needsRecovery) return false
        return _state.compareAndSet(current, current.copy(unlocked = true))
    }

    suspend fun beginRecovery(): Boolean {
        val uid = mutex.withLock {
            val current = _state.value as? LocalSecurityState.Active ?: return false
            if (owner() != current.uid || !current.hasPin || current.unlocked) return false
            if (!store.requestRecovery(current.uid)) return false
            current.uid
        }
        return try {
            auth.signOut()
            true
        } catch (_: Exception) {
            mutex.withLock { if (owner() == uid) store.cancelRecovery() }
            false
        }
    }

    fun onBackground() = onBackground(SystemClock.elapsedRealtime())
    internal fun onBackground(now: Long) { stoppedAt = now }
    fun onForeground() = onForeground(SystemClock.elapsedRealtime())
    internal fun onForeground(now: Long) {
        val stopped = stoppedAt ?: return
        stoppedAt = null
        if (now - stopped >= 30_000L) {
            val current = _state.value as? LocalSecurityState.Active ?: return
            if (current.hasPin) _state.compareAndSet(current, current.copy(unlocked = false))
        }
    }

    private fun validPin(pin: String) = pin.length == CURRENT_PIN_LENGTH && pin.all { it in '0'..'9' }
    private fun check(uid: String, pin: String) = when (store.check(uid, pin)) {
        PinCheck.SUCCESS -> SecurityResult.SUCCESS
        PinCheck.WRONG -> SecurityResult.WRONG_PIN
        PinCheck.LOCKED -> SecurityResult.LOCKED
        PinCheck.NO_PIN -> SecurityResult.NO_PIN
        PinCheck.UNAVAILABLE -> SecurityResult.UNAVAILABLE
    }
}
