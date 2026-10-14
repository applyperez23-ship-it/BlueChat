package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.PlayerState
import com.example.cloud.UsernameValidationResult
import com.example.data.local.GhostMeshDatabase
import com.example.data.local.entities.ContactEntity
import com.example.data.local.entities.MessageEntity
import com.example.data.local.entities.PacketLogEntity
import com.example.data.model.MeshNode
import com.example.data.model.MeshPacket
import com.example.mesh.MeshConnectionStatus
import com.example.mesh.MeshEngine
import com.example.mesh.MeshForegroundService
import com.example.mesh.MeshIdentityManager
import com.example.repository.MeshRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

enum class MeshTab {
    CHATS,
    RADAR,
    TOPOLOGY,
    LOGS,
    SETTINGS
}

class MeshViewModel(application: Application) : AndroidViewModel(application) {
    val repository: MeshRepository

    val identityManager: MeshIdentityManager
    private val database = GhostMeshDatabase.getInstance(application)

    init {
        val idManager = MeshIdentityManager(application)
        identityManager = idManager
        val engine = MeshEngine(application, idManager, database)
        repository = MeshRepository(application, engine, database)

        // Start foreground service only if permissions have been granted
        if (MeshForegroundService.hasConnectedDevicePermissions(application)) {
            MeshForegroundService.start(application)
        }
    }

    private val _currentTab = MutableStateFlow(MeshTab.CHATS)
    val currentTab: StateFlow<MeshTab> = _currentTab.asStateFlow()

    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatId: StateFlow<String?> = _activeChatId.asStateFlow()

    private val _activeChatPeer = MutableStateFlow<MeshNode?>(null)
    val activeChatPeer: StateFlow<MeshNode?> = _activeChatPeer.asStateFlow()

    private val _activeChatMessages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val activeChatMessages: StateFlow<List<MessageEntity>> = _activeChatMessages.asStateFlow()

    val contacts: StateFlow<List<ContactEntity>> = repository.getAllContactsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allMessages: StateFlow<List<MessageEntity>> = repository.getAllMessagesFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val packetLogs: StateFlow<List<PacketLogEntity>> = repository.getPacketLogsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val activeNodes: StateFlow<Map<String, MeshNode>> = repository.activeNodes
    val isBluetoothEnabled: StateFlow<Boolean> = repository.isBluetoothEnabled
    val isBluetoothSupported: Boolean get() = repository.isBluetoothSupported
    val isScanning: StateFlow<Boolean> = repository.isScanning
    val connectionStatus: StateFlow<MeshConnectionStatus> = repository.connectionStatus
    val isInternetAvailable: StateFlow<Boolean> = repository.cloudRegistry.isInternetAvailable

    val relayedCount: StateFlow<Int> = repository.relayedCount
    val deliveredCount: StateFlow<Int> = repository.deliveredCount

    val playerState: StateFlow<PlayerState> = repository.audioPlayer.playerState

