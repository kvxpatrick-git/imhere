package com.imhere.app.runtime

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.imhere.core.domain.port.VoiceDetectionConfigUpdater
import com.imhere.core.domain.port.VoiceDetectionEngine
import com.imhere.core.model.DetectionConfig
import com.imhere.core.model.DetectionError
import com.imhere.core.model.StopDetected
import com.imhere.core.model.WakeDetected
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class AndroidSpeechVoiceDetectionEngine(
    private val context: Context
) : VoiceDetectionEngine, VoiceDetectionConfigUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var recognizer: SpeechRecognizer? = null
    private var running: Boolean = false
    private var config: DetectionConfig = DetectionConfig(
        keywords = listOf("폰아 울려"),
        stopPhrases = listOf("멈춰"),
        confirmPhrase = "여기 있어",
        twoStepTriggerEnabled = false,
        confidenceThreshold = 0.62f,
        profile = com.imhere.core.model.EngineProfile.BALANCED,
        cooldownSec = 15
    )

    private val _wakeEvents = MutableSharedFlow<WakeDetected>(extraBufferCapacity = 16)
    private val _stopEvents = MutableSharedFlow<StopDetected>(extraBufferCapacity = 16)
    private val _errors = MutableSharedFlow<DetectionError>(extraBufferCapacity = 16)

    override val wakeEvents: Flow<WakeDetected> = _wakeEvents
    override val stopEvents: Flow<StopDetected> = _stopEvents
    override val errors: Flow<DetectionError> = _errors

    override suspend fun start(config: DetectionConfig) {
        this.config = config
        running = true
        ensureRecognizerOnMain()
        startListeningInternalOnMain()
    }

    override suspend fun stop() {
        running = false
        withContext(Dispatchers.Main.immediate) {
            recognizer?.stopListening()
        }
    }

    override fun updateKeywords(keywords: List<String>) {
        config = config.copy(keywords = keywords.distinct())
    }

    override suspend fun updateConfig(config: DetectionConfig) {
        this.config = config
    }

    private suspend fun ensureRecognizerOnMain() = withContext(Dispatchers.Main.immediate) {
        if (recognizer != null) return@withContext
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _errors.tryEmit(DetectionError("E-DET-UNAVAILABLE", recoverable = false, message = "SpeechRecognizer unavailable"))
            return@withContext
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    _errors.tryEmit(DetectionError("E-DET-$error", recoverable = true, message = "Recognizer error=$error"))
                    if (running) {
                        scope.launch { startListeningInternalOnMain() }
                    }
                }

                override fun onResults(results: Bundle?) {
                    handleRecognitionBundle(results)
                    if (running) {
                        scope.launch { startListeningInternalOnMain() }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    handleRecognitionBundle(partialResults)
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    private fun handleRecognitionBundle(bundle: Bundle?) {
        val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        if (matches.isEmpty()) return
        val spoken = matches.first().lowercase(Locale.getDefault())
        val now = System.currentTimeMillis()

        config.stopPhrases.firstOrNull { spoken.contains(it.lowercase(Locale.getDefault())) }?.let { phrase ->
            _stopEvents.tryEmit(StopDetected(phrase = phrase, confidence = 0.8f, detectedAtEpochMs = now))
            return
        }
        if (spoken.contains(config.confirmPhrase.lowercase(Locale.getDefault()))) {
            _wakeEvents.tryEmit(WakeDetected(keyword = config.confirmPhrase, confidence = 0.8f, detectedAtEpochMs = now))
            return
        }
        config.keywords.firstOrNull { spoken.contains(it.lowercase(Locale.getDefault())) }?.let { keyword ->
            _wakeEvents.tryEmit(WakeDetected(keyword = keyword, confidence = 0.8f, detectedAtEpochMs = now))
        }
    }

    private suspend fun startListeningInternalOnMain() = withContext(Dispatchers.Main.immediate) {
        val rec = recognizer ?: return@withContext
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try {
            rec.startListening(intent)
        } catch (t: Throwable) {
            _errors.tryEmit(DetectionError("E-DET-START", recoverable = true, message = t.message ?: "start failed"))
        }
    }
}
