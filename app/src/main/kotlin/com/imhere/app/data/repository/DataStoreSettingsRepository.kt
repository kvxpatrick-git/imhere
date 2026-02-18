package com.imhere.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.imhere.core.domain.port.SettingsRepository
import com.imhere.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "imhere_settings")

class DataStoreSettingsRepository(
    private val context: Context
) : SettingsRepository {
    override suspend fun get(): UserSettings = settingsFlow.first()

    override val settingsFlow: Flow<UserSettings> = context.settingsDataStore.data.map { pref ->
        UserSettings(
            stopPhrases = pref[STOP_PHRASES]?.split("|")?.filter { it.isNotBlank() } ?: listOf("멈춰"),
            forcePlaybackEnabled = pref[FORCE_PLAYBACK] ?: true,
            twoStepTriggerEnabled = pref[TWO_STEP] ?: false,
            confirmPhrase = pref[CONFIRM_PHRASE] ?: "여기 있어",
            batterySaverEnabled = pref[BATTERY_SAVER] ?: true,
            batteryThresholdPercent = pref[BATTERY_THRESHOLD] ?: 20,
            autoResumeOnBoot = pref[AUTO_RESUME_ON_BOOT] ?: false
        )
    }

    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        val next = transform(get())
        context.settingsDataStore.edit { pref ->
            pref[STOP_PHRASES] = next.stopPhrases.joinToString("|")
            pref[FORCE_PLAYBACK] = next.forcePlaybackEnabled
            pref[TWO_STEP] = next.twoStepTriggerEnabled
            pref[CONFIRM_PHRASE] = next.confirmPhrase
            pref[BATTERY_SAVER] = next.batterySaverEnabled
            pref[BATTERY_THRESHOLD] = next.batteryThresholdPercent
            pref[AUTO_RESUME_ON_BOOT] = next.autoResumeOnBoot
        }
    }

    private companion object {
        val STOP_PHRASES = stringPreferencesKey("stop_phrases")
        val FORCE_PLAYBACK = booleanPreferencesKey("force_playback")
        val TWO_STEP = booleanPreferencesKey("two_step_trigger")
        val CONFIRM_PHRASE = stringPreferencesKey("confirm_phrase")
        val BATTERY_SAVER = booleanPreferencesKey("battery_saver_enabled")
        val BATTERY_THRESHOLD = intPreferencesKey("battery_threshold_percent")
        val AUTO_RESUME_ON_BOOT = booleanPreferencesKey("auto_resume_on_boot")
    }
}
