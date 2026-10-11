package com.example

import com.example.crypto.MeshCrypto
import com.example.data.model.MeshNode
import com.example.data.model.MeshPacket
import com.example.data.model.PacketType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleUnitTest {
    @Test
    fun testNodeIdParsing() {
        val fullId = "9F4B21AC#GhostShadow"
        val (hash, name) = MeshNode.parseNodeId(fullId)
        assertEquals("9F4B21AC", hash)
        assertEquals("GhostShadow", name)
    }

    @Test
    fun testKeyDerivationSymmetry() {
        val nodeA = "11AA22BB#Alice"
        val nodeB = "33CC44DD#Bob"

        val keyAB = MeshCrypto.deriveChatKey(nodeA, nodeB)
        val keyBA = MeshCrypto.deriveChatKey(nodeB, nodeA)

        assertEquals(keyAB.algorithm, keyBA.algorithm)
        assertTrue(keyAB.encoded.contentEquals(keyBA.encoded))
    }

    @Test
    fun testEncryptionDecryptionRoundtrip() {
        val secretKey = MeshCrypto.getBroadcastKey()
        val originalMessage = "GhostMesh Offline AES Encryption Test"

        val (cipher, iv) = MeshCrypto.encryptString(originalMessage, secretKey)
        val decrypted = MeshCrypto.decryptString(cipher, iv, secretKey)

        assertEquals(originalMessage, decrypted)
    }

    @Test
    fun testMeshPacketSerialization() {
        val packet = MeshPacket(
            packetId = UUID.randomUUID().toString(),
            sourceNodeId = "AA11#Sender",
            destNodeId = "BB22#Receiver",
            ttl = 5,
            hopCount = 0,
            type = PacketType.TEXT,
            encryptedPayload = "CIPHER_DATA_BASE64",
            iv = "IV_BASE64",
            timestamp = 1700000000L,
            senderDisplayName = "Sender"
        )

        val json = packet.toJson()
        val deserialized = MeshPacket.fromJson(json)

        assertNotNull(deserialized)
        assertEquals(packet.packetId, deserialized!!.packetId)
        assertEquals(packet.sourceNodeId, deserialized.sourceNodeId)
        assertEquals(packet.destNodeId, deserialized.destNodeId)
        assertEquals(packet.ttl, deserialized.ttl)
        assertEquals(packet.type, deserialized.type)
        assertEquals(packet.encryptedPayload, deserialized.encryptedPayload)
    }
}
