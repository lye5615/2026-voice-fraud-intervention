package com.example.callguard.presentation.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callguard.domain.interfaces.RiskLevel
import com.example.callguard.domain.interfaces.RiskScore
import com.example.callguard.domain.service.CallService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * CallViewModel coordinates the UI state with the CallService backend.
 * Handles binding to the Service and translating flows into Compose states.
 */
class CallViewModel : ViewModel() {
    private val TAG = "CallViewModel"

    private var callService: CallService? = null
    private var isServiceBound = false

    // Service binding state Flow
    private val _isBound = MutableStateFlow(false)
    val isBound: StateFlow<Boolean> = _isBound.asStateFlow()

    // Call state from service
    private val _callState = MutableStateFlow(CallService.CallState.IDLE)
    val callState: StateFlow<CallService.CallState> = _callState.asStateFlow()

    // Real-time transcript state
    private val _transcripts = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val transcripts: StateFlow<List<Pair<String, String>>> = _transcripts.asStateFlow()

    // Real-time risk scoring
    private val _riskScore = MutableStateFlow(RiskScore(0.0f, RiskLevel.SAFE, emptyList()))
    val riskScore: StateFlow<RiskScore> = _riskScore.asStateFlow()

    // Control over warning overlays
    private val _showWarningOverlay = MutableStateFlow(false)
    val showWarningOverlay: StateFlow<Boolean> = _showWarningOverlay.asStateFlow()

    // Audio status states
    private val _isLocalMuted = MutableStateFlow(false)
    val isLocalMuted: StateFlow<Boolean> = _isLocalMuted.asStateFlow()

    private val _isRemoteMuted = MutableStateFlow(false)
    val isRemoteMuted: StateFlow<Boolean> = _isRemoteMuted.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as CallService.CallServiceBinder
            val boundService = binder.getService()
            callService = boundService
            isServiceBound = true
            _isBound.value = true

            // Sync states and collect flows
            viewModelScope.launch {
                boundService.callState.collect { state ->
                    _callState.value = state
                    if (state == CallService.CallState.IDLE) {
                        resetUiStates()
                    }
                }
            }

            viewModelScope.launch {
                boundService.audioPipeline.transcriptFlow.collect { pair ->
                    _transcripts.value = _transcripts.value + pair
                }
            }

            viewModelScope.launch {
                boundService.audioPipeline.riskScoreFlow.collect { risk ->
                    _riskScore.value = risk
                }
            }

            viewModelScope.launch {
                boundService.interventionTriggered.collect { level ->
                    if (level == RiskLevel.SCAM) {
                        _showWarningOverlay.value = true
                        _isRemoteMuted.value = true
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            callService = null
            isServiceBound = false
            _isBound.value = false
            Log.d(TAG, "CallService disconnected")
        }
    }

    fun bindCallService(context: Context) {
        val intent = Intent(context, CallService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun unbindCallService(context: Context) {
        if (isServiceBound) {
            context.unbindService(serviceConnection)
            isServiceBound = false
            _isBound.value = false
        }
    }

    /**
     * Start the Call via Service Foreground transition
     */
    fun initiateCall(context: Context) {
        val intent = Intent(context, CallService::class.java)
        context.startService(intent)
        
        // Wait briefly for service binding or directly call start if bound
        viewModelScope.launch {
            if (!isServiceBound) {
                bindCallService(context)
            }
            // Poll for binding to start calling
            var count = 0
            while (callService == null && count < 20) {
                kotlinx.coroutines.delay(100)
                count++
            }
            callService?.startInAppCall()
        }
    }

    fun endCall() {
        callService?.hangUpCall()
        resetUiStates()
    }

    fun toggleLocalMute() {
        val current = _isLocalMuted.value
        _isLocalMuted.value = !current
        callService?.muteLocalMic(!current)
    }

    fun toggleRemoteMute() {
        val current = _isRemoteMuted.value
        _isRemoteMuted.value = !current
        callService?.muteRemoteAudio(!current)
    }

    fun dismissOverlay() {
        _showWarningOverlay.value = false
    }

    /**
     * Simulation tool: Inject phrases on behalf of the remote participant
     */
    fun simulateRemoteSpeech(phrase: String) {
        callService?.remoteSpeechRecognizer?.injectPhrase(phrase)
    }

    private fun resetUiStates() {
        _transcripts.value = emptyList()
        _riskScore.value = RiskScore(0.0f, RiskLevel.SAFE, emptyList())
        _showWarningOverlay.value = false
        _isLocalMuted.value = false
        _isRemoteMuted.value = false
    }
}
