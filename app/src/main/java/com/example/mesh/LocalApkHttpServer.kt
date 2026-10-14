package com.example.mesh

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

/**
 * Lightweight local HTTP daemon to serve the BlueChat APK directly to nearby peers
 * scanning the QR code or visiting http://<ip>:8899/BlueChat.apk
 */
class LocalApkHttpServer(private val context: Context) {
    companion object {
        private const val TAG = "LocalApkHttpServer"
        const val SERVER_PORT = 8899
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null

    val isRunning: Boolean
        get() = serverSocket?.isClosed == false && serverJob?.isActive == true

    fun startServer(): String {
        if (isRunning) return getDownloadUrl()

        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(SERVER_PORT).apply {
                    reuseAddress = true
                }
                Log.i(TAG, "Local APK HTTP server listening on port $SERVER_PORT")

                while (isActive && serverSocket?.isClosed == false) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        launch { handleClient(client) }
                    } catch (e: Exception) {
                        if (!isActive) break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind server socket on port $SERVER_PORT", e)
            }
        }

        return getDownloadUrl()
    }

    fun stopServer() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverJob?.cancel()
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            try {
                val input = s.getInputStream().bufferedReader()
                val requestLine = input.readLine() ?: return
                // Drain headers
                var header: String? = input.readLine()
                while (!header.isNullOrBlank()) {
                    header = input.readLine()
                }

                val apkFile = resolveApkFile()
                val out = BufferedOutputStream(s.getOutputStream())

                if (apkFile != null && apkFile.exists()) {
                    val length = apkFile.length()
                    val headerText = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/vnd.android.package-archive\r\n" +
                            "Content-Length: $length\r\n" +
                            "Content-Disposition: attachment; filename=\"BlueChat.apk\"\r\n" +
                            "Connection: close\r\n\r\n"
                    out.write(headerText.toByteArray(Charsets.UTF_8))

                    FileInputStream(apkFile).use { fis ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (fis.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                        }
                    }
                    out.flush()
                } else {
                    val errorHtml = "<html><body><h2>BlueChat APK no disponible en almacenamiento local.</h2></body></html>"
                    val errorResp = "HTTP/1.1 404 Not Found\r\n" +
                            "Content-Type: text/html\r\n" +
                            "Content-Length: ${errorHtml.length}\r\n" +
                            "Connection: close\r\n\r\n$errorHtml"
                    out.write(errorResp.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Client socket error: ${e.message}")
            }
        }
    }

    private fun resolveApkFile(): File? {
        val cacheApk = File(context.cacheDir, "BlueChat.apk")
        if (cacheApk.exists()) return cacheApk

        val sourceApk = File(context.applicationInfo.sourceDir)
        if (sourceApk.exists()) return sourceApk

        return null
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    fun getDownloadUrl(): String {
        val ip = getLocalIpAddress()
        return "http://$ip:$SERVER_PORT/BlueChat.apk"
    }
}
