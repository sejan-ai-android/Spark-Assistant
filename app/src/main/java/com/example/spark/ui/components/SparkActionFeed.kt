package com.example.spark.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.example.spark.data.ActionLogEntity
import com.example.spark.ui.TranscriptMessage
import com.example.ui.theme.CardBorder
import com.example.ui.theme.SparkAmber
import com.example.ui.theme.SparkCyan
import com.example.ui.theme.SparkGreen
import com.example.ui.theme.SparkPurple
import com.example.ui.theme.SparkRed
import com.example.ui.theme.SurfaceDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SparkActionFeed(
    transcripts: List<TranscriptMessage>,
    actionLogs: List<ActionLogEntity>,
    modifier: Modifier = Modifier
) {
    val timeFormat = SimpleDateFormat("h:mm:ss a", Locale.getDefault())

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "REAL-TIME EXECUTION STREAM",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = SparkCyan,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "${transcripts.size} events",
                fontSize = 11.sp,
                color = Color(0xFF64748B),
                fontFamily = FontFamily.Monospace
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("action_feed_list"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(transcripts, key = { it.id }) { msg ->
                TranscriptCard(msg = msg, timeFormat = timeFormat)
            }
        }
    }
}

@Composable
fun TranscriptCard(
    msg: TranscriptMessage,
    timeFormat: SimpleDateFormat
) {
    val isAction = msg.text.startsWith("[Action Executed]") || msg.text.startsWith("⚡")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .border(
                1.dp,
                if (isAction) SparkAmber.copy(alpha = 0.4f)
                else if (msg.isUser) SparkCyan.copy(alpha = 0.3f)
                else SparkPurple.copy(alpha = 0.3f),
                RoundedCornerShape(16.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (isAction) SparkAmber.copy(alpha = 0.15f)
                        else if (msg.isUser) SparkCyan.copy(alpha = 0.15f)
                        else SparkPurple.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isAction) Icons.Default.CheckCircle
                                  else if (msg.isUser) Icons.Default.Person
                                  else Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = if (isAction) SparkAmber else if (msg.isUser) SparkCyan else SparkPurple,
                    modifier = Modifier.size(15.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isAction) "Native Action" else if (msg.isUser) "You" else "Spark Assist",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAction) SparkAmber else if (msg.isUser) SparkCyan else SparkPurple
                    )
                    Text(
                        text = timeFormat.format(Date(msg.timestamp)),
                        fontSize = 10.sp,
                        color = Color(0xFF64748B),
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = msg.text,
                    fontSize = 13.sp,
                    color = Color(0xFFF1F5F9),
                    lineHeight = 18.sp
                )
            }
        }
    }
}
