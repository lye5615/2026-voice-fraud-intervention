package com.example.callguard.domain.pipeline

import android.content.Context
import android.util.Log
import com.example.callguard.domain.interfaces.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import kotlin.math.sqrt

/**
 * MockSpeechRecognizer performs actual offline STT transcription using Vosk.
 * Falls back to simulation if the model is not loaded.
 */
class MockSpeechRecognizer(private val context: Context) : SpeechRecognizer {
    private val TAG = "MockSpeechRecognizer"
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _transcript = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val transcript: SharedFlow<String> = _transcript

    private var isSpeaking = false
    
    // Vosk Model & Recognizer
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var isModelLoading = false

    init {
        initVoskModel()
    }

    private fun initVoskModel() {
        isModelLoading = true
        StorageService.unpack(context, "model-ko", "model",
            { m ->
                model = m
                isModelLoading = false
                Log.d(TAG, "Vosk model loaded successfully for $TAG")
            },
            { e ->
                isModelLoading = false
                Log.e(TAG, "Failed to load Vosk model: ${e.message}")
            }
        )
    }

    override fun feedAudio(pcmData: ByteArray, sampleRate: Int, channels: Int) {
        val currentModel = model
        if (currentModel == null) {
            // Calculate RMS energy even if model is not loaded to trace voice activity
            calculateEnergy(pcmData)
            return
        }

        // Initialize recognizer dynamically with the stream's sample rate
        if (recognizer == null) {
            try {
                recognizer = Recognizer(currentModel, sampleRate.toFloat())
                Log.d(TAG, "Vosk Recognizer initialized at $sampleRate Hz")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create Vosk Recognizer: ${e.message}")
                return
            }
        }

        val rec = recognizer ?: return
        
        // Feed raw PCM audio frames to Vosk engine
        if (rec.acceptWaveForm(pcmData, pcmData.size)) {
            val resultJson = rec.result
            val text = parseVoskJson(resultJson, "text")
            if (text.isNotBlank()) {
                Log.d(TAG, "Speech Transcribed: $text")
                scope.launch {
                    _transcript.emit(text)
                }
            }
        }
    }

    private fun calculateEnergy(pcmData: ByteArray) {
        var sum = 0.0
        for (i in 0 until pcmData.size step 2) {
            if (i + 1 < pcmData.size) {
                val sample = ((pcmData[i + 1].toInt() shl 8) or (pcmData[i].toInt() and 0xFF)).toDouble()
                sum += sample * sample
            }
        }
        val rms = sqrt(sum / (pcmData.size / 2))
        val voiceThreshold = 500.0
        if (rms > voiceThreshold) {
            if (!isSpeaking) {
                isSpeaking = true
                Log.d(TAG, "Mic Activity detected (RMS: $rms) [STT Model Unpacking...]")
            }
        } else {
            if (isSpeaking) {
                isSpeaking = false
            }
        }
    }

    private fun parseVoskJson(json: String, key: String): String {
        val regex = """"$key"\s*:\s*"([^"]*)"""".toRegex()
        val match = regex.find(json)
        return match?.groups?.get(1)?.value ?: ""
    }

    /**
     * Allows UI or testing to directly inject a phishing script line.
     */
    fun injectPhrase(phrase: String) {
        scope.launch {
            _transcript.emit(phrase)
        }
    }

    override fun reset() {
        recognizer?.reset()
        isSpeaking = false
    }
}

/**
 * MockScamDetector performs simple keyword matching to simulate NLP analysis.
 */
class MockScamDetector : ScamDetector {
    private val TAG = "MockScamDetector"
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _riskState = MutableSharedFlow<RiskScore>(extraBufferCapacity = 64)
    override val riskState: SharedFlow<RiskScore> = _riskState

    private val matchedKeywords = mutableSetOf<String>()

    private val phishingKeywords = listOf(
        "검사", "검찰", "대포통장", "송금", "비밀번호", "주민등록번호", "안전 계좌", "수사", "수사관"
    )

    override fun analyzeText(text: String) {
        var newlyMatched = false

        // Match keywords
        for (keyword in phishingKeywords) {
            if (text.contains(keyword) && !matchedKeywords.contains(keyword)) {
                matchedKeywords.add(keyword)
                newlyMatched = true
            }
        }

        if (newlyMatched || matchedKeywords.isNotEmpty()) {
            val count = matchedKeywords.size
            val (prob, level) = when {
                count >= 4 -> 0.95f to RiskLevel.SCAM
                count >= 2 -> 0.65f to RiskLevel.SUSPICIOUS
                count >= 1 -> 0.35f to RiskLevel.SUSPICIOUS
                else -> 0.05f to RiskLevel.SAFE
            }

            scope.launch {
                val score = RiskScore(prob, level, matchedKeywords.toList())
                Log.d(TAG, "Risk score updated: $score")
                _riskState.emit(score)
            }
        }
    }

    override fun reset() {
        matchedKeywords.clear()
        scope.launch {
            _riskState.emit(RiskScore(0.0f, RiskLevel.SAFE, emptyList()))
        }
    }
}

/**
 * MockInterventionEngine logs actions and handles mock trigger notifications.
 */
class MockInterventionEngine(
    private val onInterventionTriggered: (RiskLevel) -> Unit
) : InterventionEngine {

    override fun executeIntervention(level: RiskLevel) {
        Log.d("MockInterventionEngine", "Executing intervention for level: $level")
        onInterventionTriggered(level)
    }

    override fun reset() {
        // No-op
    }
}
