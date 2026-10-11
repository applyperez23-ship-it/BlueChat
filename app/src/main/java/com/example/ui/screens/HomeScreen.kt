package com.example.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Hub
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MeshNode
import com.example.ui.components.HardwareHashBadge
import com.example.ui.theme.GhostCyan
import com.example.ui.viewmodel.MeshTab
import com.example.ui.viewmodel.MeshViewModel

@Composable
fun HomeScreen(
    viewModel: MeshViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.currentTab.collectAsState()
    val activeChatId by viewModel.activeChatId.collectAsState()
    val activePeer by viewModel.activeChatPeer.collectAsState()
    val showAddContactNode by viewModel.showAddContactNode.collectAsState()

    // If active chat is open, display detail screen
    if (activeChatId != null) {
        ChatDetailScreen(
            conversationId = activeChatId!!,
            peerNode = activePeer,
            viewModel = viewModel,
            onBack = { viewModel.closeChat() },
            modifier = modifier
        )
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideScreen = maxWidth > 600.dp

        if (isWideScreen) {
            // Adaptive Tablet / Landscape layout with NavigationRail
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
                            selectedIconColor = GhostCyan,
                            selectedTextColor = GhostCyan
                        ),
                        modifier = Modifier.testTag("tab_chats")
                    )
                    NavigationRailItem(
                        selected = currentTab == MeshTab.RADAR,
                        onClick = { viewModel.selectTab(MeshTab.RADAR) },
                        icon = { Icon(Icons.Default.Radar, contentDescription = "Radar") },
                        label = { Text("Radar", fontSize = 11.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = GhostCyan,
                            selectedTextColor = GhostCyan
                        ),
                        modifier = Modifier.testTag("tab_radar")
                    )
                    NavigationRailItem(
                        selected = currentTab == MeshTab.TOPOLOGY,
                        onClick = { viewModel.selectTab(MeshTab.TOPOLOGY) },
                        icon = { Icon(Icons.Default.Hub, contentDescription = "Topología") },
                        label = { Text("Topología", fontSize = 11.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = GhostCyan,
                            selectedTextColor = GhostCyan
                        ),
                        modifier = Modifier.testTag("tab_topology")
                    )
                    NavigationRailItem(
                        selected = currentTab == MeshTab.LOGS,
                        onClick = { viewModel.selectTab(MeshTab.LOGS) },
                        icon = { Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = "Monitor") },
                        label = { Text("Monitor", fontSize = 11.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = GhostCyan,
                            selectedTextColor = GhostCyan
                        ),
                        modifier = Modifier.testTag("tab_logs")
                    )
                    NavigationRailItem(
                        selected = currentTab == MeshTab.SETTINGS,
                        onClick = { viewModel.selectTab(MeshTab.SETTINGS) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Ajustes") },
                        label = { Text("Ajustes", fontSize = 11.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = GhostCyan,
                            selectedTextColor = GhostCyan
                        ),
                        modifier = Modifier.testTag("tab_settings")
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    ScreenContent(currentTab = currentTab, viewModel = viewModel)
                }
            }
        } else {
            // Mobile layout with Bottom NavigationBar
            Scaffold(
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        NavigationBarItem(
                            selected = currentTab == MeshTab.CHATS,
                            onClick = { viewModel.selectTab(MeshTab.CHATS) },
                            icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chats") },
                            label = { Text("Chats", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = GhostCyan,
                                selectedTextColor = GhostCyan,
                                indicatorColor = GhostCyan.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("tab_chats")
                        )
                        NavigationBarItem(
                            selected = currentTab == MeshTab.RADAR,
                            onClick = { viewModel.selectTab(MeshTab.RADAR) },
                            icon = { Icon(Icons.Default.Radar, contentDescription = "Radar") },
                            label = { Text("Radar", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = GhostCyan,
                                selectedTextColor = GhostCyan,
                                indicatorColor = GhostCyan.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("tab_radar")
                        )
                        NavigationBarItem(
                            selected = currentTab == MeshTab.TOPOLOGY,
                            onClick = { viewModel.selectTab(MeshTab.TOPOLOGY) },
                            icon = { Icon(Icons.Default.Hub, contentDescription = "Topología") },
                            label = { Text("Topología", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = GhostCyan,
                                selectedTextColor = GhostCyan,
                                indicatorColor = GhostCyan.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("tab_topology")
                        )
                        NavigationBarItem(
                            selected = currentTab == MeshTab.LOGS,
                            onClick = { viewModel.selectTab(MeshTab.LOGS) },
                            icon = { Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = "Monitor") },
                            label = { Text("Monitor", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = GhostCyan,
                                selectedTextColor = GhostCyan,
                                indicatorColor = GhostCyan.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("tab_logs")
                        )
                        NavigationBarItem(
                            selected = currentTab == MeshTab.SETTINGS,
                            onClick = { viewModel.selectTab(MeshTab.SETTINGS) },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Ajustes") },
                            label = { Text("Ajustes", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = GhostCyan,
                                selectedTextColor = GhostCyan,
                                indicatorColor = GhostCyan.copy(alpha = 0.15f)
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

    // Add / Edit Contact Dialog
    if (showAddContactNode != null) {
        val targetNode = showAddContactNode!!
        val (hash, originalName) = MeshNode.parseNodeId(targetNode.nodeId)
        var aliasInput by remember { mutableStateOf(targetNode.customAlias ?: targetNode.displayName) }

        AlertDialog(
            onDismissRequest = { viewModel.showAddContactNode.value = null },
            title = { Text("Guardar Contacto de Malla") },
            text = {
                Column {
                    Text(
                        text = "Asocia un alias personalizado con el identificador de hardware único para evitar colisiones de nombres.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        Text("Hardware Hash: ", fontSize = 12.sp)
                        HardwareHashBadge(hash = hash)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = aliasInput,
                        onValueChange = { aliasInput = it },
                        label = { Text("Alias personalizado") },
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
                    Text("Guardar", color = GhostCyan)
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
        MeshTab.TOPOLOGY -> TopologyScreen(viewModel = viewModel)
        MeshTab.LOGS -> RelayLogsScreen(viewModel = viewModel)
        MeshTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
    }
}
