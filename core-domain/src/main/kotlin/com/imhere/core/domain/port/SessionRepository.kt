package com.imhere.core.domain.port

import com.imhere.core.model.StopReason

data class FindSession(
    val id: String,
    val triggeredKeywordProtected: String,
    val triggerConfidence: Float?,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val stageReached: Int,
    val stopReason: StopReason?,
    val forcePlaybackApplied: Boolean,
    val batterySaverActive: Boolean,
    val detectionLatencyMs: Long?,
    val stopLatencyMs: Long?
)

interface SessionRepository {
    suspend fun create(session: FindSession)
    suspend fun updateStage(sessionId: String, stage: Int)
    suspend fun close(sessionId: String, reason: StopReason, stopLatencyMs: Long?)
    suspend fun listRecent(limit: Int): List<FindSession>
    suspend fun purgeExpired(nowEpochMs: Long)
}
