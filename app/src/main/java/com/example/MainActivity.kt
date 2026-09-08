package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.spark.ui.SparkViewModel
import com.example.spark.ui.components.SparkActionFeed
import com.example.spark.ui.components.SparkDock
import com.example.spark.ui.components.SparkMemoryVaultDialog
import com.example.spark.ui.components.SparkPermissionCard
import com.example.spark.ui.components.SparkQuantumHUD
import com.example.spark.ui.components.SparkSettingsDialog
import com.example.spark.ui.components.SparkToolSandboxDialog
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.CardBorder
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SparkAmber
import com.example.ui.theme.SparkCyan
import com.example.ui.theme.SurfaceDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                SparkApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SparkApp(viewModel: SparkViewModel = viewModel()) {
    val connectionState by viewModel.connectionState.collectAsState()
    val liveStats by viewModel.liveStats.collectAsState()
    val micAmplitude by viewModel.micAmplitude.collectAsState()
    val outputAmplitude by viewModel.outputAmplitude.collectAsState()
    val transcripts by viewModel.transcripts.collectAsState()
    val actionLogs by viewModel.actionLogs.collectAsState()
    val coreMemories by viewModel.coreMemories.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val isFlashlightOn by viewModel.isFlashlightOn.collectAsState()
    val isOverlayActive by viewModel.isOverlayActive.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val isAutoReplyEnabled by viewModel.isAutoReplyEnabled.collectAsState()

    var showMemoryVault by remember { mutableStateOf(false) }
    var showToolSandbox by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // Multi-permission request launcher
    val permissionsToRequest = mutableListOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.CAMERA,
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.SEND_SMS
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.checkPermissions()
    }

    LaunchedEffect(Unit) {
        viewModel.checkPermissions()
        permissionLauncher.launch(permissionsToRequest)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .testTag("spark_main_screen"),
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(SparkCyan.copy(alpha = 0.2f))
                                .border(1.dp, SparkCyan, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = null,
                                tint = SparkCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "SPARK ASSISTANT",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Voice-First Android Native Engine",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                },
                actions = {
                    // Tool Sandbox Action
                    IconButton(
                        onClick = { showToolSandbox = true },
                        modifier = Modifier.testTag("appbar_tools_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Tools",
                            tint = SparkCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Memory Vault Action
                    IconButton(
                        onClick = { showMemoryVault = true },
                        modifier = Modifier.testTag("appbar_memory_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Memories",
                            tint = SparkAmber,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Settings Action
                    IconButton(
                        onClick = { showSettings = true },
                        modifier = Modifier.testTag("appbar_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark
                )
            )
        },
        bottomBar = {
            SparkDock(
                isFlashlightOn = isFlashlightOn,
                isOverlayActive = isOverlayActive,
                onToggleFlashlight = {
                    viewModel.testToolDirectly(
                        viewModel.toolRegistry.toolDefinitions.first { it.name == "control_device_hardware" }
                    )
                },
                onToggleOverlay = { viewModel.toggleOverlayService() },
                onOpenSettings = { showSettings = true },
                onSendCommand = { viewModel.executeVoiceCommand(it) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Quantum HUD: Glowing Pulsing Orb, Waveform & Latency
            SparkQuantumHUD(
                connectionState = connectionState,
                liveStats = liveStats,
                micAmplitude = micAmplitude,
                outputAmplitude = outputAmplitude,
                onOrbClick = {
                    if (!permissions.hasAudio) {
                        permissionLauncher.launch(permissionsToRequest)
                    } else {
                        viewModel.toggleVoiceStreaming()
                    }
                }
            )

            // System Permissions Bar
            SparkPermissionCard(
                permissions = permissions,
                onRequestRuntimePermissions = {
                    permissionLauncher.launch(permissionsToRequest)
                }
            )

            // Live Action Execution Feed & Transcripts
            Box(modifier = Modifier.weight(1f)) {
                SparkActionFeed(
                    transcripts = transcripts,
                    actionLogs = actionLogs
                )
            }
        }
    }

    // Dialogs
    if (showMemoryVault) {
        SparkMemoryVaultDialog(
            memories = coreMemories,
            onSaveMemory = { key, value, category ->
                viewModel.saveMemory(key, value, category)
            },
            onDeleteMemory = { id ->
                viewModel.deleteMemory(id)
            },
            onDismiss = { showMemoryVault = false }
        )
    }

    if (showToolSandbox) {
        SparkToolSandboxDialog(
            tools = viewModel.toolRegistry.toolDefinitions,
            onTestTool = { tool ->
                viewModel.testToolDirectly(tool)
            },
            onDismiss = { showToolSandbox = false }
        )
    }

    if (showSettings) {
        SparkSettingsDialog(
            currentApiKey = apiKey,
            currentModel = selectedModel,
            isAutoReplyEnabled = isAutoReplyEnabled,
            onSaveApiKey = { viewModel.saveApiKey(it) },
            onSelectModel = { viewModel.setModel(it) },
            onToggleAutoReply = {
                viewModel.testToolDirectly(
                    viewModel.toolRegistry.toolDefinitions.first { it.name == "manage_notification_listener" },
                    org.json.JSONObject().put("action", "toggle_auto_reply").put("enabled", it)
                )
            },
            onDismiss = { showSettings = false }
        )
    }
}
