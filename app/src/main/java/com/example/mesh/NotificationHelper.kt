package com.example.mesh

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.example.MainActivity

/**
 * Helper to display high-priority Heads-up notifications equipped with RemoteInput
 * for direct inline replies, and incoming call awakening notifications.
 */
object NotificationHelper {

    const val CHANNEL_MESSAGES_ID = "bluechat_messages_v2"
    const val CHANNEL_CALLS_ID = "bluechat_calls_v2"
    const val KEY_TEXT_REPLY = "key_inline_reply_text"
    const val EXTRA_CONVERSATION_ID = "extra_conversation_id"
    const val EXTRA_SENDER_NAME = "extra_sender_name"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val EXTRA_IS_CALL = "extra_is_call"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // High Priority Messages Channel for Heads-up Banners and Inline Reply
            val messageChannel = NotificationChannel(
                CHANNEL_MESSAGES_ID,
                "Mensajes de BlueChat",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones interactivas para responder mensajes de la red de malla"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }
            manager.createNotificationChannel(messageChannel)

            // High Priority Calls Channel for Heads-up Call Awakening
            val callsChannel = NotificationChannel(
                CHANNEL_CALLS_ID,
                "Llamadas de BlueChat",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas de llamadas de voz y video en la red local"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }
            manager.createNotificationChannel(callsChannel)
        }
    }

    fun showIncomingMessageNotification(
        context: Context,
        conversationId: String,
        senderName: String,
        messageText: String,
        isVoiceNote: Boolean = false
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        createNotificationChannels(context)

        val notificationId = conversationId.hashCode()

        // 1. Content Intent: Opens conversation in MainActivity
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        // 2. Inline Reply Action with RemoteInput
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Responder a $senderName...")
            .build()

        val replyIntent = Intent(context, NotificationReplyReceiver::class.java).apply {
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
            putExtra(EXTRA_SENDER_NAME, senderName)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Responder",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()

        val displayText = if (isVoiceNote) "[Nota de voz recibida]" else messageText

        // 3. High-Priority Heads-up Notification
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(senderName)
            .setContentText(displayText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(openPendingIntent)
            .addAction(replyAction)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        manager.notify(notificationId, notification)
    }

    /**
     * Bluetooth Call Awakening Notification:
     * Triggered when recipient has Wi-Fi turned off or app in background when an incoming call starts.
     */
    fun showIncomingCallNotification(
        context: Context,
        callerId: String,
        callerName: String,
        isVideo: Boolean,
        isWifiNeeded: Boolean
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        createNotificationChannels(context)

        val notificationId = ("CALL_$callerId").hashCode()

        // 1. Intent to answer call inside app
        val answerIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CONVERSATION_ID, callerId)
            putExtra(EXTRA_IS_CALL, true)
        }
        val answerPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        // 2. Intent to open Wi-Fi settings if needed
        val wifiIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val wifiPendingIntent = PendingIntent.getActivity(
            context,
            notificationId + 1,
            wifiIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val callTypeDesc = if (isVideo) "Llamada de video" else "Llamada de voz"
        val bodyText = if (isWifiNeeded) {
            "Llamada entrante: activa el Wi-Fi para responder"
        } else {
            "$callTypeDesc entrante"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_CALLS_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle("Llamada de $callerName")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(answerPendingIntent)
            .setAutoCancel(true)
            .setOngoing(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        if (isWifiNeeded) {
            builder.addAction(android.R.drawable.ic_menu_manage, "Activar Wi-Fi", wifiPendingIntent)
        }
        builder.addAction(android.R.drawable.ic_menu_call, "Responder", answerPendingIntent)

        manager.notify(notificationId, builder.build())
    }

    fun dismissNotification(context: Context, conversationId: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.cancel(conversationId.hashCode())
        manager?.cancel(("CALL_$conversationId").hashCode())
    }
}
