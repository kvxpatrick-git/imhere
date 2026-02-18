package com.imhere.app.di

import com.imhere.app.AppBootstrap
import com.imhere.app.data.local.AppDatabase
import com.imhere.app.data.repository.DataStoreSettingsRepository
import com.imhere.app.data.repository.RoomKeywordRepository
import com.imhere.app.data.repository.RoomSessionRepository
import com.imhere.app.runtime.AndroidAlertPlayer
import com.imhere.app.runtime.AndroidSpeechVoiceDetectionEngine
import com.imhere.core.domain.policy.BatteryPolicyController
import com.imhere.core.domain.policy.DefaultPolicyManager
import com.imhere.core.domain.port.AlertPlayer
import com.imhere.core.domain.port.KeywordRepository
import com.imhere.core.domain.port.PolicyManager
import com.imhere.core.domain.port.SessionRepository
import com.imhere.core.domain.port.SettingsRepository
import com.imhere.core.domain.port.VoiceDetectionConfigUpdater
import com.imhere.core.domain.port.VoiceDetectionEngine
import com.imhere.core.domain.state.FinderStateMachine
import com.imhere.core.domain.usecase.SettingsSyncUseCase
import com.imhere.feature.listening.FinderOrchestrator
import com.imhere.platform.monitoring.FinderForegroundServiceEntry
import com.imhere.platform.monitoring.RuntimePolicyMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single { AppDatabase.create(androidContext()) }
    single { get<AppDatabase>().keywordDao() }
    single { get<AppDatabase>().sessionDao() }

    single<SettingsRepository> { DataStoreSettingsRepository(androidContext()) }
    single<KeywordRepository> { RoomKeywordRepository(get()) }
    single<SessionRepository> { RoomSessionRepository(get()) }

    single { AndroidSpeechVoiceDetectionEngine(androidContext()) }
    single<VoiceDetectionEngine> { get<AndroidSpeechVoiceDetectionEngine>() }
    single<VoiceDetectionConfigUpdater> { get<AndroidSpeechVoiceDetectionEngine>() }
    single<AlertPlayer> { AndroidAlertPlayer(androidContext()) }
    single<PolicyManager> { DefaultPolicyManager() }
    single { BatteryPolicyController() }
    single { FinderStateMachine() }
    single { RuntimePolicyMonitor() }
    single { FinderForegroundServiceEntry() }

    single {
        FinderOrchestrator(
            voiceDetectionEngine = get(),
            alertPlayer = get(),
            sessionRepository = get(),
            policyManager = get(),
            batteryPolicyController = get(),
            stateMachine = get(),
            scope = get()
        )
    }

    single {
        SettingsSyncUseCase(
            scope = get(),
            settingsRepository = get(),
            keywordRepository = get(),
            voiceDetectionEngine = get(),
            configUpdater = get()
        )
    }

    single {
        AppBootstrap(
            context = androidContext(),
            foregroundServiceEntry = get(),
            runtimePolicyMonitor = get(),
            orchestrator = get(),
            scope = get()
        )
    }
}
