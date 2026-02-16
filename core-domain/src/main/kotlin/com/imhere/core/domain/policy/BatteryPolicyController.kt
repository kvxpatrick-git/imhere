package com.imhere.core.domain.policy

import com.imhere.core.model.EngineProfile

class BatteryPolicyController(
    private val enterThresholdPercent: Int = 20,
    private val exitThresholdPercent: Int = 25
) {
    init {
        require(enterThresholdPercent < exitThresholdPercent) {
            "enterThresholdPercent must be lower than exitThresholdPercent"
        }
    }

    fun nextProfile(current: EngineProfile, batteryPct: Int, saverEnabled: Boolean): EngineProfile {
        if (!saverEnabled) {
            return EngineProfile.BALANCED
        }
        return when {
            batteryPct <= enterThresholdPercent -> EngineProfile.POWER_SAVE
            batteryPct >= exitThresholdPercent -> EngineProfile.BALANCED
            else -> current
        }
    }
}
