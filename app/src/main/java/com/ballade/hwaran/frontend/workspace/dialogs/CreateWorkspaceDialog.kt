package com.ballade.hwaran.frontend.workspace.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ballade.hwaran.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateWorkspaceDialog(
    initialMediaMode: Int,
    initialVideoLayoutMode: Int,
    onDismiss: () -> Unit,
    onCreate: (String, Int) -> Unit,
    onMoveEntireWorkspaceClick: () -> Unit,
    onMoveSpecificCoversClick: () -> Unit,
    onRenameWorkspaceClick: () -> Unit,
    onDeleteWorkspaceClick: () -> Unit
) {
    var nameInput by remember { mutableStateOf("") }
    val defaultIconIndex = if (initialMediaMode == 2 && initialVideoLayoutMode == 1) 3 else initialMediaMode
    var selectedIconIndex by remember { mutableStateOf(defaultIconIndex) }
    val primaryColor = MaterialTheme.colorScheme.primary

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Workspace", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Configure your isolated workspace.", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(20.dp))
                
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Workspace Name", color = Color.Gray) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    )
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                val wsLabels = listOf("Toon", "Book", "Series", "Channel")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0..3) {
                        val isSelected = selectedIconIndex == i
                        val color = if (isSelected) primaryColor else Color.White.copy(alpha = 0.3f)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) primaryColor.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable { selectedIconIndex = i }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                when (i) {
                                    0 -> ToonIndicator()
                                    1 -> BookIndicator()
                                    2 -> VideoSeriesIndicator()
                                    3 -> VideoCreatorIndicator()
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(wsLabels[i], color = color, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onMoveEntireWorkspaceClick() }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Move Entire Workspace", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onMoveSpecificCoversClick() }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.FolderShared, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Move Items To Different Workspace", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onRenameWorkspaceClick() }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.EditNote, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Rename Workspace", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onDeleteWorkspaceClick() }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFE57373).copy(alpha = 0.8f))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete Workspace", fontSize = 12.sp, color = Color(0xFFE57373))
                    }
                }
            }
        },
        containerColor = Color(0xFF161616),
        shape = RoundedCornerShape(28.dp),
        confirmButton = {
            TextButton(onClick = {
                if (nameInput.isNotBlank()) {
                    onCreate(nameInput, selectedIconIndex)
                }
            }) { Text("Create", color = primaryColor, fontWeight = FontWeight.ExtraBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}
