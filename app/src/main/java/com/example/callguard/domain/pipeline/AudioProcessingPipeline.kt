package com.example.callguard.domain.pipeline

import com.example.callguard.domain.interfaces.RiskLevel
import com.example.callguard.domain.interfaces.RiskScore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * AudioProcessingPipeline connects WebRTC audio interception callbacks
 * with the AI analysis stages (STT -> Intent Detection -> Risk Assessment -> Intervention).
 */
class AudioProcessingPipeline(
    val localSpeechRecognizer: MockSpeechRecognizer,
    val remoteSpeechRecognizer: MockSpeechRecognizer,
    val scamDetector: MockScamDetector,
    val interventionEngine: MockInterventionEngine
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _transcriptFlow = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 64) // speaker to text
    val transcriptFlow: SharedFlow<Pair<String, String>> = _transcriptFlow

    val riskScoreFlow: SharedFlow<RiskScore> = scamDetector.riskState

    init {
        // Collect transcripts from local speaker
        scope.launch {
            localSpeechRecognizer.transcript.collect { text ->
                _transcriptFlow.emit("LOCAL" to text)
                scamDetector.analyzeText(text)
            }
        }

        // Collect transcripts from remote participant
        scope.launch {
            remoteSpeechRecognizer.transcript.collect { text ->
                _transcriptFlow.emit("REMOTE" to text)
                scamDetector.analyzeText(text)
            }
        }

        // Monitor risk updates and trigger intervention engine
        scope.launch {
            scamDetector.riskState.collect { risk ->
                if (risk.level != RiskLevel.SAFE) {
                    interventionEngine.executeIntervention(risk.level)
                }
            }
        }
    }

    /**
     * Feeds captured local mic audio frames into the local speech recognizer.
     */
    fun onLocalAudioFrame(pcmData: ByteArray, sampleRate: Int, channels: Int) {
        localSpeechRecognizer.feedAudio(pcmData, sampleRate, channels)
    }

    /**
     * Feeds captured remote audio frames into the remote speech recognizer.
     */
    fun onRemoteAudioFrame(pcmData: ByteArray, sampleRate: Int, channels: Int) {
        remoteSpeechRecognizer.feedAudio(pcmData, sampleRate, channels)
    }

    /**
     * Resets pipeline state for a new call.
     */
    fun reset() {
        localSpeechRecognizer.reset()
        remoteSpeechRecognizer.reset()
        scamDetector.reset()
        interventionEngine.reset()
    }
}
