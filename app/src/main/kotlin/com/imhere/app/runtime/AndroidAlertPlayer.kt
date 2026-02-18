package com.imhere.app.runtime

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.imhere.core.domain.port.AlertPlayer
import com.imhere.core.model.PlayResult
import com.imhere.core.model.PlaybackPolicy
import com.imhere.core.model.StopReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AndroidAlertPlayer(
    private val context: Context
) : AlertPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    private var stageJob: Job? = null
    private var playing: Boolean = false
    private var previousVolume: Int = -1
    @Volatile
    private var activeRingtone: Ringtone? = null

    override suspend fun playSequence(policy: PlaybackPolicy): PlayResult {
        val started = System.currentTimeMillis()
        lock.withLock {
            stopInternal(StopReason.USER_STOP)
            previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            playing = true
            stageJob = scope.launch {
                playStage(policy.stage1Volume, policy.stage1Sec)
                if (!playing) return@launch
                playStage(policy.stage2Volume, policy.stage2Sec)
                if (!playing) return@launch
                playStage(policy.stage3Volume, policy.stage3MaxSec, vibrate = true, pattern = policy.vibratePatternMs)
                stopInternal(StopReason.STAGE3_TIMEOUT)
            }
        }
        return PlayResult(startedAtEpochMs = started, firstAudioAtEpochMs = started, volumeOverrideApplied = policy.forcePlaybackEnabled)
    }

    override suspend fun stop(reason: StopReason) {
        lock.withLock { stopInternal(reason) }
    }

    override fun isPlaying(): Boolean = playing

    private suspend fun playStage(volumePercent: Int, durationSec: Int, vibrate: Boolean = false, pattern: LongArray = longArrayOf()) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (max * (volumePercent / 100f)).toInt().coerceIn(1, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)

        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val ringtone = RingtoneManager.getRingtone(context, ringtoneUri)
        ringtone.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        activeRingtone = ringtone
        try {
            ringtone.play()
            if (vibrate) startVibration(pattern)
            delay(durationSec * 1_000L)
        } finally {
            runCatching { ringtone.stop() }
            if (activeRingtone === ringtone) {
                activeRingtone = null
            }
            if (vibrate) vibrator.cancel()
        }
    }

    private fun startVibration(pattern: LongArray) {
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 0)
        }
    }

    private fun stopInternal(@Suppress("UNUSED_PARAMETER") reason: StopReason) {
        stageJob?.cancel()
        stageJob = null
        activeRingtone?.let { ringtone ->
            runCatching { ringtone.stop() }
        }
        activeRingtone = null
        vibrator.cancel()
        if (previousVolume >= 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume, 0)
            previousVolume = -1
        }
        playing = false
    }
}
