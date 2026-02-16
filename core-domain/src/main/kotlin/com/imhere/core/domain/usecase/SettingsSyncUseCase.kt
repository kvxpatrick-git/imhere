package com.imhere.core.domain.usecase

import com.imhere.core.domain.port.KeywordRepository
import com.imhere.core.domain.port.SettingsRepository
import com.imhere.core.domain.port.VoiceDetectionEngine
import com.imhere.core.domain.port.VoiceDetectionConfigUpdater
import com.imhere.core.model.DetectionConfig
import com.imhere.core.model.EngineProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class SettingsSyncUseCase(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val keywordRepository: KeywordRepository,
    private val voiceDetectionEngine: VoiceDetectionEngine,
    private val configUpdater: VoiceDetectionConfigUpdater? = null
) {
    private var syncJob: Job? = null

    fun start(): Job {
        val existing = syncJob
        if (existing != null) {
            return existing
        }

        return scope.launch {
            combine(
                settingsRepository.settingsFlow,
                keywordRepository.keywordsFlow
            ) { settings, keywords ->
                DetectionConfig(
                    keywords = keywords,
                    stopPhrases = settings.stopPhrases,
                    confirmPhrase = settings.confirmPhrase,
                    twoStepTriggerEnabled = settings.twoStepTriggerEnabled,
                    confidenceThreshold = if (settings.batterySaverEnabled) 0.70f else 0.62f,
                    profile = if (settings.batterySaverEnabled) EngineProfile.POWER_SAVE else EngineProfile.BALANCED,
                    cooldownSec = 15
                )
            }
                .distinctUntilChanged()
                .collect { config ->
                    voiceDetectionEngine.updateKeywords(config.keywords)
                    configUpdater?.updateConfig(config)
                }
        }.also { syncJob = it }
    }

    fun stop() {
        syncJob?.cancel()
        syncJob = null
    }
}
