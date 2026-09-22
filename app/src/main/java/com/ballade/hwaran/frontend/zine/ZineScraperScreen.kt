package com.ballade.hwaran.frontend.zine

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ballade.hwaran.R
import com.ballade.hwaran.core.network.ScrapeTaskInfo
import com.ballade.hwaran.core.network.ZineServerClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private val DarkOnyxBackground = Color(0xFF090A0F)
private val CardSurfaceColor = Color(0xFF141721)
private val CardSubtleSurface = Color(0xFF181C28)
private val AccentTitanium = Color(0xFFD4D8E0)
private val BorderSubtle = Color.White.copy(alpha = 0.10f)
private val BorderActive = Color.White.copy(alpha = 0.22f)
private val SoftEmerald = Color(0xFF6EE7B7)
private val SoftAmber = Color(0xFFFCD34D)
private val SoftCoral = Color(0xFFF87171)

private const val ZINE_GITHUB_URL = "https://github.com/valse-de-anshu/zine-scraper.git"
private const val ZINE_SETUP_COMMANDS = "git clone https://github.com/valse-de-anshu/zine-scraper.git\ncd zine-scraper\npip install -r requirements.txt\npython orchestrator.py --server"

data class ActivityLogEntry(
    val timestamp: String,
    val message: String,
    val isError: Boolean = false
)

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

    var isSearchingServer by remember { mutableStateOf(false) }
    var isServerConnected by remember { mutableStateOf(false) }
    var connectionLostMidTask by remember { mutableStateOf(false) }

    // Aesthetic Modal States (Shift bloat out of main screen)
    var showServerDialog by remember { mutableStateOf(false) }
    var showSetupGuideDialog by remember { mutableStateOf(false) }

    var urlInput by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf("quick_grab") } // "quick_grab" or "vacuum"
    var selectedTransferMethod by remember { mutableStateOf("hybrid") }

    var activeTask by remember { mutableStateOf<ScrapeTaskInfo?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var isDirectDownloading by remember { mutableStateOf(false) }
    var directDownloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedLocalDir by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Live signal activity log feed
    val activityLogs = remember { mutableStateListOf<ActivityLogEntry>() }

    fun addLog(msg: String, isError: Boolean = false) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        activityLogs.add(0, ActivityLogEntry(time, msg, isError))
        if (activityLogs.size > 20) activityLogs.removeLast()
    }

    // Blinking animation for minimal status indicator ball
    val infiniteTransition = rememberInfiniteTransition(label = "blinking_indicator")
    val blinkingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blinking_alpha"
    )
    val blinkingScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blinking_scale"
    )

    BackHandler {
        onNavigateBack()
    }

    // Auto-discover server or test saved IP on launch
    LaunchedEffect(Unit) {
        if (serverIp.isNotBlank()) {
            val alive = ZineServerClient.pingServer(serverIp, serverPort)
            if (alive) {
                isServerConnected = true
                addLog("Connected to Zine Server at $serverIp:$serverPort")
                return@LaunchedEffect
            }
        }

        isSearchingServer = true
        addLog("Scanning local subnet for Zine Scraper beacon...")
        val found = ZineServerClient.discoverServer(context, timeoutMs = 2000)
        isSearchingServer = false

        if (found != null) {
            serverIp = found.host
            serverPort = found.port
            isServerConnected = true
            prefs.edit().putString("server_ip", serverIp).putInt("server_port", serverPort).apply()
            addLog("Discovered Zine Server at ${found.host}:${found.port}")
        } else if (serverIp.isNotBlank()) {
            isServerConnected = ZineServerClient.pingServer(serverIp, serverPort)
            if (isServerConnected) {
                addLog("Connected to saved server $serverIp:$serverPort")
            } else {
                addLog("Zine server unreachable at $serverIp:$serverPort", isError = true)
            }
        } else {
            isServerConnected = false
            addLog("No server detected. Tap server icon to configure IP.", isError = true)
        }
    }

    // Task polling loop
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
                if (activeTask?.status != updated.status || activeTask?.message != updated.message) {
                    addLog("[Task ${updated.taskId.take(8)}] ${updated.status.uppercase()}: ${updated.message}")
                }
                activeTask = updated

                // Automatic direct download fallback if transfer method is direct or server instructs direct download
                if (updated.status == "completed" &&
                    (updated.message.contains("direct download", ignoreCase = true) || selectedTransferMethod == "direct")
                ) {
                    if (downloadedLocalDir == null && !isDirectDownloading) {
                        coroutineScope.launch {
                            isDirectDownloading = true
                            addLog("Initiating direct HTTP ZIP stream to phone storage...")
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
                                addLog("✓ Downloaded and extracted into ${dir.name}")
                                Toast.makeText(context, "Harvest saved into ${dir.name}", Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                errorMessage = "Failed to stream archive: ${err.message}"
                                addLog("Error downloading archive: ${err.message}", isError = true)
                            }
                        }
                    }
                }
            }.onFailure {
                consecutiveFailures++
                if (consecutiveFailures >= 3) {
                    connectionLostMidTask = true
                    addLog("Lost connection to Zine Server during task", isError = true)
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
            .displayCutoutPadding()
            .padding(top = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
        ) {
            // 1. Safe Top Bar: Back Button, Title with Status Dot, and Action Icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button + Header Title
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "ZINE SCRAPER",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                            // Minimal status indicator dot
                            Surface(
                                shape = CircleShape,
                                color = when {
                                    isServerConnected -> SoftEmerald
                                    isSearchingServer -> SoftAmber
                                    else -> SoftCoral
                                },
                                modifier = Modifier
                                    .size(8.dp)
                                    .scale(if (isSearchingServer || activeTask?.status == "scraping") blinkingScale else 1f)
                            ) {}
                        }
                        Text(
                            text = if (isServerConnected) "$serverIp:$serverPort" else "Local Media Ingestion",
                            fontSize = 11.5.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }

                // Top Aesthetic Action Icons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Server Connection & Subnet Scan Icon
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showServerDialog = true
                        },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.06f),
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isServerConnected) Icons.Rounded.Dns else Icons.Rounded.WifiOff,
                                contentDescription = "Server Settings",
                                tint = if (isServerConnected) SoftEmerald else AccentTitanium,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    // Original Setup Card / CLI Info Icon
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showSetupGuideDialog = true
                        },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.06f),
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Construction,
                                contentDescription = "CLI Tool Info",
                                tint = AccentTitanium,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // 2. Connection Warning (if disconnected or interrupted)
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
                        Icon(Icons.Rounded.WifiOff, contentDescription = null, tint = SoftCoral, modifier = Modifier.size(22.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Connection to Server Interrupted", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = SoftCoral)
                            Text(
                                "Polling lost connection to $serverIp. Your task is still running on the server.",
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
                                        addLog("Reconnected to Zine Server")
                                        Toast.makeText(context, "Reconnected!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Server still offline", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SoftCoral.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Reconnect", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            // 3. Centerpiece: Uncluttered & Spacious Link Processor Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = CardSurfaceColor,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "MEDIA LINK PROCESSOR",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.6f),
                        letterSpacing = 1.sp
                    )

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
                                text = "Paste media URL (manga, anime, novel, video)...",
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
                                            tint = Color.White.copy(alpha = 0.5f),
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

                    Spacer(modifier = Modifier.height(20.dp))

                    // Scrape Scope Selection: Elegant Dual Pills
                    Text(
                        text = "HARVEST SCOPE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.5f),
                        letterSpacing = 0.8.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val isQuickGrab = selectedMode == "quick_grab"
                        val isVacuum = selectedMode == "vacuum"

                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedMode = "quick_grab"
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isQuickGrab) Color.White.copy(alpha = 0.10f) else CardSubtleSurface,
                            border = BorderStroke(1.dp, if (isQuickGrab) AccentTitanium else BorderSubtle),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FlashOn,
                                        contentDescription = null,
                                        tint = if (isQuickGrab) Color.White else Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Quick Grab",
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isQuickGrab) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isQuickGrab) Color.White else Color.White.copy(alpha = 0.7f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Single chapter / clip",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.45f)
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedMode = "vacuum"
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isVacuum) Color.White.copy(alpha = 0.10f) else CardSubtleSurface,
                            border = BorderStroke(1.dp, if (isVacuum) AccentTitanium else BorderSubtle),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Layers,
                                        contentDescription = null,
                                        tint = if (isVacuum) Color.White else Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Vacuum",
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isVacuum) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isVacuum) Color.White else Color.White.copy(alpha = 0.7f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Full series run",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.45f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Delivery Protocol Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TRANSFER METHOD",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.5f),
                            letterSpacing = 0.6.sp
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("hybrid" to "Hybrid", "localsend" to "LocalSend", "direct" to "Direct").forEach { (method, label) ->
                                val isChosen = selectedTransferMethod == method
                                Surface(
                                    onClick = { selectedTransferMethod = method },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isChosen) Color.White.copy(alpha = 0.14f) else Color.Transparent,
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

                    Spacer(modifier = Modifier.height(22.dp))

                    // Error message banner
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
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Main Action: Transmit Signal to Server Button
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (!isServerConnected && serverIp.isBlank()) {
                                Toast.makeText(context, "Please connect to Zine Server first", Toast.LENGTH_SHORT).show()
                                showServerDialog = true
                                return@Button
                            }
                            if (urlInput.isBlank()) {
                                Toast.makeText(context, "Please paste a media URL", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            coroutineScope.launch {
                                isSubmitting = true
                                errorMessage = null
                                downloadedLocalDir = null

                                addLog("Transmitting ingestion signal from ${Build.MODEL} to $serverIp:$serverPort...")
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
                                    addLog("✓ Ingestion signal acknowledged! Task ID: ${task.taskId}")
                                    Toast.makeText(context, "Signal sent to Zine Server!", Toast.LENGTH_SHORT).show()
                                }.onFailure { err ->
                                    errorMessage = "Submission error: ${err.message}"
                                    addLog("Signal delivery failed: ${err.message}", isError = true)
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
                            .height(50.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = DarkOnyxBackground,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Transmitting Signal...",
                                color = DarkOnyxBackground,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Sensors,
                                contentDescription = null,
                                tint = DarkOnyxBackground,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Send Signal to Zine Server",
                                color = DarkOnyxBackground,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. Live Signal & Ingestion Monitor (With Minimal Blinking Ball)
            activeTask?.let { task ->
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
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Minimal Blinking Ball
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .scale(if (task.status in listOf("queued", "scraping", "analyzing")) blinkingScale else 1f)
                                        .background(
                                            color = when (task.status) {
                                                "completed" -> SoftEmerald
                                                "failed" -> SoftCoral
                                                else -> SoftAmber.copy(alpha = blinkingAlpha)
                                            },
                                            shape = CircleShape
                                        )
                                )

                                Text(
                                    text = "LIVE INGESTION MONITOR",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.7f),
                                    letterSpacing = 0.6.sp
                                )
                            }

                            // Status Pill
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when (task.status) {
                                    "completed" -> SoftEmerald.copy(alpha = 0.15f)
                                    "failed" -> SoftCoral.copy(alpha = 0.15f)
                                    else -> SoftAmber.copy(alpha = 0.15f)
                                }
                            ) {
                                Text(
                                    text = task.status.uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (task.status) {
                                        "completed" -> SoftEmerald
                                        "failed" -> SoftCoral
                                        else -> SoftAmber
                                    },
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Media Title
                        Text(
                            text = task.mediaTitle.ifBlank { "Task #${task.taskId.take(8)} · ${task.mode.replace('_', ' ')}" },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Effective Progress & percentage
                        val effectiveProgress = if (isDirectDownloading) directDownloadProgress else task.progress
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isDirectDownloading) "Direct ZIP stream to phone..." else task.message.ifBlank { "Awaiting server output..." },
                                fontSize = 11.5.sp,
                                color = Color.White.copy(alpha = 0.65f),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${(effectiveProgress * 100).toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentTitanium
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        LinearProgressIndicator(
                            progress = { effectiveProgress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = AccentTitanium,
                            trackColor = Color.White.copy(alpha = 0.10f)
                        )

                        // Completed / Direct Download Actions
                        if (task.status == "completed") {
                            Spacer(modifier = Modifier.height(12.dp))
                            if (downloadedLocalDir != null && onImportFolder != null) {
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
                                    Text("Import into Hwaran Library", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SoftEmerald)
                                }
                            } else {
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
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // 5. Minimal Activity Log Stream (Clean Terminal Style)
            if (activityLogs.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Black.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SIGNAL & ACTIVITY LOGS",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.45f),
                                letterSpacing = 0.6.sp
                            )
                            Text(
                                text = "${Build.MANUFACTURER} ${Build.MODEL}",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.35f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        activityLogs.take(5).forEach { log ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = log.timestamp,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White.copy(alpha = 0.35f)
                                )
                                Text(
                                    text = log.message,
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (log.isError) SoftCoral else Color.White.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // ==========================================
    // MODAL DIALOGS (Keeps main screen clean & unbloated)
    // ==========================================

    // 1. ORIGINAL Zine Scraper CLI Tool Dialog (User's preferred setup card)
    if (showSetupGuideDialog) {
        Dialog(onDismissRequest = { showSetupGuideDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = CardSurfaceColor,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Calm Minimal Tool Icon Header
                    Surface(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Construction,
                                contentDescription = "Zine Scraper Tool",
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Zine Scraper CLI Tool",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.06f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                    ) {
                        Text(
                            text = "Scrape & Build Local Media",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "A powerful CLI tool for downloading and organizing 18+, videos, music, books, images, metadata, anime, manhua, manga, and novels from supported websites directly into local folders. Perfectly compatible with the Hwaran app to seamlessly import and enjoy your media collection offline.",
                        fontSize = 12.5.sp,
                        color = Color.White.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Setup commands container
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Black.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = ZINE_SETUP_COMMANDS,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White.copy(alpha = 0.85f),
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Buttons
                    Button(
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(ZINE_GITHUB_URL)
                            )
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot open browser", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF222631),
                            contentColor = Color(0xFFE6E8EC)
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_github),
                            contentDescription = null,
                            tint = Color(0xFFE6E8EC),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "View CLI Scraper Repository",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFFE6E8EC)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = {
                            val clip = android.content.ClipData.newPlainText("Zine Commands", ZINE_SETUP_COMMANDS)
                            clipboard?.setPrimaryClip(clip)
                            Toast.makeText(context, "Commands copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = AccentTitanium, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Setup Commands", fontSize = 12.sp, color = AccentTitanium)
                    }

                    TextButton(
                        onClick = { showSetupGuideDialog = false },
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = "Close",
                            color = Color.White.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    // 2. Server Endpoint & Network Settings Dialog
    if (showServerDialog) {
        Dialog(onDismissRequest = { showServerDialog = false }) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = CardSurfaceColor,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
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
                                shape = CircleShape,
                                color = if (isServerConnected) SoftEmerald.copy(alpha = 0.15f) else SoftCoral.copy(alpha = 0.15f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isServerConnected) Icons.Rounded.Dns else Icons.Rounded.WifiOff,
                                        contentDescription = null,
                                        tint = if (isServerConnected) SoftEmerald else SoftCoral,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Column {
                                Text("Server Connection", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(
                                    text = if (isServerConnected) "Online & Connected" else "Offline / Not Found",
                                    fontSize = 11.sp,
                                    color = if (isServerConnected) SoftEmerald else SoftCoral
                                )
                            }
                        }

                        IconButton(
                            onClick = { showServerDialog = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.5f))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = serverIp,
                            onValueChange = { serverIp = it.trim() },
                            label = { Text("PC / Host IP", fontSize = 11.sp) },
                            placeholder = { Text("192.168.x.x", fontSize = 11.sp, color = Color.White.copy(alpha = 0.3f)) },
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

                    Spacer(modifier = Modifier.height(14.dp))

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
                                        addLog("Connected to $serverIp:$serverPort")
                                        Toast.makeText(context, "Connected successfully!", Toast.LENGTH_SHORT).show()
                                        showServerDialog = false
                                    } else {
                                        addLog("Ping to $serverIp:$serverPort failed", isError = true)
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
                                    addLog("Scanning LAN subnet for Zine beacon...")
                                    val found = ZineServerClient.discoverServer(context, timeoutMs = 2500)
                                    isSearchingServer = false
                                    if (found != null) {
                                        serverIp = found.host
                                        serverPort = found.port
                                        isServerConnected = true
                                        prefs.edit().putString("server_ip", serverIp).putInt("server_port", serverPort).apply()
                                        addLog("Discovered server at ${found.host}:${found.port}")
                                        Toast.makeText(context, "Found server at ${found.host}:${found.port}", Toast.LENGTH_SHORT).show()
                                        showServerDialog = false
                                    } else {
                                        addLog("No server beacon found on LAN", isError = true)
                                        Toast.makeText(context, "No beacon discovered on LAN", Toast.LENGTH_SHORT).show()
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
}
