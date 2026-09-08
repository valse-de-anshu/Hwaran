package com.ballade.hwaran.ui.screens

import androidx.compose.animation.core.animateDpAsState
import java.io.File
import android.content.Intent
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import com.ballade.hwaran.ui.dialogs.HolographicCloverPanel
import com.ballade.hwaran.ui.dialogs.PremiumGlassPanel
import com.ballade.hwaran.ui.dialogs.PremiumSlider
import com.ballade.hwaran.ui.dialogs.SidebarIcon
import com.ballade.hwaran.ui.screens.video.VideoPreview
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.material3.ripple
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.runtime.toMutableStateList
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import coil.request.videoFrameMillis
import coil.request.ImageRequest
import com.ballade.hwaran.ui.viewmodels.DescriptionViewModel
import com.ballade.hwaran.ui.viewmodels.SettingsViewModel
import com.ballade.hwaran.ui.components.JellyToggle
import com.ballade.hwaran.ui.components.JellyToggle3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import androidx.compose.animation.core.animate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ballade.hwaran.ui.components.WobblySnakeRing
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

// Exact App Colors are now derived from MaterialTheme locally

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DescriptionScreen(
    mangaId: Long,
    descriptionViewModel: DescriptionViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToMedia: (Long, Int) -> Unit,
    onNavigateToDescription: (Long) -> Unit
) {
    LaunchedEffect(mangaId) {
        descriptionViewModel.loadManga(mangaId)
    }

    val BgDark = MaterialTheme.colorScheme.background
    val CardBg = MaterialTheme.colorScheme.surface
    val DividerColor = MaterialTheme.colorScheme.surfaceVariant
    val TextMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val PrimaryPurple = MaterialTheme.colorScheme.primary

    val manga by descriptionViewModel.manga.collectAsState()
    val rootManga by descriptionViewModel.rootManga.collectAsState()
    val chapters by descriptionViewModel.chapters.collectAsState()
    val childBoxes by descriptionViewModel.childBoxes.collectAsState()
    val isEditMode by descriptionViewModel.isEditMode.collectAsState()

    val draftDesc by descriptionViewModel.draftDescription.collectAsState()
    val draftThoughts by descriptionViewModel.draftThoughts.collectAsState()
    val draftCover by descriptionViewModel.draftCoverPath.collectAsState()

    val animationSpeed by settingsViewModel.animationSpeed.collectAsState()
    val animationVisibility by settingsViewModel.animationVisibility.collectAsState()
    val animationType by settingsViewModel.animationType.collectAsState()
    val descriptionUiTransparency by settingsViewModel.descriptionUiTransparency.collectAsState()
    val descriptionLayoutMode by settingsViewModel.descriptionLayoutMode.collectAsState()
    val sfwText by settingsViewModel.sfwText.collectAsState()
    val nsfwText by settingsViewModel.nsfwText.collectAsState()
    val videoLayoutMode by settingsViewModel.videoLayoutMode.collectAsState()
    val glowBrightness by settingsViewModel.glowBrightness.collectAsState()
    val glowRadius by settingsViewModel.glowRadius.collectAsState()
    val glowColor by settingsViewModel.glowColor.collectAsState()
    val storageMode by settingsViewModel.storageMode.collectAsState()

    val isChannelMode = manga?.contentType == 2 && videoLayoutMode == 1

    val draftTitle by descriptionViewModel.draftTitle.collectAsState()
    val draftIsNsfw by descriptionViewModel.draftIsNsfw.collectAsState()

    var showChapters by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    BackHandler(enabled = isEditMode) {
        descriptionViewModel.toggleEditMode()
    }
    var isSwitchingNsfw by remember { mutableStateOf(false) }

    var showDeleteChoiceDialog by remember { mutableStateOf(false) }
    var showParentDeleteWarningDialog by remember { mutableStateOf(false) }
    var isDeleteMode by remember { mutableStateOf(false) }
    val selectedChapters = remember { mutableStateListOf<Long>() }
    
    var showCreateBoxDialog by remember { mutableStateOf(false) }
    var showInitialLabelDialog by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    
    // Pagination state for Video Mode
    var currentPage by remember { mutableStateOf(0) }
    val itemsPerPage = 8
    
    var newBoxLabel by remember { mutableStateOf("") }
    var currentBoxLabel by remember { mutableStateOf("") }
    
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        delay(600)
        showChapters = true
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { 
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            descriptionViewModel.draftCoverPath.value = it.toString() 
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { descriptionViewModel.importChapters(it, storageMode) }
    }

    val multipleVideoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            descriptionViewModel.importMultipleVideos(uris, storageMode)
        }
    }

    val singleVideoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { descriptionViewModel.importMultipleVideos(listOf(it), storageMode) }
    }

    var showChannelOptions by remember { mutableStateOf(false) }
    var chapterToUpdateThumbnail by remember { mutableStateOf<Long?>(null) }
    val chapterThumbnailLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            chapterToUpdateThumbnail?.let { chapterId ->
                descriptionViewModel.updateChapterThumbnail(chapterId, it.toString())
                chapterToUpdateThumbnail = null
            }
        }
    }

    if (showDeleteChoiceDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteChoiceDialog = false },
            title = { Text("Delete Options", color = Color.White) },
            text = {
                Column {
                    TextButton(onClick = { 
                        showDeleteChoiceDialog = false
                        isDeleteMode = true 
                        selectedChapters.clear()
                    }) {
                        Text(if (manga?.contentType == 2) "Delete Videos" else "Delete Chapters", color = PrimaryPurple, fontSize = 18.sp)
                    }
                    TextButton(onClick = { 
                        showDeleteChoiceDialog = false
                        val hasLinkedScreens = manga?.parentMangaId == null && childBoxes.isNotEmpty()
                        if (hasLinkedScreens) {
                            showParentDeleteWarningDialog = true
                        } else {
                            showDeleteDialog = true
                        }
                    }) {
                        Text("Delete ${manga?.title}", color = Color(0xFFE57373), fontSize = 18.sp)
                    }
                }
            },
            confirmButton = {},
            containerColor = CardBg
        )
    }

    if (showParentDeleteWarningDialog) {
        AlertDialog(
            onDismissRequest = { showParentDeleteWarningDialog = false },
            title = { Text("Delete Warning", color = Color.White) },
            text = {
                Text(
                    "If you delete this parent screen, you will lose all of the other screens (seasons) that are linked to it.\n\n" +
                    "To delete this series, you must delete the child screens first.",
                    color = TextMuted
                )
            },
            confirmButton = {
                TextButton(onClick = { showParentDeleteWarningDialog = false }) {
                    Text("Ok, I understand", color = PrimaryPurple)
                }
            },
            containerColor = CardBg
        )
    }

    if (showInitialLabelDialog) {
        AlertDialog(
            onDismissRequest = { showInitialLabelDialog = false },
            title = { Text("Label Current Content", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Give this current item a label before adding related content.", color = TextMuted, fontSize = 14.sp)
                    TextField(
                        value = currentBoxLabel, 
                        onValueChange = { currentBoxLabel = it }, 
                        placeholder = { Text("e.g. Season 1") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White, 
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (currentBoxLabel.isNotBlank()) {
                        manga?.let { descriptionViewModel.updateBoxLabel(it.id, currentBoxLabel) }
                        showInitialLabelDialog = false
                        showCreateBoxDialog = true
                    }
                }) { Text("Next", color = PrimaryPurple) }
            },
            dismissButton = {
                TextButton(onClick = { showInitialLabelDialog = false }) { Text("Cancel", color = TextMuted) }
            },
            containerColor = CardBg
        )
    }

    if (showCreateBoxDialog) {
        AlertDialog(
            onDismissRequest = { showCreateBoxDialog = false },
            title = { Text("Add Related Content", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the label for the new content box.", color = TextMuted, fontSize = 14.sp)
                    TextField(
                        value = newBoxLabel, 
                        onValueChange = { newBoxLabel = it }, 
                        placeholder = { Text("e.g. Season 2") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White, 
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newBoxLabel.isNotBlank()) {
                        showCreateBoxDialog = false
                        descriptionViewModel.createChildBox(newBoxLabel, "") { newId ->
                            onNavigateToDescription(newId)
                        }
                        newBoxLabel = ""
                    }
                }) { Text("Create", color = PrimaryPurple) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateBoxDialog = false }) { Text("Cancel", color = TextMuted) }
            },
            containerColor = CardBg
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Remove ${manga?.title}?") },
            text = { Text("Do you want to remove ${manga?.title}?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    descriptionViewModel.deleteManga { parentId ->
                        if (parentId != null) {
                            onNavigateToDescription(parentId)
                        } else {
                            onNavigateBack()
                        }
                    }
                }) {
                    Text("Yes", color = Color(0xFFE57373))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("No", color = TextMuted)
                }
            },
            containerColor = CardBg,
            titleContentColor = Color.White,
            textContentColor = TextMuted
        )
    }

    var isDraggingBox by remember { mutableStateOf(false) }
    var isDescriptionDrawerExpanded by remember { mutableStateOf(false) }
    var isCloverExpanded by remember { mutableStateOf(false) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (!isDeleteMode) {
                Box(
                    modifier = Modifier
                        .offset(y = (-48).dp)
                        .graphicsLayer { alpha = descriptionUiTransparency },
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(60.dp)
                    ) {
                        // THE SEPARATE REVEALED PILL (Expands upwards)
                        AnimatedVisibility(
                            visible = isDescriptionDrawerExpanded,
                            enter = expandVertically(
                                animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
                                expandFrom = Alignment.Bottom
                            ) + fadeIn(tween(300)),
                            exit = shrinkVertically(
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
                                shrinkTowards = Alignment.Bottom
                            ) + fadeOut(tween(200))
                        ) {
                            PremiumGlassPanel(modifier = Modifier.wrapContentSize()) {
                                Column(
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp).width(52.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Edit Mode Icon (AutoFixHigh from MusicPlayer)
                                    SidebarIcon(
                                        icon = Icons.Rounded.AutoFixHigh,
                                        isSelected = isEditMode,
                                        onClick = { 
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            descriptionViewModel.toggleEditMode() 
                                        }
                                    )
                                    
                                    val cloverInteractionSource = remember { MutableInteractionSource() }
                                    val isCloverPressed by cloverInteractionSource.collectIsPressedAsState()
                                    val cloverScale by animateFloatAsState(
                                        targetValue = if (isCloverPressed) 0.85f else if (isCloverExpanded) 1.15f else 1f,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                        label = "clover_scale"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .graphicsLayer {
                                                scaleX = cloverScale
                                                scaleY = cloverScale
                                            }
                                            .clip(CircleShape)
                                            .background(if (isCloverExpanded) PrimaryPurple.copy(alpha = 0.15f) else Color.Transparent)
                                            .border(if (isCloverExpanded) 1.dp else 0.dp, if (isCloverExpanded) PrimaryPurple.copy(alpha = 0.5f) else Color.Transparent, CircleShape)
                                            .clickable(interactionSource = cloverInteractionSource, indication = null) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                isCloverExpanded = !isCloverExpanded
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("☘︎", color = if (isCloverExpanded) PrimaryPurple else Color.White, fontSize = 28.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // THE STANDALONE TRIGGER BUTTON
                        val isImportingFab by descriptionViewModel.isImporting.collectAsState()
                        val importProgressFab by descriptionViewModel.importProgress.collectAsState()
                        // Show import feedback only for Toon (contentType 0 with chapters) and Video (2)
                        val showImportFeedback = isImportingFab && manga?.contentType != 0

                        val triggerInteractionSource = remember { MutableInteractionSource() }
                        val isTriggerPressed by triggerInteractionSource.collectIsPressedAsState()
                        val triggerScale by animateFloatAsState(
                            targetValue = if (isTriggerPressed) 0.85f else if (isDescriptionDrawerExpanded) 1.1f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                            label = "trigger_scale"
                        )
                        
                        PremiumGlassPanel(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(interactionSource = triggerInteractionSource, indication = null) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (showImportFeedback) {
                                            // Cancel ongoing import — do NOT open drawer
                                            descriptionViewModel.cancelImport()
                                        } else {
                                            isDescriptionDrawerExpanded = !isDescriptionDrawerExpanded
                                            if (!isDescriptionDrawerExpanded) isCloverExpanded = false
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (showImportFeedback) {
                                    // Show jellyball-style wobbly loading ring with % inside
                                    val density = LocalDensity.current
                                    val animatedFabProgress by animateFloatAsState(
                                        targetValue = (importProgressFab / 100f).coerceIn(0f, 1f),
                                        animationSpec = tween(400),
                                        label = "fab_import_progress"
                                    )
                                    WobblySnakeRing(
                                        modifier = Modifier.size(40.dp),
                                        colors = listOf(PrimaryPurple, Color.White),
                                        progress = animatedFabProgress,
                                        strokeWidth = 2.5f,
                                        wobbleAmplitude = 4f,
                                        wobbleSpeed = 5000,
                                        baseRadiusOverride = with(density) { 16.dp.toPx() },
                                        showOuterRing = false
                                    )
                                    Text(
                                        text = "$importProgressFab%",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else if (isDescriptionDrawerExpanded) {
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = "Close",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                } else {
                                    Text(
                                        text = "𑣲⋆", 
                                        color = Color.White, 
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (manga == null && !isEditMode) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryPurple)
                }
                return@Box
            }

            val scrollState = rememberLazyListState()
            // NOTE: The scroll-offset→settingsViewModel LaunchedEffect was removed.
            // It fired on every scroll frame and wrote to SettingsViewModel, causing
            // the global navigation background to recompose in sync with every scroll
            // event — the primary source of frame drops on this screen.

            // Shared Coil ImageLoader for all video thumbnails on this screen.
            // Creating one per-item (as the old code did) allocates a new loader
            // and codec pipeline on every list item composition.
            val sharedVideoImageLoader = remember {
                coil.ImageLoader.Builder(context)
                    .components { add(VideoFrameDecoder.Factory()) }
                    .build()
            }

            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = padding.calculateBottomPadding())
                    .padding(horizontal = if (isLandscape) 40.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = !isDraggingBox
            ) {
                item {
                    Spacer(modifier = Modifier.height(if (isLandscape) 80.dp else 120.dp))
                }
                
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isEditMode) {
                            Text("Edit Profile", color = TextMuted, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        } else if (isDeleteMode) {
                            Text(if (manga?.contentType == 2) "Delete Videos" else if (manga?.contentType == 3) "Delete Songs" else "Delete Chapters", color = TextMuted, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        } else {
                            Text(
                                manga?.title ?: "Profile", 
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (isEditMode) {
                    item {
                        TextField(
                            value = draftTitle,
                            onValueChange = { descriptionViewModel.draftTitle.value = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            placeholder = { Text("Manga Title", color = TextMuted, fontSize = 24.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 24.sp, color = Color.White),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = PrimaryPurple,
                            )
                        )
                    }
                }

                // Top Section (Cover + Thoughts)
                if (isEditMode && isChannelMode) {
                    item {
                        // Centered Cover for Channel Edit Mode
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(220.dp, 300.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(CardBg)
                                    .clickable { imagePickerLauncher.launch(arrayOf("image/*")) },
                                contentAlignment = Alignment.Center
                            ) {
                                val currentCover = (draftCover.ifEmpty { manga?.coverPath ?: "" }).let { if (it == "android.resource://android/drawable/ic_menu_gallery") "" else it }
                                if (currentCover.isNotEmpty()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(Uri.parse(currentCover)).build(),
                                        contentDescription = "Cover",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Box(
                                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Image, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                                }
                            }
                            Text("Tap image to change cover", color = PrimaryPurple, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                } else if (!isChannelMode) {
                    item {
                        if (isLandscape) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = if (isEditMode) 0.dp else 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Cover Art
                                Box(
                                    modifier = Modifier
                                        .size(160.dp, 220.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(CardBg)
                                        .clickable(enabled = isEditMode) { imagePickerLauncher.launch(arrayOf("image/*")) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val currentCover = (if (isEditMode) draftCover else manga?.coverPath ?: "").let { if (it == "android.resource://android/drawable/ic_menu_gallery") "" else it }
                                    if (currentCover.isNotEmpty()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context).data(Uri.parse(currentCover)).build(),
                                            contentDescription = "Cover",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(Icons.Rounded.Image, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                                    }
                                    if (isEditMode && currentCover.isNotEmpty()) {
                                        Icon(Icons.Rounded.Image, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                                    }
                                }

                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    // Thoughts
                                    AnnotatedCard(
                                        modifier = Modifier.fillMaxWidth().graphicsLayer(alpha = descriptionUiTransparency),
                                        title = "My Thoughts",
                                        coverTransparency = descriptionUiTransparency,
                                        cardBg = CardBg
                                    ) {
                                        if (isEditMode) {
                                            TextField(
                                                value = draftThoughts,
                                                onValueChange = { descriptionViewModel.draftThoughts.value = it },
                                                placeholder = { Text("add text here", color = TextMuted, fontSize = 14.sp) },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = TextFieldDefaults.colors(
                                                    focusedContainerColor = Color.Transparent,
                                                    unfocusedContainerColor = Color.Transparent,
                                                    focusedIndicatorColor = PrimaryPurple,
                                                    unfocusedIndicatorColor = DividerColor,
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White
                                                )
                                            )
                                        } else {
                                            Text(
                                                text = manga?.thoughts ?: "No thoughts added.",
                                                color = TextMuted,
                                                fontSize = 14.sp,
                                                lineHeight = 18.sp
                                            )
                                        }
                                    }

                                    // Description
                                    AnnotatedCard(
                                        modifier = Modifier.fillMaxWidth().graphicsLayer(alpha = descriptionUiTransparency),
                                        title = "Description",
                                        coverTransparency = descriptionUiTransparency,
                                        cardBg = CardBg
                                    ) {
                                        if (isEditMode) {
                                            TextField(
                                                value = draftDesc,
                                                onValueChange = { descriptionViewModel.draftDescription.value = it },
                                                modifier = Modifier.fillMaxWidth(),
                                                placeholder = { Text("add text here", color = TextMuted, fontSize = 14.sp) },
                                                colors = TextFieldDefaults.colors(
                                                    focusedContainerColor = Color.Transparent,
                                                    unfocusedContainerColor = Color.Transparent,
                                                    focusedIndicatorColor = PrimaryPurple,
                                                    unfocusedIndicatorColor = DividerColor,
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White
                                                )
                                            )
                                        } else {
                                            Text(
                                                text = manga?.description ?: "No description added yet.",
                                                color = TextMuted,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = if (isEditMode) 0.dp else 16.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                // Cover Art (Red Box 1)
                                Box(
                                    modifier = Modifier
                                        .size(160.dp, 220.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(CardBg)
                                        .clickable(enabled = isEditMode) { imagePickerLauncher.launch(arrayOf("image/*")) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val currentCover = (if (isEditMode) draftCover else manga?.coverPath ?: "").let { if (it == "android.resource://android/drawable/ic_menu_gallery") "" else it }
                                    if (currentCover.isNotEmpty()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context).data(Uri.parse(currentCover)).build(),
                                            contentDescription = "Cover",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(Icons.Rounded.Image, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                                    }
                                    if (isEditMode && currentCover.isNotEmpty()) {
                                        Icon(Icons.Rounded.Image, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                                    }
                                }

                                if (!isDeleteMode) {
                                    Spacer(modifier = Modifier.width(12.dp))

                                    // Thoughts Card (Blue Box 2)
                                    AnnotatedCard(
                                        modifier = Modifier.weight(1f).height(220.dp).graphicsLayer(alpha = descriptionUiTransparency),
                                        title = "My Thoughts",
                                        coverTransparency = descriptionUiTransparency,
                                        cardBg = CardBg
                                    ) {
                                        if (isEditMode) {
                                            TextField(
                                                value = draftThoughts,
                                                onValueChange = { descriptionViewModel.draftThoughts.value = it },
                                                placeholder = { Text("add text here", color = TextMuted, fontSize = 14.sp) },
                                                modifier = Modifier.fillMaxSize(),
                                                colors = TextFieldDefaults.colors(
                                                    focusedContainerColor = Color.Transparent,
                                                    unfocusedContainerColor = Color.Transparent,
                                                    focusedIndicatorColor = PrimaryPurple,
                                                    unfocusedIndicatorColor = DividerColor,
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White
                                                )
                                            )
                                        } else {
                                            Text(
                                                text = manga?.thoughts ?: "No thoughts added.",
                                                color = TextMuted,
                                                fontSize = 14.sp,
                                                lineHeight = 18.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (!isDeleteMode && !isChannelMode && !isLandscape) {
                    // Description Card (Blue Box 3)
                    item {
                        AnnotatedCard(
                            modifier = Modifier.fillMaxWidth().graphicsLayer(alpha = descriptionUiTransparency),
                            title = "Description",
                            coverTransparency = descriptionUiTransparency,
                            cardBg = CardBg
                        ) {
                            if (isEditMode) {
                                TextField(
                                    value = draftDesc,
                                    onValueChange = { descriptionViewModel.draftDescription.value = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("add text here", color = TextMuted, fontSize = 14.sp) },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = PrimaryPurple,
                                        unfocusedIndicatorColor = DividerColor,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                            } else {
                                Text(
                                    text = manga?.description ?: "No description added yet.",
                                    color = TextMuted,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    // Content Boxes Section (New)
                    item {
                        if (manga?.contentType == 2) {
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Related Content",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = { 
                                            if (manga?.boxLabel == null) {
                                                currentBoxLabel = ""
                                                showInitialLabelDialog = true
                                            } else {
                                                showCreateBoxDialog = true
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Rounded.Add, contentDescription = "Add Content Box", tint = PrimaryPurple)
                                    }
                                }
                                
                                if (childBoxes.isNotEmpty() || manga?.boxLabel != null) {
                                    val displayRoot = rootManga ?: manga
                                    val allBoxes = remember(displayRoot, childBoxes) {
                                        if (displayRoot != null) {
                                            (listOf(displayRoot) + childBoxes)
                                                .distinctBy { it.id }
                                                .sortedBy { it.position }
                                        } else childBoxes.sortedBy { it.position }
                                    }
                                    val mutableBoxes = remember(allBoxes) { allBoxes.toMutableStateList() }
                                    
                                    // State for Dragging
                                    var draggingId by remember { mutableStateOf<Long?>(null) }
                                    var dragPosition by remember { mutableStateOf(Offset.Zero) }
                                    val itemPositions = remember { mutableStateMapOf<Long, Rect>() }
                                    var containerSize by remember { mutableStateOf(IntSize.Zero) }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onGloballyPositioned { containerSize = it.size }
                                    ) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            mutableBoxes.forEach { item ->
                                                key(item.id) {
                                                    val isCurrent = item.id == manga?.id
                                                    val isDragging = draggingId == item.id

                                                    Surface(
                                                        onClick = {
                                                            if (!isEditMode && draggingId == null && !isCurrent) {
                                                                onNavigateToDescription(item.id)
                                                            }
                                                        },
                                                        enabled = !isEditMode, // Completely disable surface interaction in edit mode
                                                        modifier = Modifier
                                                            .onGloballyPositioned { layoutCoordinates ->
                                                                itemPositions[item.id] = layoutCoordinates.boundsInParent()
                                                            }
                                                            .graphicsLayer {
                                                                alpha = if (isDragging) 0f else 1f
                                                            }
                                                            .pointerInput(item.id, isEditMode) {
                                                                if (isEditMode) {
                                                                    detectDragGesturesAfterLongPress(
                                                                        onDragStart = { offset ->
                                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                            draggingId = item.id
                                                                            isDraggingBox = true
                                                                            val rect = itemPositions[item.id]
                                                                            if (rect != null) {
                                                                                dragPosition = rect.topLeft + offset
                                                                            } else {
                                                                                dragPosition = offset
                                                                            }
                                                                        },
                                                                        onDrag = { change, dragAmount ->
                                                                            change.consume()
                                                                            
                                                                            // Safe constraint logic within container
                                                                            val nextX = (dragPosition.x + dragAmount.x).coerceIn(0f, containerSize.width.toFloat())
                                                                            val nextY = (dragPosition.y + dragAmount.y).coerceIn(0f, containerSize.height.toFloat())
                                                                            dragPosition = Offset(nextX, nextY)
                                                                            
                                                                            // Target hit testing
                                                                            var bestTargetId: Long? = null
                                                                            var minDistance = Float.MAX_VALUE
                                                                            
                                                                            val snapshot = itemPositions.toMap()
                                                                            snapshot.forEach { (targetId, rect) ->
                                                                                if (targetId != draggingId) {
                                                                                    val distance = (dragPosition - rect.center).getDistance()
                                                                                    if (distance < minDistance && distance < rect.size.minDimension * 0.8f) {
                                                                                        minDistance = distance
                                                                                        bestTargetId = targetId
                                                                                    }
                                                                                }
                                                                            }
                                                                            
                                                                            bestTargetId?.let { targetId ->
                                                                                val fromIdx = mutableBoxes.indexOfFirst { it.id == draggingId }
                                                                                val toIdx = mutableBoxes.indexOfFirst { it.id == targetId }
                                                                                
                                                                                if (fromIdx != -1 && toIdx != -1 && fromIdx != toIdx) {
                                                                                    val itemToMove = mutableBoxes.removeAt(fromIdx)
                                                                                    mutableBoxes.add(toIdx, itemToMove)
                                                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                                }
                                                                            }
                                                                        },
                                                                        onDragEnd = {
                                                                            draggingId = null
                                                                            isDraggingBox = false
                                                                            descriptionViewModel.updateAllBoxPositions(mutableBoxes.toList())
                                                                        },
                                                                        onDragCancel = {
                                                                            draggingId = null
                                                                            isDraggingBox = false
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                            .clip(RoundedCornerShape(12.dp)),
                                                        color = if (isCurrent) PrimaryPurple.copy(alpha = 0.25f) else CardBg,
                                                        shape = RoundedCornerShape(12.dp),
                                                        border = if (isCurrent) BorderStroke(1.dp, PrimaryPurple) else null
                                                    ) {
                                                        Column(modifier = Modifier.padding(12.dp)) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Text(
                                                                    item.boxLabel ?: item.title, 
                                                                    color = if (isCurrent) PrimaryPurple else Color.White, 
                                                                    fontWeight = FontWeight.Bold, 
                                                                    fontSize = 14.sp,
                                                                    modifier = Modifier.weight(1f, fill = false)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // The Floating Overlay Item
                                        val draggedItem = mutableBoxes.find { it.id == draggingId }
                                        if (draggedItem != null) {
                                            val rect = itemPositions[draggedItem.id]
                                            if (rect != null) {
                                                Surface(
                                                    modifier = Modifier
                                                        .offset {
                                                            androidx.compose.ui.unit.IntOffset(
                                                                (dragPosition.x - rect.width / 2f).toInt(),
                                                                (dragPosition.y - rect.height / 2f).toInt()
                                                            )
                                                        }
                                                        .zIndex(100f)
                                                        .graphicsLayer {
                                                            scaleX = 1.1f
                                                            scaleY = 1.1f
                                                            rotationZ = -2f
                                                            shadowElevation = 20f
                                                            alpha = 0.9f
                                                        }
                                                        .clip(RoundedCornerShape(12.dp)),
                                                    color = CardBg,
                                                    shape = RoundedCornerShape(12.dp),
                                                    border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.5f))
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                draggedItem.boxLabel ?: draggedItem.title, 
                                                                color = Color.White, 
                                                                fontWeight = FontWeight.Bold, 
                                                                fontSize = 14.sp,
                                                                modifier = Modifier.weight(1f, fill = false)
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
                } // End if (!isDeleteMode && !isChannelMode)

                if (!isDeleteMode && (!isChannelMode || (showChannelOptions && !isEditMode))) {
                    // Action / Settings Card (Blue Box 4)
                    item {
                        AnnotatedCard(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).graphicsLayer(alpha = descriptionUiTransparency),
                            title = "",
                            coverTransparency = descriptionUiTransparency,
                            cardBg = CardBg
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    if (manga?.parentMangaId != null) {
                                        Surface(
                                            onClick = {
                                                // Always use folder picker for Seasons to allow "Use this folder"
                                                folderPickerLauncher.launch(null)
                                            },
                                            color = Color.Transparent,
                                            shape = CircleShape,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.2f))
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Rounded.Add, contentDescription = "Add Content", tint = PrimaryPurple)
                                                Text("Add Content", color = PrimaryPurple, modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    } else {
                                        val currentIsNsfw = if (isEditMode) draftIsNsfw else manga?.isNsfw == true
                                        JellyToggle(
                                            option1 = sfwText,
                                            option2 = nsfwText,
                                            isOption2 = currentIsNsfw,
                                            onToggle = { isOption2 ->
                                                if (isEditMode) descriptionViewModel.draftIsNsfw.value = isOption2
                                                else descriptionViewModel.setNsfw(isOption2)
                                            },
                                            glowBrightness = glowBrightness,
                                            glowRadius = glowRadius,
                                            showGlow = false,
                                            glowColorOverride = Color(glowColor),
                                            toggleWidth = 110.dp
                                        )
                                    }

                                    // Local / External storage mode toggle pill
                                    JellyToggle(
                                        option1 = "Local",
                                        option2 = "External",
                                        isOption2 = storageMode == 1,
                                        onToggle = { isExternal ->
                                            settingsViewModel.setStorageMode(if (isExternal) 1 else 0)
                                        },
                                        glowBrightness = glowBrightness,
                                        glowRadius = glowRadius,
                                        showGlow = false,
                                        glowColorOverride = PrimaryPurple,
                                        toggleWidth = 135.dp
                                    )
                                }

                                IconButton(
                                    modifier = Modifier.padding(start = 4.dp),
                                    onClick = { 
                                        if (manga?.contentType == 1) {
                                            val hasLinkedScreens = manga?.parentMangaId == null && childBoxes.isNotEmpty()
                                            if (hasLinkedScreens) {
                                                showParentDeleteWarningDialog = true
                                            } else {
                                                showDeleteDialog = true
                                            }
                                        } else {
                                            showDeleteChoiceDialog = true 
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Rounded.DeleteSweep,
                                        contentDescription = "Delete",
                                        tint = PrimaryPurple.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Tracker Section
                if (!isEditMode && (manga?.lastReadTitle != null || manga?.lastReadPage != null)) {
                    item {
                        PremiumGlassPanel(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp)
                                .graphicsLayer(alpha = descriptionUiTransparency)
                                .clip(RoundedCornerShape(32.dp))
                                .clickable {
                                    val contentType = manga?.contentType ?: 0
                                    if (contentType == 1) {
                                        onNavigateToMedia(mangaId, 1)
                                    } else {
                                        val targetTitle = manga?.lastReadTitle
                                        val targetChapter = chapters.find { it.title == targetTitle }
                                        if (targetChapter != null) {
                                            onNavigateToMedia(targetChapter.id, contentType)
                                        }
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Content-type-specific icon
                                val trackedIcon = when (manga?.contentType) {
                                    0 -> Icons.AutoMirrored.Rounded.MenuBook // Toon — stacked panels look (目)
                                    1 -> Icons.Rounded.Book                  // Book / PDF
                                    else -> Icons.Rounded.PlayArrow           // Video (series / channel)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryPurple.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = trackedIcon,
                                        contentDescription = null,
                                        tint = PrimaryPurple,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Last Tracked",
                                        color = PrimaryPurple.copy(alpha = 0.7f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    // Just show the chapter title (or track name for audio)
                                    val chapterLabel = manga?.lastReadTitle?.takeIf { it.isNotBlank() }
                                        ?: when (manga?.contentType) {
                                            1 -> "Page ${manga?.lastReadPage ?: 1}"
                                            else -> "Chapter 1"
                                        }
                                    Text(
                                        text = chapterLabel,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Chapters Section (Blue Box 5)
                if (manga?.contentType != 1 && !isEditMode) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (manga?.contentType == 2) "Videos" else "Chapters",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            
                            IconButton(
                                onClick = { 
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (manga?.contentType == 2) {
                                        multipleVideoPickerLauncher.launch(arrayOf("video/*"))
                                    } else {
                                        folderPickerLauncher.launch(null)
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = if (manga?.contentType == 2) "Add Videos" else "Add Chapters", tint = PrimaryPurple)
                            }
                        }
                    }

                    if (showChapters) {
                        if (manga?.contentType == 2) {
                            val totalPages = (chapters.size + itemsPerPage - 1) / itemsPerPage
                            val currentItems = chapters.drop(currentPage * itemsPerPage).take(itemsPerPage)

                                items(currentItems, key = { it.id }) { chapter ->
                                val isSelected = selectedChapters.contains(chapter.id)

                                // ── Snappy Premium Video Card ──
                                var showPreview by remember { mutableStateOf(false) }
                                var wasPreviewing by remember { mutableStateOf(false) }

                                val videoScale by animateFloatAsState(
                                    targetValue = if (showPreview) 1.1f else 1f,
                                    animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
                                    label = "video_scale"
                                )

                                val videoDuration = if (chapter.duration > 0) {
                                    val totalSeconds = chapter.duration / 1000
                                    val minutes = totalSeconds / 60
                                    val seconds = totalSeconds % 60
                                    String.format("%02d:%02d", minutes, seconds)
                                } else null

                                // Thumbnail strategy (fast path):
                                // ViewModel.scanForMissingMetadata() already extracts thumbnails
                                // in the background on IO and writes them to the DB. Once written,
                                // the chapters Flow emits with thumbnailUri populated → this item
                                // recomposes with a valid path automatically.
                                // The old inline LaunchedEffect + MediaMetadataRetriever duplicated
                                // that work on the UI thread's IO pool, causing frame drops while
                                // scrolling because every visible item was competing for disk I/O.

                                val videoCardWidth = when (descriptionLayoutMode) {
                                    0 -> 0.45f // Compact
                                    1 -> 0.7f  // Large
                                    else -> 1f // Full
                                }

                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth(if (isLandscape) videoCardWidth else 1f)
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                            .graphicsLayer { alpha = descriptionUiTransparency },
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        // Top: Large Video Container
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(16f / 9f)
                                            .graphicsLayer {
                                                scaleX = videoScale
                                                scaleY = videoScale
                                                shape = RoundedCornerShape(24.dp)
                                                clip = true
                                            }
                                            .background(Color.Black)
                                            .pointerInput(chapter.id, isDeleteMode) {
                                                awaitEachGesture {
                                                    val down = awaitFirstDown(requireUnconsumed = false)
                                                    // In delete/select mode, don't activate preview — let clickable handle selection
                                                    if (isDeleteMode) return@awaitEachGesture
                                                    showPreview = false
                                                    wasPreviewing = false
                                                    var held = true
                                                    val startTime = System.currentTimeMillis()
                                                    do {
                                                        val event = awaitPointerEvent()
                                                        val pointer = event.changes.firstOrNull { it.id == down.id }
                                                        if (pointer == null || !pointer.pressed) {
                                                            held = false
                                                            break
                                                        }
                                                        val elapsed = System.currentTimeMillis() - startTime
                                                        if (!showPreview && elapsed >= 100L) {
                                                            showPreview = true
                                                            wasPreviewing = true
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        }
                                                    } while (held)
                                                    showPreview = false
                                                }
                                            }
                                            .clickable {
                                                if (isDeleteMode) {
                                                    if (isSelected) selectedChapters.remove(chapter.id)
                                                    else selectedChapters.add(chapter.id)
                                                } else {
                                                    if (!wasPreviewing) {
                                                        onNavigateToMedia(chapter.id, manga?.contentType ?: 0)
                                                    }
                                                    wasPreviewing = false
                                                }
                                            }
                                    ) {
                                        val thumbnailAlpha by animateFloatAsState(
                                            targetValue = if (showPreview) 0f else 1f,
                                            animationSpec = tween(300),
                                            label = "ThumbnailAlpha"
                                        )

                                        // Cache key strategy:
                                        // - When thumbnailUri is set → use it as both model AND cache key.
                                        //   The file path is stable and unique per-thumbnail.
                                        // - When null → use folderUri as model (Coil VideoFrameDecoder
                                        //   extracts a frame) AND as cache key.
                                        // Crucially, the two keys differ so Coil NEVER serves the
                                        // stale first-frame decode after the ViewModel writes the
                                        // real thumbnail path to the DB (which changes the key).
                                        val (modelData, cacheKey) = remember(chapter.thumbnailUri, chapter.folderUri) {
                                            if (chapter.thumbnailUri != null) {
                                                // Use the exact path as the cache key. If the path changes (e.g. from
                                                // a re-extraction), Coil will fetch the new image instead of serving
                                                // a stale, potentially half-black cached version.
                                                File(chapter.thumbnailUri) to chapter.thumbnailUri!!
                                            } else {
                                                Uri.parse(chapter.folderUri) to "vthumb_raw_${chapter.id}"
                                            }
                                        }

                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(modelData)
                                                .crossfade(false)
                                                .memoryCacheKey(cacheKey)
                                                .diskCacheKey(cacheKey)
                                                .build(),
                                            imageLoader = sharedVideoImageLoader,
                                            contentDescription = "Video Thumbnail",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = thumbnailAlpha }
                                        )

                                        // Overlay Video Preview when active
                                        if (showPreview) {
                                            VideoPreview(
                                                uri = Uri.parse(chapter.folderUri),
                                                initialDuration = chapter.duration,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        
                                        // Timestamp overlay
                                        if (videoDuration != null && !showPreview) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(12.dp)
                                            ) {
                                                Text(
                                                    text = videoDuration,
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier
                                                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        if (isDeleteMode) {
                                            Box(modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                                                Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = null,
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = PrimaryPurple,
                                                        uncheckedColor = Color.White.copy(alpha = 0.6f)
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    Text(
                                        text = chapter.title,
                                        color = if (isSelected) PrimaryPurple else Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                            // Pagination Controls for Video Mode
                            if (totalPages > 1) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val density = androidx.compose.ui.platform.LocalDensity.current
                                        val glowPaint = remember(PrimaryPurple) {
                                            android.graphics.Paint().apply {
                                                isAntiAlias = true
                                                color = PrimaryPurple.copy(alpha = 0.6f).toArgb()
                                                maskFilter = android.graphics.BlurMaskFilter(
                                                    with(density) { 6.dp.toPx() },
                                                    android.graphics.BlurMaskFilter.Blur.NORMAL
                                                )
                                            }
                                        }

                                        // Prev Button (Minimal Icon)
                                        val isPrevEnabled = currentPage > 0
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(CardBg.copy(alpha = 0.6f))
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isPrevEnabled) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable(enabled = isPrevEnabled) { 
                                                    currentPage--
                                                    coroutineScope.launch { scrollState.animateScrollToItem(0) }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                                contentDescription = "Prev",
                                                tint = if (isPrevEnabled) Color.White else Color.White.copy(alpha = 0.25f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Page Numbers with sliding window and last page always shown
                                        val pageItems = remember(currentPage, totalPages) {
                                            val items = mutableListOf<Int?>()
                                            if (totalPages <= 5) {
                                                for (p in 0 until totalPages) {
                                                    items.add(p)
                                                }
                                            } else {
                                                val start = maxOf(0, minOf(currentPage - 1, totalPages - 3))
                                                if (start + 2 < totalPages - 2) {
                                                    items.add(start)
                                                    items.add(start + 1)
                                                    items.add(start + 2)
                                                    items.add(null) // Represents dots ".."
                                                    items.add(totalPages - 1)
                                                } else {
                                                    for (p in (totalPages - 4) until totalPages) {
                                                        items.add(p)
                                                    }
                                                }
                                            }
                                            items
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            pageItems.forEach { pageIndex ->
                                                if (pageIndex != null) {
                                                    val isPageSelected = pageIndex == currentPage
                                                    Box(
                                                        modifier = Modifier
                                                            .size(40.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .drawBehind {
                                                                if (isPageSelected) {
                                                                    drawIntoCanvas { canvas ->
                                                                        canvas.nativeCanvas.drawRoundRect(
                                                                            -3.dp.toPx(), -3.dp.toPx(),
                                                                            size.width + 3.dp.toPx(), size.height + 3.dp.toPx(),
                                                                            11.dp.toPx(), 11.dp.toPx(),
                                                                            glowPaint
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                            .background(if (isPageSelected) PrimaryPurple.copy(alpha = 0.2f) else CardBg.copy(alpha = 0.6f))
                                                            .border(
                                                                width = 1.5.dp,
                                                                color = if (isPageSelected) PrimaryPurple else Color.Transparent,
                                                                shape = RoundedCornerShape(8.dp)
                                                            )
                                                            .clickable { 
                                                                currentPage = pageIndex
                                                                coroutineScope.launch { scrollState.animateScrollToItem(0) }
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = (pageIndex + 1).toString(),
                                                            color = if (isPageSelected) Color.White else Color.White.copy(alpha = 0.8f),
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontSize = 15.sp
                                                        )
                                                    }
                                                } else {
                                                    Text(
                                                        text = "..",
                                                        color = Color.White.copy(alpha = 0.5f),
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Next Button (Minimal Icon)
                                        val isNextEnabled = currentPage < totalPages - 1
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(CardBg.copy(alpha = 0.6f))
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isNextEnabled) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable(enabled = isNextEnabled) { 
                                                    currentPage++
                                                    coroutineScope.launch { scrollState.animateScrollToItem(0) }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                                contentDescription = "Next",
                                                tint = if (isNextEnabled) Color.White else Color.White.copy(alpha = 0.25f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // ── Minimal Chapter Dial Picker ──
                            item {
                                val listState = rememberLazyListState()
                                val itemHeight = 56.dp
                                val visibleItems = 4
                                val pickerHeight = itemHeight * visibleItems
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(pickerHeight)
                                        .padding(horizontal = 16.dp)
                                        .graphicsLayer { alpha = descriptionUiTransparency }
                                ) {
                                    // ── Chapter Dial ──
                                    // The edge-fade MUST use CompositingStrategy.Offscreen +
                                    // BlendMode.DstIn so the LazyColumn items become genuinely
                                    // transparent at the edges. A gradient Box overlay only works
                                    // against a flat/known background color — it breaks immersion
                                    // when the user changes themes or the background is animated.
                                    // DstIn makes the edges of the content itself transparent,
                                    // so whatever is behind (any color, any animation) shows through.
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                compositingStrategy = CompositingStrategy.Offscreen
                                            }
                                            .drawWithContent {
                                                drawContent()
                                                drawRect(
                                                    brush = Brush.verticalGradient(
                                                        0f to Color.Transparent,
                                                        0.25f to Color.Black,
                                                        0.75f to Color.Black,
                                                        1f to Color.Transparent
                                                    ),
                                                    blendMode = BlendMode.DstIn
                                                )
                                            }
                                    ) {
                                        LazyColumn(
                                            state = listState,
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(vertical = 84.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            items(chapters, key = { it.id }) { chapter ->
                                                val isSelected = selectedChapters.contains(chapter.id)

                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(itemHeight)
                                                        .clickable(
                                                            interactionSource = remember { MutableInteractionSource() },
                                                            indication = null
                                                        ) {
                                                            if (isDeleteMode) {
                                                                if (isSelected) selectedChapters.remove(chapter.id)
                                                                else selectedChapters.add(chapter.id)
                                                            } else {
                                                                descriptionViewModel.updateTracker(title = chapter.title)
                                                                onNavigateToMedia(chapter.id, manga?.contentType ?: 0)
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        if (isDeleteMode) {
                                                            Checkbox(
                                                                checked = isSelected,
                                                                onCheckedChange = null,
                                                                modifier = Modifier.padding(end = 8.dp).size(20.dp),
                                                                colors = CheckboxDefaults.colors(
                                                                    checkedColor = PrimaryPurple,
                                                                    uncheckedColor = Color.White.copy(alpha = 0.6f)
                                                                )
                                                            )
                                                        }
                                                        Text(
                                                            text = chapter.title,
                                                            color = if (isSelected) PrimaryPurple else Color.White,
                                                            fontSize = 24.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Central indicator dial line
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .offset(x = (-70).dp)
                                            .width(24.dp)
                                            .height(3.dp)
                                            .background(PrimaryPurple, RoundedCornerShape(50))
                                    )
                                }
                            }
                        }
                    } // closes if (showChapters)
                } else if (!isEditMode && manga?.contentType == 1) {
                    item {
                        Button(
                            onClick = { onNavigateToMedia(mangaId, 1) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 16.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Text("Read Now", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } // closes if (!isEditMode && manga?.contentType != 1)
                
                item {
                    Spacer(modifier = Modifier.height(180.dp))
                }
            }

            // 1. FULL SCREEN BLUR OVERLAY
            androidx.compose.animation.AnimatedVisibility(
                visible = isDescriptionDrawerExpanded,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(400)),
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(400)),
                modifier = Modifier.zIndex(100f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Background Blur Layer
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f))
                            .blur(24.dp)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                isDescriptionDrawerExpanded = false
                                isCloverExpanded = false
                            }
                    )
                }
            }

            // --- FLOATING TOP BAR ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .zIndex(10f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 40.dp, bottom = 12.dp)
                ) {
                    IconButton(
                        onClick = { 
                            if (isEditMode) descriptionViewModel.toggleEditMode() 
                            else if (isDeleteMode) isDeleteMode = false
                            else onNavigateBack() 
                        },
                        modifier = Modifier.padding(start = 12.dp, top = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack, 
                            contentDescription = "Back", 
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    if (isEditMode) {
                        IconButton(
                            onClick = { descriptionViewModel.saveManga() },
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp, top = 12.dp)
                        ) {
                            Icon(imageVector = Icons.Rounded.Save, contentDescription = "Save", tint = PrimaryPurple)
                        }
                    } else if (isDeleteMode) {
                        IconButton(
                            onClick = { 
                                descriptionViewModel.deleteSelectedChapters(selectedChapters.toList())
                                isDeleteMode = false
                                selectedChapters.clear()
                            },
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp, top = 12.dp)
                        ) {
                            Icon(imageVector = Icons.Rounded.DeleteSweep, contentDescription = "Delete Selected", tint = Color(0xFFE57373))
                        }
                    } else if (isChannelMode && !isEditMode) {
                        IconButton(
                            onClick = { showChannelOptions = !showChannelOptions },
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp, top = 12.dp)
                        ) {
                            Icon(
                                imageVector = if (showChannelOptions) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, 
                                contentDescription = if (showChannelOptions) "Collapse" else "Expand", 
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            // --- FULL SCREEN BLUR OVERLAY (Detached clover panel) ---
            androidx.compose.animation.AnimatedVisibility(
                visible = isCloverExpanded,
                enter = androidx.compose.animation.fadeIn(tween(400)),
                exit = androidx.compose.animation.fadeOut(tween(400)),
                modifier = Modifier.zIndex(100f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Background Blur Layer
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.65f)) // Darkened to compensate for removed blur
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { isCloverExpanded = false }
                            )
                    )

                    HolographicCloverPanel(
                        isExpanded = isCloverExpanded,
                        onDismiss = { isCloverExpanded = false },
                        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}) 
                    ) {
                        PremiumSlider(
                            label = "Description Transparency",
                            value = descriptionUiTransparency,
                            onValueChange = { settingsViewModel.setDescriptionUiTransparency(it) }
                        )

                        if (manga?.contentType == 2) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Layout Mode",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            JellyToggle3(
                                options = listOf("Compact", "Large", "Full"),
                                selectedIndex = descriptionLayoutMode,
                                onToggle = { settingsViewModel.setDescriptionLayoutMode(it) },
                                glowBrightness = 0.8f,
                                glowRadius = 15f,
                                showGlow = false,
                                glowColorOverride = PrimaryPurple
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnnotatedCard(
    modifier: Modifier = Modifier,
    title: String,
    coverTransparency: Float = 1f,
    cardBg: Color,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        color = cardBg.copy(alpha = coverTransparency),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            content()
        }
    }
}
