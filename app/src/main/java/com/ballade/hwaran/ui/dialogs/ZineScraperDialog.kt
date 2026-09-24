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

// Minimal Titanium Palette
private val DialogDarkOnyx = Color(0xFF08090C)
private val DialogGlassSurface = Color(0xFF101218)
private val DialogTitanium = Color(0xFFF1F3F9)
private val DialogSubtleBorder = Color(0xFF27272A)
private val DialogEmerald = Color(0xFF34D399)
private val DialogCoral = Color(0xFFF87171)
private val DialogAmber = Color(0xFFFBBF24)

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
    var selectedScopeKey by remember { mutableStateOf("auto") } // "auto", "single" (--0), "vacuum" (--a), "next_5" (--5)
    var flagMetaOnly by remember { mutableStateOf(false) } // --meta

    var activeTask by remember { mutableStateOf<ScrapeTaskInfo?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var isCancelingRequested by remember { mutableStateOf(false) }
    var isStoppingRequested by remember { mutableStateOf(false) }
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
        if (task.status in listOf("completed", "failed", "canceled")) return@LaunchedEffect

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
                            mode = updated.mode,
                            mediaTitle = updated.mediaTitle
                        )
                        dlRes.onSuccess { dir ->
                            localDownloadResultPath = dir.absolutePath
                            Toast.makeText(context, "Downloaded into ${dir.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            if (activeTask?.status in listOf("completed", "failed", "canceled")) break
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DialogGlassSurface,
            border = BorderStroke(1.dp, DialogSubtleBorder),
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
                            color = Color.White.copy(alpha = 0.06f),
                            border = BorderStroke(1.dp, DialogSubtleBorder)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.DriveFileMove,
                                    contentDescription = null,
                                    tint = DialogTitanium,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                        Column {
                            Text("Zine Scraper Hub", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DialogTitanium)
                            Text("Remote Media Ingestion", fontSize = 11.sp, color = DialogTitanium.copy(alpha = 0.55f))
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = DialogTitanium.copy(alpha = 0.6f))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Server Status Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.03f),
                    border = BorderStroke(1.dp, DialogSubtleBorder),
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
                                    color = if (isServerConnected) DialogEmerald else if (isSearchingServer) DialogAmber else DialogCoral
                                ) {}
                                Text(
                                    text = if (isServerConnected) "Server: $serverIp:$serverPort" else if (isSearchingServer) "Scanning LAN for Zine..." else "Server Offline",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DialogTitanium.copy(alpha = 0.85f)
                                )
                            }

                            Text(
                                text = if (showManualIp) "Done" else "Configure",
                                fontSize = 11.5.sp,
                                color = DialogTitanium,
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
                                        focusedTextColor = DialogTitanium,
                                        unfocusedTextColor = DialogTitanium,
                                        focusedBorderColor = DialogTitanium,
                                        unfocusedBorderColor = DialogSubtleBorder
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
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                                    border = BorderStroke(1.dp, DialogSubtleBorder),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Test", fontSize = 12.sp, color = DialogTitanium)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // URL Input Field with Paste Button
                Text("MEDIA URL", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = DialogTitanium.copy(alpha = 0.45f), letterSpacing = 1.1.sp)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    placeholder = { Text("Paste Manga, Anime, Novel or Video link...", fontSize = 12.sp, color = DialogTitanium.copy(alpha = 0.35f)) },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val item = clipboard?.primaryClip?.getItemAt(0)
                                val text = item?.text?.toString() ?: ""
                                if (text.isNotBlank()) urlInput = text.trim()
                            }
                        ) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste", tint = DialogTitanium, modifier = Modifier.size(18.dp))
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DialogTitanium,
                        unfocusedTextColor = DialogTitanium,
                        focusedBorderColor = DialogTitanium,
                        unfocusedBorderColor = DialogSubtleBorder
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Ingestion Mode Selector (Auto vs Single vs Vacuum vs Next 5)
                Text("SCRAPE SCOPE", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = DialogTitanium.copy(alpha = 0.45f), letterSpacing = 1.1.sp)
                Spacer(modifier = Modifier.height(6.dp))

                val scopes = listOf(
                    Triple("auto", "Auto (Smart Link)", "AUTO"),
                    Triple("single", "Single Item", "--0"),
                    Triple("vacuum", "Vacuum All", "--a"),
                    Triple("next_5", "Next 5", "--5")
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    scopes.take(2).forEach { (scopeKey, label, badge) ->
                        val isSelected = selectedScopeKey == scopeKey
                        Surface(
                            onClick = { selectedScopeKey = scopeKey },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f),
                            border = BorderStroke(1.dp, if (isSelected) DialogTitanium else DialogSubtleBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) DialogTitanium else DialogTitanium.copy(alpha = 0.65f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isSelected) DialogTitanium.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f)
                                ) {
                                    Text(
                                        text = badge,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) DialogTitanium else DialogTitanium.copy(alpha = 0.45f),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    scopes.drop(2).forEach { (scopeKey, label, badge) ->
                        val isSelected = selectedScopeKey == scopeKey
                        Surface(
                            onClick = { selectedScopeKey = scopeKey },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f),
                            border = BorderStroke(1.dp, if (isSelected) DialogTitanium else DialogSubtleBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) DialogTitanium else DialogTitanium.copy(alpha = 0.65f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isSelected) DialogTitanium.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f)
                                ) {
                                    Text(
                                        text = badge,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) DialogTitanium else DialogTitanium.copy(alpha = 0.45f),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Metadata Only Toggle
                Surface(
                    onClick = { flagMetaOnly = !flagMetaOnly },
                    shape = RoundedCornerShape(10.dp),
                    color = if (flagMetaOnly) DialogEmerald.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f),
                    border = BorderStroke(1.dp, if (flagMetaOnly) DialogEmerald.copy(alpha = 0.40f) else DialogSubtleBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Metadata Only (--meta)",
                            fontSize = 11.sp,
                            fontWeight = if (flagMetaOnly) FontWeight.Bold else FontWeight.Medium,
                            color = if (flagMetaOnly) DialogEmerald else DialogTitanium.copy(alpha = 0.65f)
                        )
                        Surface(
                            shape = CircleShape,
                            color = if (flagMetaOnly) DialogEmerald else Color.White.copy(alpha = 0.10f),
                            modifier = Modifier.size(16.dp)
                        ) {
                            if (flagMetaOnly) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = DialogDarkOnyx,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Active Scrape Task Progress Card
                activeTask?.let { task ->
                    val isTaskActive = task.status in listOf("scraping", "analyzing", "queued")
                    val isTaskStopping = isStoppingRequested || task.isStopping || task.message.contains("stopping", ignoreCase = true)

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.05f),
                        border = BorderStroke(1.dp, DialogSubtleBorder),
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
                                    color = when (task.status) {
                                        "completed" -> DialogEmerald
                                        "failed" -> DialogCoral
                                        "canceled" -> DialogCoral
                                        else -> DialogTitanium
                                    }
                                )
                                Text(
                                    text = "${(task.progress * 100).toInt()}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DialogTitanium
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { task.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = DialogTitanium,
                                trackColor = Color.White.copy(alpha = 0.10f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = task.message.ifBlank { "Processing..." },
                                fontSize = 11.5.sp,
                                color = DialogTitanium.copy(alpha = 0.7f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Action buttons inside task card when running
                            if (isTaskActive) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Cancel Button
                                    Button(
                                        onClick = {
                                            if (!isCancelingRequested) {
                                                isCancelingRequested = true
                                                coroutineScope.launch {
                                                    val res = ZineServerClient.cancelTask(serverIp, serverPort, task.taskId)
                                                    res.onSuccess {
                                                        isCancelingRequested = false
                                                        activeTask = activeTask?.copy(status = "canceled", message = "Task canceled immediately")
                                                        Toast.makeText(context, "Task cancelled immediately!", Toast.LENGTH_SHORT).show()
                                                    }.onFailure { err ->
                                                        isCancelingRequested = false
                                                        Toast.makeText(context, "Cancel failed: ${err.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = DialogCoral.copy(alpha = 0.15f)),
                                        border = BorderStroke(1.dp, DialogCoral.copy(alpha = 0.40f)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f).height(40.dp)
                                    ) {
                                        Text(if (isCancelingRequested) "Canceling..." else "Cancel", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = DialogCoral)
                                    }

                                    // Stop (Ctrl+T) Button
                                    Button(
                                        onClick = {
                                            if (!isTaskStopping) {
                                                isStoppingRequested = true
                                                coroutineScope.launch {
                                                    val res = ZineServerClient.stopTask(serverIp, serverPort, task.taskId, "truncate")
                                                    res.onSuccess {
                                                        Toast.makeText(context, "Stopping after current (Ctrl+T)...", Toast.LENGTH_SHORT).show()
                                                    }.onFailure { err ->
                                                        isStoppingRequested = false
                                                        Toast.makeText(context, "Failed to stop: ${err.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = if (isTaskStopping) DialogAmber.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f)),
                                        border = BorderStroke(1.dp, if (isTaskStopping) DialogAmber.copy(alpha = 0.40f) else DialogSubtleBorder),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1.2f).height(40.dp)
                                    ) {
                                        Text(if (isTaskStopping) "Finishing..." else "Stop (Ctrl+T)", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = if (isTaskStopping) DialogAmber else DialogTitanium)
                                    }
                                }
                            }

                            if (localDownloadResultPath != null && onImportFolder != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        onImportFolder(localDownloadResultPath!!)
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = DialogTitanium),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(42.dp)
                                ) {
                                    Text("Import into Hwaran Library", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DialogDarkOnyx)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                val isScrapingActive = activeTask != null && (activeTask?.status in listOf("scraping", "analyzing", "queued"))

                if (!isScrapingActive) {
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
                                val flags = mutableListOf<String>()
                                var modeVal = "auto"
                                var limitVal: Int? = null

                                when (selectedScopeKey) {
                                    "auto" -> {
                                        modeVal = "auto"
                                    }
                                    "single" -> {
                                        flags.add("--0")
                                        modeVal = "quick_grab"
                                    }
                                    "vacuum" -> {
                                        flags.add("--a")
                                        modeVal = "vacuum"
                                    }
                                    "next_5" -> {
                                        flags.add("--5")
                                        limitVal = 5
                                        modeVal = "vacuum"
                                    }
                                }
                                if (flagMetaOnly) {
                                    flags.add("--meta")
                                }

                                val result = ZineServerClient.submitScrape(
                                    serverHost = serverIp,
                                    serverPort = serverPort,
                                    mediaUrl = urlInput.trim(),
                                    mode = modeVal,
                                    flags = flags,
                                    limit = limitVal,
                                    transferMethod = "direct",
                                    clientIp = ZineServerClient.getLocalDeviceIp(),
                                    keepOnPc = true
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
                            containerColor = DialogTitanium,
                            disabledContainerColor = Color.White.copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = DialogDarkOnyx, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Submitting...", color = DialogDarkOnyx, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = DialogDarkOnyx, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Scrape & Transfer",
                                color = DialogDarkOnyx,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
