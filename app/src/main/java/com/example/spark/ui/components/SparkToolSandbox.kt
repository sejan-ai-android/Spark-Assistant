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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spark.model.ToolDefinition
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SparkCyan
import com.example.ui.theme.SparkGreen
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceGlass

@Composable
fun SparkToolSandboxDialog(
    tools: List<ToolDefinition>,
    onTestTool: (ToolDefinition) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("tool_sandbox_dialog"),
        containerColor = SurfaceGlass,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = SparkCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tool Registry (${tools.size} Tools)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tools, key = { it.name }) { tool ->
                    ToolSandboxCard(
                        tool = tool,
                        onExecute = { onTestTool(tool) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = SparkCyan)
            }
        }
    )
}

@Composable
fun ToolSandboxCard(
    tool: ToolDefinition,
    onExecute: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tool.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = SparkCyan,
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SparkGreen.copy(alpha = 0.15f))
                        .border(1.dp, SparkGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable { onExecute() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Test",
                        tint = SparkGreen,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Run Test",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SparkGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = tool.description,
                fontSize = 12.sp,
                color = Color(0xFFCBD5E1),
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Try: ${tool.examplePrompt}",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
