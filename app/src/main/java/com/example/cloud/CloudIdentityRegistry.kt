package com.example.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.mesh.MeshIdentityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
                        "El identificador '$fullId' ya está registrado por otro dispositivo. Elige otro nombre de usuario."
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
}
