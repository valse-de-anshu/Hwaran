package com.ballade.hwaran.frontend.workspace.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RenameWorkspaceDialog(
    workspaces: List<String>,
    onDismiss: () -> Unit,
    onRename: (String, String) -> Unit
) {
    val screenNames = remember(workspaces) { workspaces.filter { it != "I Love It" } }
    var selectedToRename by remember { mutableStateOf<String?>(null) }
    var newNameInput by remember { mutableStateOf("") }
    val primaryColor = MaterialTheme.colorScheme.primary

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Workspace", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp)) {
                Text("Select a workspace and enter a new name.", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(12.dp))
                
                if (screenNames.isEmpty()) {
                    Text("No custom workspaces to rename.", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(screenNames.size) { i ->
                            val name = screenNames[i]
                            val isSelected = name == selectedToRename
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) primaryColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.03f))
                                    .border(1.dp, if (isSelected) primaryColor.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(10.dp))
                                    .clickable { selectedToRename = name; newNameInput = name }
                                    .padding(vertical = 10.dp, horizontal = 14.dp)
                            ) {
                                Text(
                                    text = name,
                                    color = if (isSelected) primaryColor else Color.White.copy(alpha = 0.8f),
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                    
                    if (selectedToRename != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = newNameInput,
                            onValueChange = { newNameInput = it },
                            label = { Text("New Name", color = Color.Gray) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = primaryColor
                            )
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFF161616),
        shape = RoundedCornerShape(24.dp),
        confirmButton = {
            TextButton(
                enabled = selectedToRename != null && newNameInput.isNotBlank() && newNameInput.trim() != "I Love It",
                onClick = {
                    if (selectedToRename != null && newNameInput.isNotBlank()) {
                        onRename(selectedToRename!!, newNameInput.trim())
                    }
                }
            ) { Text("Rename", color = if (selectedToRename != null && newNameInput.isNotBlank()) primaryColor else Color.Gray, fontWeight = FontWeight.ExtraBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}
