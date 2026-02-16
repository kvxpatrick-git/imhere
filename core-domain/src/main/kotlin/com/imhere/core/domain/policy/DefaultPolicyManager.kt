package com.imhere.core.domain.policy

import com.imhere.core.domain.port.PolicyManager
import com.imhere.core.domain.port.PolicyRejectReason
import com.imhere.core.domain.port.StartDecision
import com.imhere.core.domain.port.TriggerDecision
import com.imhere.core.domain.port.TriggerInput
import com.imhere.core.model.EngineProfile
import com.imhere.core.model.PlaybackPolicy
import com.imhere.core.model.UserSettings

class DefaultPolicyManager : PolicyManager {
    override fun evaluateTrigger(input: TriggerInput): TriggerDecision {
        if (input.cooldownRemainingSec > 0) {
            return TriggerDecision.Reject(PolicyRejectReason.COOLDOWN)
        }
        if (!input.hasWake) {
            return TriggerDecision.Reject(PolicyRejectReason.MISSING_CONFIRM)
        }
        if (!input.twoStepEnabled) {
            return TriggerDecision.StartSession
        }
        return if (input.hasConfirm && input.confirmWithinSec in 0..4) {
            TriggerDecision.StartSession
        } else {
            TriggerDecision.WaitForConfirm
        }
    }

    override fun selectEngineProfile(batteryPct: Int, saverEnabled: Boolean): EngineProfile {
        if (!saverEnabled) {
            return EngineProfile.BALANCED
        }
        return if (batteryPct <= 20) EngineProfile.POWER_SAVE else EngineProfile.BALANCED
    }

    override fun canStartListening(userInitiated: Boolean, hasMicPermission: Boolean): StartDecision {
        if (!hasMicPermission) {
            return StartDecision.Block(PolicyRejectReason.PERMISSION_NOT_GRANTED)
        }
        if (!userInitiated) {
            return StartDecision.Block(PolicyRejectReason.BACKGROUND_START_RESTRICTED)
        }
        return StartDecision.Allow
    }

    override fun selectPlaybackPolicy(settings: UserSettings, isSilentMode: Boolean): PlaybackPolicy {
        val forcePlayback = settings.forcePlaybackEnabled || !isSilentMode
        return PlaybackPolicy(forcePlaybackEnabled = forcePlayback)
    }
}
