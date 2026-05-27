package com.example.callguard.domain.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.example.callguard.MainActivity
import com.example.callguard.R
import com.example.callguard.data.webrtc.WebRtcManager
import com.example.callguard.domain.interfaces.RiskLevel
import com.example.callguard.domain.interfaces.RiskScore
import com.example.callguard.domain.pipeline.AudioProcessingPipeline
import com.example.callguard.domain.pipeline.MockInterventionEngine
import com.example.callguard.domain.pipeline.MockScamDetector
import com.example.callguard.domain.pipeline.MockSpeechRecognizer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Foreground service to manage active call state and process audio.
 * Using LifecycleService to simplify Coroutine lifecycle bindings.
 */
class CallService : LifecycleService() {
    private val TAG = "CallService"
    private val NOTIFICATION_ID = 101
    private val CHANNEL_ID = "CallServiceChannel"

    private val binder = CallServiceBinder()

    // WebRTC & Audio Pipeline Core
    lateinit var webRtcManager: WebRtcManager
        private set
    lateinit var audioPipeline: AudioProcessingPipeline
        private set

    // Mock Engines exposed for testing
    lateinit var localSpeechRecognizer: MockSpeechRecognizer
    lateinit var remoteSpeechRecognizer: MockSpeechRecognizer
    lateinit var scamDetector: MockScamDetector

    // Expose flow states for UI observation
    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState

    private val _interventionTriggered = MutableSharedFlow<RiskLevel>(extraBufferCapacity = 8)
    val interventionTriggered: SharedFlow<RiskLevel> = _interventionTriggered

    enum class CallState {
        IDLE, RINGING, CONNECTED, DISCONNECTED
    }

    inner class CallServiceBinder : Binder() {
        fun getService(): CallService = this@CallService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "CallService Created")
        createNotificationChannel()

        // 1. Initialize Mock AI modules
        localSpeechRecognizer = MockSpeechRecognizer()
        remoteSpeechRecognizer = MockSpeechRecognizer()
        scamDetector = MockScamDetector()
        
        val mockIntervention = MockInterventionEngine { riskLevel ->
            Log.w(TAG, "Intervention triggered for risk level: $riskLevel")
            _interventionTriggered.tryEmit(riskLevel)

            // Automate intervention when scam is detected
            if (riskLevel == RiskLevel.SCAM) {
                // Perform automated mitigation: Mute remote audio to protect victim
                muteRemoteAudio(true)
            }
        }

        // 2. Connect pipeline
        audioPipeline = AudioProcessingPipeline(
            localSpeechRecognizer,
            remoteSpeechRecognizer,
            scamDetector,
            mockIntervention
        )

        // 3. Initialize WebRTC manager with pipeline inputs
        webRtcManager = WebRtcManager(
            context = applicationContext,
            localAudioCallback = { data, sampleRate, channels ->
                audioPipeline.onLocalAudioFrame(data, sampleRate, channels)
            },
            remoteAudioCallback = { data, sampleRate, channels ->
                audioPipeline.onRemoteAudioFrame(data, sampleRate, channels)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "CallService onStartCommand")
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        Log.d(TAG, "CallService Bound")
        return binder
    }

    /**
     * Triggers WebRTC local audio track generation and starts loopback connection negotiation.
     */
    fun startInAppCall() {
        _callState.value = CallState.RINGING
        webRtcManager.startLocalAudioCapture()
        
        // Simulating immediate answer negotiation via Loopback signaling
        webRtcManager.startLoopbackCall(
            onOfferCreated = { sdp ->
                Log.d(TAG, "SDP Offer created successfully: ${sdp.type}")
            },
            onAnswerCreated = { sdp ->
                Log.d(TAG, "SDP Answer created successfully: ${sdp.type}")
                _callState.value = CallState.CONNECTED
            }
        )
    }

    /**
     * Mute local microphone input.
     */
    fun muteLocalMic(mute: Boolean) {
        webRtcManager.setLocalAudioMuted(mute)
    }

    /**
     * Mute incoming remote participant audio (intervention action).
     */
    fun muteRemoteAudio(mute: Boolean) {
        webRtcManager.setRemoteAudioMuted(mute)
    }

    /**
     * Ends call session and shuts down WebRTC and Pipelines.
     */
    fun hangUpCall() {
        Log.d(TAG, "Hanging up active call")
        _callState.value = CallState.DISCONNECTED
        webRtcManager.stopCall()
        audioPipeline.reset()
        _callState.value = CallState.IDLE
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.call_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.call_notification_title))
            .setContentText(getString(R.string.call_notification_desc))
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        Log.d(TAG, "CallService Destroyed")
        hangUpCall()
        super.onDestroy()
    }
}
