package com.ballade.hwaran.frontend.workspace.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
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
fun DeleteWorkspaceDialog(
    workspaces: List<String>,
    onDismiss: () -> Unit,
    onDelete: (String) -> Unit
) {
    val screenNames = remember(workspaces) { workspaces.filter { it != "I Love It" } }
    var selectedToDelete by remember { mutableStateOf<String?>(null) }
    val deleteInteractionSource = remember { MutableInteractionSource() }
    val primaryColor = MaterialTheme.colorScheme.primary

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Workspace", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                Text("Select a workspace to permanently delete from workspace list. Its items will be merged to 'I Love It'.", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(screenNames.size) { i ->
                        val name = screenNames[i]
                        val isSelected = name == selectedToDelete
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) primaryColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.03f))
                                .border(1.dp, if (isSelected) primaryColor.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable(
                                    interactionSource = deleteInteractionSource,
                                    indication = null
                                ) { selectedToDelete = name }
                                .padding(vertical = 12.dp, horizontal = 16.dp)
                        ) {
                            Text(
                                text = name,
                                color = if (isSelected) primaryColor else Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
                
                if (screenNames.isEmpty()) {
                    Text("No workspaces found.", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp).align(Alignment.CenterHorizontally))
                }
            }
        },
        containerColor = Color(0xFF161616),
        shape = RoundedCornerShape(24.dp),
        confirmButton = {
            TextButton(
                enabled = selectedToDelete != null,
                onClick = {
                    selectedToDelete?.let { onDelete(it) }
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Delete",
                    tint = if (selectedToDelete != null) Color(0xFFE57373) else Color.Gray
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}
