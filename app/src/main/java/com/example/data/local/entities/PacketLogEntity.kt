package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "packet_logs")
data class PacketLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packetId: String,
    val sourceNodeId: String,
    val destNodeId: String,
    val packetType: String,
    val action: String, // CREATED, RELAYED, DELIVERED, DROPPED_TTL, DROPPED_DUPLICATE
    val hopCount: Int,
    val remainingTtl: Int,
    val payloadSize: Int,
    val timestamp: Long = System.currentTimeMillis()
)
