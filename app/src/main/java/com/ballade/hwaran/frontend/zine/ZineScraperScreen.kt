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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
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

    // Modals for settings & tool info (zero main-screen clutter)
    var showServerDialog by remember { mutableStateOf(false) }
    var showSetupGuideDialog by remember { mutableStateOf(false) }

    var urlInput by remember { mutableStateOf("") }
    var selectedScopeKey by remember { mutableStateOf("single") } // "single" (--0), "next_5" (--5), "next_10" (--10), "all" (-a)
    var selectedMode by remember { mutableStateOf("quick_grab") } // "quick_grab" or "vacuum"
    var sequentialLimit by remember { mutableIntStateOf(0) }
    var flagMetaOnly by remember { mutableStateOf(false) } // --meta
    var isConfigExpanded by remember { mutableStateOf(false) }
    var selectedTransferMethod by remember { mutableStateOf("hybrid") }

    // Post-download processing choice: "import" (save to library), "open" (open immediately), "downloads" (raw storage only)
    var postDownloadAction by remember { mutableStateOf("import") }

    var activeTask by remember { mutableStateOf<ScrapeTaskInfo?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var isDirectDownloading by remember { mutableStateOf(false) }
    var directDownloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedLocalDir by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var didLaunchLocalSend by remember { mutableStateOf(false) }

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

    // 1. Continuous Heartbeat Loop & Background Discovery
    LaunchedEffect(serverIp, serverPort) {
        // Immediate initial check
        if (serverIp.isNotBlank()) {
            val alive = ZineServerClient.pingServer(serverIp, serverPort, timeoutMs = 1000)
            isServerConnected = alive
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
            } else if (serverIp.isNotBlank()) {
                isServerConnected = ZineServerClient.pingServer(serverIp, serverPort, timeoutMs = 1000)
            } else {
                isServerConnected = false
            }
        }

        // Active heartbeat polling every 2.5 seconds: debounced to prevent false disconnects during heavy network traffic
        var consecutivePingFails = 0
        while (true) {
            delay(2500)
            if (serverIp.isNotBlank()) {
                val alive = ZineServerClient.pingServer(serverIp, serverPort, timeoutMs = 2500)
                if (alive) {
                    consecutivePingFails = 0
                    if (!isServerConnected) {
                        isServerConnected = true
                    }
                } else {
                    consecutivePingFails++
                    if (consecutivePingFails >= 2 && isServerConnected) {
                        isServerConnected = false
                    }
                }
                // If disconnected for 2+ consecutive checks, attempt background discovery in case IP changed
                if (!alive && consecutivePingFails >= 2) {
                    val quickFound = ZineServerClient.discoverServer(context, timeoutMs = 800)
                    if (quickFound != null) {
                        serverIp = quickFound.host
                        serverPort = quickFound.port
                        isServerConnected = true
                        consecutivePingFails = 0
                        prefs.edit().putString("server_ip", serverIp).putInt("server_port", serverPort).apply()
                    }
                }
            } else {
                isServerConnected = false
            }
        }
    }

    // 2. Active Task polling loop with connection loss detection
    LaunchedEffect(activeTask?.taskId) {
        val task = activeTask ?: return@LaunchedEffect
        if (task.status in listOf("completed", "failed")) return@LaunchedEffect

        var consecutiveFailures = 0
        while (true) {
            delay(1200)
            val res = ZineServerClient.getTaskStatus(serverIp, serverPort, task.taskId)
            res.onSuccess { updated ->
                consecutiveFailures = 0
                isServerConnected = true
                activeTask = updated

                // Automatically launch LocalSend on phone when server initiates LocalSend delivery
                val isLocalSendTransfer = updated.status == "transferring" || updated.message.contains("LocalSend", ignoreCase = true)
                if (isLocalSendTransfer && !didLaunchLocalSend) {
                    didLaunchLocalSend = true
                    openLocalSendApp(context)
                }

                // If completed or failed and we opened LocalSend, automatically bring Hwaran back to the front!
                if (updated.status in listOf("completed", "failed") && didLaunchLocalSend) {
                    didLaunchLocalSend = false
                    coroutineScope.launch {
                        delay(1200)
                        bringHwaranToFront(context)
                    }
                }

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
                                when (postDownloadAction) {
                                    "import" -> {
                                        onImportFolder?.invoke(dir.absolutePath)
                                        Toast.makeText(context, "Media saved and added to Library!", Toast.LENGTH_SHORT).show()
                                    }
                                    "open" -> {
                                        onImportFolder?.invoke(dir.absolutePath)
                                        Toast.makeText(context, "Ready! Opening media...", Toast.LENGTH_SHORT).show()
                                    }
                                    else -> {
                                        Toast.makeText(context, "Saved into Download/Zine Scraper/", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }.onFailure { err ->
                                errorMessage = "Stream failed: ${err.message}"
                            }
                        }
                    }
                }
            }.onFailure {
                consecutiveFailures++
                if (consecutiveFailures >= 2) {
                    isServerConnected = false
                    if (activeTask?.status !in listOf("completed", "failed")) {
                        activeTask = activeTask?.copy(
                            status = "failed",
                            message = "Server stopped responding",
                            error = "Companion server connection lost"
                        )
                    }
                }
            }

            if (activeTask?.status in listOf("completed", "failed")) break
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkOnyxBackground)
    ) {
        // Soft ambient aura in background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(440.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1B2130).copy(alpha = 0.40f),
                            Color.Transparent
                        ),
                        radius = 700f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
                .padding(top = 18.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Safe Top Navigation Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                    Surface(
                        shape = CircleShape,
                        color = when {
                            isServerConnected -> SoftEmerald
                            isSearchingServer -> SoftAmber.copy(alpha = pulseAlpha)
                            else -> SoftCoral
                        },
                        modifier = Modifier
                            .size(7.dp)
                            .scale(if (isSearchingServer || activeTask?.status in listOf("scraping", "analyzing")) pulseScale else 1f)
                    ) {}

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
                    // Normal Server Icon
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

                    // Vacuum Icon for CLI Pop up
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

            Spacer(modifier = Modifier.height(34.dp))

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

            Spacer(modifier = Modifier.height(30.dp))

            // 3. The Minimal Pill URL Input
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color.White.copy(alpha = 0.04f),
                border = BorderStroke(1.dp, SubtleBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(19.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = {
                            urlInput = it
                            errorMessage = null
                        },
                        placeholder = {
                            Text(
                                text = "Paste manga, anime or video URL...",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.32f)
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    if (urlInput.isNotBlank()) {
                        IconButton(
                            onClick = { urlInput = "" },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "Clear",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                            if (clipText.isNotBlank()) {
                                urlInput = clipText.trim()
                                Toast.makeText(context, "Pasted", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Clipboard empty", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, SubtleBorder),
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = "Paste",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentTitanium,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
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

            // Expandable Configuration Section (Scope & Metadata Flag)
            AnimatedVisibility(
                visible = isConfigExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "DOWNLOAD SCOPE & FLAGS",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.45f),
                        letterSpacing = 1.2.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Scope & Quantity Pills
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White.copy(alpha = 0.035f),
                        border = BorderStroke(1.dp, SubtleBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val scopes = listOf(
                                "single" to "Single (--0)",
                                "next_5" to "Next 5 (--5)",
                                "next_10" to "Next 10 (--10)",
                                "all" to "All (-a)"
                            )

                            scopes.forEach { (key, label) ->
                                val isSelected = selectedScopeKey == key
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedScopeKey = key
                                        when (key) {
                                            "single" -> {
                                                selectedMode = "quick_grab"
                                                sequentialLimit = 0
                                            }
                                            "next_5" -> {
                                                selectedMode = "vacuum"
                                                sequentialLimit = 5
                                            }
                                            "next_10" -> {
                                                selectedMode = "vacuum"
                                                sequentialLimit = 10
                                            }
                                            "all" -> {
                                                selectedMode = "vacuum"
                                                sequentialLimit = 0
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) Color.White.copy(alpha = 0.12f) else Color.Transparent,
                                    border = BorderStroke(1.dp, if (isSelected) ActiveBorder else Color.Transparent),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.50f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Metadata Only Flag Pill
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            flagMetaOnly = !flagMetaOnly
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (flagMetaOnly) SoftEmerald.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.035f),
                        border = BorderStroke(1.dp, if (flagMetaOnly) SoftEmerald.copy(alpha = 0.6f) else SubtleBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(text = "📑", fontSize = 13.sp)
                                Text(
                                    text = "Metadata Only (--meta)",
                                    fontSize = 11.5.sp,
                                    fontWeight = if (flagMetaOnly) FontWeight.Bold else FontWeight.Normal,
                                    color = if (flagMetaOnly) SoftEmerald else Color.White.copy(alpha = 0.70f)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (flagMetaOnly) SoftEmerald else Color.White.copy(alpha = 0.1f),
                                modifier = Modifier.size(width = 34.dp, height = 18.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = if (flagMetaOnly) Alignment.CenterEnd else Alignment.CenterStart
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (flagMetaOnly) Color.Black else Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier.padding(2.dp).size(14.dp)
                                    ) {}
                                }
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
                            "all" -> flags.add("-a")
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
                            transferMethod = selectedTransferMethod,
                            flags = flags,
                            limit = limitVal
                        )
                        isSubmitting = false

                        result.onSuccess { task ->
                            activeTask = task
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

            // 7. Live Ingestion Status Pill (Minimal Zen blinking ball, no progress bar)
            activeTask?.let { task ->
                val isCompleted = (task.status == "completed" && !isDirectDownloading)
                val isFailed = (task.status == "failed")
                val isAlmostDone = (!isCompleted && !isFailed && (
                    isDirectDownloading ||
                    task.status == "transferring" ||
                    task.progress >= 0.70f ||
                    task.message.contains("almost", ignoreCase = true) ||
                    task.message.contains("direct download", ignoreCase = true) ||
                    task.message.contains("Preparing transfer", ignoreCase = true)
                ))

                Spacer(modifier = Modifier.height(24.dp))

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = GlassSurface,
                    border = BorderStroke(1.dp, SubtleBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // Blinking Ball (Still when complete, fast pulse when almost done, normal pulse when downloading)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(20.dp)
                            ) {
                                if (!isCompleted && !isFailed) {
                                    Surface(
                                        shape = CircleShape,
                                        color = (if (isAlmostDone) SoftEmerald else SoftAmber).copy(
                                            alpha = (if (isAlmostDone) fastPulseAlpha else pulseAlpha) * 0.35f
                                        ),
                                        modifier = Modifier
                                            .size(20.dp)
                                            .scale(if (isAlmostDone) fastPulseScale else pulseScale)
                                    ) {}
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = when {
                                        isCompleted -> SoftEmerald
                                        isFailed -> SoftCoral
                                        isAlmostDone -> SoftEmerald
                                        else -> SoftAmber
                                    },
                                    modifier = Modifier
                                        .size(10.dp)
                                        .scale(
                                            when {
                                                isCompleted || isFailed -> 1f
                                                isAlmostDone -> fastPulseScale
                                                else -> pulseScale
                                            }
                                        )
                                        .alpha(
                                            when {
                                                isCompleted || isFailed -> 1f
                                                isAlmostDone -> fastPulseAlpha
                                                else -> pulseAlpha
                                            }
                                        )
                                ) {}
                            }

                            Column {
                                Text(
                                    text = when {
                                        isCompleted -> "Task Complete"
                                        isFailed -> "Download Failed"
                                        isAlmostDone -> "Almost downloaded..."
                                        else -> "Downloading..."
                                    },
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        isCompleted -> SoftEmerald
                                        isFailed -> SoftCoral
                                        isAlmostDone -> SoftEmerald
                                        else -> Color.White.copy(alpha = 0.95f)
                                    }
                                )
                                Text(
                                    text = task.mediaTitle.ifBlank {
                                        if (isDirectDownloading) "Direct ZIP pull..."
                                        else task.message.ifBlank { "Task #${task.taskId.take(8)}" }
                                    },
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.50f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (isCompleted && downloadedLocalDir != null && onImportFolder != null) {
                            Text(
                                text = "Import",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SoftEmerald,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clickable {
                                        downloadedLocalDir?.let { onImportFolder(it.absolutePath) }
                                        Toast.makeText(context, "Importing to library...", Toast.LENGTH_SHORT).show()
                                    }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
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

private fun openLocalSendApp(context: Context) {
    try {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage("org.localsend.localsend_app")
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        }
    } catch (e: Exception) {
        // LocalSend not installed
    }
}

private fun bringHwaranToFront(context: Context) {
    try {
        val intent = Intent(context, com.ballade.hwaran.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {}
}
