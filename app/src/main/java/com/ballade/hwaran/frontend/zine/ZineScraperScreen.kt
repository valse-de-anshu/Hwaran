package com.ballade.hwaran.frontend.zine

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
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

private val DarkOnyxBackground = Color(0xFF07080C)
private val GlassSurface = Color(0xFF10131B)
private val AccentTitanium = Color(0xFFE0E3EB)
private val SubtleBorder = Color.White.copy(alpha = 0.08f)
private val ActiveBorder = Color.White.copy(alpha = 0.22f)
private val SoftEmerald = Color(0xFF6EE7B7)
private val SoftAmber = Color(0xFFFCD34D)
private val SoftCoral = Color(0xFFF87171)

private const val ZINE_GITHUB_URL = "https://github.com/valse-de-anshu/zine-scraper.git"

@Composable
fun LuminousPulsingBall(
    color: Color,
    isPulsing: Boolean,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 8.dp
) {
    if (isPulsing) {
        val infiniteTransition = rememberInfiniteTransition(label = "luminous_pulse")
        // Core contracts smoothly (goes small) and returns to normal
        val coreScale by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 0.65f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "core_scale"
        )
        // Outer faint glow pulses outward and dissolves
        val auraScale by infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 2.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "aura_scale"
        )
        val auraAlpha by infiniteTransition.animateFloat(
            initialValue = 0.38f,
            targetValue = 0.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "aura_alpha"
        )

        Box(
            modifier = modifier.size(size * 2.3f),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = auraAlpha),
                modifier = Modifier
                    .size(size)
                    .scale(auraScale)
            ) {}
            Surface(
                shape = CircleShape,
                color = color,
                modifier = Modifier
                    .size(size)
                    .scale(coreScale)
            ) {}
        }
    } else {
        Box(
            modifier = modifier.size(size * 2.3f),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.18f),
                modifier = Modifier
                    .size(size)
                    .scale(1.4f)
            ) {}
            Surface(
                shape = CircleShape,
                color = color,
                modifier = Modifier.size(size)
            ) {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZineScraperScreen(
    onNavigateBack: () -> Unit,
    onImportFolder: ((folderPath: String, openWhenDone: Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val clipboard = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager }
    val focusManager = LocalFocusManager.current
    var isInputFocused by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("hwaran_zine_server", Context.MODE_PRIVATE) }
    var serverIp by remember { mutableStateOf(prefs.getString("server_ip", "") ?: "") }
    var serverPort by remember { mutableIntStateOf(prefs.getInt("server_port", 53318)) }

    var isSearchingServer by remember { mutableStateOf(false) }
    var isServerConnected by remember { mutableStateOf(false) }

    // Modals for settings & tool info (zero main-screen clutter)
    var showServerDialog by remember { mutableStateOf(false) }
    var showSetupGuideDialog by remember { mutableStateOf(false) }

    var urlInput by remember { mutableStateOf("") }
    var selectedScopeKey by remember { mutableStateOf("single") } // "single" (--0), "next_5" (--5), "next_10" (--10), "all" (-a)
    var selectedMode by remember { mutableStateOf("quick_grab") } // "quick_grab" or "vacuum"
    var sequentialLimit by remember { mutableIntStateOf(0) }
    var flagMetaOnly by remember { mutableStateOf(false) } // --meta
    var isConfigExpanded by remember { mutableStateOf(false) }

    // Post-download processing choice: "import" (save to library), "open" (open immediately), "downloads" (raw storage only)
    var postDownloadAction by remember { mutableStateOf("import") }

    var activeTask by remember { mutableStateOf<ScrapeTaskInfo?>(null) }
    var tasksList by remember { mutableStateOf<List<ScrapeTaskInfo>>(emptyList()) }
    var isSubmitting by remember { mutableStateOf(false) }
    var isDirectDownloading by remember { mutableStateOf(false) }
    var directDownloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedLocalDir by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Animated breathing indicator for connection & active extraction
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_aura")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    // Faster pulsation for "almost downloaded" state
    val fastPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fast_pulse_alpha"
    )
    val fastPulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fast_pulse_scale"
    )

    BackHandler {
        onNavigateBack()
    }

    // 1. Initial Server Check & Automatic Active Task Recovery
    LaunchedEffect(serverIp, serverPort) {
        if (serverIp.isNotBlank()) {
            val alive = ZineServerClient.pingServer(serverIp, serverPort, timeoutMs = 3000)
            isServerConnected = alive
            if (alive) {
                // Auto-recover any ongoing or recent task on the companion server!
                ZineServerClient.getActiveTasks(serverIp, serverPort).onSuccess { list ->
                    tasksList = list
                    if (activeTask == null) {
                        list.firstOrNull { it.status !in listOf("failed") }?.let { activeTask = it }
                    }
                }
            }
        }

        if (!isServerConnected) {
            isSearchingServer = true
            val found = ZineServerClient.discoverServer(context, timeoutMs = 1500)
            isSearchingServer = false
            if (found != null) {
                serverIp = found.host
                serverPort = found.port
                isServerConnected = true
                prefs.edit().putString("server_ip", serverIp).putInt("server_port", serverPort).apply()
                // Auto-recover task on discovered server
                ZineServerClient.getActiveTasks(serverIp, serverPort).onSuccess { list ->
                    tasksList = list
                    if (activeTask == null) {
                        list.firstOrNull { it.status !in listOf("failed") }?.let { activeTask = it }
                    }
                }
            } else if (serverIp.isNotBlank()) {
                isServerConnected = ZineServerClient.pingServer(serverIp, serverPort, timeoutMs = 3000)
            } else {
                isServerConnected = false
            }
        }
    }

    // 2. Calm Idle Heartbeat Loop: Only runs when IDLE (never collides with active scrape/downloads)
    LaunchedEffect(serverIp, serverPort, activeTask?.status) {
        val isTaskActive = activeTask != null && activeTask?.status !in listOf("completed", "failed")
        if (isTaskActive) return@LaunchedEffect // Active task poller manages connection during downloads

        var consecutivePingFails = 0
        while (true) {
            delay(10000) // Calm 10-second check, no connection flapping or Wi-Fi congestion
            if (serverIp.isNotBlank()) {
                val alive = ZineServerClient.pingServer(serverIp, serverPort, timeoutMs = 4000)
                if (alive) {
                    consecutivePingFails = 0
                    if (!isServerConnected) {
                        isServerConnected = true
                        // Auto-recover task if one was triggered externally
                        ZineServerClient.getActiveTasks(serverIp, serverPort).onSuccess { list ->
                            tasksList = list
                            if (activeTask == null) {
                                list.firstOrNull { it.status !in listOf("failed") }?.let { activeTask = it }
                            }
                        }
                    }
                } else {
                    consecutivePingFails++
                    // Require 4 consecutive failures (40 seconds of continuous silence) before declaring disconnected
                    if (consecutivePingFails >= 4 && isServerConnected) {
                        isServerConnected = false
                    }
                }
            } else {
                isServerConnected = false
            }
        }
    }

    // 3. Resilient Active Task Polling: NEVER aborts or drops download card on temporary network hiccups
    LaunchedEffect(activeTask?.taskId) {
        val task = activeTask ?: return@LaunchedEffect
        if (task.status in listOf("completed", "failed")) return@LaunchedEffect

        var consecutiveFailures = 0
        while (true) {
            delay(1500)
            val res = ZineServerClient.getTaskStatus(serverIp, serverPort, task.taskId)
            res.onSuccess { updated ->
                consecutiveFailures = 0
                isServerConnected = true
                activeTask = updated
                tasksList = if (tasksList.any { it.taskId == updated.taskId }) {
                    tasksList.map { if (it.taskId == updated.taskId) updated else it }
                } else {
                    listOf(updated) + tasksList
                }

                // Automatically stream and extract media into Hwaran library when scrape completes
                if (updated.status == "completed") {
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
                                when (postDownloadAction) {
                                    "import" -> {
                                        onImportFolder?.invoke(dir.absolutePath, false)
                                        Toast.makeText(context, "Media saved and added to Library!", Toast.LENGTH_SHORT).show()
                                    }
                                    "open" -> {
                                        onImportFolder?.invoke(dir.absolutePath, true)
                                        Toast.makeText(context, "Ready! Opening media...", Toast.LENGTH_SHORT).show()
                                    }
                                    else -> {
                                        Toast.makeText(context, "Saved into Download/Zine Scraper/", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }.onFailure { err ->
                                errorMessage = "Download failed: ${err.message}"
                            }
                        }
                    }
                }
            }.onFailure {
                consecutiveFailures++
                // DO NOT fail the task or break the loop!
                // During heavy download/scrape, Wi-Fi latency can briefly spike.
                // We keep activeTask alive and retry indefinitely!
                if (consecutiveFailures >= 6) {
                    isServerConnected = false
                }
                // Back off slightly when network is unresponsive
                delay(1000)
            }

            if (activeTask?.status in listOf("completed", "failed")) break
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkOnyxBackground)
    ) {
        // Soft ambient aura in background — full height, fades out gradually
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1B2130).copy(alpha = 0.22f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Scrollable Content (Passes underneath the pinned top bar and fading blur)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
                .padding(top = 68.dp)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 2. Artistic Centerpiece: Server Artifact
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.05f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.server),
                        contentDescription = "Zine Server Artifact",
                        tint = AccentTitanium,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Fetch Remote Media",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Paste any supported link to ingest media directly from your PC",
                fontSize = 12.5.sp,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(26.dp))

            // 3. The Minimal Pill URL Input with unrestricted horizontal sliding
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color.White.copy(alpha = 0.04f),
                border = BorderStroke(1.dp, SubtleBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(19.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    BasicTextField(
                        value = urlInput,
                        onValueChange = {
                            urlInput = it
                            errorMessage = null
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = if (isInputFocused) SolidColor(Color.White) else SolidColor(Color.Transparent),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { isInputFocused = it.isFocused },
                        decorationBox = { innerTextField ->
                            if (urlInput.isEmpty()) {
                                Text(
                                    text = "Paste manga, anime or video URL...",
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.32f),
                                    maxLines = 1
                                )
                            }
                            innerTextField()
                        }
                    )

                    if (urlInput.isNotBlank()) {
                        IconButton(
                            onClick = {
                                urlInput = ""
                                focusManager.clearFocus()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "Clear",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                            if (clipText.isNotBlank()) {
                                urlInput = clipText.trim()
                                focusManager.clearFocus()
                                Toast.makeText(context, "Pasted link", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Clipboard empty", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, SubtleBorder)
                    ) {
                        Text(
                            text = "Paste",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentTitanium,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Post-Download Processing (After Download Action)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "AFTER DOWNLOAD ACTION",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.45f),
                    letterSpacing = 1.2.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Interactive Tag / Pill Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val actions = listOf(
                        Triple("import", "Save to Library", Icons.AutoMirrored.Rounded.DriveFileMove),
                        Triple("open", "Open / Play", Icons.Rounded.PlayArrow),
                        Triple("downloads", "Downloads Only", Icons.Rounded.Folder)
                    )

                    actions.forEach { (actionKey, label, icon) ->
                        val isSelected = postDownloadAction == actionKey
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                postDownloadAction = actionKey
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.035f),
                            border = BorderStroke(1.dp, if (isSelected) ActiveBorder else SubtleBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.45f),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.60f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = when (postDownloadAction) {
                        "import" -> "Auto-catalogs and stores files into your library via SAF"
                        "open" -> "Directly launches reader or video player when ready"
                        else -> "Leaves media in Download/Zine Scraper/ without importing"
                    },
                    fontSize = 10.5.sp,
                    color = Color.White.copy(alpha = 0.40f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Expandable "v" Chevron Button for Advanced Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isConfigExpanded = !isConfigExpanded
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = if (isConfigExpanded) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.04f),
                    border = BorderStroke(1.dp, if (isConfigExpanded) ActiveBorder else SubtleBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val hasCustomConfig = selectedScopeKey != "single" || flagMetaOnly
                        if (hasCustomConfig) {
                            Surface(
                                shape = CircleShape,
                                color = SoftEmerald,
                                modifier = Modifier.size(6.dp)
                            ) {}
                        }
                        Text(
                            text = if (isConfigExpanded) "Options" else "Configure",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isConfigExpanded) Color.White else Color.White.copy(alpha = 0.55f)
                        )
                        Icon(
                            imageVector = if (isConfigExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                            contentDescription = if (isConfigExpanded) "Collapse options" else "Expand options",
                            tint = if (isConfigExpanded) AccentTitanium else Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            // Expandable Configuration Section (All Flags Together + Delivery Mode)
            AnimatedVisibility(
                visible = isConfigExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = GlassSurface,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Card Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.08f),
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Terminal,
                                            contentDescription = null,
                                            tint = AccentTitanium,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "Scrape Scope & CLI Flags",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Command parameters passed to companion engine",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.45f)
                                    )
                                }
                            }

                            if (selectedScopeKey != "single" || flagMetaOnly) {
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedScopeKey = "single"
                                        selectedMode = "quick_grab"
                                        sequentialLimit = 0
                                        flagMetaOnly = false
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White.copy(alpha = 0.07f)
                                ) {
                                    Text(
                                        text = "Reset",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "INGESTION SCOPE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.40f),
                            letterSpacing = 1.1.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // 2x2 Scope Grid: Solid, clear cards with no cramped text
                        val scopes = listOf(
                            Triple("single", "Single Item", "--0"),
                            Triple("next_5", "Next 5", "--5"),
                            Triple("next_10", "Next 10", "--10"),
                            Triple("all", "All Media", "--a")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            scopes.take(2).forEach { (key, label, flag) ->
                                val isSelected = selectedScopeKey == key
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedScopeKey = key
                                        when (key) {
                                            "single" -> { selectedMode = "quick_grab"; sequentialLimit = 0 }
                                            "next_5" -> { selectedMode = "vacuum"; sequentialLimit = 5 }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.035f),
                                    border = BorderStroke(1.dp, if (isSelected) ActiveBorder else SubtleBorder),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.70f)
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSelected) AccentTitanium.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.05f)
                                        ) {
                                            Text(
                                                text = flag,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isSelected) AccentTitanium else Color.White.copy(alpha = 0.45f),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            scopes.drop(2).forEach { (key, label, flag) ->
                                val isSelected = selectedScopeKey == key
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedScopeKey = key
                                        when (key) {
                                            "next_10" -> { selectedMode = "vacuum"; sequentialLimit = 10 }
                                            "all" -> { selectedMode = "vacuum"; sequentialLimit = 0 }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.035f),
                                    border = BorderStroke(1.dp, if (isSelected) ActiveBorder else SubtleBorder),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.70f)
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSelected) AccentTitanium.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.05f)
                                        ) {
                                            Text(
                                                text = flag,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isSelected) AccentTitanium else Color.White.copy(alpha = 0.45f),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "ADDITIONAL FLAGS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.40f),
                            letterSpacing = 1.1.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Metadata Only Flag Toggle Card (NO EMOJIS, clean icon)
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                flagMetaOnly = !flagMetaOnly
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (flagMetaOnly) SoftEmerald.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.035f),
                            border = BorderStroke(1.dp, if (flagMetaOnly) SoftEmerald.copy(alpha = 0.45f) else SubtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Description,
                                        contentDescription = null,
                                        tint = if (flagMetaOnly) SoftEmerald else Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Metadata Only (--meta)",
                                            fontSize = 12.sp,
                                            fontWeight = if (flagMetaOnly) FontWeight.Bold else FontWeight.Medium,
                                            color = if (flagMetaOnly) SoftEmerald else Color.White.copy(alpha = 0.85f)
                                        )
                                        Text(
                                            text = "Fetch synopsis & cover without downloading chapter files",
                                            fontSize = 10.5.sp,
                                            color = Color.White.copy(alpha = 0.40f)
                                        )
                                    }
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = if (flagMetaOnly) SoftEmerald else Color.White.copy(alpha = 0.10f),
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    if (flagMetaOnly) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = DarkOnyxBackground,
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Terminal Command Preview Line
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Code,
                                    contentDescription = null,
                                    tint = AccentTitanium.copy(alpha = 0.4f),
                                    modifier = Modifier.size(13.dp)
                                )
                                val flagPreview = buildString {
                                    append("zine ")
                                    when (selectedScopeKey) {
                                        "single" -> append("--0 ")
                                        "next_5" -> append("--5 ")
                                        "next_10" -> append("--10 ")
                                        "all" -> append("--a ")
                                    }
                                    if (flagMetaOnly) append("--meta ")
                                    append(if (urlInput.isNotBlank()) urlInput.take(28) + if (urlInput.length > 28) "..." else "" else "<url>")
                                }
                                Text(
                                    text = flagPreview,
                                    fontSize = 11.sp,
                                    color = AccentTitanium.copy(alpha = 0.70f),
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 6. Signal Transmission Button ("Send Signal" with Signal Tower Icon)
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    focusManager.clearFocus()
                    // Automatically collapse "v" section when sending signal
                    isConfigExpanded = false

                    if (!isServerConnected) {
                        Toast.makeText(context, "Companion server offline. Run 'zine --server' on your PC", Toast.LENGTH_SHORT).show()
                        showServerDialog = true
                        return@Button
                    }
                    if (urlInput.isBlank()) {
                        Toast.makeText(context, "Please enter a valid media link", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    coroutineScope.launch {
                        isSubmitting = true
                        errorMessage = null
                        downloadedLocalDir = null

                        val flags = mutableListOf<String>()
                        when (selectedScopeKey) {
                            "single" -> flags.add("--0")
                            "next_5" -> flags.add("--5")
                            "next_10" -> flags.add("--10")
                            "all" -> flags.add("--a")
                        }
                        if (flagMetaOnly) flags.add("--meta")

                        val limitVal = when (selectedScopeKey) {
                            "next_5" -> 5
                            "next_10" -> 10
                            else -> null
                        }

                        val result = ZineServerClient.submitScrape(
                            serverHost = serverIp,
                            serverPort = serverPort,
                            mediaUrl = urlInput.trim(),
                            mode = if (selectedScopeKey == "single") "quick_grab" else "vacuum",
                            transferMethod = "direct",
                            clientIp = getLocalDeviceIp(),
                            flags = flags,
                            limit = limitVal
                        )
                        isSubmitting = false

                        result.onSuccess { task ->
                            activeTask = task
                            tasksList = listOf(task) + tasksList.filter { it.taskId != task.taskId }
                            Toast.makeText(context, "Signal sent to Zine Server!", Toast.LENGTH_SHORT).show()
                        }.onFailure { err ->
                            errorMessage = "Failed to dispatch: ${err.message}"
                        }
                    }
                },
                enabled = !isSubmitting && (urlInput.isNotBlank() || !isServerConnected),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isServerConnected) AccentTitanium else SoftCoral.copy(alpha = 0.88f),
                    disabledContainerColor = Color.White.copy(alpha = 0.08f)
                ),
                shape = RoundedCornerShape(26.dp),
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
                        text = "Transmitting Signal...",
                        color = DarkOnyxBackground,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else if (!isServerConnected) {
                    Icon(
                        imageVector = Icons.Rounded.Storage,
                        contentDescription = null,
                        tint = DarkOnyxBackground,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Server Offline (Tap to Configure)",
                        color = DarkOnyxBackground,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.CellTower,
                        contentDescription = null,
                        tint = DarkOnyxBackground,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Send Signal",
                        color = DarkOnyxBackground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 7. Tasks & Transfers Section (Showing all tasks + Clear All button)
            val displayTasks = remember(tasksList, activeTask) {
                val combined = mutableListOf<ScrapeTaskInfo>()
                activeTask?.let { combined.add(it) }
                tasksList.forEach { t ->
                    if (combined.none { it.taskId == t.taskId }) {
                        combined.add(t)
                    }
                }
                combined
            }

            if (displayTasks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TASKS & TRANSFERS (${displayTasks.size})",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.45f),
                        letterSpacing = 1.2.sp
                    )

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            coroutineScope.launch {
                                ZineServerClient.clearAllTasks(serverIp, serverPort)
                                activeTask = null
                                tasksList = emptyList()
                                Toast.makeText(context, "All tasks cleared", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.05f),
                        border = BorderStroke(1.dp, SubtleBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = "Clear All Tasks",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Clear All",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    displayTasks.forEach { task ->
                        val isTaskCompleted = (task.status == "completed" && !(task.taskId == activeTask?.taskId && isDirectDownloading))
                        val isTaskFailed = (task.status == "failed")
                        val isTaskAlmostDone = (!isTaskCompleted && !isTaskFailed && (
                            (task.taskId == activeTask?.taskId && isDirectDownloading) ||
                            task.status == "transferring" ||
                            task.progress >= 0.70f ||
                            task.message.contains("almost", ignoreCase = true) ||
                            task.message.contains("direct download", ignoreCase = true) ||
                            task.message.contains("Preparing transfer", ignoreCase = true)
                        ))

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = GlassSurface,
                            border = BorderStroke(1.dp, SubtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Pulsing Ball Indicator
                                    LuminousPulsingBall(
                                        color = when {
                                            isTaskCompleted -> SoftEmerald
                                            isTaskFailed -> SoftCoral
                                            isTaskAlmostDone -> SoftEmerald
                                            else -> SoftAmber
                                        },
                                        isPulsing = !isTaskCompleted && !isTaskFailed,
                                        size = 9.dp
                                    )

                                    Column {
                                        Text(
                                            text = when {
                                                isTaskCompleted -> "Task Complete"
                                                isTaskFailed -> "Download Failed"
                                                isTaskAlmostDone -> "Almost downloaded..."
                                                else -> "Downloading..."
                                            },
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                isTaskCompleted -> SoftEmerald
                                                isTaskFailed -> SoftCoral
                                                isTaskAlmostDone -> SoftEmerald
                                                else -> Color.White.copy(alpha = 0.95f)
                                            }
                                        )
                                        Text(
                                            text = task.mediaTitle.ifBlank {
                                                if (task.taskId == activeTask?.taskId && isDirectDownloading) "Direct ZIP pull..."
                                                else task.message.ifBlank { "Task #${task.taskId.take(8)}" }
                                            },
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.50f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (isTaskCompleted && downloadedLocalDir != null && onImportFolder != null && task.taskId == activeTask?.taskId) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (postDownloadAction == "open" || postDownloadAction == "downloads") {
                                            Surface(
                                                onClick = {
                                                    downloadedLocalDir?.let { onImportFolder(it.absolutePath, true) }
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                color = SoftEmerald.copy(alpha = 0.15f),
                                                border = BorderStroke(1.dp, SoftEmerald.copy(alpha = 0.40f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.PlayArrow,
                                                        contentDescription = "Open",
                                                        tint = SoftEmerald,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Text(
                                                        text = "Open",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = SoftEmerald
                                                    )
                                                }
                                            }
                                        }
                                        if (postDownloadAction == "import" || postDownloadAction == "downloads") {
                                            Surface(
                                                onClick = {
                                                    downloadedLocalDir?.let { onImportFolder(it.absolutePath, false) }
                                                    Toast.makeText(context, "Importing to library...", Toast.LENGTH_SHORT).show()
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color.White.copy(alpha = 0.08f),
                                                border = BorderStroke(1.dp, SubtleBorder)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Rounded.DriveFileMove,
                                                        contentDescription = "Import",
                                                        tint = AccentTitanium,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Text(
                                                        text = "Import",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = AccentTitanium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }

        // Pinned Top Navigation Bar with Fading Blur Gradient directly underneath
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkOnyxBackground.copy(alpha = 0.95f))
                    .statusBarsPadding()
                    .displayCutoutPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateBack()
                    },
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, SubtleBorder),
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

                // Title & Connection Light
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LuminousPulsingBall(
                        color = when {
                            isServerConnected -> SoftEmerald
                            isSearchingServer -> SoftAmber
                            else -> SoftCoral
                        },
                        isPulsing = isSearchingServer || (activeTask?.status in listOf("scraping", "analyzing", "transferring")),
                        size = 7.dp
                    )

                    Text(
                        text = if (isServerConnected) "ZINE BRIDGE" else "BRIDGE OFFLINE",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isServerConnected) Color.White.copy(alpha = 0.90f) else SoftCoral,
                        letterSpacing = 1.6.sp
                    )
                }

                // Top Actions (Normal Server Icon & Vacuum Icon)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showServerDialog = true
                        },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.05f),
                        border = BorderStroke(1.dp, SubtleBorder),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isServerConnected) Icons.Rounded.Dns else Icons.Rounded.Storage,
                                contentDescription = "Server Network",
                                tint = if (isServerConnected) SoftEmerald else SoftCoral,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showSetupGuideDialog = true
                        },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.05f),
                        border = BorderStroke(1.dp, SubtleBorder),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.vacum),
                                contentDescription = "Zine Scraper Tool",
                                tint = AccentTitanium,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Graceful fading gradient directly below top bar so scrolling content fades smoothly
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                DarkOnyxBackground.copy(alpha = 0.95f),
                                DarkOnyxBackground.copy(alpha = 0.40f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }

    // ==========================================
    // MODALS (Zero bloat on the main screen)
    // ==========================================

    // 1. ORIGINAL Zine Scraper CLI Tool Dialog (Updated with new vacuum icon and NO command box)
    if (showSetupGuideDialog) {
        Dialog(onDismissRequest = { showSetupGuideDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = GlassSurface,
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
                    // Vacuum Icon Header
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
                                painter = painterResource(id = R.drawable.vacum),
                                contentDescription = "Zine Scraper Tool",
                                tint = AccentTitanium,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Zine Scraper CLI Tool",
                        fontSize = 18.sp,
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
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                        lineHeight = 17.5.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // View Repository Button (Clean, NO command block)
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

                    Spacer(modifier = Modifier.height(10.dp))

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

    // 2. Server Endpoint & Discovery Dialog
    if (showServerDialog) {
        Dialog(onDismissRequest = { showServerDialog = false }) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = GlassSurface,
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
                                        imageVector = if (isServerConnected) Icons.Rounded.Dns else Icons.Rounded.Storage,
                                        contentDescription = null,
                                        tint = if (isServerConnected) SoftEmerald else SoftCoral,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Column {
                                Text("Server Link", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(
                                    text = if (isServerConnected) "Online · Connected" else "Offline / Not detected",
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
                            label = { Text("Host IP", fontSize = 11.sp) },
                            placeholder = { Text("192.168.x.x", fontSize = 11.sp, color = Color.White.copy(alpha = 0.3f)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AccentTitanium,
                                unfocusedBorderColor = SubtleBorder
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
                                unfocusedBorderColor = SubtleBorder
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
                                        Toast.makeText(context, "Connected!", Toast.LENGTH_SHORT).show()
                                        showServerDialog = false
                                    } else {
                                        Toast.makeText(context, "Cannot reach $serverIp:$serverPort", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                            border = BorderStroke(1.dp, ActiveBorder),
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
                                        isServerConnected = true
                                        prefs.edit().putString("server_ip", serverIp).putInt("server_port", serverPort).apply()
                                        Toast.makeText(context, "Found server at ${found.host}:${found.port}", Toast.LENGTH_SHORT).show()
                                        showServerDialog = false
                                    } else {
                                        Toast.makeText(context, "No beacon on LAN", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                            border = BorderStroke(1.dp, ActiveBorder),
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

private fun getLocalDeviceIp(): String = ZineServerClient.getLocalDeviceIp()
