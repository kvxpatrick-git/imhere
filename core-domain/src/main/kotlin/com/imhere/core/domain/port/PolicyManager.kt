package com.imhere.core.domain.port

import com.imhere.core.model.EngineProfile
import com.imhere.core.model.PlaybackPolicy
import com.imhere.core.model.UserSettings

interface PolicyManager {
    fun evaluateTrigger(input: TriggerInput): TriggerDecision
    fun selectEngineProfile(batteryPct: Int, saverEnabled: Boolean): EngineProfile
    fun canStartListening(userInitiated: Boolean, hasMicPermission: Boolean): StartDecision
    fun selectPlaybackPolicy(settings: UserSettings, isSilentMode: Boolean): PlaybackPolicy
}

data class TriggerInput(
    val twoStepEnabled: Boolean,
    val hasWake: Boolean,
    val hasConfirm: Boolean,
    val confirmWithinSec: Int,
    val cooldownRemainingSec: Int
)

sealed interface TriggerDecision {
    data object StartSession : TriggerDecision
    data object WaitForConfirm : TriggerDecision
    data class Reject(val reason: PolicyRejectReason) : TriggerDecision
}

sealed interface StartDecision {
    data object Allow : StartDecision
    data class Block(val reason: PolicyRejectReason) : StartDecision
}

enum class PolicyRejectReason {
    COOLDOWN,
    MISSING_CONFIRM,
    BATTERY_HARD_BLOCK,
    PERMISSION_NOT_GRANTED,
    BACKGROUND_START_RESTRICTED
}
