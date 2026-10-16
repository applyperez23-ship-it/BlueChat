# Plan de Implementación: Solución Integral de Entrega de Mensajes, Canal General, Llamadas y Modo Extender Alcance

## 1. Diagnóstico y Problemas Identificados

1. **Falta de transporte en la nube para modo de largo alcance:**
   - Anteriormente, el modo de emergencia solo marcaba una variable en memoria (`emergencyInternetChatId`) pero **nunca** enviaba ni recibía paquetes a través de la API REST de Firestore o backend en la nube.
   - Si no hay conexión Bluetooth física directa entre teléfonos distantes, los mensajes no llegaban porque no existía un buzón de intercambio por Internet.

2. **Entrega de paquetes por Bluetooth y Canal General:**
   - `sendPacketOverBluetooth` solo escribía en sockets que ya estaban activos en ese instante; si el socket hacia el dispositivo de destino se estaba abriendo en segundo plano, el paquete no quedaba en cola de envío para cuando el socket terminara de conectarse.
   - En el Canal General (`BROADCAST_DEST`), los paquetes se descartaban si el remitente ya los había guardado en el caché de deduplicación con el mismo ID local sin diferenciación, y no se enviaban a todos los dispositivos descubiertos.

3. **Llamadas y Videollamadas P2P:**
   - La transmisión de streaming UDP requiere que ambos dispositivos compartan su dirección IP local directa (mediante Wi-Fi o Wi-Fi Direct). Actualmente el paquete de control solo enviaba la palabra `"VIDEO"` o `"AUDIO"` sin la dirección IP local ni el puerto de destino del peer.
   - Falta el aviso claro exigido por el usuario: recordar activar el Wi-Fi en ambos teléfonos y advertir que el alcance es local (red Wi-Fi / línea directa de visión).

4. **Botón de compartir APK dentro del chat:**
   - En la barra de entrada del chat se colocó un botón para compartir el archivo APK cuando el texto está vacío. El usuario ha solicitado explícitamente retirarlo del interior de las conversaciones.

5. **Cambio de nombre del Modo:**
   - Reemplazar "Modo de emergencia" por **"Extender el alcance (Internet/Nube)"** en toda la interfaz (títulos, diálogos, menús de 3 toques, ajustes y chips de estado).

---

## 2. Plan de Cambios y Arquitectura

### A. Repositorio de Nube & Modo "Extender el Alcance" (`CloudIdentityRegistry.kt` y `MeshEngine.kt`)
- Implementar métodos de envío y recepción de paquetes en `CloudIdentityRegistry`:
  - `sendPacketToCloudMailbox(packet: MeshPacket)`: Publica el payload cifrado en la colección `messages_relay` de Firestore con TTL y destinatario (`destNodeId` o `BROADCAST_DEST`).
  - `startCloudMailboxListener(onPacketReceived: (MeshPacket) -> Unit)`: Escucha periódicamente o por snapshot los paquetes pendientes dirigidos al `nodeId` local o al Canal General y los entrega a `onIncomingPacket()`.
- Cuando "Extender el alcance" esté activo (o haya internet disponible y no haya enlace de radio directo), los mensajes se entregarán mediante este buzón en la nube con cifrado E2E intacto.

### B. Canal General y Cola de Envíos Bluetooth/UDP
- **Cola de reintento (`PendingPacketQueue`):** Si un socket Bluetooth o Wi-Fi Direct está en proceso de conexión, el paquete se encola y se despacha automáticamente una vez que `handleConnectedSocket` se establece.
- **Canal General (`BROADCAST_DEST`):**
  - Despacho simultáneo a todos los sockets activos, a todos los dispositivos descubiertos mediante intento de conexión inmediata, a la subred UDP (`255.255.255.255` y broadcasts de interfaces) y al buzón de la nube si hay internet.
  - Asegurar que al recibir un broadcast no se filtre erróneamente por deduplicación del remitente local.

### C. Arreglo de Llamadas y Videollamadas Locales P2P (`LocalCallManager.kt`, `CallOverlay.kt`)
- **Intercambio de IP local:** Enviar la IP local en el payload de `CALL_AWAKEN` y `CALL_OFFER` (ej. `{"type":"VIDEO","ip":"192.168.1.45","port":45455}`) para que el receptor sepa exactamente a qué dirección IP transmitir audio/video P2P sin depender de broadcasts lentos.
- **Aviso en pantalla y confirmación:**
  - Agregar advertencia visual destacada en el diálogo de llamada y overlay:
    - *"Recuerda que ambos dispositivos deben tener el Wi-Fi activado (misma red o zona Wi-Fi directa). El alcance máximo es la cobertura Wi-Fi local."*
  - Comprobación y activación asistida de Wi-Fi.

### D. Limpieza de Interfaz (`ChatDetailScreen.kt` y `EmergencyUtilityDialog.kt`)
- Eliminar el botón de compartir APK de la barra de texto en `ChatDetailScreen.kt`.
- Renombrar todas las menciones de "Modo de emergencia" a **"Extender el alcance (Internet/Nube)"**.

---

## 3. Plan de Verificación

1. **Compilación y Pruebas Unitarias:**
   - Ejecutar `compile_applet` para garantizar cero errores de sintaxis o tipos.
   - Ejecutar `gradle :app:testDebugUnitTest` para comprobar que la lógica de mensajes, Room DB y deduplicación funciona correctamente.
2. **Pruebas de Flujo:**
   - Verificar que al enviar en el Canal General, el mensaje se persiste en Room y se envía por radio + nube.
   - Verificar que al pulsar llamadas se muestra el recordatorio de Wi-Fi y alcance limitado.
   - Verificar que el botón de compartir APK ya no aparece dentro del chat.
