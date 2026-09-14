package com.ballade.hwaran.frontend.home

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ballade.hwaran.core.database.entity.MangaEntity
import com.ballade.hwaran.core.service.VaultMigrationManager
import com.ballade.hwaran.core.util.CoverArtResolver
import com.ballade.hwaran.core.util.LocalVaultMigrator

@Composable
fun MediaQuickActionsSheet(
    manga: MangaEntity,
    isMigrating: Boolean = false,
    migrationProgress: Int = 0,
    migrationStatus: String = "",
    onShiftToLocal: () -> Unit,
    onEditMetadata: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isVault = remember(manga.parentUri) { LocalVaultMigrator.isItemInVault(manga) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val currentMigration by VaultMigrationManager.currentMigration.collectAsState()
    val isThisItemMigrating = currentMigration?.mangaId == manga.id
    val effectiveIsMigrating = isMigrating || isThisItemMigrating
    val effectiveProgress = if (isThisItemMigrating) currentMigration?.progress ?: 0 else migrationProgress
    val effectiveStatus = if (isThisItemMigrating) currentMigration?.status ?: "" else migrationStatus

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(if (isLandscape) 0.55f else 1f)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* prevent backdrop dismissal */ }
                    .border(
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                    ),
                color = Color(0xFF101015),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Drag handle
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f))
                    )

                    // Media Header (Cover + Title + Badges)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        val coverModel = remember(manga.coverPath, manga.parentUri) {
                            CoverArtResolver.resolveCoverModel(manga.coverPath, manga.parentUri, null, context)
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1A1924),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                            modifier = Modifier
                                .width(56.dp)
                                .height(80.dp)
                        ) {
                            if (coverModel != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(coverModel)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = manga.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (manga.contentType) {
                                            1 -> Icons.Rounded.Book
                                            2 -> Icons.Rounded.Tv
                                            3 -> Icons.Rounded.MusicNote
                                            4 -> Icons.Rounded.ImportContacts
                                            else -> Icons.AutoMirrored.Rounded.MenuBook
                                        },
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.25f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    color = Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                                ) {
                                    Text(
                                        text = when (manga.contentType) {
                                            0 -> if (manga.boxPurpose == "manhua") "MANHUA" else "MANGA"
                                            1 -> "BOOK"
                                            2 -> if (manga.boxPurpose == "channel") "CHANNEL" else "SERIES"
                                            3 -> "MUSIC"
                                            4 -> "NOVEL"
                                            else -> "MEDIA"
                                        },
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Surface(
                                    color = if (isVault) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, if (isVault) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.09f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isVault) Icons.Rounded.Lock else Icons.Rounded.FolderShared,
                                            contentDescription = null,
                                            tint = if (isVault) Color(0xFFE2E8F0) else Color.White.copy(alpha = 0.5f),
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Text(
                                            text = if (isVault) "LOCAL VAULT" else "EXTERNAL SAF",
                                            color = if (isVault) Color(0xFFE2E8F0) else Color.White.copy(alpha = 0.5f),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Text(
                                text = manga.title,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (!manga.genre.isNullOrBlank()) {
                                Text(
                                    text = manga.genre,
                                    color = Color.White.copy(alpha = 0.45f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.06f))
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    // Migration in progress banner
                    if (effectiveIsMigrating) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF161922),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = Color(0xFFE2E8F0),
                                        strokeWidth = 2.5.dp
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Shifting to Local Vault... $effectiveProgress%",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (effectiveStatus.isNotBlank()) effectiveStatus else "Transferring files in background • Safe to close app",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { effectiveProgress / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(CircleShape),
                                    color = Color(0xFFE2E8F0),
                                    trackColor = Color.White.copy(alpha = 0.10f)
                                )
                            }
                        }
                    }

                    // ── Action 1: Shift to Local Vault ──
                    QuickActionItem(
                        icon = if (isVault) Icons.Rounded.LockClock else Icons.Rounded.Lock,
                        title = if (isVault) "Already in Local Vault" else "Shift to Local Vault",
                        subtitle = if (isVault) "Media is stored in private app vault (.nomedia protected)"
                                   else "Background service moves files so other apps cannot see it",
                        enabled = !isVault && !effectiveIsMigrating,
                        trailingBadge = if (isVault) "PROTECTED" else null,
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onShiftToLocal()
                        }
                    )

                    // ── Action 2: Edit Metadata ──
                    QuickActionItem(
                        icon = Icons.Rounded.EditNote,
                        title = "Edit Metadata",
                        subtitle = "Modify title, description, cover art, tags, and reading notes",
                        enabled = !effectiveIsMigrating,
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onEditMetadata()
                        }
                    )

                    // ── Action 3: Delete Media ──
                    QuickActionItem(
                        icon = Icons.Rounded.DeleteOutline,
                        title = "Delete Media",
                        subtitle = if (isVault) "Permanently delete files from vault and remove from library"
                                   else "Delete files and remove entry from your library",
                        isDestructive = true,
                        enabled = !effectiveIsMigrating,
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            showDeleteConfirmDialog = true
                        }
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }

    // Confirmation Alert Dialog for Deletion
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = Color(0xFF161520),
            title = {
                Text(
                    text = "Delete \"${manga.title}\"?",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isVault) {
                        "This will permanently delete this title's files from your private vault and remove it from your library."
                    } else {
                        "This will remove \"${manga.title}\" from your library and delete the original files from storage."
                    },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun QuickActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    trailingBadge: String? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = if (isDestructive) Color(0xFF2A1418).copy(alpha = 0.45f) else Color.White.copy(alpha = 0.04f),
        border = BorderStroke(
            1.dp,
            if (isDestructive) Color(0xFFEF5350).copy(alpha = 0.30f)
            else if (enabled) Color.White.copy(alpha = 0.10f)
            else Color.White.copy(alpha = 0.05f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isDestructive) Color(0xFFEF5350).copy(alpha = 0.15f)
                        else Color.White.copy(alpha = 0.07f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isDestructive) Color(0xFFEF5350)
                           else if (enabled) Color(0xFFE2E8F0)
                           else Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        color = if (isDestructive) Color(0xFFFF8A80)
                               else if (enabled) Color.White
                               else Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (trailingBadge != null) {
                        Surface(
                            color = Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                        ) {
                            Text(
                                text = trailingBadge,
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    color = if (isDestructive) Color(0xFFFF8A80).copy(alpha = 0.65f)
                           else Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = if (isDestructive) Color(0xFFEF5350).copy(alpha = 0.5f)
                       else Color.White.copy(alpha = 0.25f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
