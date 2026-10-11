package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.PlayerState
import com.example.ui.theme.GhostCyan

@Composable
fun VoiceNoteBubble(
    audioFilePath: String,
    durationSec: Int,
    isOutgoing: Boolean,
    playerState: PlayerState,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCurrentPlaying = (playerState.playingPath == audioFilePath && playerState.isPlaying)
    val progress = if (playerState.playingPath == audioFilePath && playerState.durationMs > 0) {
        (playerState.currentPositionMs.toFloat() / playerState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val bubbleBg = if (isOutgoing) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bubbleBg)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onPlayClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isOutgoing) MaterialTheme.colorScheme.primary else GhostCyan.copy(alpha = 0.2f))
                .testTag("voice_note_play_button")
        ) {
            Icon(
                imageVector = if (isCurrentPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isCurrentPlaying) "Pausar nota de voz" else "Reproducir nota de voz",
                tint = if (isOutgoing) MaterialTheme.colorScheme.onPrimary else GhostCyan,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            modifier = Modifier.width(160.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Nota de voz cifrada",
                    fontSize = 11.sp,
                    color = contentColor.copy(alpha = 0.8f)
                )
                Text(
                    text = formatDuration(if (isCurrentPlaying) (playerState.currentPositionMs / 1000) else durationSec),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = contentColor
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (isOutgoing) MaterialTheme.colorScheme.primary else GhostCyan,
                trackColor = contentColor.copy(alpha = 0.2f),
            )
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%02d:%02d".format(mins, secs)
}
