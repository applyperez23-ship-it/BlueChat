package com.example.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.MeshNode
import com.example.mesh.MeshForegroundService
import com.example.ui.components.HardwareHashBadge
import com.example.ui.components.NewChatDialog
import com.example.ui.theme.BlueChatPrimary
import com.example.ui.viewmodel.MeshTab
import com.example.ui.viewmodel.MeshViewModel

@Composable
fun HomeScreen(
    viewModel: MeshViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsState()
    val activeChatId by viewModel.activeChatId.collectAsState()
    val activePeer by viewModel.activeChatPeer.collectAsState()
    val showAddContactNode by viewModel.showAddContactNode.collectAsState()
    val showNewChatDialog by viewModel.showNewChatDialog.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val activeNodes by viewModel.activeNodes.collectAsState()

    // Bluetooth enable prompt launcher
    val enableBtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.startMeshScan()
    }

    // Automatic permission request on initial app start
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val hasBluetooth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            grants[Manifest.permission.BLUETOOTH_SCAN] == true ||
            grants[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else {
            grants[Manifest.permission.BLUETOOTH] == true
        }

        if (hasBluetooth) {
            viewModel.startMeshScan()
            if (MeshForegroundService.hasConnectedDevicePermissions(context)) {
                MeshForegroundService.start(context)
            }
        }

        // If Bluetooth is physically off, prompt user to enable it
        try {
            val btAdapter = (context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter
            if (btAdapter != null && !btAdapter.isEnabled) {
                val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBtLauncher.launch(enableIntent)
            }
        } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()

        // Bluetooth Permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        // Microphone for Voice Notes
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        // Notifications (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // Permissions already granted, start scan & check if BT needs turning on
            viewModel.startMeshScan()
            try {
                val btAdapter = (context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter
                if (btAdapter != null && !btAdapter.isEnabled) {
                    val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    enableBtLauncher.launch(enableIntent)
                }
            } catch (_: Exception) {}
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideScreen = maxWidth > 600.dp

        if (isWideScreen) {
            // Adaptive Tablet / Foldable layout (Canonical List-Detail Two-Panel Design)
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    NavigationRailItem(
                        selected = currentTab == MeshTab.CHATS,
                        onClick = { viewModel.selectTab(MeshTab.CHATS) },
                        icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chats") },
                        label = { Text("Chats", fontSize = 11.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = BlueChatPrimary,
                            selectedTextColor = BlueChatPrimary
                        ),
                        modifier = Modifier.testTag("tab_chats")
                    )
                    NavigationRailItem(
                        selected = currentTab == MeshTab.RADAR,
                        onClick = { viewModel.selectTab(MeshTab.RADAR) },
                        icon = { Icon(Icons.Default.Radar, contentDescription = "Cercanos") },
                        label = { Text("Cercanos", fontSize = 11.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = BlueChatPrimary,
                            selectedTextColor = BlueChatPrimary
                        ),
                        modifier = Modifier.testTag("tab_radar")
                    )
                    NavigationRailItem(
                        selected = currentTab == MeshTab.SETTINGS,
                        onClick = { viewModel.selectTab(MeshTab.SETTINGS) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Ajustes") },
                        label = { Text("Ajustes", fontSize = 11.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = BlueChatPrimary,
                            selectedTextColor = BlueChatPrimary
                        ),
                        modifier = Modifier.testTag("tab_settings")
                    )
                }

                // Left Panel: Chat List / Radar / Settings (Master list)
                Box(
                    modifier = Modifier
                        .widthIn(min = 340.dp, max = 380.dp)
                        .fillMaxHeight()
                ) {
                    ScreenContent(currentTab = currentTab, viewModel = viewModel)
                }

                // Vertical Divider between list and detail panes
                VerticalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Right Panel: Active Chat Thread or Welcome Placeholder
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    if (activeChatId != null) {
                        ChatDetailScreen(
                            conversationId = activeChatId!!,
                            peerNode = activePeer,
                            viewModel = viewModel,
                            onBack = { viewModel.closeChat() },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(BlueChatPrimary.copy(alpha = 0.12f)),
                                    contentAlignment = androidx.compose.ui.Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Chat,
                                        contentDescription = null,
                                        tint = BlueChatPrimary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Selecciona una conversación",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Elige un chat de la lista o descubre dispositivos cercanos para comunicarte por la red de malla local sin Internet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Compact screen (phones): Single-panel navigation
            if (activeChatId != null) {
                ChatDetailScreen(
                    conversationId = activeChatId!!,
                    peerNode = activePeer,
                    viewModel = viewModel,
                    onBack = { viewModel.closeChat() },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp
                        ) {
                            NavigationBarItem(
                                selected = currentTab == MeshTab.CHATS,
                                onClick = { viewModel.selectTab(MeshTab.CHATS) },
                                icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chats") },
                                label = { Text("Chats", fontSize = 12.sp, fontWeight = if (currentTab == MeshTab.CHATS) FontWeight.Bold else FontWeight.Normal) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = BlueChatPrimary,
                                    selectedTextColor = BlueChatPrimary,
                                    indicatorColor = BlueChatPrimary.copy(alpha = 0.12f)
                                ),
                                modifier = Modifier.testTag("tab_chats")
                            )
                            NavigationBarItem(
                                selected = currentTab == MeshTab.RADAR,
                                onClick = { viewModel.selectTab(MeshTab.RADAR) },
                                icon = { Icon(Icons.Default.Radar, contentDescription = "Cercanos") },
                                label = { Text("Cercanos", fontSize = 12.sp, fontWeight = if (currentTab == MeshTab.RADAR) FontWeight.Bold else FontWeight.Normal) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = BlueChatPrimary,
                                    selectedTextColor = BlueChatPrimary,
                                    indicatorColor = BlueChatPrimary.copy(alpha = 0.12f)
                                ),
                                modifier = Modifier.testTag("tab_radar")
                            )
                            NavigationBarItem(
                                selected = currentTab == MeshTab.SETTINGS,
                                onClick = { viewModel.selectTab(MeshTab.SETTINGS) },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Ajustes") },
                                label = { Text("Ajustes", fontSize = 12.sp, fontWeight = if (currentTab == MeshTab.SETTINGS) FontWeight.Bold else FontWeight.Normal) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = BlueChatPrimary,
                                    selectedTextColor = BlueChatPrimary,
                                    indicatorColor = BlueChatPrimary.copy(alpha = 0.12f)
                                ),
                                modifier = Modifier.testTag("tab_settings")
                            )
                        }
                    }
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        ScreenContent(currentTab = currentTab, viewModel = viewModel)
                    }
                }
            }
        }
    }

    // New Chat Dialog / Sheet
    if (showNewChatDialog) {
        NewChatDialog(
            contacts = contacts,
            activeNodes = activeNodes.values.toList(),
            onDismiss = { viewModel.showNewChatDialog.value = false },
            onStartChat = { userInput ->
                viewModel.startChatWithUsernameOrId(userInput)
            },
            onSelectNode = { node ->
                viewModel.openChat(node.nodeId, node)
                viewModel.showNewChatDialog.value = false
            }
        )
    }

    // Save / Edit Contact Dialog
    if (showAddContactNode != null) {
        val targetNode = showAddContactNode!!
        val (hash, originalName) = MeshNode.parseNodeId(targetNode.nodeId)
        var aliasInput by remember { mutableStateOf(targetNode.customAlias ?: targetNode.displayName) }

        AlertDialog(
            onDismissRequest = { viewModel.showAddContactNode.value = null },
            title = { Text("Guardar Contacto", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Asigna un nombre para este usuario:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        Text("ID: ", fontSize = 12.sp)
                        HardwareHashBadge(hash = hash)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = aliasInput,
                        onValueChange = { aliasInput = it },
                        label = { Text("Nombre del contacto") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("contact_alias_input")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveContact(
                            nodeId = targetNode.nodeId,
                            alias = aliasInput,
                            originalName = originalName,
                            macHash = hash
                        )
                    },
                    modifier = Modifier.testTag("save_contact_confirm_button")
                ) {
                    Text("Guardar", color = BlueChatPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showAddContactNode.value = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun ScreenContent(
    currentTab: MeshTab,
    viewModel: MeshViewModel
) {
    when (currentTab) {
        MeshTab.CHATS -> ChatListScreen(viewModel = viewModel)
        MeshTab.RADAR -> RadarScreen(viewModel = viewModel)
        MeshTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
        MeshTab.TOPOLOGY -> TopologyScreen(viewModel = viewModel)
        MeshTab.LOGS -> RelayLogsScreen(viewModel = viewModel)
    }
}
