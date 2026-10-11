package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.ui.components.HardwareHashBadge
import com.example.ui.theme.GhostCyan
import com.example.ui.theme.GhostGreen
import com.example.ui.theme.GhostRed
import com.example.ui.viewmodel.MeshViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MeshViewModel,
    modifier: Modifier = Modifier
) {
    val userName by viewModel.userName.collectAsState()
    val nodeId by viewModel.nodeId.collectAsState()
    val maxTtl by viewModel.maxTtl.collectAsState()
    val isAutoRelay by viewModel.isAutoRelay.collectAsState()
    val isSimulatorEnabled by viewModel.isSimulatorEnabled.collectAsState()
    val isBtEnabled by viewModel.isBluetoothEnabled.collectAsState()
    val showWipeDialog by viewModel.showWipeConfirmDialog.collectAsState()
    val showEditIdentity by viewModel.showEditIdentityDialog.collectAsState()

    var editedNameText by remember { mutableStateOf(userName) }
    var ttlSliderValue by remember(maxTtl) { mutableFloatStateOf(maxTtl.toFloat()) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Ajustes de Malla y Privacidad",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Identity Section
            item {
                Text(
                    text = "IDENTIDAD DEL NODO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(GhostCyan.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Badge,
                                        contentDescription = null,
                                        tint = GhostCyan
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = userName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Nombre visible en la malla",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    editedNameText = userName
                                    viewModel.showEditIdentityDialog.value = true
                                },
                                modifier = Modifier.testTag("edit_identity_button")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Editar nombre", tint = GhostCyan)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F172A))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "ID Único de Hardware (anti-colisión):",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = nodeId,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = GhostCyan
                                )
                            }
                        }
                    }
                }
            }

            // Mesh Routing Configuration
            item {
                Text(
                    text = "CONFIGURACIÓN DEL PROTOCOLO MALLADO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Max TTL Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = null, tint = GhostCyan, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Límite TTL (Saltos Máximos)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                }
                                Text(
                                    text = "${ttlSliderValue.toInt()} saltos",
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = GhostCyan
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Evita bucles infinitos. Los paquetes mueren si superan este número de saltos.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = ttlSliderValue,
                                onValueChange = {
                                    ttlSliderValue = it
                                    viewModel.updateMaxTtl(it.toInt())
                                },
                                valueRange = 1f..10f,
                                steps = 8,
                                colors = SliderDefaults.colors(
                                    thumbColor = GhostCyan,
                                    activeTrackColor = GhostCyan
                                ),
                                modifier = Modifier.testTag("ttl_slider")
                            )
                        }

                        // Auto-Relay Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Modo Repetidor Automático", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    text = "Permite a tu teléfono retransmitir paquetes ajenos como nodo puente (store-and-forward).",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isAutoRelay,
                                onCheckedChange = { viewModel.toggleAutoRelay(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = GhostCyan, checkedTrackColor = GhostCyan.copy(alpha = 0.5f)),
                                modifier = Modifier.testTag("auto_relay_switch")
                            )
                        }

                        // Simulator Mode Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Science, contentDescription = null, tint = GhostGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Simulador de Nodos Virtuales", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                }
                                Text(
                                    text = "Genera repetidores de prueba para ensayar saltos y cifrado en emuladores.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isSimulatorEnabled,
                                onCheckedChange = { viewModel.toggleSimulator(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = GhostGreen, checkedTrackColor = GhostGreen.copy(alpha = 0.5f)),
                                modifier = Modifier.testTag("simulator_switch")
                            )
                        }
                    }
                }
            }

            // Security & Danger Zone
            item {
                Text(
                    text = "ZONA DE PRIVACIDAD Y SEGURIDAD",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = GhostRed,
                    letterSpacing = 1.sp
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = GhostRed, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Borrado Definitivo y Modo Pánico", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Purga de forma irrecuperable todas las tablas SQLite de mensajes, contactos guardados y notas de voz temporales del almacenamiento local.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        ElevatedButton(
                            onClick = { viewModel.showWipeConfirmDialog.value = true },
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = GhostRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("secure_wipe_button")
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Borrado Seguro de Toda la Base de Datos", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Technical Architecture Explainer
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF0F172A)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = GhostCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Protocolo BlueChat", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• Cifrado E2E: AES-256 (CBC/PKCS5) extremo a extremo. Los repetidores intermedios solo ven el ID del destino, no pueden leer los mensajes ni audios.\n" +
                                   "• Sin Internet: Enrutamiento hop-by-hop a través de sockets RFCOMM y balizas BLE.\n" +
                                   "• Anti-Storm: Deduplicación por hash de paquete y decremento de TTL en cada salto.",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    // Edit Name Dialog
    if (showEditIdentity) {
        AlertDialog(
            onDismissRequest = { viewModel.showEditIdentityDialog.value = false },
            title = { Text("Editar Nombre de Usuario") },
            text = {
                Column {
                    Text(
                        text = "Tu nombre visible en la red mallada. Se combinará con tu hash de hardware permanente.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editedNameText,
                        onValueChange = { editedNameText = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_name_text_field")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.updateUserName(editedNameText) },
                    modifier = Modifier.testTag("save_name_button")
                ) {
                    Text("Guardar", color = GhostCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showEditIdentityDialog.value = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Secure Wipe Confirmation Dialog
    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showWipeConfirmDialog.value = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = GhostRed, modifier = Modifier.size(36.dp)) },
            title = { Text("¿Confirmar Borrado de Emergencia?") },
            text = {
                Text("Esta acción eliminará de forma irreversible todas las conversaciones, audios y contactos almacenados en este dispositivo. No se puede deshacer.")
            },
            confirmButton = {
                ElevatedButton(
                    onClick = { viewModel.wipeDatabaseSecurely() },
                    colors = ButtonDefaults.elevatedButtonColors(containerColor = GhostRed, contentColor = Color.White),
                    modifier = Modifier.testTag("confirm_wipe_button")
                ) {
                    Text("Confirmar y Borrar Todo")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showWipeConfirmDialog.value = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
