package com.imhere.app.data.repository

import com.imhere.app.data.local.SessionDao
import com.imhere.app.data.local.SessionEntity
import com.imhere.core.domain.port.FindSession
import com.imhere.core.domain.port.SessionRepository
import com.imhere.core.model.StopReason

class RoomSessionRepository(
    private val dao: SessionDao
) : SessionRepository {
    override suspend fun create(session: FindSession) {
        dao.insert(session.toEntity())
    }

    override suspend fun updateStage(sessionId: String, stage: Int) {
        dao.updateStage(sessionId, stage)
    }

    override suspend fun close(sessionId: String, reason: StopReason, stopLatencyMs: Long?) {
        dao.close(
            sessionId = sessionId,
            endedAt = System.currentTimeMillis(),
            reason = reason,
            stopLatencyMs = stopLatencyMs
        )
    }

    override suspend fun listRecent(limit: Int): List<FindSession> {
        return dao.listRecent(limit).map { it.toDomain() }
    }

    override suspend fun purgeExpired(nowEpochMs: Long) {
        dao.purgeExpired(nowEpochMs - SESSION_TTL_MS)
    }
}

private const val SESSION_TTL_MS = 30L * 24L * 60L * 60L * 1000L

private fun FindSession.toEntity(): SessionEntity {
    return SessionEntity(
        id = id,
        triggeredKeywordProtected = triggeredKeywordProtected,
        triggerConfidence = triggerConfidence,
        startedAtEpochMs = startedAtEpochMs,
        endedAtEpochMs = endedAtEpochMs,
        stageReached = stageReached,
        stopReason = stopReason,
        forcePlaybackApplied = forcePlaybackApplied,
        batterySaverActive = batterySaverActive,
        detectionLatencyMs = detectionLatencyMs,
        stopLatencyMs = stopLatencyMs
    )
}

private fun SessionEntity.toDomain(): FindSession {
    return FindSession(
        id = id,
        triggeredKeywordProtected = triggeredKeywordProtected,
        triggerConfidence = triggerConfidence,
        startedAtEpochMs = startedAtEpochMs,
        endedAtEpochMs = endedAtEpochMs,
        stageReached = stageReached,
        stopReason = stopReason,
        forcePlaybackApplied = forcePlaybackApplied,
        batterySaverActive = batterySaverActive,
        detectionLatencyMs = detectionLatencyMs,
        stopLatencyMs = stopLatencyMs
    )
}
