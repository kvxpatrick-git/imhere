package com.imhere.platform.audio

import com.imhere.core.domain.port.AlertPlayer
import com.imhere.core.model.PlayResult
import com.imhere.core.model.PlaybackPolicy
import com.imhere.core.model.StopReason
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PlatformAlertPlayer : AlertPlayer {
    private val lock = Mutex()

    private var playing: Boolean = false
    private var lastStopReason: StopReason? = null

    override suspend fun playSequence(policy: PlaybackPolicy): PlayResult {
        val startedAt = System.currentTimeMillis()
        lock.withLock {
            playing = true
            lastStopReason = null
        }
        return PlayResult(
            startedAtEpochMs = startedAt,
            firstAudioAtEpochMs = startedAt,
            volumeOverrideApplied = policy.forcePlaybackEnabled
        )
    }

    override suspend fun stop(reason: StopReason) {
        lock.withLock {
            playing = false
            lastStopReason = reason
        }
    }

    override fun isPlaying(): Boolean = playing

    fun lastStopReason(): StopReason? = lastStopReason
}