    // Voice recording states
    private val _isRecordingAudio = MutableStateFlow(false)
    val isRecordingAudio: StateFlow<Boolean> = _isRecordingAudio.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0)
    val recordingDurationSeconds: StateFlow<Int> = _recordingDurationSeconds.asStateFlow()
    private var recordingTimerJob: Job? = null

    // Dialogs
    val showNewChatDialog = MutableStateFlow(false)
    val showAddContactNode = MutableStateFlow<MeshNode?>(null)
    val showWipeConfirmDialog = MutableStateFlow(false)
    val showClearChatConfirmDialog = MutableStateFlow(false)
    val showEditIdentityDialog = MutableStateFlow(false)
    val showApkShareDialog = MutableStateFlow(false)
    val showClearDevDataConfirmDialog = MutableStateFlow(false)

    // Current user info
    private val _userName = MutableStateFlow(identityManager.userName)
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _nodeId = MutableStateFlow(identityManager.nodeId)
    val nodeId: StateFlow<String> = _nodeId.asStateFlow()

    val deviceModel: String get() = identityManager.deviceModel

    private val _maxTtl = MutableStateFlow(identityManager.maxTtl)
    val maxTtl: StateFlow<Int> = _maxTtl.asStateFlow()

    private val _isAutoRelay = MutableStateFlow(identityManager.isAutoRelayEnabled)
    val isAutoRelay: StateFlow<Boolean> = _isAutoRelay.asStateFlow()

    private val _isSimulatorEnabled = MutableStateFlow(identityManager.isSimulatorEnabled)
    val isSimulatorEnabled: StateFlow<Boolean> = _isSimulatorEnabled.asStateFlow()

    private var activeChatJob: Job? = null

    fun selectTab(tab: MeshTab) {
        _currentTab.value = tab
    }

    fun openChat(conversationId: String, peerNode: MeshNode? = null) {
        _activeChatId.value = conversationId
        _activeChatPeer.value = peerNode

        activeChatJob?.cancel()
        activeChatJob = viewModelScope.launch {
            repository.getMessagesForConversation(conversationId).collect { msgs ->
                _activeChatMessages.value = msgs
            }
        }
    }

    fun startChatWithUsernameOrId(input: String) {
        val clean = input.trim()
        if (clean.isBlank()) return

        // 1. Check if user typed an existing contact's alias or original name
        val matchedContact = contacts.value.firstOrNull {
            it.customAlias.equals(clean, ignoreCase = true) ||
            it.originalDisplayName.equals(clean, ignoreCase = true) ||
            it.nodeId.equals(clean, ignoreCase = true)
        }
        if (matchedContact != null) {
            val node = MeshNode(
                nodeId = matchedContact.nodeId,
                displayName = matchedContact.originalDisplayName,
                macHash = matchedContact.macHash,
                customAlias = matchedContact.customAlias
            )
            openChat(matchedContact.nodeId, node)
            showNewChatDialog.value = false
            return
        }

        // 2. Check if user typed an active nearby node's name
        val matchedActiveNode = activeNodes.value.values.firstOrNull {
            it.effectiveName.equals(clean, ignoreCase = true) ||
            it.displayName.equals(clean, ignoreCase = true) ||
            it.nodeId.equals(clean, ignoreCase = true)
        }
        if (matchedActiveNode != null) {
            openChat(matchedActiveNode.nodeId, matchedActiveNode)
            showNewChatDialog.value = false
            return
        }

        // 3. New contact by device_model#username or username
        val targetNodeId = if (clean.contains("#")) {
            clean
        } else {
            "${identityManager.deviceModel}#$clean"
        }
        val (_, name) = MeshNode.parseNodeId(targetNodeId)
        val hash = MeshNode.parseNodeId(targetNodeId).first

        val newNode = MeshNode(
            nodeId = targetNodeId,
            displayName = name,
            macHash = hash
        )

        // Save pre-added contact for offline use and queue cloud check
        viewModelScope.launch {
            repository.saveOrUpdateContact(
                nodeId = targetNodeId,
                customAlias = name,
                originalDisplayName = name,
                macHash = hash,
                status = if (isInternetAvailable.value) "PENDING" else "OFFLINE"
            )
        }

        openChat(targetNodeId, newNode)
        showNewChatDialog.value = false
    }

    fun closeChat() {
        activeChatJob?.cancel()
        _activeChatId.value = null
        _activeChatPeer.value = null
        _activeChatMessages.value = emptyList()
    }

    fun sendTextMessage(text: String) {
        val chatId = _activeChatId.value ?: return
        if (text.isBlank()) return
        repository.sendTextMessage(chatId, text.trim())
    }

    fun deleteMessageForMe(messageId: Long) {
        viewModelScope.launch {
            repository.deleteMessageLocally(messageId)
        }
    }

    fun deleteMessageForEveryone(packetId: String) {
        val chatId = _activeChatId.value ?: return
        viewModelScope.launch {
            repository.deleteMessageForEveryone(chatId, packetId)
        }
    }

    fun startVoiceRecording() {
        if (_isRecordingAudio.value) return
        val started = repository.audioRecorder.startRecording()
        if (started) {
            _isRecordingAudio.value = true
            _recordingDurationSeconds.value = 0
            recordingTimerJob?.cancel()
            recordingTimerJob = viewModelScope.launch {
                while (_isRecordingAudio.value) {
                    delay(1000)
                    _recordingDurationSeconds.value += 1
                }
            }
        }
    }

    fun stopAndSendVoiceRecording() {
        if (!_isRecordingAudio.value) return
        val chatId = _activeChatId.value ?: return

        recordingTimerJob?.cancel()
        _isRecordingAudio.value = false
        val duration = _recordingDurationSeconds.value
        _recordingDurationSeconds.value = 0

        val result = repository.audioRecorder.stopRecording()
        if (result != null) {
            viewModelScope.launch {
                repository.sendAudioVoiceNote(chatId, result)
            }
        }
    }

    fun cancelVoiceRecording() {
        recordingTimerJob?.cancel()
        _isRecordingAudio.value = false
        _recordingDurationSeconds.value = 0
        repository.audioRecorder.cancelRecording()
    }

    fun playAudio(filePath: String) {
        repository.audioPlayer.play(filePath)
    }

    fun pauseAudio() {
        repository.audioPlayer.pause()
    }

    fun saveContact(nodeId: String, alias: String, originalName: String, macHash: String) {
        viewModelScope.launch {
            repository.saveOrUpdateContact(nodeId, alias, originalName, macHash)
            showAddContactNode.value = null
        }
    }

    fun deleteContact(nodeId: String) {
        viewModelScope.launch {
            repository.deleteContact(nodeId)
        }
    }

    fun clearActiveChat() {
        val chatId = _activeChatId.value ?: return
        viewModelScope.launch {
            repository.clearConversation(chatId)
            showClearChatConfirmDialog.value = false
        }
    }

    fun clearDevAndMockData() {
        viewModelScope.launch {
            repository.clearDevAndMockData()
            showClearDevDataConfirmDialog.value = false
            closeChat()
        }
    }

    fun wipeDatabaseSecurely() {
        viewModelScope.launch {
            repository.wipeEntireDatabaseSecurely()
            showWipeConfirmDialog.value = false
            closeChat()
        }
    }

    fun verifyAndSaveUsername(
        newUsername: String,
        onResult: (UsernameValidationResult) -> Unit
    ) {
        val sanitized = newUsername.trim().replace("#", "").take(20)
        if (sanitized.isBlank()) return

        viewModelScope.launch {
            val result = repository.cloudRegistry.verifyAndRegisterUsername(
                deviceModel = identityManager.deviceModel,
                username = sanitized
            )

            when (result) {
                is UsernameValidationResult.Available,
                is UsernameValidationResult.ClaimedByMe -> {
                    identityManager.userName = sanitized
                    _userName.value = identityManager.userName
                    _nodeId.value = identityManager.nodeId
                    showEditIdentityDialog.value = false
                    repository.meshEngine.refreshNodesWithSimulator()
                    onResult(result)
                }
                is UsernameValidationResult.DuplicateRejected -> {
                    onResult(result)
                }
                is UsernameValidationResult.OfflineOrError -> {
                    // Save locally when offline
                    identityManager.userName = sanitized
                    _userName.value = identityManager.userName
                    _nodeId.value = identityManager.nodeId
                    showEditIdentityDialog.value = false
                    repository.meshEngine.refreshNodesWithSimulator()
                    onResult(result)
                }
            }
        }
    }

    fun updateMaxTtl(ttl: Int) {
        identityManager.maxTtl = ttl
        _maxTtl.value = ttl
    }

    fun toggleAutoRelay(enabled: Boolean) {
        identityManager.isAutoRelayEnabled = enabled
        _isAutoRelay.value = enabled
    }

    fun toggleSimulator(enabled: Boolean) {
        identityManager.isSimulatorEnabled = enabled
        _isSimulatorEnabled.value = enabled
        repository.meshEngine.refreshNodesWithSimulator()
    }

    fun triggerBroadcastBeacon() {
        repository.meshEngine.simulator.triggerSampleBroadcast()
    }

    fun startMeshScan() {
        if (MeshForegroundService.hasConnectedDevicePermissions(getApplication())) {
            MeshForegroundService.start(getApplication())
        }
        repository.meshEngine.bluetoothManager.startMeshRadio()
    }

    /**
     * Hidden feature: Share application APK file via Android Share Sheet or Bluetooth transfer
     */
    fun shareApkFile(context: Context) {
        try {
            val sourceApk = File(context.applicationInfo.sourceDir)
            if (!sourceApk.exists()) {
                Toast.makeText(context, "Archivo APK no encontrado", Toast.LENGTH_SHORT).show()
                return
            }

            // Copy to cache for clean sharing
            val cacheApk = File(context.cacheDir, "BlueChat.apk")
            if (!cacheApk.exists() || cacheApk.length() != sourceApk.length()) {
                FileInputStream(sourceApk).use { input ->
                    FileOutputStream(cacheApk).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheApk
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, apkUri)
                putExtra(Intent.EXTRA_SUBJECT, "BlueChat APK")
                putExtra(Intent.EXTRA_TEXT, "Instala BlueChat para comunicarte por malla Bluetooth sin internet.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Compartir APK de BlueChat vía Bluetooth o aplicación").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Error al compartir APK: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
