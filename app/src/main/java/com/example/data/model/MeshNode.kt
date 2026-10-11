package com.example.data.model

data class MeshNode(
    val nodeId: String,
    val displayName: String,
    val macHash: String,
    val rssi: Int = -60,
    val hopDistance: Int = 1,
    val isDirectConnected: Boolean = false,
    val lastSeenEpochMs: Long = System.currentTimeMillis(),
    val isSavedContact: Boolean = false,
    val customAlias: String? = null
) {
    val effectiveName: String
        get() = customAlias?.takeIf { it.isNotBlank() } ?: displayName

    companion object {
        fun parseNodeId(fullId: String): Pair<String, String> {
            val parts = fullId.split("#", limit = 2)
            val hash = parts.getOrNull(0) ?: "UNKNOWN"
            val name = parts.getOrNull(1) ?: "Anonymous"
            return Pair(hash, name)
        }
    }
}
