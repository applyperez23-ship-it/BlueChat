package com.example.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.data.model.MeshPacket
import com.example.mesh.MeshIdentityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

sealed class UsernameValidationResult {
    object Available : UsernameValidationResult()
    object ClaimedByMe : UsernameValidationResult()
    data class DuplicateRejected(val reason: String) : UsernameValidationResult()
    data class OfflineOrError(val message: String) : UsernameValidationResult()
}

enum class ContactValidationState {
    PENDING,
    VERIFIED,
    NOT_FOUND,
    OFFLINE
}

class CloudIdentityRegistry(
    private val context: Context,
    private val identityManager: MeshIdentityManager,
    private val onNetworkAvailable: () -> Unit
) {
    companion object {
        private const val TAG = "CloudIdentityRegistry"
        private const val PROJECT_ID = "gen-lang-client-0330224213"
        private const val API_KEY = "AIzaSyCuQmeNvoiyd8z0c42e_neXEBIjGWamJ-o"
        private const val FIRESTORE_BASE_URL =
            "https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/(default)/documents/identities"
        private const val FIRESTORE_MESSAGES_URL =
            "https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/(default)/documents/messages_relay"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isInternetAvailable = MutableStateFlow(false)
    val isInternetAvailable: StateFlow<Boolean> = _isInternetAvailable.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        checkCurrentConnectivity()
        registerNetworkCallback()
    }

    private fun checkCurrentConnectivity() {
        val cm = connectivityManager ?: return
        val activeNetwork = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNetwork)
        val hasNet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        _isInternetAvailable.value = hasNet
    }

    private fun registerNetworkCallback() {
        try {
            val cm = connectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isInternetAvailable.value = true
                    scope.launch {
                        onNetworkAvailable()
                    }
                }

                override fun onLost(network: Network) {
                    checkCurrentConnectivity()
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback", e)
        }
    }

    private fun encodeDocId(id: String): String {
        return URLEncoder.encode(id.replace("/", "_").replace("#", "--"), StandardCharsets.UTF_8.name())
    }

    /**
     * Checks cloud backend if device_model#username is available or claimed.
     * Rejects duplicates if registered under another device's hardware hash.
     */
    suspend fun verifyAndRegisterUsername(
        deviceModel: String,
        username: String
    ): UsernameValidationResult = withContext(Dispatchers.IO) {
        if (!_isInternetAvailable.value) {
            return@withContext UsernameValidationResult.OfflineOrError("Sin conexión a internet. Cambios guardados localmente.")
        }

        val fullId = "$deviceModel#$username"
        val docId = encodeDocId(fullId)
        val url = "$FIRESTORE_BASE_URL/$docId?key=$API_KEY"

        try {
            val getRequest = Request.Builder().url(url).get().build()
            val getResponse = client.newCall(getRequest).execute()

            if (getResponse.isSuccessful) {
                val bodyStr = getResponse.body?.string() ?: ""
                val json = JSONObject(bodyStr)
                val fields = json.optJSONObject("fields")
                val ownerMac = fields?.optJSONObject("macHash")?.optString("stringValue") ?: ""

                if (ownerMac == identityManager.macHash) {
                    return@withContext UsernameValidationResult.ClaimedByMe
                } else {
                    return@withContext UsernameValidationResult.DuplicateRejected(
                        "Nombre de usuario ocupado, prueba otro."
                    )
                }
            } else if (getResponse.code == 404) {
                // Not registered yet! Register it now for this device
                val postPayload = JSONObject().apply {
                    val fieldsObj = JSONObject().apply {
                        put("nodeId", JSONObject().put("stringValue", fullId))
                        put("deviceModel", JSONObject().put("stringValue", deviceModel))
                        put("username", JSONObject().put("stringValue", username))
                        put("macHash", JSONObject().put("stringValue", identityManager.macHash))
                        put("registeredAt", JSONObject().put("integerValue", System.currentTimeMillis()))
                    }
                    put("fields", fieldsObj)
                }

                val postUrl = "$FIRESTORE_BASE_URL?documentId=$docId&key=$API_KEY"
                val body = postPayload.toString().toRequestBody("application/json".toMediaType())
                val postRequest = Request.Builder().url(postUrl).post(body).build()
                val postResponse = client.newCall(postRequest).execute()

                return@withContext if (postResponse.isSuccessful || postResponse.code == 409) {
                    UsernameValidationResult.Available
                } else {
                    UsernameValidationResult.Available
                }
            } else {
                return@withContext UsernameValidationResult.Available
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking cloud registry", e)
            return@withContext UsernameValidationResult.OfflineOrError("Error al contactar con el servicio en la nube: ${e.message}")
        }
    }

    /**
     * Checks if a contact exists in the cloud registry.
     */
    suspend fun validateContactExistence(targetNodeId: String): ContactValidationState =
        withContext(Dispatchers.IO) {
            if (!_isInternetAvailable.value) {
                return@withContext ContactValidationState.OFFLINE
            }

            val docId = encodeDocId(targetNodeId)
            val url = "$FIRESTORE_BASE_URL/$docId?key=$API_KEY"

            try {
                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    ContactValidationState.VERIFIED
                } else if (response.code == 404) {
                    ContactValidationState.NOT_FOUND
                } else {
                    ContactValidationState.PENDING
                }
            } catch (e: Exception) {
                ContactValidationState.OFFLINE
            }
        }

    /**
     * Sends an encrypted packet to the Firestore cloud relay mailbox.
     */
    fun sendPacketToCloudMailbox(packet: MeshPacket) {
        if (!_isInternetAvailable.value) return
        scope.launch(Dispatchers.IO) {
            try {
                val postPayload = JSONObject().apply {
                    val fieldsObj = JSONObject().apply {
                        put("packetId", JSONObject().put("stringValue", packet.packetId))
                        put("sourceNodeId", JSONObject().put("stringValue", packet.sourceNodeId))
                        put("destNodeId", JSONObject().put("stringValue", packet.destNodeId))
                        put("rawJson", JSONObject().put("stringValue", packet.toJson()))
                        put("timestamp", JSONObject().put("integerValue", packet.timestamp.toString()))
                    }
                    put("fields", fieldsObj)
                }

                val postUrl = "$FIRESTORE_MESSAGES_URL?documentId=${packet.packetId}&key=$API_KEY"
                val body = postPayload.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url(postUrl).post(body).build()
                val response = client.newCall(request).execute()
                Log.d(TAG, "Sent packet ${packet.packetId} to cloud mailbox: ${response.code}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed sending packet to cloud mailbox", e)
            }
        }
    }

    private var mailboxJob: Job? = null

    /**
     * Starts listening / polling the cloud mailbox for incoming packets for this node or broadcast.
     */
    fun startCloudMailboxListener(onPacketReceived: (MeshPacket) -> Unit) {
        mailboxJob?.cancel()
        mailboxJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                if (_isInternetAvailable.value) {
                    pollCloudMailbox(onPacketReceived)
                }
                delay(3000) // Poll every 3 seconds for fast cloud delivery
            }
        }
    }

    private fun pollCloudMailbox(onPacketReceived: (MeshPacket) -> Unit) {
        try {
            val url = "$FIRESTORE_MESSAGES_URL?pageSize=50&key=$API_KEY"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return

            val body = response.body?.string() ?: return
            val json = JSONObject(body)
            val documents = json.optJSONArray("documents") ?: return

            val localId = identityManager.nodeId

            for (i in 0 until documents.length()) {
                val doc = documents.getJSONObject(i)
                val fields = doc.optJSONObject("fields") ?: continue
                val packetId = fields.optJSONObject("packetId")?.optString("stringValue") ?: ""
                val sourceNodeId = fields.optJSONObject("sourceNodeId")?.optString("stringValue") ?: ""
                val destNodeId = fields.optJSONObject("destNodeId")?.optString("stringValue") ?: ""
                val rawJson = fields.optJSONObject("rawJson")?.optString("stringValue") ?: ""

                // Ignore if it came from ourselves or is malformed
                if (sourceNodeId == localId || rawJson.isBlank()) continue

                // Check if it's for us or for broadcast
                if (destNodeId == localId || destNodeId == MeshPacket.BROADCAST_DEST) {
                    val packet = MeshPacket.fromJson(rawJson)
                    if (packet != null) {
                        onPacketReceived(packet)

                        // If direct 1-to-1 message for local node, delete from cloud mailbox
                        if (destNodeId == localId) {
                            deleteFromCloudMailbox(packetId)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error polling cloud mailbox", e)
        }
    }

    private fun deleteFromCloudMailbox(packetId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val deleteUrl = "$FIRESTORE_MESSAGES_URL/$packetId?key=$API_KEY"
                val deleteRequest = Request.Builder().url(deleteUrl).delete().build()
                client.newCall(deleteRequest).execute()
            } catch (_: Exception) {}
        }
    }
}
