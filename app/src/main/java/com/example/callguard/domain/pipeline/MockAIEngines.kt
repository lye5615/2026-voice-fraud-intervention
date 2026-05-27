package com.example.callguard.domain.pipeline

import android.util.Log
import com.example.callguard.domain.interfaces.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * MockSpeechRecognizer simulates STT transcription.
 * It analyzes audio frame amplitude (energy) and periodically emits script phrases
 * to simulate speech detection in real time.
 */
class MockSpeechRecognizer : SpeechRecognizer {
    private val TAG = "MockSpeechRecognizer"
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _transcript = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val transcript: SharedFlow<String> = _transcript

    private var frameCounter = 0
    private var isSpeaking = false
    private var scriptIndex = 0

    // Typical voice phishing simulation script
    private val script = listOf(
        "안녕하세요, 대외금융지원국입니다.",
        "서울중앙지검 특별수사본부 김민수 검사입니다.",
        "본인 명의로 불법 대포통장이 개설되어 수사 중입니다.",
        "자산 보호를 위해 현금을 안전 계좌로 송금해야 합니다.",
        "지금 알려드리는 계좌번호로 즉시 송금해주십시오.",
        "확인을 위해 주민등록번호 뒷자리와 카드 비밀번호를 차례로 입력해주세요."
    )

    override fun feedAudio(pcmData: ByteArray, sampleRate: Int, channels: Int) {
        // Calculate root-mean-square (RMS) to detect audio energy/voice activity
        var sum = 0.0
        for (i in 0 until pcmData.size step 2) {
            if (i + 1 < pcmData.size) {
                // Convert 16-bit PCM bytes to short value
                val sample = ((pcmData[i + 1].toInt() shl 8) or (pcmData[i].toInt() and 0xFF)).toDouble()
                sum += sample * sample
            }
        }
        val rms = sqrt(sum / (pcmData.size / 2))

        // Simple threshold to detect active speaking (logged for research validation)
        val voiceThreshold = 500.0
        if (rms > voiceThreshold) {
            if (!isSpeaking) {
                isSpeaking = true
                Log.d(TAG, "Speech detected (RMS: $rms) - Auto script emission is disabled.")
            }
        } else {
            if (isSpeaking) {
                isSpeaking = false
            }
        }
    }

    private fun emitNextScriptPhrase() {
        if (scriptIndex < script.size) {
            val phrase = script[scriptIndex]
            scope.launch {
                _transcript.emit(phrase)
            }
            scriptIndex++
        }
    }

    /**
     * Allows UI or testing to directly trigger a phishing script line.
     */
    fun injectPhrase(phrase: String) {
        scope.launch {
            _transcript.emit(phrase)
        }
    }

    override fun reset() {
        scriptIndex = 0
        frameCounter = 0
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
