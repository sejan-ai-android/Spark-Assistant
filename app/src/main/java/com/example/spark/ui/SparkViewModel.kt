package com.example.spark.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.spark.data.ActionLogEntity
import com.example.spark.data.CoreMemoryEntity
import com.example.spark.data.SparkDatabase
import com.example.spark.data.SparkRepository
import com.example.spark.engine.AppLauncherEngine
import com.example.spark.engine.CalendarEngine
import com.example.spark.engine.HardwareEngine
import com.example.spark.engine.MediaEngine
import com.example.spark.engine.NotificationStore
import com.example.spark.engine.TelephonyEngine
import com.example.spark.live.GeminiLiveService
import com.example.spark.model.ConnectionState
import com.example.spark.model.LiveStats
import com.example.spark.model.NotificationItem
import com.example.spark.model.ToolDefinition
import com.example.spark.model.ToolExecutionResult
import com.example.spark.service.SparkOverlayService
import com.example.spark.tools.SparkToolRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

data class TranscriptMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class PermissionState(
    val hasAudio: Boolean = false,
    val hasOverlay: Boolean = false,
    val hasNotificationListener: Boolean = false,
    val hasContacts: Boolean = false,
    val hasPhone: Boolean = false,
    val hasCamera: Boolean = false,
    val hasCalendar: Boolean = false
)

class SparkViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SparkDatabase.getDatabase(application)
    val repository = SparkRepository(db.sparkDao())

    // Native Action Engines
    val hardwareEngine = HardwareEngine(application)
    val mediaEngine = MediaEngine(application)
    val telephonyEngine = TelephonyEngine(application)
    val appLauncherEngine = AppLauncherEngine(application)
    val calendarEngine = CalendarEngine(application)

    // Tools & Live Service
    val toolRegistry = SparkToolRegistry(
        context = application,
        repository = repository,
        hardwareEngine = hardwareEngine,
        mediaEngine = mediaEngine,
        telephonyEngine = telephonyEngine,
        appLauncherEngine = appLauncherEngine,
        calendarEngine = calendarEngine
    )

    val liveService = GeminiLiveService(application, toolRegistry)

    // UI States
    val connectionState: StateFlow<ConnectionState> = liveService.connectionState
    val liveStats: StateFlow<LiveStats> = liveService.liveStats
    val isFlashlightOn: StateFlow<Boolean> = hardwareEngine.isFlashlightOn
    val isAutoReplyEnabled: StateFlow<Boolean> = NotificationStore.isAutoReplyEnabled
    val activeNotifications: StateFlow<List<NotificationItem>> = NotificationStore.notificationFlow

    val micAmplitude: StateFlow<Float> = liveService.captureEngine.amplitude
    val outputAmplitude: StateFlow<Float> = liveService.player.outputAmplitude

    val coreMemories: StateFlow<List<CoreMemoryEntity>> = repository.allMemories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val actionLogs: StateFlow<List<ActionLogEntity>> = repository.recentActionLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _transcripts = MutableStateFlow<List<TranscriptMessage>>(emptyList())
    val transcripts: StateFlow<List<TranscriptMessage>> = _transcripts.asStateFlow()

    private val _permissions = MutableStateFlow(PermissionState())
    val permissions: StateFlow<PermissionState> = _permissions.asStateFlow()

    private val _isOverlayActive = MutableStateFlow(false)
    val isOverlayActive: StateFlow<Boolean> = _isOverlayActive.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow("models/gemini-3.5-flash-lite")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    init {
        // Load default or BuildConfig key
        val defaultKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
        val prefs = application.getSharedPreferences("spark_prefs", Context.MODE_PRIVATE)
        val savedKey = prefs.getString("api_key", "") ?: ""
        val resolvedKey = if (savedKey.isNotBlank()) savedKey else (if (defaultKey != "MY_GEMINI_API_KEY") defaultKey else "")
        _apiKey.value = resolvedKey
        liveService.setApiKey(resolvedKey)

        checkPermissions()

        // Observe transcript flow from service
        viewModelScope.launch {
            liveService.transcriptFlow.collect { pair ->
                val list = _transcripts.value.toMutableList()
                list.add(0, TranscriptMessage(text = pair.first, isUser = pair.second))
                _transcripts.value = list
            }
        }

        // Add welcome message
        _transcripts.value = listOf(
            TranscriptMessage(
                text = "⚡ Spark Assistant initialized. Persistent WebSocket active (No Standby).",
                isUser = false
            )
        )

        // AUTO-CONNECT: WebSocket establishes IMMEDIATELY on app launch
        if (resolvedKey.isNotBlank()) {
            liveService.connect(resolvedKey)
        }
    }

    fun checkPermissions() {
        val ctx = getApplication<Application>()
        val hasAudio = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(ctx) else true
        val hasContacts = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasPhone = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCamera = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCalendar = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED

        // Check Notification Listener
        val enabledListeners = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: ""
        val hasNotificationListener = enabledListeners.contains(ctx.packageName)

        _permissions.value = PermissionState(
            hasAudio = hasAudio,
            hasOverlay = hasOverlay,
            hasNotificationListener = hasNotificationListener,
            hasContacts = hasContacts,
            hasPhone = hasPhone,
            hasCamera = hasCamera,
            hasCalendar = hasCalendar
        )
        _isOverlayActive.value = SparkOverlayService.isOverlayRunning

        // If audio permission is granted and key is set, ensure active streaming if currently disconnected
        if (hasAudio && _apiKey.value.isNotBlank() && connectionState.value == ConnectionState.DISCONNECTED) {
            liveService.reconnectNow()
        }
    }

    fun onOrbClicked() {
        if (connectionState.value == ConnectionState.STREAMING) {
            // Streaming is active: toggle mic mute or trigger local speech prompt
            toggleMute()
        } else {
            // Force immediate reconnect
            val key = _apiKey.value
            if (key.isNotBlank()) {
                liveService.reconnectNow()
            }
        }
    }

    fun toggleVoiceStreaming() {
        onOrbClicked()
    }

    fun toggleMute() {
        val current = liveService.liveStats.value.isMicMuted
        liveService.captureEngine.setMute(!current)
    }

    fun executeVoiceCommand(command: String) {
        viewModelScope.launch {
            liveService.executeCommandText(command, speakResult = true)
        }
    }

    fun testToolDirectly(tool: ToolDefinition, customArgs: JSONObject? = null) {
        viewModelScope.launch {
            val args = customArgs ?: when (tool.name) {
                "control_device_hardware" -> JSONObject().put("action", "toggle_flashlight")
                "control_media_playback" -> JSONObject().put("action", "toggle")
                "control_incoming_call" -> JSONObject().put("action", "announce")
                "manage_notification_listener" -> JSONObject().put("action", "read_latest").put("appName", "whatsapp")
                "search_contacts" -> JSONObject().put("query", "Alex")
                "make_phone_call" -> JSONObject().put("recipient", "Rahul")
                "send_whatsapp_message" -> JSONObject().put("contactName", "Mom").put("message", "I'm on my way!")
                "send_sms_message" -> JSONObject().put("recipient", "Rahul").put("message", "Hey from Spark Assistant")
                "open_deep_link" -> JSONObject().put("url", "https://www.youtube.com/results?search_query=Believer")
                "open_app" -> JSONObject().put("appName", "spotify")
                "close_app" -> JSONObject().put("appName", "current app")
                "check_schedule" -> JSONObject()
                "schedule_new_event" -> JSONObject().put("title", "Project Review").put("timeDescription", "Tomorrow 3 PM")
                "save_core_memory" -> JSONObject().put("key", "Parking spot").put("value", "Level 2 Section B")
                "access_core_memory" -> JSONObject().put("query", "parking")
                "query_web_search" -> JSONObject().put("query", "Latest Gemini AI models")
                else -> JSONObject()
            }
            val res = toolRegistry.executeTool(tool.name, args)
            val list = _transcripts.value.toMutableList()
            list.add(0, TranscriptMessage(text = "[Action Executed] ${tool.name}: ${res.resultMessage}", isUser = false))
            _transcripts.value = list
            liveService.speak(res.resultMessage)
        }
    }

    fun toggleOverlayService() {
        val ctx = getApplication<Application>()
        if (_isOverlayActive.value) {
            SparkOverlayService.stopOverlay(ctx)
            _isOverlayActive.value = false
        } else {
            SparkOverlayService.startOverlay(ctx)
            _isOverlayActive.value = true
        }
    }

    fun saveApiKey(newKey: String) {
        _apiKey.value = newKey.trim()
        val prefs = getApplication<Application>().getSharedPreferences("spark_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("api_key", newKey.trim()).apply()
        liveService.setApiKey(newKey.trim())
    }

    fun setModel(model: String) {
        _selectedModel.value = model
        liveService.setModel(model)
    }

    fun saveMemory(key: String, value: String, category: String) {
        viewModelScope.launch {
            repository.saveMemory(key, value, category)
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            repository.deleteMemoryById(id)
        }
    }

    fun clearTranscripts() {
        _transcripts.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        liveService.release()
    }
}
