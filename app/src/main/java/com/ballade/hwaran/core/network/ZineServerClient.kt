package com.ballade.hwaran.core.network

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val error: String
)

object ZineServerClient {

    /**
     * Listens for the Zine Scraper Server's UDP beacon on LAN for up to [timeoutMs].
     */
    suspend fun discoverServer(timeoutMs: Int = 2500): ZineServerInfo? = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(53318).apply {
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
            // Timeout or port in use
        } finally {
            socket?.close()
        }
        null
    }

    /**
     * Pings a server at host:port to verify connectivity.
     */
    suspend fun pingServer(host: String, port: Int = 53318): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$host:$port/api/ping")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 1500
                readTimeout = 1500
                requestMethod = "GET"
            }
            conn.responseCode == 200
        } catch (e: Exception) {
            false
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
        transferMethod: String = "hybrid" // "hybrid", "localsend", "direct"
    ): Result<ScrapeTaskInfo> = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("http://$serverHost:$serverPort/api/scrape")
            val conn = (endpoint.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 10000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }

            val payload = JSONObject().apply {
                put("url", mediaUrl)
                put("mode", mode)
                if (clientIp.isNotBlank()) put("target_ip", clientIp)
                put("transfer", transferMethod)
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
        try {
            val endpoint = URL("http://$serverHost:$serverPort/api/tasks/$taskId")
            val conn = (endpoint.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                requestMethod = "GET"
            }
            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                Result.success(parseTaskJson(JSONObject(text)))
            } else {
                Result.failure(IOException("HTTP ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Direct download stream: fetches the zipped media archive from server and unzips it
     * into the phone's Download/Zine Scraper/ directory.
     */
    suspend fun downloadMediaZip(
        context: Context,
        serverHost: String,
        serverPort: Int,
        taskId: String,
        mode: String = "quick_grab",
        progressCb: ((Float) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("http://$serverHost:$serverPort/api/download/$taskId")
            val conn = (endpoint.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 60000
                requestMethod = "GET"
            }

            if (conn.responseCode != 200) {
                return@withContext Result.failure(IOException("Failed to download: HTTP ${conn.responseCode}"))
            }

            val totalLen = conn.contentLength.toFloat()

            // Resolve target directory on phone: Download/Zine Scraper/<Vacuum or Quick grab>/
            val subFolder = if (mode.equals("vacuum", ignoreCase = true)) "Vacuum" else "Quick grab"
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val zineRoot = File(downloadsDir, "Zine Scraper/$subFolder").apply { mkdirs() }

            val targetDir = if (zineRoot.exists() && zineRoot.canWrite()) {
                zineRoot
            } else {
                File(context.getExternalFilesDir(null), "Zine Scraper/$subFolder").apply { mkdirs() }
            }

            // Stream and extract ZIP directly
            var downloadedBytes = 0L
            val zipIn = ZipInputStream(BufferedInputStream(conn.inputStream))
            var entry = zipIn.nextEntry
            val buffer = ByteArray(8192)

            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        var len: Int
                        while (zipIn.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                            downloadedBytes += len
                            if (totalLen > 0) {
                                progressCb?.invoke(downloadedBytes / totalLen)
                            }
                        }
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
            zipIn.close()

            Result.success(targetDir)
        } catch (e: Exception) {
            Result.failure(e)
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
            error = json.optString("error", "")
        )
    }
}
