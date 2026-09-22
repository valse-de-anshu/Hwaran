package com.ballade.hwaran.core.network

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.*
import java.util.zip.ZipInputStream

data class ZineServerInfo(
    val host: String,
    val port: Int = 53318,
    val version: String = "2.1"
)

data class ScrapeTaskInfo(
    val taskId: String,
    val url: String,
    val mode: String,
    val status: String,
    val progress: Float,
    val message: String,
    val fileCount: Int,
    val error: String,
    val mediaTitle: String = ""
)

object ZineServerClient {

    // Beacon is broadcast on 53319 (dedicated discovery channel, never collides with HTTP)
    private const val BEACON_DISCOVERY_PORT = 53319

    /**
     * Listens for the Zine Scraper Server's UDP beacon on LAN (port 53319),
     * or probes the local subnet concurrently if beacon is blocked.
     * Cancels remaining probes as soon as the first live server is found.
     */
    suspend fun discoverServer(context: Context, timeoutMs: Int = 1500): ZineServerInfo? = withContext(Dispatchers.IO) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        val multicastLock = wifiManager?.createMulticastLock("zine_discovery")?.apply {
            setReferenceCounted(false)
            acquire()
        }

        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(BEACON_DISCOVERY_PORT))
                soTimeout = timeoutMs
                broadcast = true
            }
            val buffer = ByteArray(1024)
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            val jsonStr = String(packet.data, 0, packet.length)
            val json = JSONObject(jsonStr)
            if (json.optString("service") == "zine-scraper-server") {
                val host = packet.address.hostAddress ?: "127.0.0.1"
                val port = json.optInt("port", 53318)
                val version = json.optString("version", "2.1")
                return@withContext ZineServerInfo(host, port, version)
            }
        } catch (e: Exception) {
            // UDP broadcast blocked by router or timed out -> try fast subnet probe
        } finally {
            socket?.close()
            try { multicastLock?.release() } catch (e: Exception) {}
        }

        // Fast concurrent subnet probe: cancel all remaining as soon as first server is found
        val subnet = getLocalSubnetPrefix()
        if (subnet != null) {
            val result = coroutineScope {
                val jobs = (1..254).map { hostNum ->
                    async {
                        val ip = "$subnet.$hostNum"
                        if (isActive && pingServer(ip, 53318, timeoutMs = 350)) {
                            ZineServerInfo(ip, 53318)
                        } else null
                    }
                }
                // Select-first: grab the very first non-null result and cancel everything else
                var found: ZineServerInfo? = null
                for (job in jobs) {
                    val r = job.await()
                    if (r != null && found == null) {
                        found = r
                        // Cancel all remaining in-progress probes
                        jobs.forEach { it.cancel() }
                        break
                    }
                }
                found
            }
            if (result != null) return@withContext result
        }

        null
    }

    private fun getLocalSubnetPrefix(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        val parts = ip.split(".")
                        if (parts.size == 4) {
                            return "${parts[0]}.${parts[1]}.${parts[2]}"
                        }
                    }
                }
            }
        } catch (e: Exception) {}
        return null
    }

    /**
     * Pings a server at host:port to verify connectivity.
     * Always disconnects the connection in a finally block to prevent socket leaks.
     */
    suspend fun pingServer(host: String, port: Int = 53318, timeoutMs: Int = 3000): Boolean = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$host:$port/api/ping")
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
                setRequestProperty("Connection", "keep-alive")
            }
            conn.responseCode == 200
        } catch (e: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Retrieves active or recent tasks from the companion server.
     */
    suspend fun getActiveTasks(
        serverHost: String,
        serverPort: Int
    ): Result<List<ScrapeTaskInfo>> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val endpoint = URL("http://$serverHost:$serverPort/api/tasks")
            conn = (endpoint.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("Connection", "keep-alive")
            }
            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(text)
                val arr = root.optJSONArray("tasks") ?: JSONArray()
                val list = mutableListOf<ScrapeTaskInfo>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(parseTaskJson(obj))
                }
                Result.success(list)
            } else {
                Result.failure(IOException("HTTP ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Clears all scrape tasks on the companion server.
     * Sends Content-Length: 0 so HTTP/1.1 servers don't stall waiting for a body.
     */
    suspend fun clearAllTasks(
        serverHost: String,
        serverPort: Int
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val endpoint = URL("http://$serverHost:$serverPort/api/tasks/clear")
            conn = (endpoint.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "POST"
                setRequestProperty("Connection", "keep-alive")
                setRequestProperty("Content-Length", "0")
                // Explicitly do NOT set doOutput=true to avoid waiting for body
            }
            if (conn.responseCode in 200..299) {
                Result.success(true)
            } else {
                Result.failure(IOException("HTTP ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Sends a scrape job request to the Zine Scraper Server.
     */
    suspend fun submitScrape(
        serverHost: String,
        serverPort: Int,
        mediaUrl: String,
        mode: String = "quick_grab", // "quick_grab" or "vacuum"
        clientIp: String = "",
        transferMethod: String = "hybrid", // "hybrid", "localsend", "direct"
        flags: List<String> = emptyList(),
        limit: Int? = null
    ): Result<ScrapeTaskInfo> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val endpoint = URL("http://$serverHost:$serverPort/api/scrape")
            conn = (endpoint.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 15000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Connection", "keep-alive")
            }

            android.util.Log.i("HwaranZine", "Transmitting scrape signal: $mediaUrl ($mode, flags: $flags, limit: $limit) to $serverHost:$serverPort")
            val payload = JSONObject().apply {
                put("url", mediaUrl)
                put("mode", mode)
                if (flags.isNotEmpty()) {
                    val arr = JSONArray()
                    flags.forEach { arr.put(it) }
                    put("flags", arr)
                }
                if (limit != null && limit > 0) {
                    put("limit", limit)
                }
                if (clientIp.isNotBlank()) put("target_ip", clientIp)
                put("transfer", transferMethod)
                put("device_name", android.os.Build.MODEL)
                put("device_brand", android.os.Build.MANUFACTURER)
                put("app_name", "Hwaran")
                put("app_version", "2.1.0")
                put("timestamp", System.currentTimeMillis())
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            if (conn.responseCode in 200..299) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val respJson = JSONObject(responseText)
                Result.success(parseTaskJson(respJson))
            } else {
                Result.failure(IOException("Server error HTTP ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Polls the live status of an active scrape task.
     */
    suspend fun getTaskStatus(
        serverHost: String,
        serverPort: Int,
        taskId: String
    ): Result<ScrapeTaskInfo> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val endpoint = URL("http://$serverHost:$serverPort/api/tasks/$taskId")
            conn = (endpoint.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("Connection", "keep-alive")
            }
            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                Result.success(parseTaskJson(JSONObject(text)))
            } else {
                Result.failure(IOException("HTTP ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Direct high-speed download stream: fetches the media archive directly from server
     * using high-throughput 64KB buffers and extracts into Download/Zine Scraper/ directory.
     * Uses contentLengthLong (not contentLength) to correctly support files larger than 2 GB.
     */
    suspend fun downloadMediaZip(
        context: Context,
        serverHost: String,
        serverPort: Int,
        taskId: String,
        mode: String = "quick_grab",
        progressCb: ((Float) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val endpoint = URL("http://$serverHost:$serverPort/api/download/$taskId")
            conn = (endpoint.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 300000
                requestMethod = "GET"
                setRequestProperty("Connection", "keep-alive")
            }

            if (conn.responseCode != 200) {
                return@withContext Result.failure(IOException("Failed to download: HTTP ${conn.responseCode}"))
            }

            // Use contentLengthLong to correctly handle files > 2 GB (Int overflows at ~2 GB)
            val totalLen = conn.contentLengthLong.toFloat()

            // Resolve target directory on phone: Download/Zine Scraper/<Vacuum or Quick grab>/
            val subFolder = if (mode.equals("vacuum", ignoreCase = true)) "Vacuum" else "Quick grab"
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val zineRoot = File(downloadsDir, "Zine Scraper/$subFolder").apply { mkdirs() }

            val targetDir = if (zineRoot.exists() && zineRoot.canWrite()) {
                zineRoot
            } else {
                File(context.getExternalFilesDir(null), "Zine Scraper/$subFolder").apply { mkdirs() }
            }

            // High-throughput streaming extraction (64KB buffer)
            var downloadedBytes = 0L
            val zipIn = ZipInputStream(BufferedInputStream(conn.inputStream, 65536))
            var entry = zipIn.nextEntry
            val buffer = ByteArray(65536)

            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(outFile), 65536).use { bos ->
                        var len: Int
                        while (zipIn.read(buffer).also { len = it } > 0) {
                            bos.write(buffer, 0, len)
                            downloadedBytes += len
                            if (totalLen > 0) {
                                progressCb?.invoke(downloadedBytes / totalLen)
                            }
                        }
                        bos.flush()
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
            zipIn.close()

            Result.success(targetDir)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseTaskJson(json: JSONObject): ScrapeTaskInfo {
        return ScrapeTaskInfo(
            taskId = json.optString("task_id", ""),
            url = json.optString("url", ""),
            mode = json.optString("mode", "quick_grab"),
            status = json.optString("status", "queued"),
            progress = json.optDouble("progress", 0.0).toFloat(),
            message = json.optString("message", ""),
            fileCount = json.optInt("file_count", 0),
            error = json.optString("error", ""),
            mediaTitle = json.optString("media_title", json.optString("title", ""))
        )
    }
}

