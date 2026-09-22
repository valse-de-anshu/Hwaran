package com.ballade.hwaran.ui.dialogs

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ballade.hwaran.core.network.ScrapeTaskInfo
import com.ballade.hwaran.core.network.ZineServerClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ZineScraperDialog(
    onDismiss: () -> Unit,
    onImportFolder: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboard = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager }

    val prefs = remember { context.getSharedPreferences("hwaran_zine_server", Context.MODE_PRIVATE) }
    var serverIp by remember { mutableStateOf(prefs.getString("server_ip", "") ?: "") }
    var serverPort by remember { mutableIntStateOf(prefs.getInt("server_port", 53318)) }

    var isSearchingServer by remember { mutableStateOf(false) }
    var isServerConnected by remember { mutableStateOf(false) }
    var showManualIp by remember { mutableStateOf(false) }

    var urlInput by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf("quick_grab") } // "quick_grab" or "vacuum"

    var activeTask by remember { mutableStateOf<ScrapeTaskInfo?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var localDownloadResultPath by remember { mutableStateOf<String?>(null) }

    // Auto-discover server or test saved IP on open
    LaunchedEffect(Unit) {
        if (serverIp.isNotBlank()) {
            val alive = ZineServerClient.pingServer(serverIp, serverPort)
            if (alive) {
                isServerConnected = true
                return@LaunchedEffect
            }
        }
        // Run LAN discovery
        isSearchingServer = true
        val found = ZineServerClient.discoverServer(context, timeoutMs = 2000)
        isSearchingServer = false
        if (found != null) {
            serverIp = found.host
            serverPort = found.port
            isServerConnected = true
            prefs.edit().putString("server_ip", serverIp).putInt("server_port", serverPort).apply()
        } else if (serverIp.isNotBlank()) {
            isServerConnected = ZineServerClient.pingServer(serverIp, serverPort)
        }
        if (!isServerConnected) {
            showManualIp = true
        }
    }

    // Task polling loop
    LaunchedEffect(activeTask?.taskId, activeTask?.status) {
        val task = activeTask ?: return@LaunchedEffect
        if (task.status in listOf("completed", "failed")) return@LaunchedEffect

        while (true) {
            delay(1200)
            val res = ZineServerClient.getTaskStatus(serverIp, serverPort, task.taskId)
            res.onSuccess { updated ->
                activeTask = updated
                if (updated.status == "completed" && updated.message.contains("direct download", ignoreCase = true)) {
                    // Start direct download stream automatically
                    coroutineScope.launch {
                        val dlRes = ZineServerClient.downloadMediaZip(
                            context = context,
                            serverHost = serverIp,
                            serverPort = serverPort,
                            taskId = updated.taskId,
                            mode = selectedMode
                        )
                        dlRes.onSuccess { dir ->
                            localDownloadResultPath = dir.absolutePath
                            Toast.makeText(context, "Downloaded into ${dir.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            if (activeTask?.status in listOf("completed", "failed")) break
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF141721),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.DriveFileMove,
                                    contentDescription = null,
                                    tint = Color(0xFFD4D8E0),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text("Zine Scraper Hub", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Remote Media Ingestion", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.6f))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Server Status Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.04f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(8.dp),
                                    shape = CircleShape,
                                    color = if (isServerConnected) Color(0xFF6EE7B7) else if (isSearchingServer) Color(0xFFFCD34D) else Color(0xFFF87171)
                                ) {}
                                Text(
                                    text = if (isServerConnected) "Server: $serverIp:$serverPort" else if (isSearchingServer) "Scanning LAN for Zine..." else "Server Offline",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }

                            Text(
                                text = if (showManualIp) "Done" else "Configure",
                                fontSize = 11.5.sp,
                                color = Color(0xFFD4D8E0),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { showManualIp = !showManualIp }
                            )
                        }

                        if (showManualIp) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = serverIp,
                                    onValueChange = { serverIp = it.trim() },
                                    label = { Text("Server IP", fontSize = 11.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFFD4D8E0),
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            isSearchingServer = true
                                            val alive = ZineServerClient.pingServer(serverIp, serverPort)
                                            isServerConnected = alive
                                            isSearchingServer = false
                                            if (alive) {
                                                prefs.edit().putString("server_ip", serverIp).apply()
                                                showManualIp = false
                                            } else {
                                                Toast.makeText(context, "Cannot connect to $serverIp:$serverPort", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222631)),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Test", fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // URL Input Field with Paste Button
                Text("MEDIA URL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    placeholder = { Text("Paste Manga, Anime, Novel or Video link...", fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f)) },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val item = clipboard?.primaryClip?.getItemAt(0)
                                val text = item?.text?.toString() ?: ""
                                if (text.isNotBlank()) urlInput = text.trim()
                            }
                        ) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste", tint = Color(0xFFD4D8E0), modifier = Modifier.size(18.dp))
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFD4D8E0),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Ingestion Mode Selector (Quick Grab vs Vacuum)
                Text("SCRAPE SCOPE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val modes = listOf(
                        "quick_grab" to "Quick Grab (Single/Clip)",
                        "vacuum" to "Vacuum (Full Series/Run)"
                    )
                    modes.forEach { (modeKey, label) ->
                        val isSelected = selectedMode == modeKey
                        Surface(
                            onClick = { selectedMode = modeKey },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.05f),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFFD4D8E0) else Color.White.copy(alpha = 0.12f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Active Scrape Task Progress Card
                activeTask?.let { task ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.06f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = task.status.uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (task.status == "completed") Color(0xFF6EE7B7) else if (task.status == "failed") Color(0xFFF87171) else Color(0xFFD4D8E0)
                                )
                                Text(
                                    text = "${(task.progress * 100).toInt()}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { task.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFFD4D8E0),
                                trackColor = Color.White.copy(alpha = 0.12f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = task.message.ifBlank { "Processing..." },
                                fontSize = 11.5.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (localDownloadResultPath != null && onImportFolder != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        onImportFolder(localDownloadResultPath!!)
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4D8E0)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Import into Hwaran Library", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF141721))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Submit Action Button
                Button(
                    onClick = {
                        if (!isServerConnected && serverIp.isBlank()) {
                            Toast.makeText(context, "Please connect to Zine Server first", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (urlInput.isBlank()) {
                            Toast.makeText(context, "Please enter a valid URL", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        coroutineScope.launch {
                            isSubmitting = true
                            val result = ZineServerClient.submitScrape(
                                serverHost = serverIp,
                                serverPort = serverPort,
                                mediaUrl = urlInput,
                                mode = selectedMode,
                                transferMethod = "hybrid"
                            )
                            isSubmitting = false
                            result.onSuccess { task ->
                                activeTask = task
                                Toast.makeText(context, "Scrape task started on PC!", Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                Toast.makeText(context, "Error: ${err.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isSubmitting && urlInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD4D8E0),
                        disabledContainerColor = Color.White.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Submitting...", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = Color(0xFF141721), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Scrape & Transfer to Phone",
                            color = Color(0xFF141721),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
