package com.ballade.hwaran.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ballade.hwaran.ui.theme.AccentTitanium

@Composable
fun DeleteConfirmationDialog(
    title: String = "Delete Item",
    itemName: String = "",
    message: String = "Choose how you would like to remove this item:",
    onDismiss: () -> Unit,
    onRemoveFromApp: () -> Unit,
    onDeleteFromDisk: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                if (itemName.isNotBlank()) {
                    Text(
                        text = itemName,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = message,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    lineHeight = 17.5.sp
                )

                // Option 1: Remove from App (Safe - DB only)
                Surface(
                    onClick = onRemoveFromApp,
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Dns,
                            contentDescription = null,
                            tint = AccentTitanium,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Remove from App", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                            Text("Deletes entry from app database only. Original files stay intact on disk.", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, lineHeight = 14.sp)
                        }
                    }
                }

                // Option 2: Delete from Disk (Destructive - DB + Storage)
                Surface(
                    onClick = onDeleteFromDisk,
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFE57373).copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color(0xFFE57373).copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteForever,
                            contentDescription = null,
                            tint = Color(0xFFE57373),
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Delete from Disk", color = Color(0xFFE57373), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                            Text("Permanently deletes entry and all physical files from storage.", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, lineHeight = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Medium)
            }
        },
        containerColor = Color(0xFF1B1926),
        shape = RoundedCornerShape(28.dp)
    )
}
