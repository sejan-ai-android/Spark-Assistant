package com.example.spark.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spark.ui.PermissionState
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SparkGreen
import com.example.ui.theme.SparkRed
import com.example.ui.theme.SurfaceDark

@Composable
fun SparkPermissionCard(
    permissions: PermissionState,
    onRequestRuntimePermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceDark)
            .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
            .padding(12.dp)
            .testTag("spark_permission_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SYSTEM ENGINE CAPABILITIES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8)
            )

            Text(
                text = "Tap to grant",
                fontSize = 10.sp,
                color = Color(0xFF64748B)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PermissionChip(
                label = "Microphone",
                isGranted = permissions.hasAudio,
                onClick = onRequestRuntimePermissions
            )

            PermissionChip(
                label = "Overlay (HUD)",
                isGranted = permissions.hasOverlay,
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                        context.startActivity(intent)
                    }
                }
            )

            PermissionChip(
                label = "Notification Listener",
                isGranted = permissions.hasNotificationListener,
                onClick = {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            )

            PermissionChip(
                label = "Contacts",
                isGranted = permissions.hasContacts,
                onClick = onRequestRuntimePermissions
            )

            PermissionChip(
                label = "Phone Calls",
                isGranted = permissions.hasPhone,
                onClick = onRequestRuntimePermissions
            )

            PermissionChip(
                label = "Flashlight",
                isGranted = permissions.hasCamera,
                onClick = onRequestRuntimePermissions
            )

            PermissionChip(
                label = "Calendar",
                isGranted = permissions.hasCalendar,
                onClick = onRequestRuntimePermissions
            )
        }
    }
}

@Composable
fun PermissionChip(
    label: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E293B))
            .border(
                1.dp,
                if (isGranted) SparkGreen.copy(alpha = 0.5f) else SparkRed.copy(alpha = 0.5f),
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(if (isGranted) SparkGreen else SparkRed),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Warning,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(10.dp)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}
