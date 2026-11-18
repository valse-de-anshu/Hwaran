package com.ballade.hwaran.frontend.lock

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.Lock
import com.ballade.hwaran.ui.theme.AccentTitanium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.core.util.CoverArtResolver
import com.ballade.hwaran.ui.viewmodels.LibraryViewModel
import com.ballade.hwaran.ui.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockSelectionScreen(
    libraryViewModel: LibraryViewModel,
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val allManga by libraryViewModel.allMangaState.collectAsState()
    val libraryPassword by settingsViewModel.libraryPassword.collectAsState()
    val coverTransparency by settingsViewModel.coverTransparency.collectAsState()

    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val toons = remember(allManga) { allManga.filter { it.contentType == 0 } }
    val books = remember(allManga) { allManga.filter { it.contentType == 1 } }
    val videos = remember(allManga) { allManga.filter { it.contentType == 2 } }
    val music = remember(allManga) { allManga.filter { it.contentType == 3 } }
    val novels = remember(allManga) { allManga.filter { it.contentType == 4 } }

    val categories = listOf("All", "Toons", "Books", "Videos", "Music", "Novels")

    val allSections = listOf(
        "Toons" to toons,
        "Books" to books,
        "Videos" to videos,
        "Music" to music,
        "Novels" to novels
    )

    val displayedSections = remember(selectedCategory, allManga) {
        if (selectedCategory == "All") {
            allSections.filter { it.second.isNotEmpty() }
        } else {
            allSections.filter { it.first.equals(selectedCategory, ignoreCase = true) && it.second.isNotEmpty() }
        }
    }

    val accentColor = AccentTitanium

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (showPasswordDialog) {
                AlertDialog(
                    onDismissRequest = { 
                        showPasswordDialog = false 
                        passwordInput = ""
                    },
                    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
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
                                Text("Set Vault Password", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Create a password to protect your vault", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                            }
                        }
                    },
                    text = {
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                placeholder = { Text("New Password", color = Color.White.copy(alpha = 0.35f), fontSize = 14.sp) },
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
                        }
                    },
                    confirmButton = {
                        Surface(
                            onClick = {
                                if (passwordInput.isNotBlank()) {
                                    settingsViewModel.setLibraryPassword(passwordInput)
                                    settingsViewModel.setIsLibraryLocked(true)
                                    showPasswordDialog = false
                                    onNavigateBack()
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
                                Text("Set Password", color = AccentTitanium, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    },
                    dismissButton = {
                        Surface(
                            onClick = { 
                                showPasswordDialog = false 
                                passwordInput = ""
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

            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())
            ) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(this.maxLineSpan) }) {
                    Spacer(modifier = Modifier.height(72.dp))
                }
                
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(this.maxLineSpan) }) {
                    Text(
                        "Select Items to Lock", 
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

                // Category Filter Pills
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(this.maxLineSpan) }) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                    ) {
                        items(categories) { cat ->
                            val isSelected = cat == selectedCategory
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) accentColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
                                border = BorderStroke(
                                    1.dp, 
                                    if (isSelected) accentColor.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.1f)
                                ),
                                modifier = Modifier.clickable { selectedCategory = cat }
                            ) {
                                Text(
                                    text = cat,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }

                if (displayedSections.isEmpty()) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(this.maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No media found in this category",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                displayedSections.forEach { (title, items) ->
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(this.maxLineSpan) }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)
                        ) {
                            Text(
                                text = title,
                                color = accentColor,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            val lockedCount = items.count { it.isLocked }
                            if (lockedCount > 0) {
                                Text(
                                    text = "$lockedCount locked",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    items(items, key = { it.id }) { manga ->
                        LockSelectionCard(
                            manga = manga,
                            coverTransparency = coverTransparency,
                            primaryColor = accentColor,
                            onToggle = { 
                                libraryViewModel.updateMangaLockState(manga, !manga.isLocked)
                            }
                        )
                    }
                }
                
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(this.maxLineSpan) }) {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            // Floating Top Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.9f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(top = 40.dp, bottom = 32.dp)
            ) {
                CenterAlignedTopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.padding(start = 8.dp, top = 20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack, 
                                contentDescription = "Back", 
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { 
                            if (libraryPassword.isEmpty()) {
                                showPasswordDialog = true
                            } else {
                                onNavigateBack()
                            }
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Done", tint = accentColor)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            }
        }
    }
}

@Composable
fun LockSelectionCard(
    manga: MangaEntity, 
    coverTransparency: Float, 
    primaryColor: Color,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    val coverModel = remember(manga.coverPath, manga.parentUri) {
        CoverArtResolver.resolveCoverModel(manga.coverPath, manga.parentUri, null, context)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))) {
            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).graphicsLayer { alpha = coverTransparency }) {
                if (coverModel != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(coverModel)
                            .crossfade(true)
                            .build(),
                        contentDescription = manga.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.05f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No Cover", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                    }
                }

                // Lock badge overlay
                if (manga.isLocked) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(primaryColor.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "Locked",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Top fade
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                            )
                        )
                )

                // Bottom fade
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                        )
                )
            }

            Text(
                text = manga.title,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 4.dp, vertical = 10.dp)
            )
        }
    }
}

