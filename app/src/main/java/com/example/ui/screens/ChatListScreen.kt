package com.example.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.MessageEntity
import com.example.data.model.MeshNode
import com.example.data.model.MeshPacket
import com.example.ui.components.HardwareHashBadge
import com.example.ui.components.PermissionBanner
import com.example.ui.theme.GhostAmber
import com.example.ui.theme.GhostCyan
import com.example.ui.theme.GhostGreen
import com.example.ui.theme.GhostPurple
import com.example.ui.viewmodel.MeshTab
import com.example.ui.viewmodel.MeshViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: MeshViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val contacts by viewModel.contacts.collectAsState()
    val allMessages by viewModel.allMessages.collectAsState()
    val activeNodes by viewModel.activeNodes.collectAsState()
    val isBtEnabled by viewModel.isBluetoothEnabled.collectAsState()

    // Activity Result Launcher to prompt user to turn on Bluetooth
    val enableBtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.startMeshScan()
    }

    fun requestEnableBluetooth() {
        try {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBtLauncher.launch(enableBtIntent)
        } catch (_: Exception) {
            try {
                val settingsIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                context.startActivity(settingsIntent)
            } catch (_: Exception) {}
        }
    }

    // Group messages by conversation ID
    val latestMessageByConversation = remember(allMessages) {
        allMessages.groupBy { it.conversationId }
            .mapValues { entry -> entry.value.maxByOrNull { it.timestamp } }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(GhostCyan.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CellTower,
                                    contentDescription = null,
                                    tint = GhostCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "BlueChat",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        // Compact Bluetooth Status Pill in Top Bar
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isBtEnabled) GhostGreen.copy(alpha = 0.15f) else GhostAmber.copy(alpha = 0.2f)
                                )
                                .clickable {
                                    if (!isBtEnabled) requestEnableBluetooth()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isBtEnabled) GhostGreen else GhostAmber)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isBtEnabled) "Malla ON" else "Activar BT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isBtEnabled) GhostGreen else GhostAmber
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.selectTab(MeshTab.RADAR) },
                        modifier = Modifier.testTag("radar_action_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = "Radar de dispositivos",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.selectTab(MeshTab.RADAR) },
                containerColor = GhostCyan,
                contentColor = Color.Black,
                modifier = Modifier.testTag("radar_fab_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo chat o descubrir nodo")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Permission Banner (only appears if permissions are missing)
            item {
                PermissionBanner(onPermissionsUpdated = { viewModel.startMeshScan() })
            }

            // Compact Bluetooth Activation Banner (only appears if BT is disabled)
            if (!isBtEnabled) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { requestEnableBluetooth() }
                            .testTag("enable_bluetooth_card"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = GhostAmber.copy(alpha = 0.15f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BluetoothDisabled,
                                    contentDescription = null,
                                    tint = GhostAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Bluetooth desactivado",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Toca para activar y conectar con teléfonos cercanos",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            ElevatedButton(
                                onClick = { requestEnableBluetooth() },
                                colors = ButtonDefaults.elevatedButtonColors(
                                    containerColor = GhostAmber,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("turn_on_bluetooth_button")
                            ) {
                                Text("Activar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 1. PINNED ITEM: Malla General (#broadcast)
            item {
                val broadcastLatest = latestMessageByConversation[MeshPacket.BROADCAST_DEST]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.openChat(MeshPacket.BROADCAST_DEST, null) }
                        .testTag("broadcast_channel_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = GhostPurple.copy(alpha = 0.12f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(GhostPurple),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Malla General (#broadcast)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = formatTimestamp(broadcastLatest?.timestamp),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = broadcastLatest?.text
                                    ?: (if (broadcastLatest?.audioFilePath != null) "🎤 Nota de voz cifrada"
                                        else "Canal abierto para todos los nodos cercanos"),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Direct conversations list
            val directConversationIds = latestMessageByConversation.keys.filter { it != MeshPacket.BROADCAST_DEST }
            val allDirectNodeIds = (contacts.map { it.nodeId } + directConversationIds + activeNodes.keys).distinct()

            if (allDirectNodeIds.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Sin conversaciones individuales aún",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Toca el botón + para encontrar nodos cercanos.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(allDirectNodeIds, key = { it }) { targetNodeId ->
                    val contact = contacts.firstOrNull { it.nodeId == targetNodeId }
                    val activeNode = activeNodes[targetNodeId]
                    val latestMsg = latestMessageByConversation[targetNodeId]

                    val (hash, name) = MeshNode.parseNodeId(targetNodeId)
                    val displayName = contact?.customAlias ?: activeNode?.effectiveName ?: name

                    CleanConversationRow(
                        targetNodeId = targetNodeId,
                        displayName = displayName,
                        macHash = hash,
                        latestMessage = latestMsg,
                        isOnline = activeNode != null,
                        onClick = {
                            viewModel.openChat(
                                conversationId = targetNodeId,
                                peerNode = activeNode ?: MeshNode(
                                    nodeId = targetNodeId,
                                    displayName = displayName,
                                    macHash = hash,
                                    customAlias = contact?.customAlias
                                )
                            )
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun CleanConversationRow(
    targetNodeId: String,
    displayName: String,
    macHash: String,
    latestMessage: MessageEntity?,
    isOnline: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("conversation_item_${macHash}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isOnline) GhostCyan.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = if (isOnline) GhostCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        HardwareHashBadge(hash = macHash)
                    }

                    Text(
                        text = formatTimestamp(latestMessage?.timestamp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = latestMessage?.text
                        ?: (if (latestMessage?.audioFilePath != null) "🎤 Nota de voz cifrada"
                            else "Toca para abrir chat"),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatTimestamp(epochMs: Long?): String {
    if (epochMs == null || epochMs == 0L) return ""
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMs))
}
