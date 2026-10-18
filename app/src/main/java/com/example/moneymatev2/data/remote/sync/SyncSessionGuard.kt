package com.example.moneymatev2.data.remote.sync

import com.example.moneymatev2.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthSessionVersion @Inject constructor() {
    private val version = AtomicLong()
    fun current(): Long = version.get()
    fun invalidate() { version.incrementAndGet() }
}

@Singleton
class SyncSessionGuard @Inject constructor(
    private val auth: AuthRepository,
    private val version: AuthSessionVersion
) {
    class Session internal constructor(val userId: String, internal val version: Long)

    fun capture(userId: String): Session = Session(userId, version.current()).also(::check)

    fun check(session: Session) {
        if (session.userId.isBlank() || auth.getCurrentUserId() != session.userId ||
            version.current() != session.version) {
            throw CancellationException("SYNC_AUTH_SESSION_CHANGED")
        }
    }
}
