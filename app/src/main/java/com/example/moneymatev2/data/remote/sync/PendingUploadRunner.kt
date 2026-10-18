package com.example.moneymatev2.data.remote.sync

import androidx.room.withTransaction
import com.example.moneymatev2.data.local.AppDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject

/** Network I/O stays outside Room transactions; acknowledgements compare the full snapshot. */
class PendingUploadRunner @Inject constructor(
    private val db: AppDatabase,
    private val sessions: SyncSessionGuard
) {
    fun capture(userId: String) = sessions.capture(userId)
    fun check(session: SyncSessionGuard.Session) = sessions.check(session)

    suspend fun <T : Any> run(
        session: SyncSessionGuard.Session,
        snapshot: T,
        readCurrent: suspend () -> T?,
        upload: suspend () -> Unit,
        acknowledge: suspend () -> Unit,
        recordFailure: suspend (Exception) -> Unit
    ) {
        currentCoroutineContext().ensureActive()
        sessions.check(session)
        if (readCurrent() != snapshot) return
        try {
            currentCoroutineContext().ensureActive()
            sessions.check(session)
            upload()
            db.withTransaction {
                sessions.check(session)
                val unchanged = readCurrent() == snapshot
                sessions.check(session)
                if (unchanged) acknowledge()
                sessions.check(session)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            db.withTransaction {
                sessions.check(session)
                val unchanged = readCurrent() == snapshot
                sessions.check(session)
                if (unchanged) recordFailure(e)
                sessions.check(session)
            }
            throw e
        }
    }
}
