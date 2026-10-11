package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.DeliveryStatus

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packetId: String,
    val conversationId: String,
    val senderNodeId: String,
    val senderDisplayName: String,
    val recipientNodeId: String,
    val isOutgoing: Boolean,
    val text: String?,
    val audioFilePath: String?,
    val audioDurationSec: Int = 0,
    val timestamp: Long,
    val status: DeliveryStatus,
    val hopCount: Int = 0,
    val isEncrypted: Boolean = true
)
