package com.imhere.core.domain.port

import com.imhere.core.model.DetectionConfig
import com.imhere.core.model.DetectionError
import com.imhere.core.model.StopDetected
import com.imhere.core.model.WakeDetected
import kotlinx.coroutines.flow.Flow

interface VoiceDetectionEngine {
    suspend fun start(config: DetectionConfig)
    suspend fun stop()
    fun updateKeywords(keywords: List<String>)

    val wakeEvents: Flow<WakeDetected>
    val stopEvents: Flow<StopDetected>
    val errors: Flow<DetectionError>
}
