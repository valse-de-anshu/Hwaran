package com.ballade.hwaran.ui.screens.workspace.move

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.ballade.hwaran.data.local.MangaEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveItemsBetweenWorkspace(
    workspaces: List<String>,
    allManga: List<MangaEntity>,
    mediaMode: Int,
    videoLayoutMode: Int,
    onDismiss: () -> Unit,
    onMoveItems: (List<Long>, String, String) -> Unit
) {
    var selectedSourceWorkspace by remember { mutableStateOf<String?>(null) }
    var selectedTargetWorkspace by remember { mutableStateOf<String?>(null) }
    var isSourceDropdownExpanded by remember { mutableStateOf(false) }
    var isTargetDropdownExpanded by remember { mutableStateOf(false) }
    
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
    
    val selectedItems = remember { mutableStateListOf<Long>() }
    val primaryColor = MaterialTheme.colorScheme.primary

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move Specific Covers", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 500.dp)) {
                Text("Moves the root cover and all nested contents (seasons, episodes) together.", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = selectedSourceWorkspace ?: "From",
                            onValueChange = {}, 
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White.copy(alpha = 0.3f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                            )
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
                                        selectedItems.clear()
                                    }
                                )
                            }
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = selectedTargetWorkspace ?: "To",
                            onValueChange = {}, 
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White.copy(alpha = 0.3f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                            )
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
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Select Items:", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                if (selectedSourceWorkspace != null) {
                    if (itemsInSource.isEmpty()) {
                        Text("No items in this workspace.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            items(itemsInSource.size) { i ->
                                val manga = itemsInSource[i]
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { 
                                        if (selectedItems.contains(manga.id)) selectedItems.remove(manga.id) else selectedItems.add(manga.id)
                                    }.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = selectedItems.contains(manga.id),
                                        onCheckedChange = null,
                                        colors = CheckboxDefaults.colors(checkedColor = primaryColor)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp, 48.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.White.copy(alpha = 0.05f))
                                            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp)),
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
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(manga.boxLabel ?: manga.title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFF161616),
        shape = RoundedCornerShape(28.dp),
        confirmButton = {
            TextButton(
                enabled = selectedSourceWorkspace != null && selectedTargetWorkspace != null && selectedSourceWorkspace != selectedTargetWorkspace && selectedItems.isNotEmpty(),
                onClick = {
                    if (selectedSourceWorkspace != null && selectedTargetWorkspace != null && selectedItems.isNotEmpty()) {
                        onMoveItems(selectedItems.toList(), selectedSourceWorkspace!!, selectedTargetWorkspace!!)
                    }
                }
            ) { Text("Move Selected", color = if (selectedSourceWorkspace != null && selectedTargetWorkspace != null && selectedSourceWorkspace != selectedTargetWorkspace && selectedItems.isNotEmpty()) primaryColor else Color.Gray, fontWeight = FontWeight.ExtraBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}
