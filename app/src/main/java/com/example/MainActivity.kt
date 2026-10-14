package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.BlueChatTheme
import com.example.ui.viewmodel.MeshViewModel

class MainActivity : ComponentActivity() {
    private var pendingConversationId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingConversationId = intent?.getStringExtra(com.example.mesh.NotificationHelper.EXTRA_CONVERSATION_ID)

        setContent {
            BlueChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val viewModel: MeshViewModel = viewModel()

                    androidx.compose.runtime.LaunchedEffect(pendingConversationId) {
                        pendingConversationId?.let { convId ->
                            viewModel.openChat(convId)
                            pendingConversationId = null
                        }
                    }

                    HomeScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(com.example.mesh.NotificationHelper.EXTRA_CONVERSATION_ID)?.let { convId ->
            pendingConversationId = convId
        }
    }
}
