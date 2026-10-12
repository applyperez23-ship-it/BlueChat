package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PersonAdd
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.local.entities.MessageEntity
import com.example.data.model.MeshNode
import com.example.data.model.MeshPacket
import com.example.ui.components.HardwareHashBadge
import com.example.ui.components.HopIndicator
import com.example.ui.components.VoiceNoteBubble
import com.example.ui.theme.BlueChatGreen
import com.example.ui.theme.BlueChatPrimary
import com.example.ui.theme.BlueChatRed
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
    val context = LocalContext.current

    val messages by viewModel.activeChatMessages.collectAsState()
    val isRecording by viewModel.isRecordingAudio.collectAsState()
    val recordingDuration by viewModel.recordingDurationSeconds.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val showClearDialog by viewModel.showClearChatConfirmDialog.collectAsState()
    val contacts by viewModel.contacts.collectAsState()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val isBroadcast = conversationId == MeshPacket.BROADCAST_DEST
    val existingContact = remember(conversationId, contacts) {
        contacts.firstOrNull { it.nodeId == conversationId }
    }

    val (hash, originalName) = remember(conversationId) { MeshNode.parseNodeId(conversationId) }
    val title = if (isBroadcast) "Canal General" else (existingContact?.customAlias ?: peerNode?.effectiveName ?: originalName)
    val initials = title.trim().take(1).uppercase()

    var showEditContactDialog by remember { mutableStateOf(false) }
    var aliasInput by remember(title) { mutableStateOf(existingContact?.customAlias ?: peerNode?.customAlias ?: originalName) }

    // Permission launcher for microphone recording
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceRecording()
        }
    }

    fun handleMicClick() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.startVoiceRecording()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            if (!isBroadcast) {
                                aliasInput = existingContact?.customAlias ?: title
                                showEditContactDialog = true
                            }
                        }
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isBroadcast) BlueChatPrimary else MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isBroadcast) {
                                Icon(
                                    imageVector = Icons.Default.Hub,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Text(
                                    text = initials,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

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
                                    contentDescription = "Cifrado",
                                    tint = BlueChatGreen,
                                    modifier = Modifier.size(13.dp)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!isBroadcast) {
                                    HardwareHashBadge(hash = hash)
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = if (isBroadcast) "Canal público en malla" else (if (existingContact != null) "Contacto guardado" else "Toca para guardar contacto"),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = if (isBroadcast) MaterialTheme.colorScheme.onSurfaceVariant else BlueChatGreen
                                )
                            }
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
                    if (!isBroadcast) {
                        IconButton(
                            onClick = {
                                aliasInput = existingContact?.customAlias ?: title
                                showEditContactDialog = true
                            },
                            modifier = Modifier.testTag("save_or_edit_contact_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = "Guardar contacto",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.showClearChatConfirmDialog.value = true },
                        modifier = Modifier.testTag("clear_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Vaciar conversación",
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
            // Messenger input bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                if (isRecording) {
                    // Recording live state
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(BlueChatRed)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Grabando: %02d:%02d".format(recordingDuration / 60, recordingDuration % 60),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = BlueChatRed,
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
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(BlueChatPrimary)
                                    .testTag("send_recording_button")
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Enviar audio", tint = Color.White)
                            }
                        }
                    }
                } else {
                    // Normal text input & mic bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Escribe un mensaje...", fontSize = 14.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_input_field"),
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BlueChatPrimary,
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
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(BlueChatPrimary)
                                    .testTag("send_text_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Enviar",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { handleMicClick() },
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(BlueChatPrimary)
                                    .testTag("record_audio_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Nota de voz",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
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
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(BlueChatPrimary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = BlueChatPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Conversación Cifrada",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Los mensajes y audios viajan de teléfono a teléfono de forma segura mediante la red Bluetooth.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
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
                verticalArrangement = Arrangement.spacedBy(6.dp)
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

    // Save or Edit contact dialog
    if (showEditContactDialog) {
        AlertDialog(
            onDismissRequest = { showEditContactDialog = false },
            title = { Text("Guardar Contacto", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Nombre que verás para este usuario:",
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
                        label = { Text("Nombre personalizado") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveContact(
                            nodeId = conversationId,
                            alias = aliasInput.ifBlank { originalName },
                            originalName = originalName,
                            macHash = hash
                        )
                        showEditContactDialog = false
                    }
                ) {
                    Text("Guardar", color = BlueChatPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditContactDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showClearChatConfirmDialog.value = false },
            title = { Text("¿Vaciar conversación?") },
            text = { Text("Se eliminarán todos los mensajes y notas de voz de este chat.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.clearActiveChat() },
                    modifier = Modifier.testTag("confirm_clear_chat_button")
                ) {
                    Text("Vaciar", color = BlueChatRed, fontWeight = FontWeight.Bold)
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
        RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = bubbleAlignment
    ) {
        if (!isOut) {
            val (hash, name) = MeshNode.parseNodeId(message.senderNodeId)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
            ) {
                Text(
                    text = message.senderDisplayName.ifBlank { name },
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BlueChatPrimary
                )
                Spacer(modifier = Modifier.width(4.dp))
                HardwareHashBadge(hash = hash)
            }
        }

        Box(
            modifier = Modifier
                .clip(bubbleShape)
                .background(
                    if (isOut) BlueChatPrimary else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
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
                        color = if (isOut) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
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
                        color = (if (isOut) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    )

                    if (isOut) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = "Entregado",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}
