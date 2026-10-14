package com.example.mesh

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.crypto.MeshCrypto
import com.example.data.local.GhostMeshDatabase
import com.example.data.local.entities.MessageEntity
import com.example.data.local.entities.PacketLogEntity
import com.example.data.model.DeliveryStatus
import com.example.data.model.MeshNode
import com.example.data.model.MeshPacket
import com.example.data.model.PacketType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MeshEngine(
    private val context: Context,
    val identityManager: MeshIdentityManager,
    private val database: GhostMeshDatabase
) {
    companion object {
        private const val TAG = "MeshEngine"
        private const val PACKET_CACHE_EXPIRY_MS = 10 * 60 * 1000L // 10 minutes cache
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val processedPacketCache = ConcurrentHashMap<String, Long>()

    // Bluetooth manager
    lateinit var bluetoothManager: BluetoothMeshManager
        private set

    // Wi-Fi Direct manager (Dual local transport)
    lateinit var wifiDirectManager: WifiDirectMeshManager
        private set

    // Simulator
    lateinit var simulator: MeshSimulator
        private set

    // Emergency Internet Mode: strictly in-memory session override for selected chat
    val emergencyInternetChatId = MutableStateFlow<String?>(null)

    private val _activeNodesMap = MutableStateFlow<Map<String, MeshNode>>(emptyMap())
    val activeNodesMap: StateFlow<Map<String, MeshNode>> = _activeNodesMap.asStateFlow()

    private val _relayedPacketsCount = MutableStateFlow(0)
    val relayedPacketsCount: StateFlow<Int> = _relayedPacketsCount.asStateFlow()

    private val _deliveredPacketsCount = MutableStateFlow(0)
    val deliveredPacketsCount: StateFlow<Int> = _deliveredPacketsCount.asStateFlow()

    init {
        initSubsystems()
    }

    private fun initSubsystems() {
        bluetoothManager = BluetoothMeshManager(
            context = context,
            identityManager = identityManager,
            onPacketReceived = { packet -> onIncomingPacket(packet) }
        )

        wifiDirectManager = WifiDirectMeshManager(
            context = context,
            identityManager = identityManager,
            onPacketReceived = { packet -> onIncomingPacket(packet) }
        )

        simulator = MeshSimulator(
            identityManager = identityManager,
            onSimulatedPacketForLocalNode = { packet -> onIncomingPacket(packet) },
            onSimulatedRelayLog = { src, dst, hops, ttl, action ->
                recordPacketLog(
                    packetId = UUID.randomUUID().toString(),
                    src = src,
                    dst = dst,
                    type = "MULTI_HOP_FORWARD",
                    action = action,
                    hops = hops,
                    ttl = ttl,
                    size = 128
                )
            }
        )

        // Observe discovered nodes from bluetooth and Wi-Fi Direct
        scope.launch {
            bluetoothManager.discoveredNodes.collect {
                syncAllNodes()
            }
        }

        scope.launch {
            wifiDirectManager.discoveredNodes.collect {
                syncAllNodes()
            }
        }

        refreshNodesWithSimulator()
    }

    fun refreshNodesWithSimulator() {
        syncAllNodes()
    }

    private fun syncAllNodes() {
        val merged = mutableMapOf<String, MeshNode>()
        if (identityManager.isSimulatorEnabled) {
            simulator.getVirtualNodes().forEach {
                merged[it.nodeId] = it
            }
        }
        bluetoothManager.discoveredNodes.value.forEach { (id, node) ->
            merged[id] = node
        }
        wifiDirectManager.discoveredNodes.value.forEach { (id, node) ->
            merged[id] = node
        }
        _activeNodesMap.value = merged
    }

    /**
     * Core Store-and-Forward Mesh Routing Algorithm
     */
    fun onIncomingPacket(packet: MeshPacket) {
        val now = System.currentTimeMillis()

        // 1. Loop Prevention: Drop if packet was already seen
        if (processedPacketCache.containsKey(packet.packetId)) {
            recordPacketLog(
                packetId = packet.packetId,
                src = packet.sourceNodeId,
                dst = packet.destNodeId,
                type = packet.type.name,
                action = "DROPPED_DUPLICATE",
                hops = packet.hopCount,
                ttl = packet.ttl,
                size = packet.encryptedPayload.length
            )
            return
        }

        // Add to deduplication cache
        processedPacketCache[packet.packetId] = now
        cleanupOldPacketCache(now)

        val localId = identityManager.nodeId

        // 2. Destination check
        when {
            // Is it an ACK for one of our sent messages?
            packet.type == PacketType.ACK && packet.destNodeId == localId -> {
                handleAckPacket(packet)
            }

            // Is it directly for our node?
            packet.destNodeId == localId -> {
                handlePacketForLocalNode(packet)
            }

            // Is it a Broadcast packet for everyone?
            packet.destNodeId == MeshPacket.BROADCAST_DEST -> {
                handleBroadcastPacket(packet)
            }

            // Intermediate Store-and-Forward Relay (Packet is for another node!)
            else -> {
                forwardPacketAsRelay(packet)
            }
        }
    }

    private fun handleAckPacket(packet: MeshPacket) {
        val originalPacketId = packet.encryptedPayload
        scope.launch {
            database.messageDao().updateMessageStatus(originalPacketId, DeliveryStatus.DELIVERED)
            recordPacketLog(
                packetId = packet.packetId,
                src = packet.sourceNodeId,
                dst = packet.destNodeId,
                type = PacketType.ACK.name,
                action = "ACK_CONFIRMED",
                hops = packet.hopCount,
                ttl = packet.ttl,
                size = 32
            )
        }
    }

    private fun handlePacketForLocalNode(packet: MeshPacket) {
        _deliveredPacketsCount.value += 1
        recordPacketLog(
            packetId = packet.packetId,
            src = packet.sourceNodeId,
            dst = packet.destNodeId,
            type = packet.type.name,
            action = "DELIVERED_TO_ME",
            hops = packet.hopCount,
            ttl = packet.ttl,
            size = packet.encryptedPayload.length
        )

        scope.launch {
            // E2E AES decryption using derived conversation key
            val secretKey = MeshCrypto.deriveChatKey(identityManager.nodeId, packet.sourceNodeId)

            when (packet.type) {
                PacketType.DELETE_MESSAGE -> {
                    val targetPacketId = MeshCrypto.decryptString(packet.encryptedPayload, packet.iv, secretKey)
                    if (targetPacketId != null) {
                        database.messageDao().markMessageDeletedByPacketId(targetPacketId)
                    }
                }

                PacketType.TEXT -> {
                    val decryptedText = MeshCrypto.decryptString(packet.encryptedPayload, packet.iv, secretKey)
                    if (decryptedText != null) {
                        val senderName = packet.senderDisplayName.ifBlank { packet.sourceNodeId }
                        val messageEntity = MessageEntity(
                            packetId = packet.packetId,
                            conversationId = packet.sourceNodeId, // Direct chat
                            senderNodeId = packet.sourceNodeId,
                            senderDisplayName = senderName,
                            recipientNodeId = identityManager.nodeId,
                            isOutgoing = false,
                            text = decryptedText,
                            audioFilePath = null,
                            audioDurationSec = 0,
                            timestamp = packet.timestamp,
                            status = DeliveryStatus.DELIVERED,
                            hopCount = packet.hopCount,
                            isEncrypted = true
                        )
                        database.messageDao().insertMessage(messageEntity)

                        NotificationHelper.showIncomingMessageNotification(
                            context = context,
                            conversationId = packet.sourceNodeId,
                            senderName = senderName,
                            messageText = decryptedText,
                            isVoiceNote = false
                        )
                    }
                }

                PacketType.AUDIO -> {
                    val decryptedAudioBytes = MeshCrypto.decryptBytes(packet.encryptedPayload, packet.iv, secretKey)
                    if (decryptedAudioBytes != null) {
                        val audioFile = saveAudioToCache(packet.packetId, decryptedAudioBytes)
                        val senderName = packet.senderDisplayName.ifBlank { packet.sourceNodeId }
                        val messageEntity = MessageEntity(
                            packetId = packet.packetId,
                            conversationId = packet.sourceNodeId,
                            senderNodeId = packet.sourceNodeId,
                            senderDisplayName = senderName,
                            recipientNodeId = identityManager.nodeId,
                            isOutgoing = false,
                            text = null,
                            audioFilePath = audioFile.absolutePath,
                            audioDurationSec = 5,
                            timestamp = packet.timestamp,
                            status = DeliveryStatus.DELIVERED,
                            hopCount = packet.hopCount,
                            isEncrypted = true
                        )
                        database.messageDao().insertMessage(messageEntity)

                        NotificationHelper.showIncomingMessageNotification(
                            context = context,
                            conversationId = packet.sourceNodeId,
                            senderName = senderName,
                            messageText = "Nota de voz recibida",
                            isVoiceNote = true
                        )
                    }
                }
                else -> {}
            }

            // Send back ACK
            sendAckBack(packet.sourceNodeId, packet.packetId)
        }
    }

    private fun handleBroadcastPacket(packet: MeshPacket) {
        _deliveredPacketsCount.value += 1
        recordPacketLog(
            packetId = packet.packetId,
            src = packet.sourceNodeId,
            dst = packet.destNodeId,
            type = packet.type.name,
            action = "DELIVERED_BROADCAST",
            hops = packet.hopCount,
            ttl = packet.ttl,
            size = packet.encryptedPayload.length
        )

        scope.launch {
            // Decrypt with broadcast group key
            val groupKey = MeshCrypto.getBroadcastKey()
            when (packet.type) {
                PacketType.TEXT -> {
                    val decryptedText = MeshCrypto.decryptString(packet.encryptedPayload, packet.iv, groupKey)
                    if (decryptedText != null) {
                        val isFromSelf = (packet.sourceNodeId == identityManager.nodeId)
                        val senderName = packet.senderDisplayName.ifBlank { packet.sourceNodeId }
                        val messageEntity = MessageEntity(
                            packetId = packet.packetId,
                            conversationId = MeshPacket.BROADCAST_DEST,
                            senderNodeId = packet.sourceNodeId,
                            senderDisplayName = senderName,
                            recipientNodeId = MeshPacket.BROADCAST_DEST,
                            isOutgoing = isFromSelf,
                            text = decryptedText,
                            audioFilePath = null,
                            audioDurationSec = 0,
                            timestamp = packet.timestamp,
                            status = DeliveryStatus.DELIVERED,
                            hopCount = packet.hopCount,
                            isEncrypted = true
                        )
                        database.messageDao().insertMessage(messageEntity)

                        if (!isFromSelf) {
                            NotificationHelper.showIncomingMessageNotification(
                                context = context,
                                conversationId = MeshPacket.BROADCAST_DEST,
                                senderName = "Canal General ($senderName)",
                                messageText = decryptedText,
                                isVoiceNote = false
                            )
                        }
                    }
                }
                PacketType.AUDIO -> {
                    val decryptedAudioBytes = MeshCrypto.decryptBytes(packet.encryptedPayload, packet.iv, groupKey)
                    if (decryptedAudioBytes != null) {
                        val audioFile = saveAudioToCache(packet.packetId, decryptedAudioBytes)
                        val isFromSelf = (packet.sourceNodeId == identityManager.nodeId)
                        val senderName = packet.senderDisplayName.ifBlank { packet.sourceNodeId }
                        val messageEntity = MessageEntity(
                            packetId = packet.packetId,
                            conversationId = MeshPacket.BROADCAST_DEST,
                            senderNodeId = packet.sourceNodeId,
                            senderDisplayName = senderName,
                            recipientNodeId = MeshPacket.BROADCAST_DEST,
                            isOutgoing = isFromSelf,
                            text = null,
                            audioFilePath = audioFile.absolutePath,
                            audioDurationSec = 5,
                            timestamp = packet.timestamp,
                            status = DeliveryStatus.DELIVERED,
                            hopCount = packet.hopCount,
                            isEncrypted = true
                        )
                        database.messageDao().insertMessage(messageEntity)

                        if (!isFromSelf) {
                            NotificationHelper.showIncomingMessageNotification(
                                context = context,
                                conversationId = MeshPacket.BROADCAST_DEST,
                                senderName = "Canal General ($senderName)",
                                messageText = "Nota de voz recibida",
                                isVoiceNote = true
                            )
                        }
                    }
                }
                else -> {}
            }

            // Also repeat/relay the broadcast to other peers if TTL > 1 and auto-relay enabled
            if (identityManager.isAutoRelayEnabled && packet.ttl > 1 && packet.sourceNodeId != identityManager.nodeId) {
                val relayPacket = packet.copy(
                    ttl = packet.ttl - 1,
                    hopCount = packet.hopCount + 1
                )
                forwardOverRadio(relayPacket)
            }
        }
    }

    private fun forwardPacketAsRelay(packet: MeshPacket) {
        if (!identityManager.isAutoRelayEnabled) {
            recordPacketLog(
                packetId = packet.packetId,
                src = packet.sourceNodeId,
                dst = packet.destNodeId,
                type = packet.type.name,
                action = "RELAY_DISABLED_DROPPED",
                hops = packet.hopCount,
                ttl = packet.ttl,
                size = packet.encryptedPayload.length
            )
            return
        }

        if (packet.ttl <= 1) {
            recordPacketLog(
                packetId = packet.packetId,
                src = packet.sourceNodeId,
                dst = packet.destNodeId,
                type = packet.type.name,
                action = "DROPPED_TTL_EXPIRED",
                hops = packet.hopCount,
                ttl = 0,
                size = packet.encryptedPayload.length
            )
            return
        }

        // Intermediate relay: Decrement TTL, increment Hop Count
        val forwardedPacket = packet.copy(
            ttl = packet.ttl - 1,
            hopCount = packet.hopCount + 1
        )

        _relayedPacketsCount.value += 1
        recordPacketLog(
            packetId = forwardedPacket.packetId,
            src = forwardedPacket.sourceNodeId,
            dst = forwardedPacket.destNodeId,
            type = forwardedPacket.type.name,
            action = "STORE_AND_FORWARD_RELAYED",
            hops = forwardedPacket.hopCount,
            ttl = forwardedPacket.ttl,
            size = forwardedPacket.encryptedPayload.length
        )

        forwardOverRadio(forwardedPacket)
    }

    private fun forwardOverRadio(packet: MeshPacket) {
        // Dual Local Transport Layer: Bluetooth LE/Classic + Wi-Fi Direct (P2P)
        bluetoothManager.sendPacketOverBluetooth(packet)
        wifiDirectManager.broadcastPacket(packet)

        if (identityManager.isSimulatorEnabled) {
            simulator.processOutgoingPacket(packet)
        }
    }

    /**
     * Sends a new outgoing packet originated by the local node.
     * Enforces 100% offline local transport by default.
     * Emergency Internet Mode provides temporary session override only when explicitly enabled for that chat.
     */
    fun sendOutgoingPacket(
        destNodeId: String,
        type: PacketType,
        plainBytesOrText: ByteArray,
        durationSec: Int = 0,
        localAudioFilePath: String? = null,
        destDisplayName: String = ""
    ): String {
        val packetId = UUID.randomUUID().toString()
        val isBroadcast = (destNodeId == MeshPacket.BROADCAST_DEST)
        val isEmergencyActive = (emergencyInternetChatId.value == destNodeId)

        // 1. Encrypt with E2E AES
        val key = if (isBroadcast) {
            MeshCrypto.getBroadcastKey()
        } else {
            MeshCrypto.deriveChatKey(identityManager.nodeId, destNodeId)
        }

        val (cipher, iv) = MeshCrypto.encryptBytes(plainBytesOrText, key)

        val packet = MeshPacket(
            packetId = packetId,
            sourceNodeId = identityManager.nodeId,
            destNodeId = destNodeId,
            ttl = identityManager.maxTtl,
            hopCount = 0,
            type = type,
            encryptedPayload = cipher,
            iv = iv,
            timestamp = System.currentTimeMillis(),
            senderDisplayName = identityManager.userName
        )

        // Add to deduplication cache so we don't re-process our own packet
        processedPacketCache[packetId] = System.currentTimeMillis()

        // 2. Persist in Room DB as outgoing message
        scope.launch {
            val messageEntity = MessageEntity(
                packetId = packetId,
                conversationId = destNodeId,
                senderNodeId = identityManager.nodeId,
                senderDisplayName = identityManager.userName,
                recipientNodeId = destNodeId,
                isOutgoing = true,
                text = if (type == PacketType.TEXT) String(plainBytesOrText, Charsets.UTF_8) else null,
                audioFilePath = localAudioFilePath,
                audioDurationSec = durationSec,
                timestamp = packet.timestamp,
                status = DeliveryStatus.SENT,
                hopCount = 0,
                isEncrypted = true
            )
            database.messageDao().insertMessage(messageEntity)

            val actionLabel = if (isEmergencyActive) "ORIGINATED_EMERGENCY_INTERNET" else "ORIGINATED_BY_ME"
            recordPacketLog(
                packetId = packetId,
                src = identityManager.nodeId,
                dst = destNodeId,
                type = type.name,
                action = actionLabel,
                hops = 0,
                ttl = packet.ttl,
                size = cipher.length
            )

            // 3. Dispatch to dual radio layer (Bluetooth + Wi-Fi Direct) & simulator
            forwardOverRadio(packet)
        }

        return packetId
    }

    private fun sendAckBack(destNodeId: String, originalPacketId: String) {
        val ackPacket = MeshPacket(
            packetId = UUID.randomUUID().toString(),
            sourceNodeId = identityManager.nodeId,
            destNodeId = destNodeId,
            ttl = identityManager.maxTtl,
            hopCount = 0,
            type = PacketType.ACK,
            encryptedPayload = originalPacketId,
            iv = "",
            senderDisplayName = identityManager.userName
        )
        forwardOverRadio(ackPacket)
    }

    private fun saveAudioToCache(packetId: String, bytes: ByteArray): File {
        val audioDir = File(context.cacheDir, "audio_notes")
        if (!audioDir.exists()) audioDir.mkdirs()
        val file = File(audioDir, "mesh_audio_$packetId.m4a")
        FileOutputStream(file).use { it.write(bytes) }
        return file
    }

    private fun recordPacketLog(
        packetId: String,
        src: String,
        dst: String,
        type: String,
        action: String,
        hops: Int,
        ttl: Int,
        size: Int
    ) {
        scope.launch {
            val log = PacketLogEntity(
                packetId = packetId,
                sourceNodeId = src,
                destNodeId = dst,
                packetType = type,
                action = action,
                hopCount = hops,
                remainingTtl = ttl,
                payloadSize = size,
                timestamp = System.currentTimeMillis()
            )
            database.packetLogDao().insertLog(log)
        }
    }

    fun sendDeleteMessagePacket(destNodeId: String, targetPacketId: String) {
        val isBroadcast = (destNodeId == MeshPacket.BROADCAST_DEST)
        val key = if (isBroadcast) {
            MeshCrypto.getBroadcastKey()
        } else {
            MeshCrypto.deriveChatKey(identityManager.nodeId, destNodeId)
        }
        val (cipher, iv) = MeshCrypto.encryptString(targetPacketId, key)

        val packet = MeshPacket(
            packetId = UUID.randomUUID().toString(),
            sourceNodeId = identityManager.nodeId,
            destNodeId = destNodeId,
            ttl = identityManager.maxTtl,
            hopCount = 0,
            type = PacketType.DELETE_MESSAGE,
            encryptedPayload = cipher,
            iv = iv,
            timestamp = System.currentTimeMillis(),
            senderDisplayName = identityManager.userName
        )

        scope.launch {
            // Mark locally as deleted
            database.messageDao().markMessageDeletedByPacketId(targetPacketId)
            // Forward across mesh
            forwardOverRadio(packet)
        }
    }

    fun deleteMessageLocally(messageId: Long) {
        scope.launch {
            database.messageDao().deleteMessageById(messageId)
        }
    }

    fun deleteMultipleMessagesLocally(messageIds: List<Long>) {
        scope.launch {
            database.messageDao().deleteMessagesByIds(messageIds)
        }
    }

    fun deleteMultipleMessagesForEveryone(destNodeId: String, packetIds: List<String>) {
        for (pid in packetIds) {
            sendDeleteMessagePacket(destNodeId, pid)
        }
    }

    fun clearDevAndMockData() {
        scope.launch {
            // Delete all non-broadcast test messages or reset messages
            database.messageDao().deleteAllMessages()
            database.contactDao().deleteAllContacts()
            database.packetLogDao().deleteAllLogs()
            processedPacketCache.clear()
            _relayedPacketsCount.value = 0
            _deliveredPacketsCount.value = 0
            refreshNodesWithSimulator()
        }
    }

    private fun cleanupOldPacketCache(now: Long) {
        if (processedPacketCache.size > 500) {
            val iterator = processedPacketCache.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value > PACKET_CACHE_EXPIRY_MS) {
                    iterator.remove()
                }
            }
        }
    }
}
