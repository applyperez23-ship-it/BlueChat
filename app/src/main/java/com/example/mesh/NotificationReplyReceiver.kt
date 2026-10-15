package com.example.mesh

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.example.data.local.GhostMeshDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver to handle RemoteInput inline replies directly from notifications
 * and dispatch them over the mesh network without requiring full app UI launch.
 */
class NotificationReplyReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotificationReplyRx"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent) ?: return
        val replyText = remoteInput.getCharSequence(NotificationHelper.KEY_TEXT_REPLY)?.toString()?.trim() ?: return
        if (replyText.isBlank()) return

        val conversationId = intent.getStringExtra(NotificationHelper.EXTRA_CONVERSATION_ID) ?: return
        val senderName = intent.getStringExtra(NotificationHelper.EXTRA_SENDER_NAME) ?: "Contacto"
        val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, conversationId.hashCode())

        Log.i(TAG, "Received inline reply for $conversationId: '$replyText'")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = GhostMeshDatabase.getInstance(context)
                val idManager = MeshIdentityManager(context)
                val engine = MeshEngine(context, idManager, db)

                // Dispatch message across mesh
                engine.sendOutgoingPacket(
                    destNodeId = conversationId,
                    type = com.example.data.model.PacketType.TEXT,
                    plainBytesOrText = replyText.toByteArray(Charsets.UTF_8),
                    destDisplayName = senderName
                )

                // Update notification banner confirming delivery
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                val repliedNotification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_MESSAGES_ID)
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setContentTitle(senderName)
                    .setContentText("Enviado: $replyText")
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setTimeoutAfter(3500L)
                    .build()

                notificationManager?.notify(notificationId, repliedNotification)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send inline reply", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
