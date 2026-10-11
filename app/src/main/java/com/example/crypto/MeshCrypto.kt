package com.example.crypto

import android.os.Build
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object MeshCrypto {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val BROADCAST_GROUP_SECRET = "GHOSTMESH_OFFLINE_SECURE_CHANNEL_v1_STATIC_SALT"

    fun deriveChatKey(nodeIdA: String, nodeIdB: String): SecretKeySpec {
        val sortedNodes = listOf(nodeIdA, nodeIdB).sorted()
        val keyMaterial = "${sortedNodes[0]}:::GHOSTMESH_PEER_KEY:::${sortedNodes[1]}"
        val sha256 = MessageDigest.getInstance("SHA-256")
        val keyBytes = sha256.digest(keyMaterial.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, ALGORITHM)
    }

    fun getBroadcastKey(): SecretKeySpec {
        val sha256 = MessageDigest.getInstance("SHA-256")
        val keyBytes = sha256.digest(BROADCAST_GROUP_SECRET.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, ALGORITHM)
    }

    private fun base64Encode(bytes: ByteArray): String {
        return try {
            java.util.Base64.getEncoder().encodeToString(bytes)
        } catch (_: Throwable) {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }
    }

    private fun base64Decode(str: String): ByteArray {
        return try {
            java.util.Base64.getDecoder().decode(str)
        } catch (_: Throwable) {
            android.util.Base64.decode(str, android.util.Base64.NO_WRAP)
        }
    }

    fun encryptBytes(data: ByteArray, secretKey: SecretKeySpec): Pair<String, String> {
        val iv = ByteArray(16)
        SecureRandom().nextBytes(iv)
        val ivSpec = IvParameterSpec(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
        val encryptedBytes = cipher.doFinal(data)

        val cipherBase64 = base64Encode(encryptedBytes)
        val ivBase64 = base64Encode(iv)
        return Pair(cipherBase64, ivBase64)
    }

    fun decryptBytes(cipherBase64: String, ivBase64: String, secretKey: SecretKeySpec): ByteArray? {
        return try {
            val cipherBytes = base64Decode(cipherBase64)
            val ivBytes = base64Decode(ivBase64)
            val ivSpec = IvParameterSpec(ivBytes)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            cipher.doFinal(cipherBytes)
        } catch (e: Exception) {
            null
        }
    }

    fun encryptString(plainText: String, secretKey: SecretKeySpec): Pair<String, String> {
        return encryptBytes(plainText.toByteArray(Charsets.UTF_8), secretKey)
    }

    fun decryptString(cipherBase64: String, ivBase64: String, secretKey: SecretKeySpec): String? {
        val bytes = decryptBytes(cipherBase64, ivBase64, secretKey) ?: return null
        return String(bytes, Charsets.UTF_8)
    }
}
