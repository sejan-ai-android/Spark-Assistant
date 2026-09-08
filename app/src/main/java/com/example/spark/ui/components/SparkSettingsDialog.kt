package com.example.spark.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spark.engine.NotificationStore
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SparkCyan
import com.example.ui.theme.SparkGreen
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceGlass

@Composable
fun SparkSettingsDialog(
    currentApiKey: String,
    currentModel: String,
    isAutoReplyEnabled: Boolean,
    onSaveApiKey: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onToggleAutoReply: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var keyInput by remember { mutableStateOf(currentApiKey) }
    var isKeyVisible by remember { mutableStateOf(false) }

    val models = listOf(
        "models/gemini-3.5-flash-lite",
        "models/gemini-3.1-flash-lite-preview",
        "gemini-2.5-flash"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("settings_dialog"),
        containerColor = SurfaceGlass,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = SparkCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Spark Assist Settings",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // BYOK API Key Section
                Text(
                    text = "BYOK (BRING YOUR OWN KEY)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    fontFamily = FontFamily.Monospace
                )

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("Gemini API Key", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                            Icon(
                                imageVector = if (isKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle key visibility",
                                tint = Color.Gray
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = SparkCyan,
                        unfocusedBorderColor = CardBorder,
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark
                    )
                )

                // Model Selection
                Text(
                    text = "ACTIVE MODEL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    fontFamily = FontFamily.Monospace
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    models.forEach { model ->
                        val isSelected = currentModel == model
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) SparkCyan.copy(alpha = 0.15f) else SurfaceDark)
                                .border(
                                    1.dp,
                                    if (isSelected) SparkCyan else CardBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelectModel(model) }
                                .padding(10.dp)
                        ) {
                            Text(
                                text = model,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) SparkCyan else Color(0xFFE2E8F0),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Messaging Auto-Reply Toggle
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
                            text = "Smart Auto-Reply",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Auto-reply to incoming WhatsApp/SMS",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Switch(
                        checked = isAutoReplyEnabled,
                        onCheckedChange = onToggleAutoReply,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SparkGreen,
                            checkedTrackColor = SparkGreen.copy(alpha = 0.3f)
                        )
                    )
                }

                // Architecture Specifications
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Zero-Latency Specifications:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SparkCyan,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Real-Time PCM: 16,000 Hz Mono Capture\n• Output Player: 24,000 Hz AudioTrack Stream\n• Acoustic Echo Shield: 200ms post-unmute decay\n• Bidirectional WebSocket: Sub-second roundtrip",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 15.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveApiKey(keyInput)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SparkCyan)
            ) {
                Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        }
    )
}
