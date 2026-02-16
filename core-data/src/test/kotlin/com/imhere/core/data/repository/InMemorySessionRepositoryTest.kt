package com.imhere.core.data.repository

import com.imhere.core.domain.port.FindSession
import com.imhere.core.model.StopReason
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InMemorySessionRepositoryTest {
    @Test
    fun closeAndListRecent_updatesStopReason() = runTest {
        val repo = InMemorySessionRepository()
        val now = System.currentTimeMillis()

        repo.create(
            FindSession(
                id = "s1",
                triggeredKeywordProtected = "masked",
                triggerConfidence = 0.8f,
                startedAtEpochMs = now,
                endedAtEpochMs = null,
                stageReached = 1,
                stopReason = null,
                forcePlaybackApplied = true,
                batterySaverActive = false,
                detectionLatencyMs = null,
                stopLatencyMs = null
            )
        )
        repo.close("s1", StopReason.VOICE_STOP, 120)

        val recent = repo.listRecent(10)
        assertEquals(1, recent.size)
        assertEquals(StopReason.VOICE_STOP, recent.first().stopReason)
        assertEquals(120, recent.first().stopLatencyMs)
    }

    @Test
    fun purgeExpired_removesOldSessions() = runTest {
        val repo = InMemorySessionRepository()
        val now = System.currentTimeMillis()

        repo.create(
            FindSession(
                id = "old",
                triggeredKeywordProtected = "masked",
                triggerConfidence = null,
                startedAtEpochMs = now - (31L * 24L * 60L * 60L * 1000L),
                endedAtEpochMs = null,
                stageReached = 1,
                stopReason = null,
                forcePlaybackApplied = true,
                batterySaverActive = false,
                detectionLatencyMs = null,
                stopLatencyMs = null
            )
        )

        repo.create(
            FindSession(
                id = "new",
                triggeredKeywordProtected = "masked",
                triggerConfidence = null,
                startedAtEpochMs = now,
                endedAtEpochMs = null,
                stageReached = 1,
                stopReason = null,
                forcePlaybackApplied = true,
                batterySaverActive = false,
                detectionLatencyMs = null,
                stopLatencyMs = null
            )
        )

        repo.purgeExpired(now)
        val result = repo.listRecent(10)
        assertEquals(listOf("new"), result.map { it.id })
    }
}
