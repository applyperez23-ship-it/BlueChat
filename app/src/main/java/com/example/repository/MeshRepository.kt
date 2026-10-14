package com.example.repository

import android.content.Context
import com.example.audio.AudioPlayerManager
import com.example.audio.AudioRecorderManager
import com.example.audio.AudioRecordingResult
import com.example.cloud.CloudIdentityRegistry
import com.example.cloud.ContactValidationState
import com.example.data.local.GhostMeshDatabase
import com.example.data.local.entities.ContactEntity
import com.example.data.local.entities.MessageEntity
import com.example.data.local.entities.PacketLogEntity
import com.example.data.model.MeshNode
import com.example.data.model.PacketType
import com.example.mesh.MeshConnectionStatus
import com.example.mesh.MeshEngine
import com.example.mesh.MeshIdentityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

class MeshRepository(
    private val context: Context,
    val meshEngine: MeshEngine,
    val database: GhostMeshDatabase
) {
    val identityManager: MeshIdentityManager get() = meshEngine.identityManager
    val audioRecorder = AudioRecorderManager(context)
    val audioPlayer = AudioPlayerManager()

    private val scope = CoroutineScope(Dispatchers.IO)

    val cloudRegistry = CloudIdentityRegistry(
        context = context,
        identityManager = identityManager,
        onNetworkAvailable = {
            validateAllPendingContacts()
        }
    )

    val activeNodes: StateFlow<Map<String, MeshNode>> get() = meshEngine.activeNodesMap
    val isBluetoothEnabled: StateFlow<Boolean> get() = meshEngine.bluetoothManager.isBluetoothEnabled
    val isBluetoothSupported: Boolean get() = meshEngine.bluetoothManager.isHardwareSupported
    val isScanning: StateFlow<Boolean> get() = meshEngine.bluetoothManager.isScanning
    val connectionStatus: StateFlow<MeshConnectionStatus> get() = meshEngine.bluetoothManager.connectionStatus
    val relayedCount: StateFlow<Int> get() = meshEngine.relayedPacketsCount
    val deliveredCount: StateFlow<Int> get() = meshEngine.deliveredPacketsCount

    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>> {
        return database.messageDao().getMessagesForConversation(conversationId)
    }

    fun getAllMessagesFlow(): Flow<List<MessageEntity>> {
        return database.messageDao().getAllMessagesFlow()
    }

    fun getAllContactsFlow(): Flow<List<ContactEntity>> {
        return database.contactDao().getAllContactsFlow()
    }

    fun getPacketLogsFlow(): Flow<List<PacketLogEntity>> {
        return database.packetLogDao().getRecentLogsFlow()
    }

    suspend fun saveOrUpdateContact(
        nodeId: String,
        customAlias: String,
        originalDisplayName: String,
        macHash: String,
        status: String = "PENDING"
    ) = withContext(Dispatchers.IO) {
        val contact = ContactEntity(
            nodeId = nodeId,
            customAlias = customAlias.trim().ifBlank { originalDisplayName },
            originalDisplayName = originalDisplayName,
            macHash = macHash,
            cloudValidationStatus = status
        )
        database.contactDao().insertOrUpdateContact(contact)

        // Asynchronously check cloud if online
        if (cloudRegistry.isInternetAvailable.value) {
            val cloudState = cloudRegistry.validateContactExistence(nodeId)
            database.contactDao().updateValidationStatus(nodeId, cloudState.name)
        }
    }

    fun validateAllPendingContacts() {
        scope.launch {
            val contacts = database.contactDao().getAllContactsList()
            for (contact in contacts) {
                if (contact.cloudValidationStatus == "PENDING" || contact.cloudValidationStatus == "OFFLINE") {
                    val result = cloudRegistry.validateContactExistence(contact.nodeId)
                    database.contactDao().updateValidationStatus(contact.nodeId, result.name)
                }
            }
        }
    }

    suspend fun deleteContact(nodeId: String) = withContext(Dispatchers.IO) {
        database.contactDao().deleteContact(nodeId)
    }

    suspend fun clearConversation(conversationId: String) = withContext(Dispatchers.IO) {
        database.messageDao().deleteConversation(conversationId)
    }

    fun deleteMessageLocally(messageId: Long) {
        meshEngine.deleteMessageLocally(messageId)
    }

    fun deleteMessageForEveryone(destNodeId: String, packetId: String) {
        meshEngine.sendDeleteMessagePacket(destNodeId, packetId)
    }

    fun clearDevAndMockData() {
        meshEngine.clearDevAndMockData()
    }

    suspend fun wipeEntireDatabaseSecurely() = withContext(Dispatchers.IO) {
        database.messageDao().deleteAllMessages()
        database.contactDao().deleteAllContacts()
        database.packetLogDao().deleteAllLogs()

        // Clean cached audio recordings
        val audioDir = File(context.cacheDir, "audio_notes")
        if (audioDir.exists()) {
            audioDir.listFiles()?.forEach { it.delete() }
        }
    }

    fun sendTextMessage(destNodeId: String, text: String): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return meshEngine.sendOutgoingPacket(
            destNodeId = destNodeId,
            type = PacketType.TEXT,
            plainBytesOrText = bytes
        )
    }

    suspend fun sendAudioVoiceNote(destNodeId: String, recording: AudioRecordingResult): String = withContext(Dispatchers.IO) {
        val audioBytes = FileInputStream(recording.file).use { it.readBytes() }
        meshEngine.sendOutgoingPacket(
            destNodeId = destNodeId,
            type = PacketType.AUDIO,
            plainBytesOrText = audioBytes,
            durationSec = recording.durationSeconds,
            localAudioFilePath = recording.file.absolutePath
        )
    }
}
