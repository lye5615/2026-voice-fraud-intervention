package com.example.callguard.data.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer

/**
 * WebRtcManager coordinates PeerConnection setup, audio stream creation,
 * and interception of both local and remote PCM audio frames.
 */
class WebRtcManager(
    private val context: Context,
    private val localAudioCallback: (ByteArray, Int, Int) -> Unit,
    private val remoteAudioCallback: (ByteArray, Int, Int) -> Unit
) {
    private val TAG = "WebRtcManager"

    private var factory: PeerConnectionFactory? = null
    private var localPeerConnection: PeerConnection? = null
    private var remotePeerConnection: PeerConnection? = null // Used for loopback test
    private var localAudioTrack: AudioTrack? = null
    private var localAudioSource: AudioSource? = null

    // Audio states for intervention
    private var isLocalAudioMuted = false
    private var isRemoteAudioMuted = false

    init {
        initPeerConnectionFactory()
    }

    private fun initPeerConnectionFactory() {
        // Initialize PeerConnectionFactory globals
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        // Setup the JavaAudioDeviceModule to intercept outgoing microphone audio
        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setSamplesReadyCallback { audioSamples ->
                // Capture local mic PCM frames directly from the WebRTC recorder
                if (!isLocalAudioMuted) {
                    val rawData = audioSamples.data
                    localAudioCallback(
                        rawData,
                        audioSamples.sampleRate,
                        audioSamples.channelCount
                    )

                    // In a loopback test call, the local microphone stream is also the incoming remote audio stream
                    if (!isRemoteAudioMuted) {
                        remoteAudioCallback(
                            rawData.clone(), // Clone to avoid buffer races
                            audioSamples.sampleRate,
                            audioSamples.channelCount
                        )
                    } else {
                        // Muted by intervention: send blank frames to pipeline
                        remoteAudioCallback(
                            ByteArray(rawData.size),
                            audioSamples.sampleRate,
                            audioSamples.channelCount
                        )
                    }
                }
            }
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        val factoryOptions = PeerConnectionFactory.Options()
        factory = PeerConnectionFactory.builder()
            .setOptions(factoryOptions)
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()
    }

    /**
     * Prepares the local audio source and audio track.
     */
    fun startLocalAudioCapture() {
        val factory = factory ?: return
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }

        localAudioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("local_audio_track", localAudioSource)
    }

    /**
     * Sets up a local loopback call (Self-Call) to test the entire WebRTC and audio pipeline
     * on a single device without a signaling server.
     */
    fun startLoopbackCall(
        onOfferCreated: (SessionDescription) -> Unit,
        onAnswerCreated: (SessionDescription) -> Unit
    ) {
        val factory = factory ?: return
        val rtcConfig = PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        // 1. Setup local PeerConnection (transmits local microphone)
        localPeerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnectionObserverAdapter() {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    Log.d(TAG, "Local PC IceCandidate: $it")
                    remotePeerConnection?.addIceCandidate(it)
                }
            }
        })

        // Add local audio track to local PeerConnection
        localAudioTrack?.let {
            localPeerConnection?.addTrack(it, listOf("stream_id"))
        }

        // 2. Setup remote PeerConnection (receives audio stream)
        remotePeerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnectionObserverAdapter() {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    Log.d(TAG, "Remote PC IceCandidate: $it")
                    localPeerConnection?.addIceCandidate(it)
                }
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is AudioTrack) {
                    Log.d(TAG, "Remote AudioTrack received via loopback connection.")
                }
            }
        })

        // 3. Initiate connection: Create Offer
        val constraints = MediaConstraints()
        localPeerConnection?.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    localPeerConnection?.setLocalDescription(SdpObserverAdapter(), it)
                    remotePeerConnection?.setRemoteDescription(SdpObserverAdapter(), it)
                    onOfferCreated(it)

                    // Create Answer on the receiving side
                    remotePeerConnection?.createAnswer(object : SdpObserverAdapter() {
                        override fun onCreateSuccess(answerDesc: SessionDescription?) {
                            answerDesc?.let {
                                remotePeerConnection?.setLocalDescription(SdpObserverAdapter(), it)
                                localPeerConnection?.setRemoteDescription(SdpObserverAdapter(), it)
                                onAnswerCreated(it)
                            }
                        }
                    }, constraints)
                }
            }
        }, constraints)
    }

    /**
     * Mutes/Unmutes local microphone.
     */
    fun setLocalAudioMuted(mute: Boolean) {
        isLocalAudioMuted = mute
        localAudioTrack?.setEnabled(!mute)
        Log.d(TAG, "Local audio mute state: $mute")
    }

    /**
     * Mutes/Unmutes remote participant audio playout (Intervention action).
     */
    fun setRemoteAudioMuted(mute: Boolean) {
        isRemoteAudioMuted = mute
        Log.d(TAG, "Remote audio mute state (Intervention): $mute")
    }

    /**
     * Cleans up all WebRTC resources when call ends.
     */
    fun stopCall() {
        localPeerConnection?.close()
        remotePeerConnection?.close()
        localAudioSource?.dispose()

        localPeerConnection = null
        remotePeerConnection = null
        localAudioTrack = null
        localAudioSource = null
        isLocalAudioMuted = false
        isRemoteAudioMuted = false
        Log.d(TAG, "WebRTC resources cleaned up.")
    }
}

/**
 * Adapter class to minimize boilerplate for PeerConnection.Observer
 */
open class PeerConnectionObserverAdapter : PeerConnection.Observer {
    override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
    override fun onIceCandidate(candidate: IceCandidate?) {}
    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
    override fun onAddStream(stream: MediaStream?) {}
    override fun onRemoveStream(stream: MediaStream?) {}
    override fun onDataChannel(channel: DataChannel?) {}
    override fun onRenegotiationNeeded() {}
    override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {}
    override fun onTrack(transceiver: RtpTransceiver?) {}
}

/**
 * Adapter class to minimize boilerplate for SdpObserver
 */
open class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {
        Log.e("SdpObserverAdapter", "SDP Create Failure: $error")
    }
    override fun onSetFailure(error: String?) {
        Log.e("SdpObserverAdapter", "SDP Set Failure: $error")
    }
}
