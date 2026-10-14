package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cloud.UsernameValidationResult
import com.example.ui.theme.BlueChatGreen
import com.example.ui.theme.BlueChatPrimary
import com.example.ui.theme.BlueChatRed
import com.example.ui.viewmodel.MeshViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MeshViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val userName by viewModel.userName.collectAsState()
    val nodeId by viewModel.nodeId.collectAsState()
    val maxTtl by viewModel.maxTtl.collectAsState()
    val isAutoRelay by viewModel.isAutoRelay.collectAsState()
    val isBtEnabled by viewModel.isBluetoothEnabled.collectAsState()
    val isInternetAvailable by viewModel.isInternetAvailable.collectAsState()
    val showWipeDialog by viewModel.showWipeConfirmDialog.collectAsState()
    val showEditIdentity by viewModel.showEditIdentityDialog.collectAsState()
    val showClearDevDataDialog by viewModel.showClearDevDataConfirmDialog.collectAsState()

    var editedNameText by remember(userName) { mutableStateOf(userName) }
    var ttlSliderValue by remember(maxTtl) { mutableFloatStateOf(maxTtl.toFloat()) }
    var isCheckingCloud by remember { mutableStateOf(false) }
    var cloudErrorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Ajustes",
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
            // Profile & Identity Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(BlueChatPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = userName.take(1).uppercase(),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = userName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "ID: $nodeId",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isInternetAvailable) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                        contentDescription = null,
                                        tint = if (isInternetAvailable) BlueChatGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isInternetAvailable) "Registro en la nube sincronizado" else "Modo desconectado (Validación local)",
                                        fontSize = 10.sp,
                                        color = if (isInternetAvailable) BlueChatGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                editedNameText = userName
                                cloudErrorMessage = null
                                viewModel.showEditIdentityDialog.value = true
                            },
                            modifier = Modifier.testTag("edit_identity_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar nombre",
                                tint = BlueChatPrimary
                            )
                        }
                    }
                }
            }

            // RED Y CONEXIÓN
            item {
                Text(
                    text = "RED Y ENRUTAMIENTO",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        // Estado Bluetooth
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = BlueChatPrimary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("Radio Bluetooth", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(
                                        text = if (isBtEnabled) "Activo y disponible" else "Desactivado",
                                        fontSize = 11.sp,
                                        color = if (isBtEnabled) BlueChatGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.startMeshScan()
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BlueChatPrimary,
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Escanear", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(14.dp))

                        // Modo Repetidor
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Modo Repetidor Automático", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    text = "Retransmite paquetes cifrados de otros teléfonos cercanos como nodo ciego de salto múltiple.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isAutoRelay,
                                onCheckedChange = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.toggleAutoRelay(it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = BlueChatPrimary,
                                    checkedTrackColor = BlueChatPrimary.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.testTag("auto_relay_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(14.dp))

                        // Slider TTL
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = null, tint = BlueChatPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Límite de Saltos (TTL): ${ttlSliderValue.toInt()}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            text = "Número máximo de intermediarios por los que viaja cada mensaje antes de expirar.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                        )
                        Slider(
                            value = ttlSliderValue,
                            onValueChange = { ttlSliderValue = it },
                            onValueChangeFinished = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.updateMaxTtl(ttlSliderValue.toInt())
                            },
                            valueRange = 1f..10f,
                            steps = 8,
                            colors = SliderDefaults.colors(
                                thumbColor = BlueChatPrimary,
                                activeTrackColor = BlueChatPrimary
                            ),
                            modifier = Modifier.testTag("ttl_slider")
                        )
                    }
                }
            }

            // COMPARTIR APK
            item {
                Text(
                    text = "DISTRIBUCIÓN SIN INTERNET",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = BlueChatPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Compartir instalador APK", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Envía la app directamente a otro celular por Bluetooth o menú de compartir para expandir la red sin internet.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.shareApkFile(context)
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BlueChatPrimary,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Enviar BlueChat.apk por Bluetooth", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // DESARROLLO Y LIMPIEZA DE PRUEBAS
            item {
                Text(
                    text = "DESARROLLO Y PRUEBAS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, tint = BlueChatPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Limpiar Chats y Datos de Prueba", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Elimina los mensajes y contactos simulados durante el desarrollo, manteniendo tu identidad.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.showClearDevDataConfirmDialog.value = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Limpiar mensajes de prueba", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // SEGURIDAD Y BORRADO PERMANENTE
            item {
                Text(
                    text = "SEGURIDAD LOCAL",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = BlueChatRed, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Borrado Seguro de Emergencia", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Elimina permanentemente todos los chats, contactos y notas de voz almacenadas en este dispositivo.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.showWipeConfirmDialog.value = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BlueChatRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("secure_wipe_button")
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Borrar toda la información local", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // Edit Name Dialog with Cloud Uniqueness Verification
    if (showEditIdentity) {
        AlertDialog(
            onDismissRequest = {
                if (!isCheckingCloud) viewModel.showEditIdentityDialog.value = false
            },
            title = { Text("Configurar Nombre de Usuario", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Identificador de dispositivo: ${viewModel.deviceModel}#$editedNameText",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = BlueChatPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Al guardar, se verificará la unicidad en el registro en la nube para asegurar que nadie use tu misma credencial.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editedNameText,
                        onValueChange = {
                            editedNameText = it
                            cloudErrorMessage = null
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_name_text_field"),
                        isError = cloudErrorMessage != null
                    )

                    if (cloudErrorMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = cloudErrorMessage!!,
                            color = BlueChatRed,
                            fontSize = 12.sp,
                            lineHeight = 15.sp
                        )
                    }

                    if (isCheckingCloud) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verificando disponibilidad en la nube...", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isCheckingCloud = true
                        cloudErrorMessage = null
                        viewModel.verifyAndSaveUsername(editedNameText) { result ->
                            isCheckingCloud = false
                            when (result) {
                                is UsernameValidationResult.Available,
                                is UsernameValidationResult.ClaimedByMe -> {
                                    Toast.makeText(context, "Nombre validado y guardado correctamente", Toast.LENGTH_SHORT).show()
                                }
                                is UsernameValidationResult.DuplicateRejected -> {
                                    cloudErrorMessage = result.reason
                                }
                                is UsernameValidationResult.OfflineOrError -> {
                                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    enabled = !isCheckingCloud && editedNameText.isNotBlank(),
                    modifier = Modifier.testTag("save_name_button")
                ) {
                    Text("Validar y Guardar", color = BlueChatPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showEditIdentityDialog.value = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Clear Dev Data Confirmation Dialog
    if (showClearDevDataDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showClearDevDataConfirmDialog.value = false },
            title = { Text("¿Limpiar datos de prueba?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Se eliminarán los chats y contactos creados durante el desarrollo para dejar un estado limpio de pruebas.")
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.clearDevAndMockData() }
                ) {
                    Text("Limpiar", color = BlueChatPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showClearDevDataConfirmDialog.value = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Secure Wipe Confirmation Dialog
    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showWipeConfirmDialog.value = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = BlueChatRed, modifier = Modifier.size(36.dp)) },
            title = { Text("¿Eliminar todos los datos?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Esta acción borrará definitivamente todas tus conversaciones, notas de voz y contactos guardados. No se puede revertir.")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.wipeDatabaseSecurely() },
                    colors = ButtonDefaults.buttonColors(containerColor = BlueChatRed, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_wipe_button")
                ) {
                    Text("Confirmar Borrado")
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
