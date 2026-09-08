package com.ballade.hwaran.frontend.workspace.move

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ballade.hwaran.core.database.entity.MangaEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveEntireWorkspace(
    workspaces: List<String>,
    allManga: List<MangaEntity>,
    mediaMode: Int,
    videoLayoutMode: Int,
    onDismiss: () -> Unit,
    onMoveEntireWorkspace: (String, String) -> Unit
) {
    var selectedSourceWorkspace by remember { mutableStateOf<String?>(null) }
    var selectedTargetWorkspace by remember { mutableStateOf<String?>(null) }
    var isSourceDropdownExpanded by remember { mutableStateOf(false) }
    var isTargetDropdownExpanded by remember { mutableStateOf(false) }
    val primaryColor = MaterialTheme.colorScheme.primary

    val itemsInSource = remember(selectedSourceWorkspace, allManga, mediaMode, videoLayoutMode) {
        if (selectedSourceWorkspace == null) emptyList()
        else allManga.filter {
            it.contentType == mediaMode &&
            it.parentMangaId == null &&
            (mediaMode != 2 || it.boxPurpose == (if (videoLayoutMode == 1) "channel" else "series") || (videoLayoutMode == 0 && it.boxPurpose == null)) &&
            if (selectedSourceWorkspace == "I Love It") {
                it.workspace.isNullOrEmpty() || it.workspace == "I Love It"
            } else {
                it.workspace == selectedSourceWorkspace
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move Entire Workspace", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 480.dp)) {
                Text("Move all contents between existing workspaces. History events will be updated automatically.", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(20.dp))
                
                // Source Dropdown
                Text("From:", color = Color.White, fontSize = 12.sp)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedSourceWorkspace ?: "Select Workspace",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White.copy(alpha = 0.3f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = Color.White) }
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { isSourceDropdownExpanded = true }
                    )
                    DropdownMenu(
                        expanded = isSourceDropdownExpanded,
                        onDismissRequest = { isSourceDropdownExpanded = false },
                        modifier = Modifier.background(Color(0xFF222222))
                    ) {
                        workspaces.forEach { ws ->
                            DropdownMenuItem(
                                text = { Text(ws, color = Color.White) },
                                onClick = { 
                                    selectedSourceWorkspace = ws
                                    if (selectedTargetWorkspace == ws) {
                                        selectedTargetWorkspace = null
                                    }
                                    isSourceDropdownExpanded = false 
                                }
                            )
                        }
                    }
                }

                // Source contents preview row
                if (selectedSourceWorkspace != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Workspace Contents (${itemsInSource.size}):", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    if (itemsInSource.isEmpty()) {
                        Text("No items inside this workspace.", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        ) {
                            items(itemsInSource) { manga ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(52.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp, 68.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(alpha = 0.05f))
                                            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (manga.coverPath.isNotEmpty() && manga.coverPath != "android.resource://android/drawable/ic_menu_gallery") {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(manga.coverPath)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = manga.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (manga.contentType == 3) Icons.Rounded.MusicNote else Icons.Rounded.Image,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.2f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = manga.boxLabel ?: manga.title,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                
                // Target Dropdown
                Text("To:", color = Color.White, fontSize = 12.sp)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedTargetWorkspace ?: "Select Destination",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White.copy(alpha = 0.3f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        ),
                        trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = Color.White) }
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { isTargetDropdownExpanded = true }
                    )
                    DropdownMenu(
                        expanded = isTargetDropdownExpanded,
                        onDismissRequest = { isTargetDropdownExpanded = false },
                        modifier = Modifier.background(Color(0xFF222222))
                    ) {
                        workspaces.forEach { ws ->
                            DropdownMenuItem(
                                text = { Text(ws, color = Color.White) },
                                onClick = { 
                                    selectedTargetWorkspace = ws
                                    if (selectedSourceWorkspace == ws) {
                                        selectedSourceWorkspace = null
                                    }
                                    isTargetDropdownExpanded = false 
                                }
                            )
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFF161616),
        shape = RoundedCornerShape(28.dp),
        confirmButton = {
            TextButton(
                enabled = selectedSourceWorkspace != null && selectedTargetWorkspace != null && selectedSourceWorkspace != selectedTargetWorkspace,
                onClick = {
                    if (selectedSourceWorkspace != null && selectedTargetWorkspace != null) {
                        onMoveEntireWorkspace(selectedSourceWorkspace!!, selectedTargetWorkspace!!)
                    }
                }
            ) { Text("Move All", color = if (selectedSourceWorkspace != null && selectedTargetWorkspace != null && selectedSourceWorkspace != selectedTargetWorkspace) primaryColor else Color.Gray, fontWeight = FontWeight.ExtraBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}
