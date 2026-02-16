package com.imhere.core.data.repository

import com.imhere.core.data.schema.RetentionPolicy
import com.imhere.core.domain.port.FindSession
import com.imhere.core.domain.port.SessionRepository
import com.imhere.core.model.StopReason
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemorySessionRepository : SessionRepository {
    private val lock = Mutex()
    private val sessions = linkedMapOf<String, FindSession>()

    override suspend fun create(session: FindSession) {
        lock.withLock {
            sessions[session.id] = session
        }
    }

    override suspend fun updateStage(sessionId: String, stage: Int) {
        lock.withLock {
            val current = sessions[sessionId] ?: return
            sessions[sessionId] = current.copy(stageReached = stage)
        }
    }

    override suspend fun close(sessionId: String, reason: StopReason, stopLatencyMs: Long?) {
        lock.withLock {
            val current = sessions[sessionId] ?: return
            sessions[sessionId] = current.copy(
                endedAtEpochMs = System.currentTimeMillis(),
                stopReason = reason,
                stopLatencyMs = stopLatencyMs
            )
        }
    }

    override suspend fun listRecent(limit: Int): List<FindSession> {
        return lock.withLock {
            sessions.values
                .sortedByDescending { it.startedAtEpochMs }
                .take(limit)
        }
    }

    override suspend fun purgeExpired(nowEpochMs: Long) {
        lock.withLock {
            val expireBefore = nowEpochMs - RetentionPolicy.SESSION_TTL_MS
            val it = sessions.iterator()
            while (it.hasNext()) {
                val (_, session) = it.next()
                if (session.startedAtEpochMs < expireBefore) {
                    it.remove()
                }
            }
        }
    }
}
