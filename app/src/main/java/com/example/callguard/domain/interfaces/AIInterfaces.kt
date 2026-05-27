package com.example.callguard.domain.interfaces

import kotlinx.coroutines.flow.SharedFlow

/**
 * Representing the risk category of the active call.
 */
enum class RiskLevel {
    SAFE,      // Normal call
    SUSPICIOUS,// Minor keywords detected
    SCAM       // Definite voice phishing indicators
}

/**
 * Model structure for the result of scam analysis.
 */
data class RiskScore(
    val probability: Float,         // 0.0f (safe) to 1.0f (definite scam)
    val level: RiskLevel,
    val matchedKeywords: List<String>
)

/**
 * SpeechRecognizer defines the interface to convert raw audio streams into text.
 */
interface SpeechRecognizer {
    val transcript: SharedFlow<String>
    val partialTranscript: SharedFlow<String>
    fun feedAudio(pcmData: ByteArray, sampleRate: Int, channels: Int)
    fun reset()
}

/**
 * ScamDetector analyzes text transcripts to assess risk.
 */
interface ScamDetector {
    val riskState: SharedFlow<RiskScore>
    fun analyzeText(text: String)
    fun reset()
}

/**
 * InterventionEngine coordinates UI overlays, mutes, or call terminations based on risk.
 */
interface InterventionEngine {
    fun executeIntervention(level: RiskLevel)
    fun reset()
}
