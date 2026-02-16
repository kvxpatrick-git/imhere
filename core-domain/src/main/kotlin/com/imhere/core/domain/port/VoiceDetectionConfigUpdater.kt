package com.imhere.core.domain.port

import com.imhere.core.model.DetectionConfig

interface VoiceDetectionConfigUpdater {
    suspend fun updateConfig(config: DetectionConfig)
}
