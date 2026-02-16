package com.imhere.core.domain.port

import com.imhere.core.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    suspend fun get(): UserSettings
    val settingsFlow: Flow<UserSettings>
    suspend fun update(transform: (UserSettings) -> UserSettings)
}
