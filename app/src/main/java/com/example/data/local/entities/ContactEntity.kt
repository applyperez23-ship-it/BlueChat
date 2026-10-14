package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey
    val nodeId: String,
    val customAlias: String,
    val originalDisplayName: String,
    val macHash: String,
    val lastSeenEpochMs: Long = System.currentTimeMillis(),
    val isBlocked: Boolean = false,
    val notes: String = "",
    val cloudValidationStatus: String = "PENDING", // PENDING, VERIFIED, NOT_FOUND, OFFLINE
    val createdAt: Long = System.currentTimeMillis()
)
