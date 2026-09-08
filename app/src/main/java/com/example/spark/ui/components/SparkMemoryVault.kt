package com.example.spark.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spark.data.CoreMemoryEntity
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SparkAmber
import com.example.ui.theme.SparkCyan
import com.example.ui.theme.SparkRed
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceGlass

@Composable
fun SparkMemoryVaultDialog(
    memories: List<CoreMemoryEntity>,
    onSaveMemory: (key: String, value: String, category: String) -> Unit,
    onDeleteMemory: (id: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var newKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("general") }
    var showAddFields by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("memory_vault_dialog"),
        containerColor = SurfaceGlass,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = SparkAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Core Memory Vault",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                IconButton(onClick = { showAddFields = !showAddFields }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Memory",
                        tint = SparkCyan
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
            ) {
                if (showAddFields) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceDark)
                            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                            .padding(10.dp)
                    ) {
                        OutlinedTextField(
                            value = newKey,
                            onValueChange = { newKey = it },
                            placeholder = { Text("Topic (e.g. Parking spot, Wi-Fi)", fontSize = 12.sp, color = Color.Gray) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = SparkCyan,
                                unfocusedBorderColor = CardBorder
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = newValue,
                            onValueChange = { newValue = it },
                            placeholder = { Text("Details (e.g. Level 2 Section B)", fontSize = 12.sp, color = Color.Gray) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = SparkCyan,
                                unfocusedBorderColor = CardBorder
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (newKey.isNotBlank() && newValue.isNotBlank()) {
                                    onSaveMemory(newKey, newValue, newCategory)
                                    newKey = ""
                                    newValue = ""
                                    showAddFields = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SparkCyan),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save To Core Memory", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (memories.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No saved memories yet.\nSay: \"Remember my parking spot is Level 2\"",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 18.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(memories, key = { it.id }) { mem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceDark)
                                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mem.key,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SparkAmber
                                    )
                                    Text(
                                        text = mem.value,
                                        fontSize = 12.sp,
                                        color = Color(0xFFE2E8F0)
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteMemory(mem.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = SparkRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = SparkCyan)
            }
        }
    )
}
