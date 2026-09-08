package com.example.spark.model

data class ToolDefinition(
    val name: String,
    val description: String,
    val parametersJson: String,
    val examplePrompt: String
)

data class ToolExecutionResult(
    val toolName: String,
    val isSuccess: Boolean,
    val resultMessage: String,
    val details: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

data class NotificationItem(
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postTime: Long = System.currentTimeMillis()
)

data class ContactItem(
    val id: String,
    val name: String,
    val phoneNumber: String
)

data class CalendarEventItem(
    val id: Long,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val location: String = ""
)

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    STREAMING,
    ERROR
}

data class LiveStats(
    val latencyMs: Long = 0,
    val bytesSent: Long = 0,
    val bytesReceived: Long = 0,
    val isMicMuted: Boolean = false,
    val isSpeaking: Boolean = false,
    val activeTool: String? = null
)
