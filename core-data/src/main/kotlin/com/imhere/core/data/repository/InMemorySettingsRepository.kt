package com.imhere.core.data.repository

import com.imhere.core.domain.port.SettingsRepository
import com.imhere.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemorySettingsRepository(
    initial: UserSettings = UserSettings(
        stopPhrases = listOf("멈춰"),
        forcePlaybackEnabled = true,
        twoStepTriggerEnabled = false,
        confirmPhrase = "여기 있어",
        batterySaverEnabled = true,
        batteryThresholdPercent = 20
    )
) : SettingsRepository {
    private val lock = Mutex()
    private val state = MutableStateFlow(initial)

    override suspend fun get(): UserSettings = state.value

    override val settingsFlow: Flow<UserSettings> = state

    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        lock.withLock {
            state.value = transform(state.value)
        }
    }
}
