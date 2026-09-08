package com.example.spark.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spark.model.ConnectionState
import com.example.spark.model.LiveStats
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SparkAmber
import com.example.ui.theme.SparkCyan
import com.example.ui.theme.SparkGreen
import com.example.ui.theme.SparkPurple
import com.example.ui.theme.SparkRed
import com.example.ui.theme.SurfaceGlass
import kotlin.math.sin

@Composable
fun SparkQuantumHUD(
    connectionState: ConnectionState,
    liveStats: LiveStats,
    micAmplitude: Float,
    outputAmplitude: Float,
    onOrbClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hud_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val isLive = connectionState == ConnectionState.STREAMING
    val activeAmp = if (liveStats.isSpeaking) outputAmplitude else micAmplitude

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceGlass)
            .border(1.dp, CardBorder, RoundedCornerShape(24.dp))
            .padding(18.dp)
            .testTag("spark_quantum_hud")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top HUD Bar: Status & Telemetry
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Connection Status Chip
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when (connectionState) {
                                    ConnectionState.STREAMING -> SparkGreen
                                    ConnectionState.CONNECTING -> SparkAmber
                                    ConnectionState.CONNECTED -> SparkCyan
                                    ConnectionState.ERROR -> SparkRed
                                    ConnectionState.DISCONNECTED -> Color.Gray
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (connectionState) {
                            ConnectionState.STREAMING -> "LIVE PCM BIDI"
                            ConnectionState.CONNECTING -> "CONNECTING..."
                            ConnectionState.CONNECTED -> "CONNECTED"
                            ConnectionState.ERROR -> "OFFLINE / REST"
                            ConnectionState.DISCONNECTED -> "STANDBY"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Sub-Second Latency Monitor
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Latency",
                        tint = SparkCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (liveStats.latencyMs > 0) "${liveStats.latencyMs} ms" else "< 220 ms",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SparkCyan,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Central Glowing Quantum Orb
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .clickable { onOrbClick() }
                    .testTag("quantum_orb_button")
            ) {
                // Outer Pulse Ring
                Canvas(modifier = Modifier.size(110.dp * (if (isLive) pulseScale else 1.0f))) {
                    val brush = Brush.radialGradient(
                        colors = listOf(
                            (if (liveStats.isSpeaking) SparkPurple else SparkCyan).copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                    drawCircle(brush = brush)
                }

                // Inner Core
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = if (isLive) {
                                    if (liveStats.isSpeaking) listOf(SparkPurple, Color(0xFF4F46E5))
                                    else listOf(SparkCyan, Color(0xFF2563EB))
                                } else {
                                    listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                                }
                            )
                        )
                        .border(
                            2.dp,
                            if (isLive) Color.White.copy(alpha = 0.8f) else Color.Gray.copy(alpha = 0.3f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (liveStats.isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mic Status",
                        tint = if (isLive) Color.White else Color.Gray,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Real-Time Audio Waveform Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val barCount = 32
                val barWidth = (canvasWidth / barCount) * 0.55f
                val spacing = canvasWidth / barCount

                val baseColor = if (liveStats.isSpeaking) SparkPurple else SparkCyan

                for (i in 0 until barCount) {
                    val norm = i.toFloat() / barCount
                    val sineVal = (sin(norm * Math.PI * 4 + wavePhase).toFloat() + 1f) / 2f
                    val ampEffect = (activeAmp * 0.85f).coerceIn(0.05f, 1f)
                    val barHeight = ((canvasHeight * 0.2f) + (canvasHeight * 0.75f * sineVal * ampEffect))
                        .coerceAtLeast(6f)

                    val x = i * spacing + (spacing - barWidth) / 2
                    val y = (canvasHeight - barHeight) / 2

                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(baseColor, baseColor.copy(alpha = 0.4f)),
                            startY = y,
                            endY = y + barHeight
                        ),
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
            }

            // HUD Bottom Indicators: Active Tool & Echo Protection Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (liveStats.activeTool != null) "⚙ Running: ${liveStats.activeTool}"
                           else if (liveStats.isSpeaking) "🔊 Spark Speaking"
                           else if (isLive) "🎙 Listening (PCM 16kHz)"
                           else "Tap Orb to Connect Live",
                    fontSize = 12.sp,
                    color = if (liveStats.activeTool != null) SparkAmber else Color(0xFFCBD5E1),
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = if (liveStats.isMicMuted) "Echo Auto-Mute" else "Echo Shield Active",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
