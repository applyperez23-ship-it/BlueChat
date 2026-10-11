package com.example.mesh

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.util.UUID

class MeshIdentityManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ghostmesh_identity_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_MAC_HASH = "mac_hash"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_MAX_TTL = "max_ttl"
        private const val KEY_AUTO_RELAY = "auto_relay_enabled"
        private const val KEY_SIMULATOR_ENABLED = "simulator_enabled"
    }

    val macHash: String
        get() {
            var hash = prefs.getString(KEY_MAC_HASH, null)
            if (hash == null) {
                // Generate a persistent 8-character hex hash representing hardware node identity
                val randomSeed = UUID.randomUUID().toString() + System.currentTimeMillis()
                val md = MessageDigest.getInstance("MD5")
                val digest = md.digest(randomSeed.toByteArray())
                hash = digest.take(4).joinToString("") { "%02X".format(it) }
                prefs.edit().putString(KEY_MAC_HASH, hash).apply()
            }
            return hash
        }

    var userName: String
        get() = prefs.getString(KEY_USER_NAME, "GhostUser") ?: "GhostUser"
        set(value) {
            val sanitized = value.trim().take(20).ifBlank { "GhostUser" }
            prefs.edit().putString(KEY_USER_NAME, sanitized).apply()
        }

    /**
     * Complete unique mesh identifier in format: [Device_MAC_Hash]#[UserName]
     * e.g., 9F4B21AC#GhostUser
     */
    val nodeId: String
        get() = "$macHash#$userName"

    var maxTtl: Int
        get() = prefs.getInt(KEY_MAX_TTL, 5).coerceIn(1, 10)
        set(value) = prefs.edit().putInt(KEY_MAX_TTL, value).apply()

    var isAutoRelayEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RELAY, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_RELAY, value).apply()

    var isSimulatorEnabled: Boolean
        get() = prefs.getBoolean(KEY_SIMULATOR_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SIMULATOR_ENABLED, value).apply()
}
