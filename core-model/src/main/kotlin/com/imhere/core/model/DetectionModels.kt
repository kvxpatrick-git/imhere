package com.imhere.core.model

enum class EngineProfile {
    BALANCED,
    POWER_SAVE
}

data class DetectionConfig(
    val keywords: List<String>,
    val stopPhrases: List<String>,
    val confirmPhrase: String,
    val twoStepTriggerEnabled: Boolean,
    val confidenceThreshold: Float,
    val profile: EngineProfile,
    val cooldownSec: Int
)

data class PlaybackPolicy(
    val forcePlaybackEnabled: Boolean,
    val stage1Sec: Int = 2,
    val stage2Sec: Int = 10,
    val stage3MaxSec: Int = 60,
    val stage1Volume: Int = 30,
    val stage2Volume: Int = 70,
    val stage3Volume: Int = 100,
    val vibratePatternMs: LongArray = longArrayOf(0, 700, 300, 700, 300)
)

data class PlayResult(
    val startedAtEpochMs: Long,
    val firstAudioAtEpochMs: Long,
    val volumeOverrideApplied: Boolean
)

data class UserSettings(
    val stopPhrases: List<String>,
    val forcePlaybackEnabled: Boolean,
    val twoStepTriggerEnabled: Boolean,
    val confirmPhrase: String,
    val batterySaverEnabled: Boolean,
    val batteryThresholdPercent: Int = 20
)
