package com.ballade.hwaran.frontend.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.*
import com.ballade.hwaran.ui.components.DeleteConfirmationDialog
import com.ballade.hwaran.ui.theme.AVAILABLE_APP_THEMES
import com.ballade.hwaran.ui.theme.AccentTitanium
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ballade.hwaran.R
import com.ballade.hwaran.core.database.AppDatabase
import com.ballade.hwaran.ui.components.DevilJellyBall
import com.ballade.hwaran.ui.components.JellyBall
import com.ballade.hwaran.ui.components.JellyToggle
import com.ballade.hwaran.ui.components.JellyToggle3
import com.ballade.hwaran.ui.dialogs.PremiumGlassPanel
import com.ballade.hwaran.ui.dialogs.PremiumPillDefaults
import com.ballade.hwaran.ui.dialogs.PremiumSlider
import com.ballade.hwaran.ui.dialogs.SidebarIcon
import com.ballade.hwaran.ui.dialogs.ZineScraperDialog
import com.ballade.hwaran.ui.viewmodels.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PremiumSection {
    CONFIG, APPEARANCE, ENVIRONMENT, ADD_BUTTON, SECURITY, ABOUT
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToLockSelection: () -> Unit = {},
    onNavigateToCanvas: () -> Unit = {}
) {
    var activeSection by remember { mutableStateOf<PremiumSection?>(PremiumSection.CONFIG) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Smooth entrance animation for the rail
    var isRailVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        isRailVisible = true
    }

    var showZineScraperDialog by remember { mutableStateOf(false) }

    if (showZineScraperDialog) {
        ZineScraperDialog(onDismiss = { showZineScraperDialog = false })
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {

            // Top Header Bar (Back button on left, Tool icon button on right)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.9f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(top = 40.dp, bottom = 32.dp, start = 8.dp, end = 16.dp)
                    .zIndex(10f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }
            }

            // Main Layout: Rail + Animated Content Panel
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = padding.calculateBottomPadding())
                    .padding(top = if (isLandscape) 64.dp else 160.dp, bottom = if (isLandscape) 16.dp else 32.dp),
                verticalAlignment = Alignment.Top
            ) {

                // --- THE NAVIGATION RAIL ---
                AnimatedVisibility(
                    visible = isRailVisible,
                    enter = slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(350, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(350)),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp)
                ) {
                    PremiumGlassPanel(modifier = Modifier.wrapContentSize()) {
                        Column(
                            modifier = Modifier
                                .heightIn(max = if (isLandscape) 320.dp else 800.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = if (isLandscape) 12.dp else 24.dp, horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 12.dp else 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            SidebarIcon(symbol = "´ཀ`", isSelected = activeSection == PremiumSection.CONFIG) {
                                activeSection = if (activeSection == PremiumSection.CONFIG) null else PremiumSection.CONFIG
                            }
                            SidebarIcon(symbol = "ִֶָ࣪☾", isSelected = activeSection == PremiumSection.APPEARANCE) {
                                activeSection = if (activeSection == PremiumSection.APPEARANCE) null else PremiumSection.APPEARANCE
                            }
                            SidebarIcon(symbol = "𓏲ּ𝄢", isSelected = activeSection == PremiumSection.ENVIRONMENT) {
                                activeSection = if (activeSection == PremiumSection.ENVIRONMENT) null else PremiumSection.ENVIRONMENT
                            }
                            SidebarIcon(symbol = "ᯓ⚽︎", isSelected = activeSection == PremiumSection.ADD_BUTTON) {
                                activeSection = if (activeSection == PremiumSection.ADD_BUTTON) null else PremiumSection.ADD_BUTTON
                            }
                            SidebarIcon(symbol = "ꗃꄗ", isSelected = activeSection == PremiumSection.SECURITY) {
                                activeSection = if (activeSection == PremiumSection.SECURITY) null else PremiumSection.SECURITY
                            }
                            SidebarIcon(symbol = "ᝰ.", isSelected = activeSection == PremiumSection.ABOUT) {
                                activeSection = if (activeSection == PremiumSection.ABOUT) null else PremiumSection.ABOUT
                            }
                        }
                    }
                }

                // --- THE EXPANDING CONTENT PANEL ---
                val isCompactSection = activeSection == PremiumSection.ADD_BUTTON
                AnimatedVisibility(
                    visible = activeSection != null,
                    enter = fadeIn(tween(250)) + scaleIn(
                        initialScale = 0.96f,
                        animationSpec = tween(250, easing = FastOutSlowInEasing)
                    ),
                    exit = fadeOut(tween(180)) + scaleOut(
                        targetScale = 0.96f,
                        animationSpec = tween(180)
                    ),
                    modifier = if (isCompactSection)
                        Modifier.weight(1f).padding(end = 16.dp).wrapContentHeight(Alignment.Top)
                    else
                        Modifier.weight(1f).padding(end = 16.dp).fillMaxHeight()
                ) {
                    PremiumGlassPanel(
                        modifier = if (isCompactSection)
                            Modifier.fillMaxWidth().wrapContentHeight()
                        else
                            Modifier.fillMaxSize()
                    ) {
                        AnimatedContent(
                            targetState = activeSection,
                            transitionSpec = {
                                fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                            },
                            modifier = if (isCompactSection)
                                Modifier.fillMaxWidth().wrapContentHeight()
                            else
                                Modifier.fillMaxSize(),
                            label = "content"
                        ) { section ->
                            val scrollState = rememberScrollState()
                            val colModifier = if (section == PremiumSection.ADD_BUTTON)
                                Modifier.fillMaxWidth().wrapContentHeight().padding(horizontal = 16.dp, vertical = 20.dp).verticalScroll(scrollState)
                            else
                                Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp).verticalScroll(scrollState)
                            Column(
                                modifier = colModifier,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                when (section) {
                                    PremiumSection.CONFIG -> ConfigContent(settingsViewModel)
                                    PremiumSection.APPEARANCE -> AppearanceContent(settingsViewModel)
                                    PremiumSection.ENVIRONMENT -> EnvironmentContent(settingsViewModel, onNavigateToCanvas)
                                    PremiumSection.ADD_BUTTON -> AddButtonContent(settingsViewModel)
                                    PremiumSection.SECURITY -> SecurityContent(settingsViewModel, onNavigateToLockSelection)
                                    PremiumSection.ABOUT -> AboutContent(scrollState, settingsViewModel, onNavigateBack)
                                    null -> {}
                                }
                                if (section != PremiumSection.ADD_BUTTON) {
                                    Spacer(modifier = Modifier.height(40.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// CONFIG SECTION
// ---------------------------------------------------------

@Composable
fun ConfigContent(vm: SettingsViewModel) {
    val glowBrightness by vm.glowBrightness.collectAsState()
    val glowRadius by vm.glowRadius.collectAsState()
    val glowColorLong by vm.glowColor.collectAsState()
    val batterySavingMode by vm.batterySavingMode.collectAsState()

    SectionHeader("Configuration", "Storage & Vault Management")

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasStoragePermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // ── All Files Access Tile ──
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (hasStoragePermission) Color(0xFF142416) else Color(0xFF261D12),
        border = BorderStroke(
            1.dp,
            if (hasStoragePermission) Color(0xFF4CAF50).copy(alpha = 0.5f) else Color(0xFFFF9800).copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        context.startActivity(intent)
                    }
                } else {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (hasStoragePermission) Color(0xFF4CAF50).copy(alpha = 0.2f)
                        else Color(0xFFFF9800).copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasStoragePermission) Icons.Rounded.CheckCircle else Icons.Rounded.FolderShared,
                    contentDescription = null,
                    tint = if (hasStoragePermission) Color(0xFF4CAF50) else Color(0xFFFF9800),
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "All Files Access",
                    color = if (hasStoragePermission) Color(0xFF4CAF50) else Color(0xFFFFB74D),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (hasStoragePermission)
                        "Active • Full access to external storage & local vault migration"
                    else
                        "Tap to grant • Required for reading external media & shifting to vault",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "Manage",
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
        }
    }

    var isPruning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val database = remember { com.ballade.hwaran.core.database.AppDatabase.getDatabase(context) }

    Spacer(modifier = Modifier.height(10.dp))

    // ── Clean Missing Media Tile ──
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isPruning) {
                isPruning = true
                scope.launch {
                    val count = com.ballade.hwaran.core.util.LocalVaultMigrator.pruneMissingMedia(context, database)
                    isPruning = false
                    android.widget.Toast.makeText(
                        context,
                        if (count > 0) "Cleaned $count deleted media items from library" else "Library is clean. No missing items found.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                if (isPruning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Clean Missing Media",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (isPruning) "Scanning library for deleted files..."
                    else "Scan and remove library entries whose files were deleted from storage",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "Run",
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(16.dp)
            )
        }
    }

    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var isDeletingAll by remember { mutableStateOf(false) }

    Spacer(modifier = Modifier.height(10.dp))

    // ── Remove All Media Tile ──
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFE57373).copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color(0xFFE57373).copy(alpha = 0.22f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isDeletingAll && !isPruning) {
                showDeleteAllDialog = true
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE57373).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                if (isDeletingAll) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFFE57373)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.DeleteForever,
                        contentDescription = "Remove All Media",
                        tint = Color(0xFFE57373),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Remove All Media",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (isDeletingAll) "Removing all media from library..."
                    else "Clear all media from the app library or delete files from storage",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "Remove",
                tint = Color(0xFFE57373).copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }

    if (showDeleteAllDialog) {
        DeleteConfirmationDialog(
            title = "Remove All Media",
            itemName = "Entire Library",
            message = "Choose how you would like to remove all media from the app:",
            onDismiss = { showDeleteAllDialog = false },
            onRemoveFromApp = {
                showDeleteAllDialog = false
                isDeletingAll = true
                scope.launch {
                    val count = com.ballade.hwaran.core.util.LocalVaultMigrator.removeAllMedia(context, database, deleteFromDisk = false)
                    isDeletingAll = false
                    android.widget.Toast.makeText(
                        context,
                        "Removed $count media items from app library",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            },
            onDeleteFromDisk = {
                showDeleteAllDialog = false
                isDeletingAll = true
                scope.launch {
                    val count = com.ballade.hwaran.core.util.LocalVaultMigrator.removeAllMedia(context, database, deleteFromDisk = true)
                    isDeletingAll = false
                    android.widget.Toast.makeText(
                        context,
                        "Deleted $count media items and files from storage",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // ── Battery Saving Mode ────────────────────────────────────────
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (batterySavingMode) Color(0xFF1A2A1A) else Color.White.copy(alpha = 0.04f),
        border = BorderStroke(
            1.dp,
            if (batterySavingMode) Color(0xFF4CAF50).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (batterySavingMode) Color(0xFF4CAF50).copy(alpha = 0.2f)
                        else Color.White.copy(alpha = 0.06f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.BatteryChargingFull,
                    contentDescription = null,
                    tint = if (batterySavingMode) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Battery Saving",
                    color = if (batterySavingMode) Color(0xFF4CAF50) else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Pauses ambient backgrounds to maximize battery",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            }
            Switch(
                checked = batterySavingMode,
                onCheckedChange = { vm.setBatterySavingMode(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.8f),
                    uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                )
            )
        }
    }
}

// ---------------------------------------------------------
// APPEARANCE SECTION
// ---------------------------------------------------------

@Composable
fun AppearanceContent(vm: SettingsViewModel) {
    val appTheme by vm.appTheme.collectAsState()

    SectionHeader("Appearance", "Luxury dark finishes")

    Text("App Theme", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    val themes = AVAILABLE_APP_THEMES.map { it.id to it.name }

    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        maxItemsInEachRow = 4
    ) {
        themes.forEach { (themeId, themeName) ->
            val isSelected = appTheme == themeId
            val gradientBrush = getGradientForTheme(themeId)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(60.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(gradientBrush)
                        .border(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f),
                            CircleShape
                        )
                        .clickable { vm.setAppTheme(themeId) }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    themeName,
                    color = if (isSelected) Color(0xFFE6E8EC) else Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ---------------------------------------------------------
// ADD BUTTON SECTION
// ---------------------------------------------------------

private data class JellyStyleVariant(
    val id: Int,
    val name: String,
    val personality: String,
    val assetName: String?
)

@Composable
fun AddButtonContent(vm: SettingsViewModel) {
    val fabStyle by vm.fabStyle.collectAsState()
    val primary = MaterialTheme.colorScheme.primary

    SectionHeader("Button Style", "Choose your JellyBall companion & animation style")

    val variants = remember {
        listOf(
            JellyStyleVariant(1, "Classic Jelly", "Playful companion", null),
            JellyStyleVariant(2, "Devil Jelly", "Mischievous mascot", null),
            JellyStyleVariant(3, "Autumn Harvest", "Golden harvest & maple leaf", "autumn_harvest_jellyball.html"),
            JellyStyleVariant(4, "Dragon Lantern", "Festive dragon lantern tassel", "chinese_new_year_dragon_lantern_jellyball.html"),
            JellyStyleVariant(5, "Sakura Blossom", "Serene pink floating petals", "sakura_jellyball.html"),
            JellyStyleVariant(6, "Spring Awakening", "Fresh emerald morning dew", "spring_awakening_jellyball.html"),
            JellyStyleVariant(7, "Winter Frost", "Crystal icy frost snowflakes", "winter_frost_jellyball.html")
        )
    }

    val chunkedVariants = remember(variants) { variants.chunked(2) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        chunkedVariants.forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowItems.forEach { variant ->
                    val isSelected = (fabStyle == variant.id) || (variant.id == 1 && fabStyle == 0)
                    Surface(
                        onClick = { vm.setFabStyle(variant.id) },
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) primary.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.03f),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) primary.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.08f)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) primary.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f))
                                    .border(
                                        1.dp,
                                        if (isSelected) primary.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                when (variant.id) {
                                    1 -> JellyBall(modifier = Modifier.size(60.dp), enableJump = false, isTrapped = true)
                                    2 -> DevilJellyBall(modifier = Modifier.size(70.dp))
                                    else -> com.ballade.hwaran.ui.components.CustomJellyBall(
                                        assetName = variant.assetName!!,
                                        modifier = Modifier.size(70.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = variant.name,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = variant.personality,
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ---------------------------------------------------------
// ENVIRONMENT SECTION
// ---------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EnvironmentContent(vm: SettingsViewModel, onNavigateToCanvas: () -> Unit = {}) {
    val haptic = LocalHapticFeedback.current
    val primary = MaterialTheme.colorScheme.primary

    SectionHeader("Environment", "Atmosphere & relaxation")

    // Dedicated Aesthetic Blank Canvas Card
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF14131F),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(
                    primary.copy(alpha = 0.65f),
                    primary.copy(alpha = 0.35f),
                    primary.copy(alpha = 0.55f)
                )
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onNavigateToCanvas()
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            primary.copy(alpha = 0.09f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header badge + Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(primary.copy(alpha = 0.15f))
                            .border(1.dp, primary.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = primary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, primary.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "ZEN ATMOSPHERE",
                            color = primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Blank Canvas",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "An immersive, distraction-free sanctuary to experience ambient visual motions and relax with your music.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Feature tags with FlowRow and no-wrap text to avoid any awkward wrapping
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("Pure Visuals", "Audio Zen", "Minimal").forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.06f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                        ) {
                            Text(
                                text = "• $tag",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Enter Canvas Action Button
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNavigateToCanvas()
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Transparent,
                    border = PremiumPillDefaults.selectedBorder(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .background(PremiumPillDefaults.selectedBackground())
                            .fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Enter Canvas",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// SECURITY SECTION
// ---------------------------------------------------------

@Composable
fun SecurityContent(
    vm: SettingsViewModel,
    onNavigateToLockSelection: () -> Unit
) {
    val isLibraryLocked by vm.isLibraryLocked.collectAsState()
    val libraryPassword by vm.libraryPassword.collectAsState()

    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var isSettingNewPassword by remember { mutableStateOf(false) }
    var isResettingPassword by remember { mutableStateOf(false) }
    var pendingVaultNav by remember { mutableStateOf(false) }
    var showIncorrectPassword by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    var showWipeConfirmation by remember { mutableStateOf(false) }
    var showActivityLogsDialog by remember { mutableStateOf(false) }

    if (showActivityLogsDialog) {
        val recentEvents by database.historyDao().getRecentHistoryEventsFlow(50).collectAsState(initial = emptyList())
        val dateFormatter = remember { java.text.SimpleDateFormat("MMM dd, HH:mm:ss", java.util.Locale.getDefault()) }

        AlertDialog(
            onDismissRequest = { showActivityLogsDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp)),
            containerColor = Color(0xFF0F0E17),
            shape = RoundedCornerShape(28.dp),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Activity Log",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (recentEvents.isEmpty()) "No recorded events" else "${recentEvents.size} events captured",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                    IconButton(
                        onClick = { showActivityLogsDialog = false },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            text = {
                if (recentEvents.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No activity events recorded yet.", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(recentEvents, key = { it.id }) { event ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White.copy(alpha = 0.04f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val (badgeBg, badgeText) = when (event.eventType) {
                                            "READ_NOVEL", "READ_TOON", "READ_BOOK" -> Color(0xFF10B981).copy(alpha = 0.16f) to Color(0xFF34D399)
                                            "WATCH", "PLAY_VIDEO" -> Color(0xFF3B82F6).copy(alpha = 0.16f) to Color(0xFF60A5FA)
                                            "LISTEN" -> Color(0xFF8B5CF6).copy(alpha = 0.16f) to Color(0xFFA78BFA)
                                            "IMPORT" -> Color(0xFFF59E0B).copy(alpha = 0.16f) to Color(0xFFFBBF24)
                                            "DELETE" -> Color(0xFFEF4444).copy(alpha = 0.16f) to Color(0xFFF87171)
                                            else -> Color.White.copy(alpha = 0.10f) to Color.White
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = badgeBg
                                        ) {
                                            Text(
                                                text = event.eventType,
                                                color = badgeText,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        Text(
                                            text = dateFormatter.format(java.util.Date(event.timestamp)),
                                            color = Color.White.copy(alpha = 0.45f),
                                            fontSize = 11.sp
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = event.itemName,
                                        color = Color.White,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (event.details.isNotBlank()) {
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = event.details,
                                            color = Color.White.copy(alpha = 0.55f),
                                            fontSize = 11.5.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showWipeConfirmation) {
        AlertDialog(
            onDismissRequest = { showWipeConfirmation = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp)),
            containerColor = Color(0xFF0F0E17),
            shape = RoundedCornerShape(28.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteForever,
                            contentDescription = null,
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text("Wipe All History", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Text(
                    text = "Do you really want to wipe all history? This will reset recent activity, continue playing/reading progress, watch timestamps, and page positions across all media.",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Surface(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            database.historyDao().clearAllHistoryEvents()
                            database.mediaDao().clearAllMediaHistory()
                            database.trackDao().clearAllChapterProgress()
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "All history and playback progress wiped", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        showWipeConfirmation = false
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f)),
                    modifier = Modifier.height(42.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("Wipe All", color = Color(0xFFF87171), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                Surface(
                    onClick = { showWipeConfirmation = false },
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier.height(42.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        )
    }

    if (showPasswordDialog) {
        val dialogTitle = when {
            isSettingNewPassword -> "Set Vault Password"
            isResettingPassword -> "Verify Current Password"
            pendingVaultNav -> "Unlock Vault Items"
            else -> "Enter Vault Password"
        }
        val dialogSubtitle = when {
            isSettingNewPassword && pendingVaultNav -> "Create a password first to manage vault items"
            isSettingNewPassword -> "Create a password to protect your private items"
            isResettingPassword -> "Enter your current password to set a new one"
            pendingVaultNav -> "Enter password to access your private items"
            else -> "Enter password to update vault lock"
        }

        AlertDialog(
            onDismissRequest = {
                showPasswordDialog = false
                passwordInput = ""
                isSettingNewPassword = false
                isResettingPassword = false
                pendingVaultNav = false
                showIncorrectPassword = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp)),
            containerColor = Color(0xFF0F0E17),
            shape = RoundedCornerShape(28.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AccentTitanium.copy(alpha = 0.12f))
                            .border(1.dp, AccentTitanium.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = AccentTitanium,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = dialogTitle,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = dialogSubtitle,
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 12.sp
                        )
                    }
                }
            },
            text = {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            if (showIncorrectPassword) showIncorrectPassword = false
                        },
                        placeholder = { Text("Enter password", color = Color.White.copy(alpha = 0.35f), fontSize = 14.sp) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AccentTitanium,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedContainerColor = Color.White.copy(alpha = 0.04f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                            cursorColor = AccentTitanium
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (showIncorrectPassword) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFF87171),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Incorrect password. Please try again.",
                                color = Color(0xFFF87171),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Surface(
                    onClick = {
                        if (isSettingNewPassword) {
                            if (passwordInput.isNotBlank()) {
                                vm.setLibraryPassword(passwordInput)
                                showPasswordDialog = false
                                val shouldNav = pendingVaultNav
                                pendingVaultNav = false
                                passwordInput = ""
                                isSettingNewPassword = false
                                if (shouldNav) {
                                    onNavigateToLockSelection()
                                }
                            }
                        } else if (isResettingPassword) {
                            if (passwordInput == libraryPassword) {
                                passwordInput = ""
                                isResettingPassword = false
                                isSettingNewPassword = true
                                showIncorrectPassword = false
                            } else {
                                showIncorrectPassword = true
                            }
                        } else if (pendingVaultNav) {
                            if (passwordInput == libraryPassword) {
                                showPasswordDialog = false
                                pendingVaultNav = false
                                passwordInput = ""
                                showIncorrectPassword = false
                                onNavigateToLockSelection()
                            } else {
                                showIncorrectPassword = true
                            }
                        } else {
                            if (passwordInput == libraryPassword) {
                                vm.setIsLibraryLocked(!isLibraryLocked)
                                showPasswordDialog = false
                                passwordInput = ""
                                showIncorrectPassword = false
                            } else {
                                showIncorrectPassword = true
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = AccentTitanium.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, AccentTitanium.copy(alpha = 0.35f)),
                    modifier = Modifier.height(42.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isSettingNewPassword) "Set Password" else "Confirm",
                            color = AccentTitanium,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                Surface(
                    onClick = {
                        showPasswordDialog = false
                        passwordInput = ""
                        isSettingNewPassword = false
                        isResettingPassword = false
                        pendingVaultNav = false
                        showIncorrectPassword = false
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier.height(42.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        )
    }

    SectionHeader("Security", "Vault & Privacy Protection")

    Text(
        text = "Secure your private library items with a password. Locked items display a placeholder cover and require a password to access.",
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.6f),
        lineHeight = 15.sp,
        modifier = Modifier.padding(bottom = 4.dp)
    )

    // ── 1. Vault Lock Switch Tile ──
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isLibraryLocked) AccentTitanium.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.04f),
        border = BorderStroke(
            1.dp,
            if (isLibraryLocked) AccentTitanium.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.08f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isLibraryLocked) AccentTitanium.copy(alpha = 0.16f)
                        else Color.White.copy(alpha = 0.06f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isLibraryLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    contentDescription = null,
                    tint = if (isLibraryLocked) AccentTitanium else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Private Vault Lock",
                    color = if (isLibraryLocked) AccentTitanium else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isLibraryLocked) "Locked • Password required for access" else "Unlocked • Vault items are visible",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
            Switch(
                checked = isLibraryLocked,
                onCheckedChange = {
                    pendingVaultNav = false
                    passwordInput = ""
                    showIncorrectPassword = false
                    if (libraryPassword.isEmpty()) {
                        isSettingNewPassword = true
                        showPasswordDialog = true
                    } else {
                        showPasswordDialog = true
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF0F0E17),
                    checkedTrackColor = AccentTitanium,
                    uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.12f)
                )
            )
        }
    }

    // ── 2. Manage Vault Items Tile ──
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                passwordInput = ""
                showIncorrectPassword = false
                if (libraryPassword.isEmpty()) {
                    isSettingNewPassword = true
                    pendingVaultNav = true
                    showPasswordDialog = true
                } else {
                    pendingVaultNav = true
                    showPasswordDialog = true
                }
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderSpecial,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Manage Vault Items",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (libraryPassword.isEmpty()) "Set password first to unlock vault" else "Password required to enter vault",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
        }
    }

    // ── 3. Vault Password Tile ──
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                pendingVaultNav = false
                passwordInput = ""
                showIncorrectPassword = false
                if (libraryPassword.isNotEmpty()) {
                    isResettingPassword = true
                    showPasswordDialog = true
                } else {
                    isSettingNewPassword = true
                    showPasswordDialog = true
                }
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Key,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (libraryPassword.isEmpty()) "Set Vault Password" else "Change Vault Password",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (libraryPassword.isEmpty()) "Create password to protect your vault" else "Update existing vault PIN / password",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))
    SectionHeader("History Tracking", "Activity & Playback Stats")

    Text(
        text = "Track app launches, reading progress, and playback statistics. Toggle tracking or inspect and clear activity logs below.",
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.6f),
        lineHeight = 15.sp,
        modifier = Modifier.padding(bottom = 4.dp)
    )

    val stopTracking by vm.stopTracking.collectAsState()
    val isTrackingEnabled = !stopTracking

    // ── 4. History Tracking Switch Tile ──
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isTrackingEnabled) Color(0xFF142416) else Color(0xFF261D12),
        border = BorderStroke(
            1.dp,
            if (isTrackingEnabled) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFFF59E0B).copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isTrackingEnabled) Color(0xFF10B981).copy(alpha = 0.2f)
                        else Color(0xFFF59E0B).copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = null,
                    tint = if (isTrackingEnabled) Color(0xFF34D399) else Color(0xFFFBBF24),
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Activity Tracking",
                    color = if (isTrackingEnabled) Color(0xFF34D399) else Color(0xFFFBBF24),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isTrackingEnabled) "Active • Logging media & app progress" else "Paused • Tracking is stopped",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
            Switch(
                checked = isTrackingEnabled,
                onCheckedChange = { isChecked -> vm.setStopTracking(!isChecked) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF10B981),
                    uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.12f)
                )
            )
        }
    }

    // ── 5. View Activity Log Tile ──
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showActivityLogsDialog = true }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "View Activity Log",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Inspect recent playback and reading events",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
        }
    }

    // ── 6. Wipe History Tile ──
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFE57373).copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color(0xFFE57373).copy(alpha = 0.22f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showWipeConfirmation = true }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE57373).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteSweep,
                    contentDescription = null,
                    tint = Color(0xFFE57373),
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Wipe All History",
                    color = Color(0xFFE57373),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Clear watch progress, read pages, and event logs",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = Color(0xFFE57373).copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ---------------------------------------------------------
// ABOUT SECTION
// ---------------------------------------------------------

@Composable
fun AboutContent(scrollState: ScrollState, vm: SettingsViewModel, onNavigateBack: () -> Unit) {
    val context = LocalContext.current

    var hasBeenTriggered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(200)
        hasBeenTriggered = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (hasBeenTriggered) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "author_section_alpha"
    )

    SectionHeader("About", "Do you believe in owning things?")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hwaran App Info Header Section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
        ) {
            AndroidView(
                factory = { ctx ->
                    android.webkit.WebView(ctx).apply {
                        setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(0) // Transparent
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        webChromeClient = android.webkit.WebChromeClient()
                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun onPageFinished(
                                view: android.webkit.WebView?,
                                url: String?
                            ) {
                                view?.evaluateJavascript(
                                    "if(window.startAuthorAnimation) window.startAuthorAnimation();",
                                    null
                                )
                            }
                        }
                        loadUrl("file:///android_asset/author.html")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Hwaran Media Player",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Developed with ꨄ by Anshu",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "I built this app as an offline reader/watcher for manhua, manga, books and videos. I'd love to know how I can improve this app for you. Hwaran is free and open-source!",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Open Source Repository Button
        Surface(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/valse-de-anshu/Hwaran.git"))
                context.startActivity(intent)
            },
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent,
            border = PremiumPillDefaults.border(),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Row(
                modifier = Modifier
                    .background(PremiumPillDefaults.background())
                    .fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_github),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Hwaran Open Source", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Socials & Community Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Socials & Community",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(12.dp))
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = Color.White.copy(alpha = 0.12f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Surface(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+p0nu4Zsk4ZQ3M2Rl"))
                context.startActivity(intent)
            },
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent,
            border = PremiumPillDefaults.border(),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Row(
                modifier = Modifier
                    .background(PremiumPillDefaults.background())
                    .fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_telegram),
                    contentDescription = null,
                    tint = Color(0xFF2CA5E0),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Join our Telegram Community", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/E79KNqqp4"))
                context.startActivity(intent)
            },
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent,
            border = PremiumPillDefaults.border(),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Row(
                modifier = Modifier
                    .background(PremiumPillDefaults.background())
                    .fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_discord),
                    contentDescription = null,
                    tint = Color(0xFF5865F2),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Join our Discord Community", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

// ---------------------------------------------------------
// UTILS & REUSABLE COMPONENTS
// ---------------------------------------------------------

@Composable
fun SectionHeader(title: String, subtitle: String) {
    Column {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                title,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Text(subtitle, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun PremiumToggleRow(label: String, modifier: Modifier = Modifier, toggle: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            toggle()
        }
    }
}

@Composable
fun PremiumTextField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.White.copy(alpha = 0.5f)) },
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
        )
    )
}

fun getGradientForTheme(themeId: Int): Brush {
    return AVAILABLE_APP_THEMES.find { it.id == themeId }?.previewBrush
        ?: androidx.compose.ui.graphics.SolidColor(Color(0xFF000000))
}
