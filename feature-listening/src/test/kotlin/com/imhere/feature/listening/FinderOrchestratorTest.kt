package com.imhere.feature.listening

import com.imhere.core.domain.port.AlertPlayer
import com.imhere.core.domain.port.VoiceDetectionEngine
import com.imhere.core.model.DetectionConfig
import com.imhere.core.model.DetectionError
import com.imhere.core.model.PlayResult
import com.imhere.core.model.PlaybackPolicy
import com.imhere.core.model.StopDetected
import com.imhere.core.model.StopReason
import com.imhere.core.model.SystemState
import com.imhere.core.model.WakeDetected
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FinderOrchestratorTest {
    @Test
    fun wakeEvent_startsPlaybackAndMovesTriggered() = runTest {
        val engine = FakeVoiceEngine()
        val player = FakeAlertPlayer()
        val orchestrator = FinderOrchestrator(
            voiceDetectionEngine = engine,
            alertPlayer = player,
            scope = backgroundScope
        )

        orchestrator.startListening(userInitiated = true, hasMicPermission = true)
        orchestrator.onWakeDetected()

        assertEquals(1, player.playCount)
        assertIs<SystemState.TRIGGERED>(orchestrator.currentState())
    }

    @Test
    fun missingMicPermission_setsErrorState() = runTest {
        val engine = FakeVoiceEngine()
        val player = FakeAlertPlayer()
        val orchestrator = FinderOrchestrator(
            voiceDetectionEngine = engine,
            alertPlayer = player,
            scope = backgroundScope
        )

        orchestrator.startListening(userInitiated = true, hasMicPermission = false)

        assertIs<SystemState.ERROR>(orchestrator.currentState())
        assertTrue(player.playCount == 0)
    }
}

private class FakeVoiceEngine : VoiceDetectionEngine {
    override suspend fun start(config: DetectionConfig) = Unit
    override suspend fun stop() = Unit
    override fun updateKeywords(keywords: List<String>) = Unit

    private val wake = MutableSharedFlow<WakeDetected>()
    private val stop = MutableSharedFlow<StopDetected>()
    private val err = MutableSharedFlow<DetectionError>()

    override val wakeEvents: Flow<WakeDetected> = wake
    override val stopEvents: Flow<StopDetected> = stop
    override val errors: Flow<DetectionError> = err
}

private class FakeAlertPlayer : AlertPlayer {
    var playCount: Int = 0
    var isPlayingState: Boolean = false

    override suspend fun playSequence(policy: PlaybackPolicy): PlayResult {
        playCount += 1
        isPlayingState = true
        val now = System.currentTimeMillis()
        return PlayResult(now, now, policy.forcePlaybackEnabled)
    }

    override suspend fun stop(reason: StopReason) {
        isPlayingState = false
    }

    override fun isPlaying(): Boolean = isPlayingState
}
