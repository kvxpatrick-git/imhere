package com.imhere.core.domain.port

import com.imhere.core.model.PlayResult
import com.imhere.core.model.PlaybackPolicy
import com.imhere.core.model.StopReason

interface AlertPlayer {
    suspend fun playSequence(policy: PlaybackPolicy): PlayResult
    suspend fun stop(reason: StopReason)
    fun isPlaying(): Boolean
}
