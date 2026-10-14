package com.example.mesh

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.model.MeshNode
import com.example.data.model.MeshPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * Dual Transport Layer: Wi-Fi Direct (WifiP2pManager) for high-speed offline peer-to-peer mesh.
 * Operates alongside BluetoothMeshManager to ensure robust offline transport.
 */
class WifiDirectMeshManager(
    private val context: Context,
    private val identityManager: MeshIdentityManager,
    private val onPacketReceived: (MeshPacket) -> Unit
) {
    companion object {
        private const val TAG = "WifiDirectMeshManager"
        const val P2P_PORT = 8988
        private const val DISCOVERY_INTERVAL_MS = 25_000L
    }

    private val wifiP2pManager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null

    private val scope = CoroutineScope(Dispatchers.IO)
    private var discoveryLoopJob: Job? = null
    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null

    private val activeSockets = ConcurrentHashMap<String, Socket>()

    private val _isWifiP2pEnabled = MutableStateFlow(false)
    val isWifiP2pEnabled: StateFlow<Boolean> = _isWifiP2pEnabled.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _discoveredNodes = MutableStateFlow<Map<String, MeshNode>>(emptyMap())
    val discoveredNodes: StateFlow<Map<String, MeshNode>> = _discoveredNodes.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo.asStateFlow()

    private var receiverRegistered = false

    private val p2pReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    _isWifiP2pEnabled.value = (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
                    Log.d(TAG, "Wi-Fi Direct State Changed: ${_isWifiP2pEnabled.value}")
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    requestPeers()
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        requestConnectionInfo()
                    } else {
                        _connectionInfo.value = null
                    }
                }
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    val device = intent.getParcelableExtra<WifiP2pDevice>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                    Log.d(TAG, "This Wi-Fi Direct device: ${device?.deviceName} (${device?.deviceAddress})")
                }
            }
        }
    }

    init {
        initializeChannel()
    }

    private fun initializeChannel() {
        if (wifiP2pManager == null) {
            Log.w(TAG, "WifiP2pManager not supported on this device")
            return
        }
        try {
            channel = wifiP2pManager.initialize(context, Looper.getMainLooper()) {
                Log.w(TAG, "WifiP2p channel disconnected, re-initializing")
                initializeChannel()
            }
            registerReceiver()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WifiP2pManager", e)
        }
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(p2pReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(p2pReceiver, filter)
            }
            receiverRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register Wi-Fi Direct broadcast receiver", e)
        }
    }

    fun startWifiDirectTransport() {
        if (!hasWifiPermissions()) {
            Log.w(TAG, "Wi-Fi Direct permissions missing")
            return
        }

        startTcpServer()
        startPeriodicDiscovery()
    }

    fun stopWifiDirectTransport() {
        discoveryLoopJob?.cancel()
        _isDiscovering.value = false
        stopTcpServer()
        closeAllSockets()
    }

    @SuppressLint("MissingPermission")
    private fun startPeriodicDiscovery() {
        discoveryLoopJob?.cancel()
        discoveryLoopJob = scope.launch {
            while (isActive) {
                if (hasWifiPermissions() && channel != null && wifiP2pManager != null) {
                    try {
                        wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                            override fun onSuccess() {
                                _isDiscovering.value = true
                                Log.d(TAG, "Wi-Fi Direct peer discovery started successfully")
                            }

                            override fun onFailure(reason: Int) {
                                _isDiscovering.value = false
                                Log.w(TAG, "Wi-Fi Direct peer discovery failed code=$reason")
                            }
                        })
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception initiating peer discovery", e)
                    }
                }
                delay(DISCOVERY_INTERVAL_MS)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestPeers() {
        val mgr = wifiP2pManager ?: return
        val ch = channel ?: return
        if (!hasWifiPermissions()) return

        mgr.requestPeers(ch) { peers: WifiP2pDeviceList? ->
            val list = peers?.deviceList ?: return@requestPeers
            val updatedNodes = mutableMapOf<String, MeshNode>()

            for (dev in list) {
                val cleanName = dev.deviceName.ifBlank { "WiFiP2P_Node" }
                val macHash = dev.deviceAddress.replace(":", "").takeLast(8).uppercase()
                val nodeId = "$cleanName#$macHash"

                updatedNodes[nodeId] = MeshNode(
                    nodeId = nodeId,
                    displayName = "$cleanName (Wi-Fi Direct)",
                    macHash = macHash,
                    rssi = -60,
                    hopDistance = 1,
                    isDirectConnected = (dev.status == WifiP2pDevice.CONNECTED),
                    lastSeenEpochMs = System.currentTimeMillis()
                )
            }

            _discoveredNodes.value = updatedNodes
        }
    }

    private fun requestConnectionInfo() {
        val mgr = wifiP2pManager ?: return
        val ch = channel ?: return

        mgr.requestConnectionInfo(ch) { info: WifiP2pInfo? ->
            _connectionInfo.value = info
            if (info?.groupFormed == true) {
                if (info.isGroupOwner) {
                    Log.i(TAG, "This device is Wi-Fi Direct Group Owner. Server active on $P2P_PORT")
                } else {
                    val hostAddress = info.groupOwnerAddress?.hostAddress
                    Log.i(TAG, "Connected as client to Group Owner at $hostAddress")
                    if (hostAddress != null) {
                        connectToHost(hostAddress)
                    }
                }
            }
        }
    }

    private fun startTcpServer() {
        if (serverSocket != null && !serverSocket!!.isClosed) return

        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(P2P_PORT).apply {
                    reuseAddress = true
                }
                Log.i(TAG, "Wi-Fi Direct TCP Server running on port $P2P_PORT")

                while (isActive && serverSocket?.isClosed == false) {
                    val client = serverSocket?.accept() ?: break
                    val clientIp = client.inetAddress.hostAddress ?: "client_${client.port}"
                    activeSockets[clientIp] = client
                    launch { listenToSocket(clientIp, client) }
                }
            } catch (e: Exception) {
                if (isActive) Log.e(TAG, "Wi-Fi Direct server error", e)
            }
        }
    }

    private fun connectToHost(hostIp: String) {
        scope.launch {
            try {
                if (activeSockets.containsKey(hostIp)) return@launch
                val socket = Socket()
                socket.connect(InetSocketAddress(hostIp, P2P_PORT), 5000)
                activeSockets[hostIp] = socket
                listenToSocket(hostIp, socket)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to connect to Wi-Fi Direct host $hostIp: ${e.message}")
            }
        }
    }

    private fun listenToSocket(key: String, socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            while (scope.isActive && !socket.isClosed) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue

                val packet = MeshPacket.fromJson(line)
                if (packet != null) {
                    onPacketReceived(packet)
                }
            }
        } catch (_: Exception) {
        } finally {
            try { socket.close() } catch (_: Exception) {}
            activeSockets.remove(key)
        }
    }

    fun broadcastPacket(packet: MeshPacket) {
        if (activeSockets.isEmpty()) return
        scope.launch {
            val jsonLine = packet.toJson()
            val deadSockets = mutableListOf<String>()

            activeSockets.forEach { (key, socket) ->
                try {
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    writer.println(jsonLine)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed sending to Wi-Fi Direct socket $key", e)
                    deadSockets.add(key)
                }
            }

            deadSockets.forEach {
                try { activeSockets[it]?.close() } catch (_: Exception) {}
                activeSockets.remove(it)
            }
        }
    }

    private fun stopTcpServer() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverJob?.cancel()
        serverSocket = null
    }

    private fun closeAllSockets() {
        activeSockets.values.forEach {
            try { it.close() } catch (_: Exception) {}
        }
        activeSockets.clear()
    }

    fun hasWifiPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val nearbyDevices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        return fineLocation && nearbyDevices
    }

    fun destroy() {
        stopWifiDirectTransport()
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(p2pReceiver)
                receiverRegistered = false
            } catch (_: Exception) {}
        }
    }
}
