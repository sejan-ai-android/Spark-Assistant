package com.example.spark.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SparkAmber
import com.example.ui.theme.SparkCyan
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceGlass

@Composable
fun SparkDock(
    isFlashlightOn: Boolean,
    isOverlayActive: Boolean,
    onToggleFlashlight: () -> Unit,
    onToggleOverlay: () -> Unit,
    onOpenSettings: () -> Unit,
    onSendCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var textInput by remember { mutableStateOf("") }
    val quickPrompts = listOf(
        "Who is calling?",
        "Turn on flashlight",
        "Pause the music",
        "Read my latest WhatsApp messages",
        "Remember my parking spot is level 2",
        "What is my parking spot?",
        "Find Alex",
        "What's on my calendar today?",
        "Open Spotify"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(SurfaceGlass)
            .border(1.dp, CardBorder, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(14.dp)
            .testTag("spark_dock")
    ) {
        // Quick Voice Prompts Horizontal Scroll
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickPrompts.forEach { prompt ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceDark)
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                        .clickable { onSendCommand(prompt) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = prompt,
                        fontSize = 12.sp,
                        color = Color(0xFFE2E8F0),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bottom Controls: Text Prompt Field & Hardware Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Text Input Field
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Speak or type command...", fontSize = 13.sp, color = Color(0xFF64748B)) },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("voice_command_input"),
                singleLine = true,
                shape = RoundedCornerShape(25.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SparkCyan,
                    unfocusedBorderColor = CardBorder,
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (textInput.isNotBlank()) {
                        onSendCommand(textInput)
                        textInput = ""
                    }
                }),
                trailingIcon = {
                    if (textInput.isNotBlank()) {
                        IconButton(onClick = {
                            onSendCommand(textInput)
                            textInput = ""
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = SparkCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            )

            // Flashlight Toggle
            IconButton(
                onClick = onToggleFlashlight,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (isFlashlightOn) SparkAmber else SurfaceDark)
                    .border(1.dp, CardBorder, CircleShape)
                    .testTag("dock_flashlight_button")
            ) {
                Icon(
                    imageVector = if (isFlashlightOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                    contentDescription = "Flashlight",
                    tint = if (isFlashlightOn) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Floating Overlay Toggle
            IconButton(
                onClick = onToggleOverlay,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (isOverlayActive) SparkCyan else SurfaceDark)
                    .border(1.dp, CardBorder, CircleShape)
                    .testTag("dock_overlay_button")
            ) {
                Icon(
                    imageVector = if (isOverlayActive) Icons.Default.Layers else Icons.Default.LayersClear,
                    contentDescription = "Overlay",
                    tint = if (isOverlayActive) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Settings Button
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(SurfaceDark)
                    .border(1.dp, CardBorder, CircleShape)
                    .testTag("dock_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
