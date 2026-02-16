package com.imhere.core.model

data class WakeDetected(
    val keyword: String,
    val confidence: Float,
    val detectedAtEpochMs: Long
)

data class StopDetected(
    val phrase: String,
    val confidence: Float,
    val detectedAtEpochMs: Long
)

data class DetectionError(
    val code: String,
    val recoverable: Boolean,
    val message: String
)

enum class StopReason {
    VOICE_STOP,
    USER_STOP,
    STAGE3_TIMEOUT,
    CALL_INTERRUPTION,
    AUDIO_FOCUS_LOST,
    ENGINE_FAILURE,
    POLICY_BLOCKED
}
