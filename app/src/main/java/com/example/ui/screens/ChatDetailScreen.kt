package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.local.entities.MessageEntity
import com.example.data.model.MeshNode
import com.example.data.model.MeshPacket
import com.example.ui.components.HardwareHashBadge
import com.example.ui.components.VoiceNoteBubble
import com.example.ui.theme.BlueChatAmber
import com.example.ui.theme.BlueChatGreen
import com.example.ui.theme.BlueChatPrimary
import com.example.ui.theme.BlueChatRed
import com.example.ui.viewmodel.MeshViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatDetailScreen(
    conversationId: String,
    peerNode: MeshNode?,
    viewModel: MeshViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Multi-message selection mode
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedMessageIds by remember { mutableStateOf(setOf<Long>()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    val emergencyChatId by viewModel.emergencyInternetChatId.collectAsState()
    val isEmergencyModeActive = (emergencyChatId == conversationId)

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedMessageIds = emptySet()
    }
    BackHandler(enabled = !isSelectionMode) { onBack() }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current

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

    // Single message context menu
    var selectedMessageForOptions by remember { mutableStateOf<MessageEntity?>(null) }

    // Permission launcher for microphone recording
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.startVoiceRecording()
        }
    }

    fun handleMicClick() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
            if (isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedMessageIds.size} seleccionados",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                isSelectionMode = false
                                selectedMessageIds = emptySet()
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancelar")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                selectedMessageIds = messages.map { it.id }.toSet()
                            }
                        ) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Seleccionar todos")
                        }
                        IconButton(
                            onClick = {
                                if (selectedMessageIds.isNotEmpty()) {
                                    showBatchDeleteDialog = true
                                }
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = BlueChatRed)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .combinedClickable(
                                    onClick = {
                                        if (!isBroadcast) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            aliasInput = existingContact?.customAlias ?: originalName
                                            showEditContactDialog = true
                                        }
                                    }
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isBroadcast) BlueChatPrimary else if (existingContact != null) BlueChatGreen else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initials,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isBroadcast || existingContact != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 16.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isEmergencyModeActive -> BlueChatRed
                                                    peerNode?.isDirectConnected == true -> BlueChatGreen
                                                    existingContact != null -> BlueChatGreen
                                                    else -> BlueChatAmber
                                                }
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = when {
                                            isEmergencyModeActive -> "Emergencia por Internet"
                                            isBroadcast -> "Malla Local • Canal Abierto"
                                            peerNode?.isDirectConnected == true -> "En línea (Local) • Malla Directa"
                                            existingContact != null -> "Contacto Guardado • E2EE"
                                            else -> "Dispositivo Cercano"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = if (isEmergencyModeActive) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isEmergencyModeActive) BlueChatRed else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onBack()
                            },
                            modifier = Modifier.testTag("chat_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        if (!isBroadcast) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.startAudioCall(conversationId, title)
                                },
                                modifier = Modifier.testTag("audio_call_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Llamada de audio",
                                    tint = BlueChatPrimary
                                )
                            }
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.startVideoCall(conversationId, title)
                                },
                                modifier = Modifier.testTag("video_call_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = "Llamada de video",
                                    tint = BlueChatPrimary
                                )
                            }
                        }

                        if (!isBroadcast && existingContact == null) {
                            TextButton(
                                onClick = {
                                    aliasInput = originalName
                                    showEditContactDialog = true
                                }
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp), tint = BlueChatPrimary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Guardar", color = BlueChatPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.showClearChatConfirmDialog.value = true
                            },
                            modifier = Modifier.testTag("clear_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Vaciar chat",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            // Messenger input bar configured to dynamically sit above the soft keyboard
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .imePadding()
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
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.cancelVoiceRecording()
                                },
                                modifier = Modifier.testTag("cancel_recording_button")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }

                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.stopAndSendVoiceRecording()
                                },
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
                                    viewModel.shareTextViaQuickShare(context, inputText)
                                },
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .testTag("quick_share_input_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Quick Share",
                                    tint = BlueChatPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                                onClick = {
                                    viewModel.shareApkFile(context)
                                },
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .testTag("quick_share_peer_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Quick Share APK / Enlace",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

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
                        text = "Los mensajes viajan de teléfono a teléfono mediante la red Bluetooth con cifrado AES-GCM.",
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
                    val isSelected = selectedMessageIds.contains(message.id)
                    ChatMessageItem(
                        message = message,
                        playerState = playerState,
                        isSelectionMode = isSelectionMode,
                        isSelected = isSelected,
                        onToggleSelect = {
                            selectedMessageIds = if (isSelected) {
                                selectedMessageIds - message.id
                            } else {
                                selectedMessageIds + message.id
                            }
                            if (selectedMessageIds.isEmpty()) {
                                isSelectionMode = false
                            }
                        },
                        onPlayAudio = { path -> viewModel.playAudio(path) },
                        onMessageLongClick = { msg ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (isSelectionMode) {
                                selectedMessageIds = if (selectedMessageIds.contains(msg.id)) {
                                    selectedMessageIds - msg.id
                                } else {
                                    selectedMessageIds + msg.id
                                }
                                if (selectedMessageIds.isEmpty()) {
                                    isSelectionMode = false
                                }
                            } else {
                                selectedMessageForOptions = msg
                            }
                        }
                    )
                }
            }
        }
    }

    // Message options dialog: Delete for me, Delete for everyone, Select, Copy
    selectedMessageForOptions?.let { msg ->
        val isDeletedText = msg.text?.contains("eliminado", ignoreCase = true) == true
        AlertDialog(
            onDismissRequest = { selectedMessageForOptions = null },
            title = { Text("Opciones del mensaje", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (msg.text != null && !isDeletedText) {
                        TextButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(msg.text))
                                Toast.makeText(context, "Texto copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                selectedMessageForOptions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = BlueChatPrimary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Copiar texto", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    if (!isDeletedText && (msg.text != null || msg.audioFilePath != null)) {
                        TextButton(
                            onClick = {
                                if (msg.audioFilePath != null) {
                                    viewModel.shareVoiceNote(context, msg.audioFilePath)
                                } else if (msg.text != null) {
                                    viewModel.shareTextViaQuickShare(context, msg.text)
                                }
                                selectedMessageForOptions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = BlueChatPrimary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                if (msg.audioFilePath != null) "Quick Share (Nota de voz)" else "Quick Share / Compartir",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            isSelectionMode = true
                            selectedMessageIds = setOf(msg.id)
                            selectedMessageForOptions = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.SelectAll, contentDescription = null, tint = BlueChatPrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Seleccionar mensajes", color = MaterialTheme.colorScheme.onSurface)
                    }

                    TextButton(
                        onClick = {
                            viewModel.deleteMessageForMe(msg.id)
                            selectedMessageForOptions = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = BlueChatRed)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Eliminar para mí", color = BlueChatRed)
                    }

                    if (msg.isOutgoing && !isDeletedText) {
                        TextButton(
                            onClick = {
                                viewModel.deleteMessageForEveryone(msg.packetId)
                                selectedMessageForOptions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, tint = BlueChatRed)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Eliminar para todos", color = BlueChatRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedMessageForOptions = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Batch delete confirmation dialog
    if (showBatchDeleteDialog) {
        val selectedEntities = remember(selectedMessageIds, messages) {
            messages.filter { it.id in selectedMessageIds }
        }
        val hasOutgoing = selectedEntities.any { it.isOutgoing && it.text?.contains("eliminado", ignoreCase = true) != true }

        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = BlueChatRed) },
            title = {
                Text("¿Eliminar ${selectedMessageIds.size} mensaje(s)?", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text("Puedes eliminar los mensajes seleccionados de tu almacenamiento local (\"Para mí\") o removerlos para todos los usuarios si los enviaste tú (\"Para todos\").")
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasOutgoing) {
                        Button(
                            onClick = {
                                val outgoingPacketIds = selectedEntities
                                    .filter { it.isOutgoing && it.text?.contains("eliminado", ignoreCase = true) != true }
                                    .map { it.packetId }
                                viewModel.deleteMultipleMessagesForEveryone(conversationId, outgoingPacketIds)
                                viewModel.deleteMultipleMessagesForMe(selectedMessageIds.toList())
                                isSelectionMode = false
                                selectedMessageIds = emptySet()
                                showBatchDeleteDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BlueChatRed)
                        ) {
                            Text("Para todos", fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = {
                            viewModel.deleteMultipleMessagesForMe(selectedMessageIds.toList())
                            isSelectionMode = false
                            selectedMessageIds = emptySet()
                            showBatchDeleteDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text("Para mí")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatMessageItem(
    message: MessageEntity,
    playerState: com.example.audio.PlayerState,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onPlayAudio: (String) -> Unit,
    onMessageLongClick: (MessageEntity) -> Unit = {}
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

    val isDeleted = message.text?.contains("eliminado", ignoreCase = true) == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) BlueChatPrimary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(enabled = isSelectionMode) { onToggleSelect() }
            .padding(vertical = 2.dp, horizontal = if (isSelected) 4.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            IconButton(
                onClick = onToggleSelect,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "Seleccionado" else "No seleccionado",
                    tint = if (isSelected) BlueChatPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = bubbleAlignment
        ) {
            if (!isOut) {
                val (_, name) = MeshNode.parseNodeId(message.senderNodeId)
                Text(
                    text = message.senderDisplayName.ifBlank { name },
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                )
            }

            Box(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(
                        if (isOut) {
                            if (isDeleted) BlueChatPrimary.copy(alpha = 0.5f) else BlueChatPrimary
                        } else {
                            if (isDeleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                    .combinedClickable(
                        onClick = {
                            if (isSelectionMode) {
                                onToggleSelect()
                            } else {
                                onMessageLongClick(message)
                            }
                        },
                        onLongClick = { onMessageLongClick(message) }
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column {
                    if (message.audioFilePath != null && !isDeleted) {
                        VoiceNoteBubble(
                            audioFilePath = message.audioFilePath,
                            durationSec = message.audioDurationSec,
                            isOutgoing = isOut,
                            playerState = playerState,
                            onPlayClick = { onPlayAudio(message.audioFilePath) }
                        )
                    } else if (isDeleted) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                tint = if (isOut) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Este mensaje fue eliminado",
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = FontStyle.Italic,
                                color = if (isOut) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                            )
                        }
                    } else if (message.text?.startsWith("Llamada perdida") == true) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (message.text.contains("Video")) Icons.Default.Videocam else Icons.Default.Call,
                                contentDescription = null,
                                tint = if (isOut) Color.White else BlueChatRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isOut) Color.White else BlueChatRed
                            )
                        }
                    } else if (message.text != null) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isOut) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    if (!isOut && !isDeleted) {
                        val hopLabel = when {
                            message.hopCount <= 1 -> "Direct"
                            message.hopCount == 2 -> "1 Hop"
                            else -> "${message.hopCount - 1} Hops"
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (message.hopCount <= 1) Icons.Default.NearMe else Icons.Default.Hub,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    modifier = Modifier.size(9.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = hopLabel,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Text(
                        text = formattedTime,
                        fontSize = 10.sp,
                        color = if (isOut) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )

                    if (isOut && !isDeleted) {
                        Spacer(modifier = Modifier.width(3.dp))
                        when (message.status) {
                            com.example.data.model.DeliveryStatus.DELIVERED -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Entregado",
                                    tint = Color.White.copy(alpha = 0.95f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            com.example.data.model.DeliveryStatus.SENT,
                            com.example.data.model.DeliveryStatus.RELAYED -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Enviado",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            com.example.data.model.DeliveryStatus.SENDING -> {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Enviando",
                                    tint = Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            com.example.data.model.DeliveryStatus.FAILED -> {
                                Text(
                                    text = "!",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
}
