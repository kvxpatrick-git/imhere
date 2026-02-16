package com.imhere.core.data.schema

object RoomTables {
    const val KEYWORD_ENTRIES = "keyword_entries"
    const val FIND_SESSIONS = "find_sessions"
}

object PreferenceKeys {
    const val FORCE_PLAYBACK_ENABLED = "force_playback_enabled"
    const val TWO_STEP_TRIGGER_ENABLED = "two_step_trigger_enabled"
    const val CONFIRM_PHRASE = "confirm_phrase"
    const val BATTERY_SAVER_ENABLED = "battery_saver_enabled"
    const val BATTERY_THRESHOLD_PERCENT = "battery_threshold_percent"
    const val STOP_PHRASES = "stop_phrases"
}

object RetentionPolicy {
    const val SESSION_TTL_DAYS = 30L
    const val SESSION_TTL_MS = SESSION_TTL_DAYS * 24L * 60L * 60L * 1000L
}
