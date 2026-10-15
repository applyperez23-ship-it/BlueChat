package com.example.mesh

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
import android.content.Intent
import android.content.IntentFilter
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

/**
 * Zero-Pairing Bluetooth Mesh Manager.
 * Operates without requiring manual OS Bluetooth pairing.
 * Combines ultra-compact 16-bit BLE advertising/scanning with Insecure RFCOMM sockets.
 */
class BluetoothMeshManager(
    private val context: Context,
    private val identityManager: MeshIdentityManager,
    private val onPacketReceived: (MeshPacket) -> Unit
) {
    companion object {
        private const val TAG = "BluetoothMeshManager"
        private const val SERVICE_NAME = "BlueChatInsecure"
        val MESH_UUID: UUID = UUID.fromString("9f4b21ac-82da-4f3b-b6d1-4c4e7f8a9b21")
        // 16-bit short UUID 0xBCC0 (BlueChat Communication) to strictly fit within 31-byte BLE legacy advertising limit
        val BLE_SERVICE_UUID: ParcelUuid = ParcelUuid.fromString("0000BCC0-0000-1000-8000-00805F9B34FB")
        private const val MAX_BACKOFF_DELAY_MS = 30_000L
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val scope = CoroutineScope(Dispatchers.IO)
    private var serverJob: Job? = null
    private var serverSocket: BluetoothServerSocket? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private var batterySaverJob: Job? = null

    // Active client & server connections
    private val activeSockets = ConcurrentHashMap<String, BluetoothSocket>()
    // Node ID -> BluetoothDevice mapping for direct targeting
    private val discoveredDevices = ConcurrentHashMap<String, BluetoothDevice>()

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
        @SuppressLint("MissingPermission")
        override fun onReceive(c: Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    updateBluetoothState()
                    if (_isBluetoothEnabled.value && hasBluetoothPermissions()) {
                        startMeshRadio()
                    } else if (!_isBluetoothEnabled.value) {
                        _connectionStatus.value = MeshConnectionStatus.Disconnected("Bluetooth apagado")
                    }
                }
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let { handleDiscoveredDevice(it, intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, (-60).toShort()).toInt()) }
                }
            }
        }
    }

    init {
        updateBluetoothState()
        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_FOUND)
            }
            context.registerReceiver(btReceiver, filter)
        } catch (e: Exception) {
            Log.w(TAG, "Failed registering BT receiver", e)
        }
    }

    fun updateBluetoothState() {
        val enabled = bluetoothAdapter?.isEnabled == true
        _isBluetoothEnabled.value = enabled
        if (!enabled) {
            _connectionStatus.value = MeshConnectionStatus.Disconnected("Bluetooth desactivado")
        }
    }

    fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun startMeshRadio() {
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
        startClassicDiscovery()
    }

    fun stopMeshRadio() {
        reconnectJob?.cancel()
        batterySaverJob?.cancel()
        stopServerListener()
        stopBleAdvertising()
        stopBleScanning()
        closeAllSockets()
        _connectionStatus.value = MeshConnectionStatus.Idle
    }

    private fun triggerExponentialBackoffReconnect() {
        if (!hasBluetoothPermissions() || bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            return
        }

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            reconnectAttempts++
            val backoffMs = min(1000L * (1 shl min(reconnectAttempts - 1, 5)), MAX_BACKOFF_DELAY_MS)
            val delaySec = (backoffMs / 1000).toInt()

            _connectionStatus.value = MeshConnectionStatus.Reconnecting(
                attempt = reconnectAttempts,
                delaySec = delaySec
            )

            delay(backoffMs)

            if (isActive && hasBluetoothPermissions() && bluetoothAdapter?.isEnabled == true) {
                startServerListener()
                startBleAdvertising()
                startBleScanning()
                _connectionStatus.value = MeshConnectionStatus.Connected
            }
        }
    }

    /**
     * Insecure RFCOMM Server Listener: Listens for incoming connections without pairing!
     */
    @SuppressLint("MissingPermission")
    private fun startServerListener() {
        if (!hasBluetoothPermissions() || bluetoothAdapter == null) return

        serverJob?.cancel()
        serverJob = scope.launch {
            try {
                // Insecure RFCOMM enables connection WITHOUT OS system pairing
                serverSocket = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(SERVICE_NAME, MESH_UUID)
                Log.d(TAG, "Zero-Pairing Insecure RFCOMM Server listening on UUID: $MESH_UUID")

                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    val remoteDevice = socket.remoteDevice
                    Log.d(TAG, "Accepted zero-pairing connection from: ${remoteDevice.address}")
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
                            // Register node as active
                            val (hash, name) = MeshNode.parseNodeId(packet.sourceNodeId)
                            val node = MeshNode(
                                nodeId = packet.sourceNodeId,
                                displayName = packet.senderDisplayName.ifBlank { name },
                                macHash = hash,
                                rssi = -50,
                                hopDistance = 1,
                                isDirectConnected = true,
                                lastSeenEpochMs = System.currentTimeMillis()
                            )
                            val updated = _discoveredNodes.value.toMutableMap()
                            updated[packet.sourceNodeId] = node
                            _discoveredNodes.value = updated

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

    /**
     * Connects to a remote peer via Insecure RFCOMM (No pairing required).
     */
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        if (!hasBluetoothPermissions() || bluetoothAdapter == null) return
        val address = device.address
        if (activeSockets.containsKey(address) && activeSockets[address]?.isConnected == true) {
            return
        }

        scope.launch {
            try {
                bluetoothAdapter.cancelDiscovery() // Discovery slows down connections
                val socket = try {
                    device.createInsecureRfcommSocketToServiceRecord(MESH_UUID)
                } catch (_: Exception) {
                    device.createRfcommSocketToServiceRecord(MESH_UUID)
                }
                socket.connect()
                Log.d(TAG, "Zero-pairing connected to remote device: $address")
                handleConnectedSocket(socket)
            } catch (e: Exception) {
                Log.w(TAG, "Failed zero-pairing connection to $address: ${e.message}")
            }
        }
    }

    fun sendPacketOverBluetooth(packet: MeshPacket): Boolean {
        val payloadLine = packet.toJson() + "\n"
        var sentAny = false

        // 1. Send to all currently connected active sockets
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

        // 2. If target is a known discovered device not yet connected, trigger instant connect & send
        val targetDevice = discoveredDevices[packet.destNodeId]
        if (targetDevice != null && !activeSockets.containsKey(targetDevice.address)) {
            scope.launch {
                connectToDevice(targetDevice)
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
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build()

            // 16-bit UUID + 14-byte compact identity fits within 31-byte limit!
            val localIdBytes = identityManager.nodeId.take(14).toByteArray(StandardCharsets.UTF_8)
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

    @SuppressLint("MissingPermission")
    private fun processScanResult(result: ScanResult) {
        val scanRecord = result.scanRecord

        // Check if packet belongs to BlueChat or is any discoverable device
        val hasOurUuid = scanRecord?.serviceUuids?.contains(BLE_SERVICE_UUID) == true
        val serviceData = scanRecord?.getServiceData(BLE_SERVICE_UUID)

        val discoveredName = if (serviceData != null && serviceData.isNotEmpty()) {
            String(serviceData, StandardCharsets.UTF_8).trim()
        } else if (!scanRecord?.deviceName.isNullOrBlank()) {
            scanRecord!!.deviceName!!.trim()
        } else if (!result.device.name.isNullOrBlank()) {
            result.device.name!!.trim()
        } else if (hasOurUuid) {
            "Peer_${result.device.address.replace(":", "").takeLast(4)}"
        } else {
            "BLE_${result.device.address.replace(":", "").takeLast(4)}"
        }

        val (hash, name) = if (discoveredName.contains("#")) {
            MeshNode.parseNodeId(discoveredName)
        } else {
            val shortHash = result.device.address.replace(":", "").takeLast(6).uppercase()
            shortHash to discoveredName
        }
        val fullNodeId = "$hash#$name"

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

        discoveredDevices[fullNodeId] = result.device
        val updated = _discoveredNodes.value.toMutableMap()
        updated[fullNodeId] = node
        _discoveredNodes.value = updated

        // Zero-pairing auto-connect if socket is not active
        if (!activeSockets.containsKey(result.device.address)) {
            connectToDevice(result.device)
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleDiscoveredDevice(device: BluetoothDevice, rssi: Int) {
        val rawName = device.name?.takeIf { it.isNotBlank() }
            ?: "Dispositivo_${device.address.replace(":", "").takeLast(4)}"

        val (hash, cleanName) = if (rawName.contains("#")) {
            MeshNode.parseNodeId(rawName)
        } else {
            val shortHash = device.address.replace(":", "").takeLast(6).uppercase()
            shortHash to rawName
        }
        val fullNodeId = "$hash#$cleanName"
        if (fullNodeId == identityManager.nodeId) return

        val node = MeshNode(
            nodeId = fullNodeId,
            displayName = cleanName,
            macHash = hash,
            rssi = rssi,
            hopDistance = 1,
            isDirectConnected = activeSockets.containsKey(device.address),
            lastSeenEpochMs = System.currentTimeMillis()
        )
        discoveredDevices[fullNodeId] = device
        val updated = _discoveredNodes.value.toMutableMap()
        updated[fullNodeId] = node
        _discoveredNodes.value = updated

        // Auto connect
        if (!activeSockets.containsKey(device.address)) {
            connectToDevice(device)
        }
    }

    @SuppressLint("MissingPermission")
    fun startClassicDiscovery() {
        if (!hasBluetoothPermissions() || bluetoothAdapter == null) return
        try {
            // First register any paired / bonded devices immediately so they show right away
            bluetoothAdapter.bondedDevices?.forEach { bondedDev ->
                handleDiscoveredDevice(bondedDev, -55)
            }
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
            bluetoothAdapter.startDiscovery()
        } catch (_: Exception) {}
    }

    fun onBatterySaverToggled() {
        stopBleScanning()
        if (_isBluetoothEnabled.value && hasBluetoothPermissions()) {
            startBleScanning()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBleScanning() {
        if (!hasBluetoothPermissions() || bluetoothAdapter == null) return
        batterySaverJob?.cancel()

        if (identityManager.isBatterySaverEnabled) {
            batterySaverJob = scope.launch {
                while (isActive) {
                    performSingleScanCycle(ScanSettings.SCAN_MODE_LOW_POWER)
                    delay(10_000L)
                    performStopScan()
                    delay(170_000L)
                }
            }
        } else {
            performSingleScanCycle(ScanSettings.SCAN_MODE_LOW_LATENCY)
        }
    }

    @SuppressLint("MissingPermission")
    private fun performSingleScanCycle(scanMode: Int) {
        try {
            bleScanner = bluetoothAdapter?.bluetoothLeScanner ?: return
            val filter = ScanFilter.Builder()
                .setServiceUuid(BLE_SERVICE_UUID)
                .build()

            val settings = ScanSettings.Builder()
                .setScanMode(scanMode)
                .build()

            // Pass filter with fallback to null filter if needed
            bleScanner?.startScan(listOf(filter), settings, scanCallback)
            _isScanning.value = true
        } catch (e: Exception) {
            Log.e(TAG, "BLE scan error", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun performStopScan() {
        try {
            bleScanner?.stopScan(scanCallback)
        } catch (_: Exception) {}
        _isScanning.value = false
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScanning() {
        batterySaverJob?.cancel()
        batterySaverJob = null
        performStopScan()
        bleScanner = null
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
