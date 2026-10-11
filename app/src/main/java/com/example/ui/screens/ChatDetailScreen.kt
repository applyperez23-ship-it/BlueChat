package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.MessageEntity
import com.example.data.model.MeshNode
import com.example.data.model.MeshPacket
import com.example.ui.components.HardwareHashBadge
import com.example.ui.components.HopIndicator
import com.example.ui.components.VoiceNoteBubble
import com.example.ui.theme.GhostCyan
import com.example.ui.theme.GhostGreen
import com.example.ui.theme.GhostRed
import com.example.ui.viewmodel.MeshViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    conversationId: String,
    peerNode: MeshNode?,
    viewModel: MeshViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val messages by viewModel.activeChatMessages.collectAsState()
    val isRecording by viewModel.isRecordingAudio.collectAsState()
    val recordingDuration by viewModel.recordingDurationSeconds.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val showClearDialog by viewModel.showClearChatConfirmDialog.collectAsState()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val isBroadcast = conversationId == MeshPacket.BROADCAST_DEST
    val title = if (isBroadcast) "Malla General (#broadcast)" else (peerNode?.effectiveName ?: conversationId.substringAfter("#", "Peer"))
    val (hash, _) = MeshNode.parseNodeId(conversationId)

    // Scroll to bottom on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Cifrado E2E",
                                tint = GhostGreen,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!isBroadcast) {
                                HardwareHashBadge(hash = hash)
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = if (isBroadcast) "Canal público mallado • AES grupo" else "Cifrado extremo a extremo AES-256",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.showClearChatConfirmDialog.value = true },
                        modifier = Modifier.testTag("clear_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Borrar mensajes",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            // Chat Input Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (isRecording) {
                    // Recording Audio UI
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(GhostRed)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Grabando nota: %02d:%02d".format(recordingDuration / 60, recordingDuration % 60),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = GhostRed,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row {
                            TextButton(
                                onClick = { viewModel.cancelVoiceRecording() },
                                modifier = Modifier.testTag("cancel_recording_button")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }

                            IconButton(
                                onClick = { viewModel.stopAndSendVoiceRecording() },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(GhostCyan)
                                    .testTag("send_recording_button")
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Enviar audio", tint = Color.Black)
                            }
                        }
                    }
                } else {
                    // Normal text input UI
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Mensaje cifrado...", fontSize = 14.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_input_field"),
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GhostCyan,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        if (inputText.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    viewModel.sendTextMessage(inputText)
                                    inputText = ""
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(GhostCyan)
                                    .testTag("send_text_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Enviar",
                                    tint = Color.Black
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { viewModel.startVoiceRecording() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .testTag("record_audio_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Grabar nota de voz",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = GhostCyan.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Canal de Malla Seguro",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Los mensajes saltan entre teléfonos cercanos con encriptación AES. No requiere internet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatMessageItem(
                        message = message,
                        playerState = playerState,
                        onPlayAudio = { path -> viewModel.playAudio(path) }
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showClearChatConfirmDialog.value = false },
            title = { Text("¿Vaciar esta conversación?") },
            text = { Text("Se eliminarán todos los mensajes y notas de voz almacenados localmente de este chat.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.clearActiveChat() },
                    modifier = Modifier.testTag("confirm_clear_chat_button")
                ) {
                    Text("Borrar", color = GhostRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showClearChatConfirmDialog.value = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun ChatMessageItem(
    message: MessageEntity,
    playerState: com.example.audio.PlayerState,
    onPlayAudio: (String) -> Unit
) {
    val isOut = message.isOutgoing
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormat.format(Date(message.timestamp)) }

    val bubbleAlignment = if (isOut) Alignment.End else Alignment.Start
    val bubbleShape = if (isOut) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = bubbleAlignment
    ) {
        // Sender display on incoming
        if (!isOut) {
            val (hash, name) = MeshNode.parseNodeId(message.senderNodeId)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            ) {
                Text(
                    text = message.senderDisplayName.ifBlank { name },
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GhostCyan
                )
                Spacer(modifier = Modifier.width(4.dp))
                HardwareHashBadge(hash = hash)
            }
        }

        Box(
            modifier = Modifier
                .clip(bubbleShape)
                .background(
                    if (isOut) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(10.dp)
        ) {
            Column {
                if (message.audioFilePath != null) {
                    VoiceNoteBubble(
                        audioFilePath = message.audioFilePath,
                        durationSec = message.audioDurationSec,
                        isOutgoing = isOut,
                        playerState = playerState,
                        onPlayClick = { onPlayAudio(message.audioFilePath) }
                    )
                } else if (message.text != null) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isOut) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HopIndicator(hopCount = message.hopCount, isEncrypted = message.isEncrypted)

                    Spacer(modifier = Modifier.weight(1f, fill = false))

                    Text(
                        text = formattedTime,
                        fontSize = 10.sp,
                        color = (if (isOut) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
