package com.example.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.call.CallState
import com.example.call.CallType
import com.example.ui.theme.BlueChatGreen
import com.example.ui.theme.BlueChatPrimary
import com.example.ui.theme.BlueChatRed
import com.example.ui.viewmodel.MeshViewModel

@Composable
fun CallOverlay(
    viewModel: MeshViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val callState by viewModel.callState.collectAsState()
    val callType by viewModel.callType.collectAsState()
    val peerName by viewModel.callRemotePeerName.collectAsState()
    val isMicMuted by viewModel.isCallMicMuted.collectAsState()
    val isSpeakerOn by viewModel.isCallSpeakerOn.collectAsState()
    val isFrontCamera by viewModel.isCallFrontCamera.collectAsState()
    val durationSec by viewModel.callDurationSec.collectAsState()
    val showWifiPrompt by viewModel.showCallWifiPrompt.collectAsState()

    // Wi-Fi check alert dialog when user initiates call with Wi-Fi disabled
    if (showWifiPrompt) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCallWifiPrompt() },
            icon = {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = BlueChatRed,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Wi-Fi local requerido",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Las llamadas de audio y video P2P utilizan la red Wi-Fi local para streaming de alta fidelidad sin consumir datos. Por favor, activa el Wi-Fi para continuar.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissCallWifiPrompt()
                        try {
                            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BlueChatPrimary)
                ) {
                    Text("Abrir Wi-Fi")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCallWifiPrompt() }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (callState != CallState.IDLE) {
        Dialog(
            onDismissRequest = { /* Prevent dismiss by touch outside */ },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0F172A)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Video Call Background / Viewport
                    if (callType == CallType.VIDEO && callState == CallState.CONNECTED) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = BlueChatPrimary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(72.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Video en directo • Cámara ${if (isFrontCamera) "frontal" else "trasera"}",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // Top Information Bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 56.dp, start = 24.dp, end = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(BlueChatPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (callType == CallType.VIDEO) Icons.Default.Videocam else Icons.Default.Person,
                                contentDescription = null,
                                tint = BlueChatPrimary,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = peerName.ifBlank { "Contacto" },
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        val statusText = when (callState) {
                            CallState.OUTGOING_RINGING -> "Llamando..."
                            CallState.INCOMING_RINGING -> "Llamada entrante"
                            CallState.CONNECTED -> {
                                val minutes = durationSec / 60
                                val seconds = durationSec % 60
                                "%02d:%02d".format(minutes, seconds)
                            }
                            CallState.ENDED -> "Llamada finalizada"
                            CallState.IDLE -> ""
                        }

                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (callState == CallState.CONNECTED) BlueChatGreen else Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Wi-Fi P2P status badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = BlueChatGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Enlace local P2P cifrado",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Bottom Controls Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 48.dp, start = 24.dp, end = 24.dp)
                    ) {
                        when (callState) {
                            CallState.INCOMING_RINGING -> {
                                // Accept or Decline Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Decline Call
                                    IconButton(
                                        onClick = { viewModel.endCall() },
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(BlueChatRed)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CallEnd,
                                            contentDescription = "Rechazar",
                                            tint = Color.White,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }

                                    // Answer Call
                                    IconButton(
                                        onClick = { viewModel.answerCall() },
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(BlueChatGreen)
                                    ) {
                                        Icon(
                                            imageVector = if (callType == CallType.VIDEO) Icons.Default.Videocam else Icons.Default.Call,
                                            contentDescription = "Responder",
                                            tint = Color.White,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                            }

                            CallState.OUTGOING_RINGING,
                            CallState.CONNECTED -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Mic Toggle Button
                                        IconButton(
                                            onClick = { viewModel.toggleCallMic() },
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isMicMuted) Color.White.copy(alpha = 0.25f)
                                                    else Color.White.copy(alpha = 0.12f)
                                                )
                                        ) {
                                            Icon(
                                                imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                                contentDescription = "Micrófono",
                                                tint = if (isMicMuted) BlueChatRed else Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        // Speaker Toggle Button
                                        IconButton(
                                            onClick = { viewModel.toggleCallSpeaker() },
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSpeakerOn) BlueChatPrimary.copy(alpha = 0.4f)
                                                    else Color.White.copy(alpha = 0.12f)
                                                )
                                        ) {
                                            Icon(
                                                imageVector = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                                contentDescription = "Altavoz",
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        // Flip Camera (If Video Call)
                                        if (callType == CallType.VIDEO) {
                                            IconButton(
                                                onClick = { viewModel.flipCallCamera() },
                                                modifier = Modifier
                                                    .size(54.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White.copy(alpha = 0.12f))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Cameraswitch,
                                                    contentDescription = "Cambiar cámara",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }

                                        // End Call Button
                                        IconButton(
                                            onClick = { viewModel.endCall() },
                                            modifier = Modifier
                                                .size(58.dp)
                                                .clip(CircleShape)
                                                .background(BlueChatRed)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CallEnd,
                                                contentDescription = "Finalizar",
                                                tint = Color.White,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            else -> {}
                        }
                    }
                }
            }
        }
    }
}
