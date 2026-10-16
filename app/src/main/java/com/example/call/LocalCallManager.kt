package com.example.call

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.net.wifi.WifiManager
import android.util.Log
import com.example.data.model.PacketType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import org.json.JSONObject

enum class CallState {
    IDLE,
    OUTGOING_RINGING,
    INCOMING_RINGING,
    CONNECTED,
    ENDED
}

enum class CallType {
    AUDIO,
    VIDEO
}

class LocalCallManager(
    private val context: Context,
    private val sendControlPacket: (destNodeId: String, type: PacketType, payload: String) -> Unit
) {
    companion object {
        private const val TAG = "LocalCallManager"
        private const val AUDIO_PORT = 50005
        private const val VIDEO_PORT = 50006
        private const val SAMPLE_RATE = 16000
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _callType = MutableStateFlow(CallType.AUDIO)
    val callType: StateFlow<CallType> = _callType.asStateFlow()

    private val _remotePeerId = MutableStateFlow<String?>(null)
    val remotePeerId: StateFlow<String?> = _remotePeerId.asStateFlow()

    private val _remotePeerName = MutableStateFlow("")
    val remotePeerName: StateFlow<String> = _remotePeerName.asStateFlow()

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(true)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    private val _callDurationSec = MutableStateFlow(0)
    val callDurationSec: StateFlow<Int> = _callDurationSec.asStateFlow()

    private val _showWifiPrompt = MutableStateFlow(false)
    val showWifiPrompt: StateFlow<Boolean> = _showWifiPrompt.asStateFlow()

    private var durationTimerJob: Job? = null
    private var audioRecordJob: Job? = null
    private var audioPlayJob: Job? = null
    private var audioSocket: DatagramSocket? = null
    private var remoteIpAddress: InetAddress? = null

    fun isWifiEnabled(): Boolean {
        return wifiManager?.isWifiEnabled == true
    }

    fun dismissWifiPrompt() {
        _showWifiPrompt.value = false
    }

    /**
     * Start an outgoing call. Checks Wi-Fi state and sends Bluetooth call awakening packet.
     */
    fun startCall(peerId: String, peerName: String, isVideo: Boolean) {
        if (!isWifiEnabled()) {
            _showWifiPrompt.value = true
        }

        _remotePeerId.value = peerId
        _remotePeerName.value = peerName
        _callType.value = if (isVideo) CallType.VIDEO else CallType.AUDIO
        _callState.value = CallState.OUTGOING_RINGING
        _isMicMuted.value = false
        _isSpeakerOn.value = isVideo // Default to speakerphone for video calls
        _isFrontCamera.value = true
        _callDurationSec.value = 0

        // 1. Send Bluetooth Call Awakening packet to awaken receiver if their Wi-Fi is sleeping/off
        sendControlPacket(peerId, PacketType.CALL_AWAKEN, if (isVideo) "VIDEO" else "AUDIO")

        // 2. Send Call Offer packet across mesh
        sendControlPacket(peerId, PacketType.CALL_OFFER, if (isVideo) "VIDEO" else "AUDIO")

        Log.i(TAG, "Initiated ${if (isVideo) "VIDEO" else "AUDIO"} call to $peerName ($peerId)")
    }

    /**
     * Handle incoming call awaken packet from Bluetooth mesh
     */
    fun onIncomingCall(callerId: String, callerName: String, isVideo: Boolean) {
        if (_callState.value != CallState.IDLE) return // Already in call

        _remotePeerId.value = callerId
        _remotePeerName.value = callerName
        _callType.value = if (isVideo) CallType.VIDEO else CallType.AUDIO
        _callState.value = CallState.INCOMING_RINGING
        _isMicMuted.value = false
        _isSpeakerOn.value = isVideo
        _isFrontCamera.value = true
        _callDurationSec.value = 0
    }

    /**
     * Answer incoming call
     */
    fun answerCall() {
        val peerId = _remotePeerId.value ?: return
        if (!isWifiEnabled()) {
            _showWifiPrompt.value = true
        }

        _callState.value = CallState.CONNECTED
        sendControlPacket(peerId, PacketType.CALL_ANSWER, "ACCEPTED")
        startStreamingSession()
    }

    /**
     * Peer answered our outgoing call
     */
    fun onCallAnswered() {
        if (_callState.value == CallState.OUTGOING_RINGING) {
            _callState.value = CallState.CONNECTED
            startStreamingSession()
        }
    }

    /**
     * End current call and notify peer
     */
    fun endCall() {
        val peerId = _remotePeerId.value
        if (peerId != null && _callState.value != CallState.IDLE) {
            sendControlPacket(peerId, PacketType.CALL_END, "ENDED")
        }
        cleanupCall()
    }

    /**
     * Remote peer ended the call
     */
    fun onCallEndedByPeer() {
        cleanupCall()
    }

    fun toggleMic() {
        _isMicMuted.value = !_isMicMuted.value
    }

    fun toggleSpeaker() {
        val newState = !_isSpeakerOn.value
        _isSpeakerOn.value = newState
        audioManager?.isSpeakerphoneOn = newState
    }

    fun flipCamera() {
        _isFrontCamera.value = !_isFrontCamera.value
    }

    private fun startStreamingSession() {
        durationTimerJob?.cancel()
        durationTimerJob = scope.launch {
            while (isActive && _callState.value == CallState.CONNECTED) {
                delay(1000)
                _callDurationSec.value += 1
            }
        }

        // Initialize low-latency audio capture and playback over UDP
        startAudioStreaming()
    }

    private fun startAudioStreaming() {
        scope.launch {
            try {
                audioSocket = DatagramSocket(AUDIO_PORT).apply {
                    broadcast = true
                }

                // Audio Playback Thread
                audioPlayJob = launch {
                    val minBuf = AudioTrack.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                    val audioTrack = AudioTrack(
                        AudioManager.STREAM_VOICE_CALL,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        minBuf.coerceAtLeast(2048),
                        AudioTrack.MODE_STREAM
                    )
                    audioTrack.play()

                    val buffer = ByteArray(1024)
                    val packet = DatagramPacket(buffer, buffer.size)

                    try {
                        while (isActive && _callState.value == CallState.CONNECTED) {
                            audioSocket?.receive(packet)
                            audioTrack.write(packet.data, 0, packet.length)
                        }
                    } catch (_: Exception) {
                    } finally {
                        audioTrack.stop()
                        audioTrack.release()
                    }
                }

                // Audio Record Thread
                audioRecordJob = launch {
                    val minBuf = AudioRecord.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                    val recordBuf = minBuf.coerceAtLeast(2048)
                    val audioRecord = try {
                        AudioRecord(
                            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            recordBuf
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "AudioRecord init fallback", e)
                        null
                    }

                    audioRecord?.let { recorder ->
                        recorder.startRecording()
                        val sendBuffer = ByteArray(640)
                        val broadcastIp = InetAddress.getByName("255.255.255.255")

                        try {
                            while (isActive && _callState.value == CallState.CONNECTED) {
                                val read = recorder.read(sendBuffer, 0, sendBuffer.size)
                                if (read > 0 && !_isMicMuted.value) {
                                    val datagram = DatagramPacket(sendBuffer, read, broadcastIp, AUDIO_PORT)
                                    audioSocket?.send(datagram)
                                }
                            }
                        } catch (_: Exception) {
                        } finally {
                            try { recorder.stop() } catch (_: Exception) {}
                            recorder.release()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in audio streaming session", e)
            }
        }
    }

    private fun cleanupCall() {
        _callState.value = CallState.ENDED
        durationTimerJob?.cancel()
        audioRecordJob?.cancel()
        audioPlayJob?.cancel()

        try {
            audioSocket?.close()
        } catch (_: Exception) {}
        audioSocket = null

        scope.launch {
            delay(1200)
            _callState.value = CallState.IDLE
            _remotePeerId.value = null
            _remotePeerName.value = ""
            _callDurationSec.value = 0
            _showWifiPrompt.value = false
        }
    }
}
