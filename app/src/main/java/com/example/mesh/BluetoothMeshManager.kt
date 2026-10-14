package com.example.mesh

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
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
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

sealed class MeshConnectionStatus {
    object Idle : MeshConnectionStatus()
    object Connected : MeshConnectionStatus()
    data class Reconnecting(val attempt: Int, val delaySec: Int) : MeshConnectionStatus()
    data class Disconnected(val reason: String) : MeshConnectionStatus()
}

class BluetoothMeshManager(
    private val context: Context,
    private val identityManager: MeshIdentityManager,
    private val onPacketReceived: (MeshPacket) -> Unit
) {
    companion object {
        private const val TAG = "BluetoothMeshManager"
        private const val SERVICE_NAME = "BlueChatDirect"
        val MESH_UUID: UUID = UUID.fromString("9f4b21ac-82da-4f3b-b6d1-4c4e7f8a9b21")
        val BLE_SERVICE_UUID: ParcelUuid = ParcelUuid.fromString("00009F4B-0000-1000-8000-00805F9B34FB")
        private const val MAX_BACKOFF_DELAY_MS = 30_000L
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val scope = CoroutineScope(Dispatchers.IO)
    private var serverJob: Job? = null
    private var serverSocket: BluetoothServerSocket? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0

    // Active client & server connections
    private val activeSockets = ConcurrentHashMap<String, BluetoothSocket>()

    private val _discoveredNodes = MutableStateFlow<Map<String, MeshNode>>(emptyMap())
    val discoveredNodes: StateFlow<Map<String, MeshNode>> = _discoveredNodes.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(false)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectionStatus = MutableStateFlow<MeshConnectionStatus>(MeshConnectionStatus.Idle)
    val connectionStatus: StateFlow<MeshConnectionStatus> = _connectionStatus.asStateFlow()

    val isHardwareSupported: Boolean
        get() = bluetoothAdapter != null

    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var bleScanner: BluetoothLeScanner? = null

    private val btReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: android.content.Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                updateBluetoothState()
                if (_isBluetoothEnabled.value && hasBluetoothPermissions()) {
                    startMeshRadio()
                } else if (!_isBluetoothEnabled.value) {
                    _connectionStatus.value = MeshConnectionStatus.Disconnected("Bluetooth apagado")
                }
            }
        }
    }

    init {
        updateBluetoothState()
        try {
            val filter = android.content.IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.registerReceiver(
                    context,
                    btReceiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
            } else {
                context.registerReceiver(btReceiver, filter)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not register bluetooth receiver", e)
        }
    }

    fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun updateBluetoothState() {
        val enabled = bluetoothAdapter != null && bluetoothAdapter.isEnabled
        _isBluetoothEnabled.value = enabled
        if (!enabled) {
            _connectionStatus.value = MeshConnectionStatus.Disconnected("Bluetooth apagado")
        }
    }

    @SuppressLint("MissingPermission")
    fun startMeshRadio() {
        updateBluetoothState()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth not available or disabled")
            return
        }

        if (!hasBluetoothPermissions()) {
            Log.w(TAG, "Bluetooth permissions not granted")
            return
        }

        reconnectAttempts = 0
        reconnectJob?.cancel()
        _connectionStatus.value = MeshConnectionStatus.Connected

        startServerListener()
        startBleAdvertising()
        startBleScanning()
    }

    fun stopMeshRadio() {
        reconnectJob?.cancel()
        stopServerListener()
        stopBleAdvertising()
        stopBleScanning()
        closeAllSockets()
        _connectionStatus.value = MeshConnectionStatus.Idle
    }

    /**
     * Exponential backoff retry mechanism when connection or socket fails unexpectedly
     */
    private fun triggerExponentialBackoffReconnect() {
        if (!hasBluetoothPermissions() || bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            return
        }

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            reconnectAttempts++
            // Delay: 1s, 2s, 4s, 8s, 16s... up to 30s
            val backoffMs = min(1000L * (1 shl min(reconnectAttempts - 1, 5)), MAX_BACKOFF_DELAY_MS)
            val delaySec = (backoffMs / 1000).toInt()

            _connectionStatus.value = MeshConnectionStatus.Reconnecting(
                attempt = reconnectAttempts,
                delaySec = delaySec
            )

            Log.d(TAG, "Reconnecting attempt #$reconnectAttempts in ${delaySec}s...")
            delay(backoffMs)

            if (isActive && hasBluetoothPermissions() && bluetoothAdapter?.isEnabled == true) {
                // Restart server and BLE radio components
                startServerListener()
                startBleAdvertising()
                startBleScanning()

                if (activeSockets.isNotEmpty()) {
                    reconnectAttempts = 0
                    _connectionStatus.value = MeshConnectionStatus.Connected
                } else {
                    _connectionStatus.value = MeshConnectionStatus.Connected
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startServerListener() {
        if (!hasBluetoothPermissions() || bluetoothAdapter == null) return

        serverJob?.cancel()
        serverJob = scope.launch {
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, MESH_UUID)
                Log.d(TAG, "RFCOMM Server listening on UUID: $MESH_UUID")

                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    val remoteDevice = socket.remoteDevice
                    Log.d(TAG, "Accepted connection from: ${remoteDevice.address}")
                    handleConnectedSocket(socket)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server listener stopped or dropped", e)
                triggerExponentialBackoffReconnect()
            }
        }
    }

    private fun stopServerListener() {
        serverJob?.cancel()
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    private fun handleConnectedSocket(socket: BluetoothSocket) {
        val deviceId = socket.remoteDevice?.address ?: UUID.randomUUID().toString()
        activeSockets[deviceId] = socket
        reconnectAttempts = 0
        _connectionStatus.value = MeshConnectionStatus.Connected

        scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.inputStream, StandardCharsets.UTF_8))
                while (isActive && socket.isConnected) {
                    val line = reader.readLine() ?: break
                    if (line.isNotBlank()) {
                        val packet = MeshPacket.fromJson(line)
                        if (packet != null) {
                            onPacketReceived(packet)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Connection lost with $deviceId: ${e.message}")
            } finally {
                activeSockets.remove(deviceId)
                try { socket.close() } catch (_: Exception) {}
                if (activeSockets.isEmpty()) {
                    triggerExponentialBackoffReconnect()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        if (!hasBluetoothPermissions() || bluetoothAdapter == null) return

        scope.launch {
            try {
                _connectionStatus.value = MeshConnectionStatus.Reconnecting(attempt = 1, delaySec = 0)
                val socket = device.createRfcommSocketToServiceRecord(MESH_UUID)
                socket.connect()
                Log.d(TAG, "Connected to remote device: ${device.address}")
                handleConnectedSocket(socket)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to device ${device.address}", e)
                triggerExponentialBackoffReconnect()
            }
        }
    }

    fun sendPacketOverBluetooth(packet: MeshPacket): Boolean {
        val payloadLine = packet.toJson() + "\n"
        var sentAny = false

        for ((_, socket) in activeSockets) {
            try {
                if (socket.isConnected) {
                    val writer = PrintWriter(socket.outputStream, true)
                    writer.println(payloadLine)
                    sentAny = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed sending to socket", e)
            }
        }
        return sentAny
    }

    // BLE Advertising and Scanning
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(TAG, "BLE advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.w(TAG, "BLE advertising failed with code: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBleAdvertising() {
        if (!hasBluetoothPermissions() || bluetoothAdapter == null) return

        try {
            bleAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
            if (bleAdvertiser == null) return

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .build()

            val localIdBytes = identityManager.nodeId.take(16).toByteArray(StandardCharsets.UTF_8)
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(BLE_SERVICE_UUID)
                .addServiceData(BLE_SERVICE_UUID, localIdBytes)
                .build()

            bleAdvertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: Exception) {
            Log.e(TAG, "BLE advertising error", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleAdvertising() {
        try {
            bleAdvertiser?.stopAdvertising(advertiseCallback)
        } catch (_: Exception) {}
        bleAdvertiser = null
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { processScanResult(it) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { processScanResult(it) }
        }
    }

    private fun processScanResult(result: ScanResult) {
        val scanRecord = result.scanRecord ?: return
        val serviceData = scanRecord.getServiceData(BLE_SERVICE_UUID)
        val discoveredName = if (serviceData != null && serviceData.isNotEmpty()) {
            String(serviceData, StandardCharsets.UTF_8).trim()
        } else {
            result.device.name ?: "Peer_${result.device.address.takeLast(4)}"
        }

        val (hash, name) = MeshNode.parseNodeId(discoveredName)
        val fullNodeId = if (discoveredName.contains("#")) discoveredName else "$hash#$name"

        if (fullNodeId == identityManager.nodeId) return // Ignore self

        val node = MeshNode(
            nodeId = fullNodeId,
            displayName = name,
            macHash = hash,
            rssi = result.rssi,
            hopDistance = 1,
            isDirectConnected = activeSockets.containsKey(result.device.address),
            lastSeenEpochMs = System.currentTimeMillis()
        )

        val updated = _discoveredNodes.value.toMutableMap()
        updated[fullNodeId] = node
        _discoveredNodes.value = updated
    }

    @SuppressLint("MissingPermission")
    private fun startBleScanning() {
        if (!hasBluetoothPermissions() || bluetoothAdapter == null) return

        try {
            bleScanner = bluetoothAdapter.bluetoothLeScanner
            if (bleScanner == null) return

            val filter = ScanFilter.Builder()
                .setServiceUuid(BLE_SERVICE_UUID)
                .build()

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            bleScanner?.startScan(listOf(filter), settings, scanCallback)
            _isScanning.value = true
        } catch (e: Exception) {
            Log.e(TAG, "BLE scan error", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScanning() {
        try {
            bleScanner?.stopScan(scanCallback)
        } catch (_: Exception) {}
        bleScanner = null
        _isScanning.value = false
    }

    fun addManualPeerNode(node: MeshNode) {
        val updated = _discoveredNodes.value.toMutableMap()
        updated[node.nodeId] = node
        _discoveredNodes.value = updated
    }

    private fun closeAllSockets() {
        for ((_, socket) in activeSockets) {
            try { socket.close() } catch (_: Exception) {}
        }
        activeSockets.clear()
    }
}
