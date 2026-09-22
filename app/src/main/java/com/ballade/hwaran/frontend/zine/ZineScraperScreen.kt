package com.ballade.hwaran.frontend.zine

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ballade.hwaran.core.network.ScrapeTaskInfo
import com.ballade.hwaran.core.network.ZineServerClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private val DarkOnyxBackground = Color(0xFF090A0F)
private val CardSurfaceColor = Color(0xFF141721)
private val CardSubtleSurface = Color(0xFF181C28)
private val AccentTitanium = Color(0xFFD4D8E0)
private val BorderSubtle = Color.White.copy(alpha = 0.09f)
private val BorderActive = Color.White.copy(alpha = 0.22f)
private val SoftEmerald = Color(0xFF6EE7B7)
private val SoftAmber = Color(0xFFFCD34D)
private val SoftCoral = Color(0xFFF87171)

private const val ZINE_GITHUB_URL = "https://github.com/valse-de-anshu/zine-scraper.git"
private const val ZINE_SETUP_COMMANDS = "git clone https://github.com/valse-de-anshu/zine-scraper.git\ncd zine-scraper\npip install -r requirements.txt\npython orchestrator.py --server"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZineScraperScreen(
    onNavigateBack: () -> Unit,
    onImportFolder: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val clipboard = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager }

    val prefs = remember { context.getSharedPreferences("hwaran_zine_server", Context.MODE_PRIVATE) }
    var serverIp by remember { mutableStateOf(prefs.getString("server_ip", "") ?: "") }
    var serverPort by remember { mutableIntStateOf(prefs.getInt("server_port", 53318)) }
    var serverVersion by remember { mutableStateOf("2.1") }

    var isSearchingServer by remember { mutableStateOf(false) }
    var isServerConnected by remember { mutableStateOf(false) }
    var connectionLostMidTask by remember { mutableStateOf(false) }
    var showServerConfig by remember { mutableStateOf(false) }

    var urlInput by remember { mutableStateOf("") }
    // Modes: "quick_grab" (single/clip) vs "vacuum" (full series run)
    var selectedMode by remember { mutableStateOf("quick_grab") }
    // Transfer: "hybrid" (LocalSend with direct HTTP fallback), "localsend", "direct"
    var selectedTransferMethod by remember { mutableStateOf("hybrid") }

    var activeTask by remember { mutableStateOf<ScrapeTaskInfo?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var isDirectDownloading by remember { mutableStateOf(false) }
    var directDownloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedLocalDir by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    BackHandler {
        onNavigateBack()
    }

    // Auto-discover server or ping saved address on launch
    LaunchedEffect(Unit) {
        if (serverIp.isNotBlank()) {
            val alive = ZineServerClient.pingServer(serverIp, serverPort)
            if (alive) {
                isServerConnected = true
                return@LaunchedEffect
            }
        }

        isSearchingServer = true
        val found = ZineServerClient.discoverServer(context, timeoutMs = 2500)
        isSearchingServer = false

        if (found != null) {
            serverIp = found.host
            serverPort = found.port
            serverVersion = found.version
            isServerConnected = true
            prefs.edit().putString("server_ip", serverIp).putInt("server_port", serverPort).apply()
        } else if (serverIp.isNotBlank()) {
            isServerConnected = ZineServerClient.pingServer(serverIp, serverPort)
        } else {
            isServerConnected = false
            showServerConfig = true
        }
    }

    // Polling loop for active scrape task
    LaunchedEffect(activeTask?.taskId, isServerConnected) {
        val task = activeTask ?: return@LaunchedEffect
        if (task.status in listOf("completed", "failed")) return@LaunchedEffect

        var consecutiveFailures = 0
        while (true) {
            delay(1200)
            val res = ZineServerClient.getTaskStatus(serverIp, serverPort, task.taskId)
            res.onSuccess { updated ->
                consecutiveFailures = 0
                connectionLostMidTask = false
                activeTask = updated

                // Automatic direct HTTP fallback if server indicates direct download or if transfer method is direct
                if (updated.status == "completed" &&
                    (updated.message.contains("direct download", ignoreCase = true) || selectedTransferMethod == "direct")
                ) {
                    if (downloadedLocalDir == null && !isDirectDownloading) {
                        coroutineScope.launch {
                            isDirectDownloading = true
                            val dlRes = ZineServerClient.downloadMediaZip(
                                context = context,
                                serverHost = serverIp,
                                serverPort = serverPort,
                                taskId = updated.taskId,
                                mode = selectedMode,
                                progressCb = { directDownloadProgress = it }
                            )
                            isDirectDownloading = false
                            dlRes.onSuccess { dir ->
                                downloadedLocalDir = dir
                                Toast.makeText(context, "Harvest saved to ${dir.name}", Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                errorMessage = "Failed to download media archive: ${err.message}"
                            }
                        }
                    }
                }
            }.onFailure {
                consecutiveFailures++
                if (consecutiveFailures >= 3) {
                    connectionLostMidTask = true
                }
            }

            if (activeTask?.status in listOf("completed", "failed")) break
        }
    }

    Scaffold(
        containerColor = DarkOnyxBackground,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            // 1. Top Bar: Back Action, Title & Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateBack()
                        },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.06f),
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = AccentTitanium,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "ZINE SCRAPER",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Local Media Harvester & Ingestion Engine",
                            fontSize = 11.5.sp,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                    }
                }

                // Live status pill
                Surface(
                    onClick = { showServerConfig = !showServerConfig },
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        isServerConnected -> SoftEmerald.copy(alpha = 0.12f)
                        isSearchingServer -> SoftAmber.copy(alpha = 0.12f)
                        else -> SoftCoral.copy(alpha = 0.12f)
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            isServerConnected -> SoftEmerald.copy(alpha = 0.35f)
                            isSearchingServer -> SoftAmber.copy(alpha = 0.35f)
                            else -> SoftCoral.copy(alpha = 0.35f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = when {
                                isServerConnected -> SoftEmerald
                                isSearchingServer -> SoftAmber
                                else -> SoftCoral
                            },
                            modifier = Modifier.size(7.dp)
                        ) {}

                        Text(
                            text = when {
                                isServerConnected -> "READY"
                                isSearchingServer -> "SCANNING"
                                else -> "OFFLINE"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isServerConnected -> SoftEmerald
                                isSearchingServer -> SoftAmber
                                else -> SoftCoral
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Server Connection Card & Offline / Connection Loss Warnings
            if (connectionLostMidTask) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SoftCoral.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, SoftCoral.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.WifiOff, contentDescription = null, tint = SoftCoral, modifier = Modifier.size(24.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Connection to Server Interrupted", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = SoftCoral)
                            Text(
                                "Polling lost connection to $serverIp:$serverPort. Your task is still running on the server.",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val alive = ZineServerClient.pingServer(serverIp, serverPort)
                                    if (alive) {
                                        isServerConnected = true
                                        connectionLostMidTask = false
                                        Toast.makeText(context, "Reconnected to Zine Server", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Server still unreachable", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SoftCoral.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Reconnect", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Server Status & Quick Config Panel
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardSurfaceColor,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isServerConnected) Icons.Rounded.Dns else Icons.Rounded.CloudOff,
                                contentDescription = null,
                                tint = if (isServerConnected) SoftEmerald else AccentTitanium,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = if (isServerConnected) "Connected to Zine Server" else if (isSearchingServer) "Scanning Local Subnet..." else "Server Offline",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isServerConnected) "$serverIp:$serverPort (v$serverVersion)" else "No active beacon discovered on port $serverPort",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }

                        TextButton(
                            onClick = { showServerConfig = !showServerConfig },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (showServerConfig) "Hide" else "Configure",
                                fontSize = 11.5.sp,
                                color = AccentTitanium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Expandable config inputs
                    AnimatedVisibility(
                        visible = showServerConfig,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 14.dp)) {
                            HorizontalDivider(color = BorderSubtle, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "MANUAL ENDPOINT CONFIGURATION",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.5f),
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = serverIp,
                                    onValueChange = { serverIp = it.trim() },
                                    label = { Text("PC / Host IP", fontSize = 11.sp) },
                                    placeholder = { Text("e.g. 192.168.29.41", fontSize = 11.sp, color = Color.White.copy(alpha = 0.3f)) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = AccentTitanium,
                                        unfocusedBorderColor = BorderSubtle
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(2f)
                                )

                                OutlinedTextField(
                                    value = serverPort.toString(),
                                    onValueChange = { serverPort = it.toIntOrNull() ?: 53318 },
                                    label = { Text("Port", fontSize = 11.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = AccentTitanium,
                                        unfocusedBorderColor = BorderSubtle
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            isSearchingServer = true
                                            val alive = ZineServerClient.pingServer(serverIp, serverPort)
                                            isSearchingServer = false
                                            isServerConnected = alive
                                            if (alive) {
                                                prefs.edit().putString("server_ip", serverIp).putInt("server_port", serverPort).apply()
                                                Toast.makeText(context, "Connected successfully!", Toast.LENGTH_SHORT).show()
                                                showServerConfig = false
                                            } else {
                                                Toast.makeText(context, "Could not reach $serverIp:$serverPort", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CardSubtleSurface),
                                    border = BorderStroke(1.dp, BorderActive),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Test Ping", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                }

                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            isSearchingServer = true
                                            val found = ZineServerClient.discoverServer(context, timeoutMs = 2500)
                                            isSearchingServer = false
                                            if (found != null) {
                                                serverIp = found.host
                                                serverPort = found.port
                                                serverVersion = found.version
                                                isServerConnected = true
                                                prefs.edit().putString("server_ip", serverIp).putInt("server_port", serverPort).apply()
                                                Toast.makeText(context, "Found server at ${found.host}:${found.port}", Toast.LENGTH_SHORT).show()
                                                showServerConfig = false
                                            } else {
                                                Toast.makeText(context, "No server beacon found on LAN", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CardSubtleSurface),
                                    border = BorderStroke(1.dp, BorderActive),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Scan Subnet", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Centerpiece: Link Processor Card
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = CardSurfaceColor,
                border = BorderStroke(1.dp, BorderActive),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Link,
                                    contentDescription = null,
                                    tint = AccentTitanium,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "LINK PROCESSOR",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = "Manga, Webtoons, Light Novels, Anime, Video",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Media URL Input Field
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = {
                            urlInput = it
                            errorMessage = null
                        },
                        placeholder = {
                            Text(
                                text = "Paste link to scrape...",
                                fontSize = 12.5.sp,
                                color = Color.White.copy(alpha = 0.35f)
                            )
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (urlInput.isNotBlank()) {
                                    IconButton(
                                        onClick = { urlInput = "" },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Clear,
                                            contentDescription = "Clear",
                                            tint = Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                        if (clipText.isNotBlank()) {
                                            urlInput = clipText.trim()
                                            Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentPaste,
                                        contentDescription = "Paste",
                                        tint = AccentTitanium,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = false,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AccentTitanium,
                            unfocusedBorderColor = BorderSubtle,
                            focusedContainerColor = Color.Black.copy(alpha = 0.25f),
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Scrape Scope Selection: Detailed Comparison Cards
                    Text(
                        text = "SELECT HARVEST SCOPE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.6f),
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Scope Option 1: Quick Grab (Single item)
                        val isQuickGrab = selectedMode == "quick_grab"
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedMode = "quick_grab"
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isQuickGrab) Color.White.copy(alpha = 0.08f) else CardSubtleSurface,
                            border = BorderStroke(1.dp, if (isQuickGrab) AccentTitanium else BorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(
                                            if (isQuickGrab) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                                            RoundedCornerShape(8.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FlashOn,
                                        contentDescription = null,
                                        tint = if (isQuickGrab) Color.White else Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Single Item (Quick Grab)",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.White.copy(alpha = 0.10f)
                                        ) {
                                            Text(
                                                text = "FAST",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AccentTitanium,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "Downloads only the specific chapter, episode, video clip, or audio track from the link. Rapid, lightweight retrieval for immediate consumption.",
                                        fontSize = 11.5.sp,
                                        color = Color.White.copy(alpha = 0.65f),
                                        lineHeight = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Target: Download/Zine Scraper/Quick grab/",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.White.copy(alpha = 0.4f)
                                    )
                                }

                                RadioButton(
                                    selected = isQuickGrab,
                                    onClick = { selectedMode = "quick_grab" },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = AccentTitanium,
                                        unselectedColor = Color.White.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        }

                        // Scope Option 2: Vacuum (Full Series / Complete Run)
                        val isVacuum = selectedMode == "vacuum"
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedMode = "vacuum"
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isVacuum) Color.White.copy(alpha = 0.08f) else CardSubtleSurface,
                            border = BorderStroke(1.dp, if (isVacuum) AccentTitanium else BorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(
                                            if (isVacuum) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                                            RoundedCornerShape(8.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Layers,
                                        contentDescription = null,
                                        tint = if (isVacuum) Color.White else Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Full Series Run (Vacuum)",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.White.copy(alpha = 0.10f)
                                        ) {
                                            Text(
                                                text = "COMPLETE",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AccentTitanium,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "Complete deep harvest: crawls the series index and extracts all available chapters, whole season episodes, or complete music album.",
                                        fontSize = 11.5.sp,
                                        color = Color.White.copy(alpha = 0.65f),
                                        lineHeight = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Target: Download/Zine Scraper/Vacuum/",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.White.copy(alpha = 0.4f)
                                    )
                                }

                                RadioButton(
                                    selected = isVacuum,
                                    onClick = { selectedMode = "vacuum" },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = AccentTitanium,
                                        unselectedColor = Color.White.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Transfer Delivery Protocol Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DELIVERY PROTOCOL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.5f),
                            letterSpacing = 0.5.sp
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("hybrid" to "Hybrid Auto", "localsend" to "LocalSend", "direct" to "Direct HTTP").forEach { (method, label) ->
                                val isChosen = selectedTransferMethod == method
                                Surface(
                                    onClick = { selectedTransferMethod = method },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isChosen) Color.White.copy(alpha = 0.15f) else Color.Transparent,
                                    border = BorderStroke(1.dp, if (isChosen) AccentTitanium else BorderSubtle)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isChosen) Color.White else Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Error Notice Banner (if any)
                    if (errorMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SoftCoral.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, SoftCoral.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = SoftCoral, modifier = Modifier.size(18.dp))
                                Text(errorMessage ?: "", fontSize = 11.5.sp, color = SoftCoral, modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Main Action: Process & Fetch Media Button
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (!isServerConnected && serverIp.isBlank()) {
                                Toast.makeText(context, "Please connect to Zine Server first", Toast.LENGTH_SHORT).show()
                                showServerConfig = true
                                return@Button
                            }
                            if (urlInput.isBlank()) {
                                Toast.makeText(context, "Please paste a media URL to scrape", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            coroutineScope.launch {
                                isSubmitting = true
                                errorMessage = null
                                downloadedLocalDir = null

                                val result = ZineServerClient.submitScrape(
                                    serverHost = serverIp,
                                    serverPort = serverPort,
                                    mediaUrl = urlInput.trim(),
                                    mode = selectedMode,
                                    transferMethod = selectedTransferMethod
                                )
                                isSubmitting = false

                                result.onSuccess { task ->
                                    activeTask = task
                                    Toast.makeText(context, "Scrape task dispatched to Zine Server!", Toast.LENGTH_SHORT).show()
                                }.onFailure { err ->
                                    errorMessage = "Error submitting scrape: ${err.message}"
                                }
                            }
                        },
                        enabled = !isSubmitting && urlInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentTitanium,
                            disabledContainerColor = Color.White.copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = DarkOnyxBackground,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Dispatching to Zine Server...",
                                color = DarkOnyxBackground,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.CloudDownload,
                                contentDescription = null,
                                tint = DarkOnyxBackground,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Fetch & Ingest Media",
                                color = DarkOnyxBackground,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 4. Live Scrape Progress & Output Monitor
            activeTask?.let { task ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CardSurfaceColor,
                    border = BorderStroke(1.dp, BorderActive),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ACTIVE HARVEST MONITOR",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.6f),
                                letterSpacing = 0.5.sp
                            )

                            // Status Badge
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when (task.status) {
                                    "completed" -> SoftEmerald.copy(alpha = 0.15f)
                                    "failed" -> SoftCoral.copy(alpha = 0.15f)
                                    "scraping" -> AccentTitanium.copy(alpha = 0.15f)
                                    else -> SoftAmber.copy(alpha = 0.15f)
                                }
                            ) {
                                Text(
                                    text = task.status.uppercase(),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (task.status) {
                                        "completed" -> SoftEmerald
                                        "failed" -> SoftCoral
                                        "scraping" -> AccentTitanium
                                        else -> SoftAmber
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Media Title (from server output)
                        Text(
                            text = task.mediaTitle.ifBlank { "Media Ingestion Task #${task.taskId.take(8)}" },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Progress bar with percentage
                        val effectiveProgress = if (isDirectDownloading) directDownloadProgress else task.progress
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isDirectDownloading) "Direct ZIP stream to phone..." else task.message.ifBlank { "Harvesting content..." },
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${(effectiveProgress * 100).toInt()}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentTitanium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { effectiveProgress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = AccentTitanium,
                            trackColor = Color.White.copy(alpha = 0.10f)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // File count & mode badge
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (task.fileCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.White.copy(alpha = 0.07f)
                                ) {
                                    Text(
                                        text = "${task.fileCount} items",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White.copy(alpha = 0.07f)
                            ) {
                                Text(
                                    text = if (task.mode == "vacuum") "Vacuum Series" else "Quick Grab",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Completed / Direct Download Actions
                        if (task.status == "completed") {
                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = BorderSubtle)
                            Spacer(modifier = Modifier.height(12.dp))

                            if (downloadedLocalDir != null) {
                                Text(
                                    text = "✓ Harvested files saved to ${downloadedLocalDir?.absolutePath}",
                                    fontSize = 11.5.sp,
                                    color = SoftEmerald
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                if (onImportFolder != null) {
                                    Button(
                                        onClick = {
                                            downloadedLocalDir?.let { onImportFolder(it.absolutePath) }
                                            Toast.makeText(context, "Initiating import into Hwaran library...", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SoftEmerald.copy(alpha = 0.20f)),
                                        border = BorderStroke(1.dp, SoftEmerald.copy(alpha = 0.4f)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null, tint = SoftEmerald, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Import Harvest into Hwaran Library", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = SoftEmerald)
                                    }
                                }
                            } else {
                                // Direct HTTP download fallback button if file was sent via LocalSend or needs pull
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            isDirectDownloading = true
                                            val dlRes = ZineServerClient.downloadMediaZip(
                                                context = context,
                                                serverHost = serverIp,
                                                serverPort = serverPort,
                                                taskId = task.taskId,
                                                mode = task.mode,
                                                progressCb = { directDownloadProgress = it }
                                            )
                                            isDirectDownloading = false
                                            dlRes.onSuccess { dir ->
                                                downloadedLocalDir = dir
                                                Toast.makeText(context, "Archive saved to ${dir.name}", Toast.LENGTH_SHORT).show()
                                            }.onFailure { err ->
                                                errorMessage = "Direct stream failed: ${err.message}"
                                            }
                                        }
                                    },
                                    enabled = !isDirectDownloading,
                                    colors = ButtonDefaults.buttonColors(containerColor = CardSubtleSurface),
                                    border = BorderStroke(1.dp, BorderActive),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = null, tint = AccentTitanium, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Download Media ZIP Directly", fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }

                        // Mid-fail fallback button
                        if (task.status == "failed") {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Scrape failed: ${task.error.ifBlank { "Unknown server error" }}",
                                fontSize = 11.5.sp,
                                color = SoftCoral
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        isDirectDownloading = true
                                        val dlRes = ZineServerClient.downloadMediaZip(
                                            context = context,
                                            serverHost = serverIp,
                                            serverPort = serverPort,
                                            taskId = task.taskId,
                                            mode = task.mode
                                        )
                                        isDirectDownloading = false
                                        dlRes.onSuccess { dir ->
                                            downloadedLocalDir = dir
                                            Toast.makeText(context, "Recovered via direct archive stream!", Toast.LENGTH_SHORT).show()
                                        }.onFailure { err ->
                                            errorMessage = "Fallback failed: ${err.message}"
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SoftCoral.copy(alpha = 0.2f)),
                                border = BorderStroke(1.dp, SoftCoral.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Try Direct Archive Pull (Fallback)", fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            // 5. Zine Scraper Info & GitHub Setup Guide Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CardSurfaceColor,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Terminal,
                                    contentDescription = null,
                                    tint = AccentTitanium,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "ZINE SCRAPER SERVER SETUP",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    letterSpacing = 0.6.sp
                                )
                                Text(
                                    text = "Run headless server on your PC / server",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }

                        // Open GitHub Link Button
                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ZINE_GITHUB_URL)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.OpenInNew,
                                contentDescription = "GitHub",
                                tint = AccentTitanium,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "To scrape and transfer media to your phone, launch the companion Zine Scraper server on your computer connected to the same Wi-Fi network.",
                        fontSize = 11.5.sp,
                        color = Color.White.copy(alpha = 0.65f),
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Code box with setup commands
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Black.copy(alpha = 0.40f),
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = ZINE_SETUP_COMMANDS,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White.copy(alpha = 0.85f),
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = ZINE_GITHUB_URL,
                            fontSize = 10.5.sp,
                            color = AccentTitanium.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ZINE_GITHUB_URL)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {}
                                }
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val clip = android.content.ClipData.newPlainText("Zine Scraper Commands", ZINE_SETUP_COMMANDS)
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "Setup commands copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CardSubtleSurface),
                            border = BorderStroke(1.dp, BorderSubtle),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = AccentTitanium, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Commands", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
