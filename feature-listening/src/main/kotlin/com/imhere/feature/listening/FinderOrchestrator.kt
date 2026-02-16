package com.imhere.feature.listening

import com.imhere.core.domain.policy.BatteryPolicyController
import com.imhere.core.domain.policy.DefaultPolicyManager
import com.imhere.core.domain.port.AlertPlayer
import com.imhere.core.domain.port.PolicyManager
import com.imhere.core.domain.port.SessionRepository
import com.imhere.core.domain.port.StartDecision
import com.imhere.core.domain.port.TriggerDecision
import com.imhere.core.domain.port.TriggerInput
import com.imhere.core.domain.port.VoiceDetectionEngine
import com.imhere.core.domain.state.FinderStateMachine
import com.imhere.core.model.DetectionConfig
import com.imhere.core.model.EngineProfile
import com.imhere.core.model.FindSessionId
import com.imhere.core.model.StopReason
import com.imhere.core.model.SystemState
import com.imhere.core.model.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class FinderOrchestrator(
    private val voiceDetectionEngine: VoiceDetectionEngine,
    private val alertPlayer: AlertPlayer,
    private val sessionRepository: SessionRepository? = null,
    private val policyManager: PolicyManager = DefaultPolicyManager(),
    private val batteryPolicyController: BatteryPolicyController = BatteryPolicyController(),
    private val stateMachine: FinderStateMachine = FinderStateMachine(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val stateMutex = Mutex()

    private var runtimeSettings: UserSettings = UserSettings(
        stopPhrases = listOf("멈춰"),
        forcePlaybackEnabled = true,
        twoStepTriggerEnabled = false,
        confirmPhrase = "여기 있어",
        batterySaverEnabled = true,
        batteryThresholdPercent = 20
    )

    private var wakeKeywords: List<String> = listOf("폰아 울려")

    @Volatile
    private var currentProfile: EngineProfile = EngineProfile.BALANCED

    private var engineCollectionJob: Job? = null
    private var confirmationTimeoutJob: Job? = null
    private var stageTimerJob: Job? = null

    fun currentState(): SystemState = stateMachine.currentState()

    suspend fun startListening(userInitiated: Boolean = true, hasMicPermission: Boolean = true) {
        stateMutex.withLock {
            when (policyManager.canStartListening(userInitiated, hasMicPermission)) {
                is StartDecision.Allow -> Unit
                is StartDecision.Block -> {
                    stateMachine.onError(code = "E-PRM-001", recoverable = false)
                    return
                }
            }

            stateMachine.startListening()
            voiceDetectionEngine.start(buildDetectionConfig())

            if (engineCollectionJob == null) {
                engineCollectionJob = scope.launch {
                    launch { voiceDetectionEngine.wakeEvents.collect { onWakeDetected() } }
                    launch { voiceDetectionEngine.stopEvents.collect { onStopDetected() } }
                    launch { voiceDetectionEngine.errors.collect { onEngineError(it.code, it.recoverable) } }
                }
            }
        }
    }

    suspend fun onWakeDetected() {
        stateMutex.withLock {
            val decision = policyManager.evaluateTrigger(
                TriggerInput(
                    twoStepEnabled = runtimeSettings.twoStepTriggerEnabled,
                    hasWake = true,
                    hasConfirm = !runtimeSettings.twoStepTriggerEnabled,
                    confirmWithinSec = 0,
                    cooldownRemainingSec = 0
                )
            )

            when (decision) {
                TriggerDecision.StartSession -> {
                    stateMachine.onWakeDetected(twoStepEnabled = false)
                    startTriggeredSessionLocked(1)
                }
                TriggerDecision.WaitForConfirm -> {
                    stateMachine.onWakeDetected(twoStepEnabled = true)
                    scheduleConfirmationTimeoutLocked()
                }
                is TriggerDecision.Reject -> Unit
            }
        }
    }

    suspend fun onStopDetected() {
        stateMutex.withLock {
            transitionToStoppedLocked(StopReason.VOICE_STOP)
        }
    }

    suspend fun onConfirmPhrase() {
        stateMutex.withLock {
            confirmationTimeoutJob?.cancel()
            val decision = policyManager.evaluateTrigger(
                TriggerInput(
                    twoStepEnabled = true,
                    hasWake = true,
                    hasConfirm = true,
                    confirmWithinSec = 0,
                    cooldownRemainingSec = 0
                )
            )
            if (decision is TriggerDecision.StartSession && stateMachine.onConfirmDetected() is SystemState.TRIGGERED) {
                startTriggeredSessionLocked(1)
            }
        }
    }

    suspend fun onStageTimeout() {
        stateMutex.withLock {
            advanceStageLocked()
        }
    }

    suspend fun stopListening() {
        stateMutex.withLock {
            cancelSessionTimersLocked()
            voiceDetectionEngine.stop()
            if (alertPlayer.isPlaying()) {
                alertPlayer.stop(StopReason.USER_STOP)
            }
            stateMachine.onStopped()
            stateMachine.onRecoveryComplete()
        }

        engineCollectionJob?.cancelAndJoin()
        engineCollectionJob = null
    }

    suspend fun stopPlayback() {
        stateMutex.withLock {
            transitionToStoppedLocked(StopReason.USER_STOP)
        }
    }

    suspend fun triggerPlayback() {
        stateMutex.withLock {
            startTriggeredSessionLocked(1)
        }
    }

    fun onEngineError(code: String, recoverable: Boolean) {
        scope.launch {
            stateMutex.withLock {
                val next = stateMachine.onError(code = code, recoverable = recoverable)
                if (next is SystemState.ERROR && next.recoverable) {
                    stateMachine.onRecoveryComplete()
                } else {
                    cancelSessionTimersLocked()
                }
            }
        }
    }

    suspend fun onBatteryLevelChanged(batteryPct: Int) {
        stateMutex.withLock {
            val baseline = policyManager.selectEngineProfile(batteryPct, runtimeSettings.batterySaverEnabled)
            currentProfile = batteryPolicyController.nextProfile(
                current = baseline,
                batteryPct = batteryPct,
                saverEnabled = runtimeSettings.batterySaverEnabled
            )
            voiceDetectionEngine.start(buildDetectionConfig())
        }
    }

    fun updateRuntimeSettings(settings: UserSettings) {
        runtimeSettings = settings
    }

    fun updateWakeKeywords(keywords: List<String>) {
        wakeKeywords = keywords
        voiceDetectionEngine.updateKeywords(keywords)
    }

    private fun scheduleConfirmationTimeoutLocked(timeoutMs: Long = 4_000L) {
        confirmationTimeoutJob?.cancel()
        confirmationTimeoutJob = scope.launch {
            delay(timeoutMs)
            stateMutex.withLock {
                stateMachine.onConfirmationTimedOut()
            }
        }
    }

    private fun startStageTimerLocked(stage1Sec: Int, stage2Sec: Int, stage3MaxSec: Int) {
        stageTimerJob?.cancel()
        stageTimerJob = scope.launch {
            delay(stage1Sec * 1_000L)
            stateMutex.withLock { advanceStageLocked() }

            delay(stage2Sec * 1_000L)
            stateMutex.withLock { advanceStageLocked() }

            delay(stage3MaxSec * 1_000L)
            stateMutex.withLock {
                if (stateMachine.currentState() is SystemState.TRIGGERED) {
                    transitionToStoppedLocked(StopReason.STAGE3_TIMEOUT)
                }
            }
        }
    }

    private suspend fun advanceStageLocked() {
        when (val next = stateMachine.onStageTimeout()) {
            is SystemState.TRIGGERED -> sessionRepository?.updateStage(activeSessionId.id, next.stage)
            SystemState.STOPPING -> transitionToStoppedLocked(StopReason.STAGE3_TIMEOUT)
            else -> Unit
        }
    }

    private suspend fun startTriggeredSessionLocked(initialStage: Int) {
        val playbackPolicy = policyManager.selectPlaybackPolicy(
            settings = runtimeSettings,
            isSilentMode = false
        )
        openSessionIfNeededLocked(initialStage)
        alertPlayer.playSequence(playbackPolicy)
        startStageTimerLocked(
            stage1Sec = playbackPolicy.stage1Sec,
            stage2Sec = playbackPolicy.stage2Sec,
            stage3MaxSec = playbackPolicy.stage3MaxSec
        )
    }

    private suspend fun transitionToStoppedLocked(reason: StopReason) {
        cancelSessionTimersLocked()
        stateMachine.onStopRequested()
        alertPlayer.stop(reason)
        stateMachine.onStopped()
        stateMachine.onRecoveryComplete()
        activeSessionId.takeIf { it != FindSessionId.EMPTY }?.let {
            sessionRepository?.close(it.id, reason, stopLatencyMs = null)
        }
        activeSessionId = FindSessionId.EMPTY
    }

    private fun cancelSessionTimersLocked() {
        confirmationTimeoutJob?.cancel()
        stageTimerJob?.cancel()
    }

    private fun buildDetectionConfig(): DetectionConfig {
        val confidenceThreshold = if (currentProfile == EngineProfile.POWER_SAVE) 0.70f else 0.62f
        return DetectionConfig(
            keywords = wakeKeywords,
            stopPhrases = runtimeSettings.stopPhrases,
            confirmPhrase = runtimeSettings.confirmPhrase,
            twoStepTriggerEnabled = runtimeSettings.twoStepTriggerEnabled,
            confidenceThreshold = confidenceThreshold,
            profile = currentProfile,
            cooldownSec = 15
        )
    }

    private suspend fun openSessionIfNeededLocked(stage: Int) {
        if (activeSessionId != FindSessionId.EMPTY) {
            return
        }
        activeSessionId = FindSessionId(UUID.randomUUID().toString())
        sessionRepository?.create(
            com.imhere.core.domain.port.FindSession(
                id = activeSessionId.id,
                triggeredKeywordProtected = "masked",
                triggerConfidence = null,
                startedAtEpochMs = System.currentTimeMillis(),
                endedAtEpochMs = null,
                stageReached = stage,
                stopReason = null,
                forcePlaybackApplied = runtimeSettings.forcePlaybackEnabled,
                batterySaverActive = currentProfile == EngineProfile.POWER_SAVE,
                detectionLatencyMs = null,
                stopLatencyMs = null
            )
        )
    }

    @Volatile
    private var activeSessionId: FindSessionId = FindSessionId.EMPTY
}
