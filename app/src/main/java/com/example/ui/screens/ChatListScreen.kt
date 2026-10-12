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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.data.model.DeliveryStatus
import com.example.data.model.MeshNode
import com.example.data.model.MeshPacket
import com.example.ui.components.HardwareHashBadge
import com.example.ui.theme.BlueChatAmber
import com.example.ui.theme.BlueChatGreen
import com.example.ui.theme.BlueChatPrimary
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

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

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

    // Map conversation IDs to latest messages efficiently
    val latestMessageByConversation = remember(allMessages) {
        allMessages.groupBy { it.conversationId }
            .mapValues { entry -> entry.value.maxByOrNull { it.timestamp } }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar en chats...", fontSize = 14.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("search_chats_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BlueChatPrimary,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "BlueChat",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                letterSpacing = (-0.5).sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Status pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isBtEnabled) BlueChatGreen.copy(alpha = 0.15f) else BlueChatAmber.copy(alpha = 0.15f))
                                    .clickable { if (!isBtEnabled) requestEnableBluetooth() }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (isBtEnabled) BlueChatGreen else BlueChatAmber)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (isBtEnabled) "En línea" else "Activar BT",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isBtEnabled) BlueChatGreen else BlueChatAmber
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) searchQuery = ""
                        },
                        modifier = Modifier.testTag("search_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { viewModel.startMeshScan() },
                        modifier = Modifier.testTag("scan_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Escanear red",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                onClick = { viewModel.showNewChatDialog.value = true },
                containerColor = BlueChatPrimary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .size(58.dp)
                    .testTag("new_chat_fab")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "Nuevo chat",
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Bluetooth Disabled Alert Banner (Only when Bluetooth is OFF)
            if (!isBtEnabled) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BlueChatAmber.copy(alpha = 0.12f))
                            .clickable { requestEnableBluetooth() }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BluetoothDisabled,
                                contentDescription = null,
                                tint = BlueChatAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Bluetooth desactivado",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Activar",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = BlueChatPrimary
                        )
                    }
                }
            }

            // 1. PINNED CANAL GENERAL (#broadcast)
            val broadcastLatest = latestMessageByConversation[MeshPacket.BROADCAST_DEST]
            val matchesBroadcast = searchQuery.isBlank() || "canal general broadcast todos malla".contains(searchQuery, ignoreCase = true)

            if (matchesBroadcast) {
                item {
                    ChatListItem(
                        title = "Canal General",
                        subtitle = broadcastLatest?.text
                            ?: (if (broadcastLatest?.audioFilePath != null) "🎤 Nota de voz" else "Canal abierto para todos los dispositivos"),
                        time = formatTimestamp(broadcastLatest?.timestamp),
                        isBroadcast = true,
                        badgeColor = BlueChatPrimary,
                        initials = "#",
                        deliveryStatus = broadcastLatest?.status,
                        isOutgoing = broadcastLatest?.isOutgoing ?: false,
                        onClick = { viewModel.openChat(MeshPacket.BROADCAST_DEST, null) }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                        modifier = Modifier.padding(start = 76.dp)
                    )
                }
            }

            // 2. DIRECT CONVERSATIONS LIST
            val directConversationIds = latestMessageByConversation.keys.filter { it != MeshPacket.BROADCAST_DEST }
            val allDirectNodeIds = (contacts.map { it.nodeId } + directConversationIds + activeNodes.keys).distinct()

            val filteredNodeIds = allDirectNodeIds.filter { targetNodeId ->
                if (searchQuery.isBlank()) true
                else {
                    val contact = contacts.firstOrNull { it.nodeId == targetNodeId }
                    val activeNode = activeNodes[targetNodeId]
                    val (_, name) = MeshNode.parseNodeId(targetNodeId)
                    val display = contact?.customAlias ?: activeNode?.effectiveName ?: name
                    display.contains(searchQuery, ignoreCase = true) || targetNodeId.contains(searchQuery, ignoreCase = true)
                }
            }

            if (filteredNodeIds.isEmpty() && matchesBroadcast) {
                // If there are no individual chats yet, show a clean invitation card
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp, horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(BlueChatPrimary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    tint = BlueChatPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Sin conversaciones individuales",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Escribe un nombre de usuario o elige un contacto para comenzar a chatear.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.showNewChatDialog.value = true },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BlueChatPrimary,
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Escribir a un contacto")
                            }
                        }
                    }
                }
            } else if (filteredNodeIds.isEmpty() && !matchesBroadcast) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No se encontraron resultados",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredNodeIds, key = { it }) { targetNodeId ->
                    val contact = contacts.firstOrNull { it.nodeId == targetNodeId }
                    val activeNode = activeNodes[targetNodeId]
                    val latestMsg = latestMessageByConversation[targetNodeId]

                    val (hash, name) = MeshNode.parseNodeId(targetNodeId)
                    val displayName = contact?.customAlias ?: activeNode?.effectiveName ?: name

                    val isOnline = activeNode != null
                    val initials = displayName.trim().take(1).uppercase()

                    ChatListItem(
                        title = displayName,
                        subtitle = latestMsg?.text
                            ?: (if (latestMsg?.audioFilePath != null) "🎤 Nota de voz" else "Toca para abrir la conversación"),
                        time = formatTimestamp(latestMsg?.timestamp),
                        isBroadcast = false,
                        badgeColor = if (isOnline) BlueChatGreen else MaterialTheme.colorScheme.surfaceVariant,
                        initials = initials,
                        hash = hash,
                        deliveryStatus = latestMsg?.status,
                        isOutgoing = latestMsg?.isOutgoing ?: false,
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

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                        modifier = Modifier.padding(start = 76.dp)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(88.dp))
            }
        }
    }
}

@Composable
fun ChatListItem(
    title: String,
    subtitle: String,
    time: String,
    isBroadcast: Boolean,
    badgeColor: Color,
    initials: String,
    hash: String? = null,
    deliveryStatus: DeliveryStatus? = null,
    isOutgoing: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar circle
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(if (isBroadcast) BlueChatPrimary else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (isBroadcast) {
                Icon(
                    imageVector = Icons.Default.Hub,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(
                    text = initials,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Center: Title and Last message
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (hash != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        HardwareHashBadge(hash = hash)
                    }
                }

                if (time.isNotBlank()) {
                    Text(
                        text = time,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isOutgoing && deliveryStatus != null) {
                    Icon(
                        imageVector = if (deliveryStatus == DeliveryStatus.DELIVERED) Icons.Default.DoneAll else Icons.Default.Check,
                        contentDescription = null,
                        tint = if (deliveryStatus == DeliveryStatus.DELIVERED) BlueChatPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.sp,
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
