package com.imhere.platform.voice

import com.imhere.core.domain.port.VoiceDetectionConfigUpdater
import com.imhere.core.domain.port.VoiceDetectionEngine
import com.imhere.core.model.DetectionConfig
import com.imhere.core.model.DetectionError
import com.imhere.core.model.StopDetected
import com.imhere.core.model.WakeDetected
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PlatformVoiceDetectionEngine : VoiceDetectionEngine, VoiceDetectionConfigUpdater {
    private val lock = Mutex()

    private var running: Boolean = false
    private var config: DetectionConfig? = null
    private var activeKeywords: List<String> = emptyList()

    private val _wakeEvents = MutableSharedFlow<WakeDetected>(extraBufferCapacity = 32)
    private val _stopEvents = MutableSharedFlow<StopDetected>(extraBufferCapacity = 32)
    private val _errors = MutableSharedFlow<DetectionError>(extraBufferCapacity = 16)

    override val wakeEvents: Flow<WakeDetected> = _wakeEvents
    override val stopEvents: Flow<StopDetected> = _stopEvents
    override val errors: Flow<DetectionError> = _errors

    override suspend fun start(config: DetectionConfig) {
        lock.withLock {
            running = true
            this.config = config
            activeKeywords = config.keywords
        }
    }

    override suspend fun stop() {
        lock.withLock {
            running = false
        }
    }

    override fun updateKeywords(keywords: List<String>) {
        activeKeywords = keywords.distinct()
    }

    override suspend fun updateConfig(config: DetectionConfig) {
        lock.withLock {
            this.config = config
            activeKeywords = config.keywords
        }
    }

    suspend fun emitWake(keyword: String, confidence: Float = 0.8f): Boolean {
        if (!running || keyword !in activeKeywords) {
            return false
        }
        return _wakeEvents.tryEmit(
            WakeDetected(
                keyword = keyword,
                confidence = confidence,
                detectedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun emitStop(phrase: String, confidence: Float = 0.8f): Boolean {
        if (!running) {
            return false
        }
        return _stopEvents.tryEmit(
            StopDetected(
                phrase = phrase,
                confidence = confidence,
                detectedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    fun emitError(code: String, recoverable: Boolean, message: String): Boolean {
        return _errors.tryEmit(DetectionError(code = code, recoverable = recoverable, message = message))
    }
}
