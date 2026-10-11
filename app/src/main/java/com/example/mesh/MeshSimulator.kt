package com.example.mesh

import com.example.crypto.MeshCrypto
import com.example.data.model.MeshNode
import com.example.data.model.MeshPacket
import com.example.data.model.PacketType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class MeshSimulator(
    private val identityManager: MeshIdentityManager,
    private val onSimulatedPacketForLocalNode: (MeshPacket) -> Unit,
    private val onSimulatedRelayLog: (sourceId: String, destId: String, hops: Int, ttl: Int, action: String) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    // Virtual mesh nodes
    val virtualRelay = MeshNode(
        nodeId = "E8F19B02#Relay_Echo",
        displayName = "Relay_Echo",
        macHash = "E8F19B02",
        rssi = -54,
        hopDistance = 1,
        isDirectConnected = true,
        lastSeenEpochMs = System.currentTimeMillis()
    )

    val virtualPeer = MeshNode(
        nodeId = "A4C27D90#Ghost_Viper",
        displayName = "Ghost_Viper",
        macHash = "A4C27D90",
        rssi = -78,
        hopDistance = 2, // Multi-hop: behind Relay_Echo
        isDirectConnected = false,
        lastSeenEpochMs = System.currentTimeMillis()
    )

    val virtualScout = MeshNode(
        nodeId = "3B90FE11#Shadow_Node",
        displayName = "Shadow_Node",
        macHash = "3B90FE11",
        rssi = -86,
        hopDistance = 3,
        isDirectConnected = false,
        lastSeenEpochMs = System.currentTimeMillis()
    )

    fun getVirtualNodes(): List<MeshNode> = listOf(virtualRelay, virtualPeer, virtualScout)

    /**
     * Intercepts outgoing packets and simulates multi-hop store-and-forward relay
     */
    fun processOutgoingPacket(packet: MeshPacket) {
        if (!identityManager.isSimulatorEnabled) return

        scope.launch {
            delay(400) // Small radio propagation latency

            when (packet.destNodeId) {
                virtualPeer.nodeId -> {
                    // Multi-hop route: Local -> Relay_Echo (hop 1) -> Ghost_Viper (hop 2)
                    onSimulatedRelayLog(
                        virtualRelay.nodeId,
                        virtualPeer.nodeId,
                        packet.hopCount + 1,
                        (packet.ttl - 1).coerceAtLeast(0),
                        "RELAYED_BY_INTERMEDIATE_NODE"
                    )

                    delay(600) // Hop 2 propagation

                    // Ghost_Viper received packet. Now Ghost_Viper sends back an ACK or response
                    val ackPacket = MeshPacket(
                        packetId = UUID.randomUUID().toString(),
                        sourceNodeId = virtualPeer.nodeId,
                        destNodeId = identityManager.nodeId,
                        ttl = identityManager.maxTtl,
                        hopCount = 2,
                        type = PacketType.ACK,
                        encryptedPayload = packet.packetId, // References original message ID
                        iv = "",
                        senderDisplayName = virtualPeer.displayName
                    )
                    onSimulatedPacketForLocalNode(ackPacket)

                    // If it was a text or audio message, simulate automated reply after 2 seconds
                    if (packet.type == PacketType.TEXT) {
                        delay(1500)
                        simulateReplyFromViper()
                    }
                }

                virtualRelay.nodeId -> {
                    // Direct 1-hop delivery to Relay_Echo
                    onSimulatedRelayLog(
                        identityManager.nodeId,
                        virtualRelay.nodeId,
                        1,
                        packet.ttl - 1,
                        "DELIVERED_DIRECT"
                    )
                    val ackPacket = MeshPacket(
                        packetId = UUID.randomUUID().toString(),
                        sourceNodeId = virtualRelay.nodeId,
                        destNodeId = identityManager.nodeId,
                        ttl = identityManager.maxTtl,
                        hopCount = 1,
                        type = PacketType.ACK,
                        encryptedPayload = packet.packetId,
                        iv = "",
                        senderDisplayName = virtualRelay.displayName
                    )
                    onSimulatedPacketForLocalNode(ackPacket)
                }

                MeshPacket.BROADCAST_DEST -> {
                    // Broadcast routed through Relay_Echo to all mesh members
                    onSimulatedRelayLog(
                        virtualRelay.nodeId,
                        MeshPacket.BROADCAST_DEST,
                        packet.hopCount + 1,
                        packet.ttl - 1,
                        "RELAYED_MESH_BROADCAST"
                    )
                }
            }
        }
    }

    private fun simulateReplyFromViper() {
        val replyText = "Copy that via mesh! Packet arrived through Relay_Echo (2 hops). Offline E2E AES encryption verified."
        val key = MeshCrypto.deriveChatKey(virtualPeer.nodeId, identityManager.nodeId)
        val (cipher, iv) = MeshCrypto.encryptString(replyText, key)

        val replyPacket = MeshPacket(
            packetId = UUID.randomUUID().toString(),
            sourceNodeId = virtualPeer.nodeId,
            destNodeId = identityManager.nodeId,
            ttl = identityManager.maxTtl,
            hopCount = 2, // Arrived through 2 hops
            type = PacketType.TEXT,
            encryptedPayload = cipher,
            iv = iv,
            senderDisplayName = virtualPeer.displayName
        )

        onSimulatedRelayLog(
            virtualPeer.nodeId,
            identityManager.nodeId,
            2,
            identityManager.maxTtl - 2,
            "FORWARDED_TO_DESTINATION"
        )

        onSimulatedPacketForLocalNode(replyPacket)
    }

    /**
     * Injects a sample broadcast message into the mesh for demonstration
     */
    fun triggerSampleBroadcast() {
        scope.launch {
            delay(1000)
            val sampleText = "[BLUECHAT BEACON] Node E8F19B02#Relay_Echo active. Repeating packets on 2.4GHz RFCOMM."
            val key = MeshCrypto.getBroadcastKey()
            val (cipher, iv) = MeshCrypto.encryptString(sampleText, key)

            val packet = MeshPacket(
                packetId = UUID.randomUUID().toString(),
                sourceNodeId = virtualRelay.nodeId,
                destNodeId = MeshPacket.BROADCAST_DEST,
                ttl = identityManager.maxTtl,
                hopCount = 1,
                type = PacketType.TEXT,
                encryptedPayload = cipher,
                iv = iv,
                senderDisplayName = virtualRelay.displayName
            )

            onSimulatedPacketForLocalNode(packet)
        }
    }
}
