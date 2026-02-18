package com.imhere.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.imhere.core.model.StopReason

@Entity(tableName = "keyword_entries")
data class KeywordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keywordProtected: String
)

@Entity(tableName = "find_sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val triggeredKeywordProtected: String,
    val triggerConfidence: Float?,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val stageReached: Int,
    val stopReason: StopReason?,
    val forcePlaybackApplied: Boolean,
    val batterySaverActive: Boolean,
    val detectionLatencyMs: Long?,
    val stopLatencyMs: Long?
)
