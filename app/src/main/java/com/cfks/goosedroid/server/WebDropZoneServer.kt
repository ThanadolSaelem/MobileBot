package com.cfks.goosedroid.server

import android.content.Context
import android.util.Log
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID

object WebDropZoneServer {
    private var server: ApplicationEngine? = null
    private var currentToken: String = ""
    private var isServerActive = false
    
    // Callback when a file is received
    var onFileReceived: ((File) -> Unit)? = null
    
    /**
     * Enumerates network interfaces directly — no WifiManager permission
     * needed (the old connectionInfo call threw SecurityException without
     * ACCESS_WIFI_STATE, which broke the web_drop_zone tool).
     */
    private fun getLocalIpAddress(@Suppress("UNUSED_PARAMETER") context: Context): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                val addresses = ni.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: continue
                    }
                }
            }
            "0.0.0.0"
        } catch (e: Exception) {
            Log.e("WebDropZone", "IP lookup failed: ${e.message}")
            "0.0.0.0"
        }
    }

    fun startServer(context: Context): String {
        if (isServerActive) return "http://${getLocalIpAddress(context)}:8080/drop?t=$currentToken"
        
        currentToken = UUID.randomUUID().toString().substring(0, 4).uppercase()
        val ip = getLocalIpAddress(context)
        val port = 8080
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                server = embeddedServer(Netty, port = port) {
                    routing {
                        get("/drop") {
                            val token = call.request.queryParameters["t"]
                            if (token != currentToken) {
                                call.respondText("Unauthorized or Expired Token", status = HttpStatusCode.Unauthorized)
                                return@get
                            }
                            
                            val html = """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                    <title>Unit Drop Zone</title>
                                    <meta name="viewport" content="width=device-width, initial-scale=1">
                                    <style>
                                        body { font-family: monospace; background: #1a1a1a; color: #fff; display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100vh; margin: 0; }
                                        .box { border: 2px dashed #4CAF50; padding: 40px; border-radius: 10px; text-align: center; }
                                        input[type=file] { margin-top: 20px; color: #fff; }
                                        button { background: #4CAF50; color: white; border: none; padding: 10px 20px; margin-top: 20px; cursor: pointer; border-radius: 5px; font-weight: bold; }
                                    </style>
                                </head>
                                <body>
                                    <div class="box">
                                        <h2>SECURE DROP ZONE</h2>
                                        <p>Select a file to send to the Unit.</p>
                                        <p style="font-size: 10px; color: #888;">Supported: JPG, PNG, TXT, PDF</p>
                                        <form action="/upload?t=$currentToken" method="post" enctype="multipart/form-data">
                                            <input type="file" name="file" required><br>
                                            <button type="submit">SEND FILE</button>
                                        </form>
                                    </div>
                                </body>
                                </html>
                            """.trimIndent()
                            call.respondText(html, ContentType.Text.Html)
                        }
                        
                        post("/upload") {
                            val token = call.request.queryParameters["t"]
                            if (token != currentToken) {
                                call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized)
                                return@post
                            }
                            
                            var fileToSave: File? = null
                            try {
                                val multipart = call.receiveMultipart()
                                multipart.forEachPart { part ->
                                    if (part is PartData.FileItem) {
                                        val ext = File(part.originalFileName ?: "file").extension.lowercase()
                                        if (ext in listOf("png", "jpg", "jpeg", "txt", "pdf")) {
                                            val cacheDir = context.cacheDir
                                            val file = File(cacheDir, "drop_${System.currentTimeMillis()}.$ext")
                                            part.streamProvider().use { its -> file.outputStream().buffered().use { its.copyTo(it) } }
                                            fileToSave = file
                                        }
                                    }
                                    part.dispose()
                                }
                                
                                if (fileToSave != null) {
                                    call.respondText("File received successfully! You can close this page.", status = HttpStatusCode.OK)
                                    onFileReceived?.invoke(fileToSave!!)
                                    // Auto stop after receiving file
                                    stopServer()
                                } else {
                                    call.respondText("File type not allowed.", status = HttpStatusCode.BadRequest)
                                }
                            } catch (e: Exception) {
                                call.respondText("Upload failed: ${e.message}", status = HttpStatusCode.InternalServerError)
                            }
                        }
                    }
                }
                server?.start(wait = false)
                isServerActive = true
            } catch (e: Exception) {
                Log.e("WebDropZone", "Server error", e)
            }
        }
        
        // Auto stop after 60 seconds
        CoroutineScope(Dispatchers.IO).launch {
            delay(60_000)
            stopServer()
        }
        
        return "http://$ip:8080/drop?t=$currentToken"
    }
    
    fun stopServer() {
        if (!isServerActive) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                server?.stop(1000, 2000)
                server = null
                isServerActive = false
                currentToken = ""
                onFileReceived = null // Prevent memory leaks
                Log.d("WebDropZone", "Server stopped.")
            } catch (e: Exception) {
                Log.e("WebDropZone", "Stop error", e)
            }
        }
    }
}
