package com.imhere.app

import com.imhere.core.domain.port.AlertPlayer
import com.imhere.core.domain.port.VoiceDetectionEngine
import com.imhere.core.model.DetectionConfig
import com.imhere.core.model.DetectionError
import com.imhere.core.model.PlayResult
import com.imhere.core.model.PlaybackPolicy
import com.imhere.core.model.StopDetected
import com.imhere.core.model.StopReason
import com.imhere.core.model.WakeDetected
import com.imhere.feature.listening.FinderOrchestrator
import com.imhere.platform.monitoring.FinderForegroundServiceEntry
import com.imhere.platform.monitoring.ForegroundServiceCommand
import com.imhere.platform.monitoring.RuntimePolicyMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppBootstrapTest {
    @Test
    fun boot_returnsStartRequested() = runTest {
        val bootstrap = AppBootstrap(
            context = null,
            foregroundServiceEntry = FinderForegroundServiceEntry(),
            runtimePolicyMonitor = RuntimePolicyMonitor(),
            orchestrator = FinderOrchestrator(
                voiceDetectionEngine = FakeVoiceEngine(),
                alertPlayer = FakeAlertPlayer(),
                scope = this
            ),
            scope = this,
            startServiceAction = {},
            stopServiceAction = {}
        )

        assertEquals(ForegroundServiceCommand.START_REQUESTED, bootstrap.boot())
        assertEquals(ForegroundServiceCommand.START_REQUESTED, bootstrap.requestUserStartListening())
        assertEquals(ForegroundServiceCommand.STOP_REQUESTED, bootstrap.requestUserStopListening())
        assertEquals(ForegroundServiceCommand.STOP_REQUESTED, bootstrap.shutdown())
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
    override suspend fun playSequence(policy: PlaybackPolicy): PlayResult {
        val now = System.currentTimeMillis()
        return PlayResult(now, now, policy.forcePlaybackEnabled)
    }

    override suspend fun stop(reason: StopReason) = Unit
    override fun isPlaying(): Boolean = false
}
