package com.example.data.model

import org.json.JSONObject

data class MeshPacket(
    val packetId: String,
    val sourceNodeId: String,
    val destNodeId: String,
    var ttl: Int,
    var hopCount: Int,
    val type: PacketType,
    val encryptedPayload: String,
    val iv: String,
    val timestamp: Long = System.currentTimeMillis(),
    val senderDisplayName: String = ""
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("id", packetId)
        obj.put("src", sourceNodeId)
        obj.put("dst", destNodeId)
        obj.put("ttl", ttl)
        obj.put("hops", hopCount)
        obj.put("type", type.name)
        obj.put("payload", encryptedPayload)
        obj.put("iv", iv)
        obj.put("ts", timestamp)
        obj.put("name", senderDisplayName)
        return obj.toString()
    }

    companion object {
        const val BROADCAST_DEST = "*#BROADCAST"
        const val DEFAULT_MAX_TTL = 5

        fun fromJson(jsonStr: String): MeshPacket? {
            return try {
                val obj = JSONObject(jsonStr)
                MeshPacket(
                    packetId = obj.getString("id"),
                    sourceNodeId = obj.getString("src"),
                    destNodeId = obj.getString("dst"),
                    ttl = obj.getInt("ttl"),
                    hopCount = obj.optInt("hops", 0),
                    type = PacketType.valueOf(obj.getString("type")),
                    encryptedPayload = obj.getString("payload"),
                    iv = obj.getString("iv"),
                    timestamp = obj.optLong("ts", System.currentTimeMillis()),
                    senderDisplayName = obj.optString("name", "")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
