package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MeshNode
import com.example.ui.components.HardwareHashBadge
import com.example.ui.theme.GhostCyan
import com.example.ui.theme.GhostGreen
import com.example.ui.theme.GhostPurple
import com.example.ui.viewmodel.MeshViewModel
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopologyScreen(
    viewModel: MeshViewModel,
    modifier: Modifier = Modifier
) {
    val activeNodes by viewModel.activeNodes.collectAsState()
    val localNodeId by viewModel.nodeId.collectAsState()
    val (localHash, localName) = remember(localNodeId) { MeshNode.parseNodeId(localNodeId) }

    var selectedNode by remember { mutableStateOf<MeshNode?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Topología de Malla",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Visualización de saltos store-and-forward",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    ElevatedButton(
                        onClick = { viewModel.triggerBroadcastBeacon() },
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = GhostPurple.copy(alpha = 0.2f),
                            contentColor = GhostPurple
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("broadcast_beacon_button")
                    ) {
                        Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Baliza", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Interactive Mesh Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF090D16))
            ) {
                val nodesList = activeNodes.values.toList()

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(nodesList) {
                            detectTapGestures { tapOffset ->
                                val cx = size.width / 2f
                                val cy = size.height / 2f

                                // Check if a node was clicked
                                val angleStep = if (nodesList.isNotEmpty()) (2 * Math.PI / nodesList.size) else 0.0
                                val radius = minOf(size.width, size.height) * 0.35f

                                var found: MeshNode? = null
                                nodesList.forEachIndexed { index, node ->
                                    val angle = index * angleStep - Math.PI / 2
                                    val nx = cx + (radius * cos(angle)).toFloat()
                                    val ny = cy + (radius * sin(angle)).toFloat()
                                    val dist = (tapOffset - Offset(nx, ny)).getDistance()
                                    if (dist <= 36f * density) {
                                        found = node
                                    }
                                }
                                selectedNode = found
                            }
                        }
                ) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val centerOffset = Offset(cx, cy)
                    val baseRadius = size.minDimension * 0.35f

                    // Draw concentric radar rings
                    drawCircle(
                        color = Color(0xFF1E293B),
                        radius = baseRadius * 0.5f,
                        center = centerOffset,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                        )
                    )
                    drawCircle(
                        color = Color(0xFF1E293B),
                        radius = baseRadius,
                        center = centerOffset,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                        )
                    )

                    // Draw connections & neighbor nodes
                    val angleStep = if (nodesList.isNotEmpty()) (2 * Math.PI / nodesList.size) else 0.0
                    val nodePositions = mutableListOf<Offset>()

                    nodesList.forEachIndexed { index, node ->
                        val angle = index * angleStep - Math.PI / 2
                        val hopDistFactor = if (node.hopDistance > 1) 1.05f else 0.85f
                        val nx = cx + (baseRadius * hopDistFactor * cos(angle)).toFloat()
                        val ny = cy + (baseRadius * hopDistFactor * sin(angle)).toFloat()
                        val nodeOffset = Offset(nx, ny)
                        nodePositions.add(nodeOffset)

                        // Link line to center or to intermediate relay
                        val linkColor = if (node.hopDistance <= 1) GhostCyan.copy(alpha = 0.6f) else GhostPurple.copy(alpha = 0.5f)
                        drawLine(
                            color = linkColor,
                            start = centerOffset,
                            end = nodeOffset,
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = if (node.hopDistance > 1) PathEffect.dashPathEffect(floatArrayOf(12f, 8f)) else null
                        )

                        // Outer glowing aura for selected
                        if (selectedNode?.nodeId == node.nodeId) {
                            drawCircle(
                                color = GhostCyan.copy(alpha = 0.3f),
                                radius = 26.dp.toPx(),
                                center = nodeOffset
                            )
                        }

                        // Node point
                        drawCircle(
                            color = if (node.hopDistance <= 1) GhostCyan else GhostPurple,
                            radius = 14.dp.toPx(),
                            center = nodeOffset
                        )
                        drawCircle(
                            color = Color(0xFF0F172A),
                            radius = 10.dp.toPx(),
                            center = nodeOffset
                        )
                        drawCircle(
                            color = if (node.hopDistance <= 1) GhostCyan else GhostPurple,
                            radius = 6.dp.toPx(),
                            center = nodeOffset
                        )
                    }

                    // Local central node (Me)
                    drawCircle(
                        color = GhostGreen.copy(alpha = 0.25f),
                        radius = 28.dp.toPx(),
                        center = centerOffset
                    )
                    drawCircle(
                        color = GhostGreen,
                        radius = 16.dp.toPx(),
                        center = centerOffset
                    )
                    drawCircle(
                        color = Color(0xFF0F172A),
                        radius = 11.dp.toPx(),
                        center = centerOffset
                    )
                    drawCircle(
                        color = GhostGreen,
                        radius = 7.dp.toPx(),
                        center = centerOffset
                    )
                }

                // Legend overlays
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(GhostGreen))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tú (Nodo Local)", fontSize = 11.sp, color = Color.White)

                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(GhostCyan))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Salto Directo", fontSize = 11.sp, color = Color.White)

                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(GhostPurple))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Multi-Salto", fontSize = 11.sp, color = Color.White)
                }
            }

            // Bottom Selected Node Info Card
            val activeSelected = selectedNode ?: activeNodes.values.firstOrNull()
            if (activeSelected != null) {
                val (sHash, sName) = MeshNode.parseNodeId(activeSelected.nodeId)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = activeSelected.effectiveName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                HardwareHashBadge(hash = sHash)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Distancia: ${activeSelected.hopDistance} saltos • RSSI: ${activeSelected.rssi} dBm",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        ElevatedButton(
                            onClick = { viewModel.openChat(activeSelected.nodeId, activeSelected) },
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = GhostCyan,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("topology_chat_button")
                        ) {
                            Text("Abrir Chat", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
