package com.example.spark.live

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Base64
import com.example.spark.audio.AudioCaptureEngine
import com.example.spark.audio.AudioTrackPlayer
import com.example.spark.model.ConnectionState
import com.example.spark.model.LiveStats
import com.example.spark.model.ToolExecutionResult
import com.example.spark.service.SparkOverlayService
import com.example.spark.tools.SparkToolRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class GeminiLiveService(
    private val context: Context,
    private val toolRegistry: SparkToolRegistry
) : TextToSpeech.OnInitListener {

    companion object {
        const val LIVE_WS_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        const val DEFAULT_MODEL = "models/gemini-3.6-flash"
        const val SYSTEM_PROMPT = """You are Spark Assistant, a voice-first AI that stays actively connected to the user's Android phone at all times. You are NEVER in standby mode from a network perspective—your WebSocket is always open.
Rules:
1. Always respond with the appropriate tool call. Do not just chat unless the user explicitly asks a general knowledge question.
2. Keep verbal responses under 3 seconds. For actions, reply with a brief confirmation like "Done" or "Calling now".
3. If the connection feels laggy, prioritize executing the tool over generating a long spoken response.
4. Listen for "Hey Spark" as a primary wake word, but if a command is clear (e.g., "turn on flashlight"), execute it immediately without requiring the wake word.
5. Your mission: Zero latency. No idle screens. No manual reconnects."""
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep-alive for streaming
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    // Background jobs for persistent connection
    private var reconnectJob: Job? = null
    private var shouldStayConnected = true
    private var reconnectAttempts = 0
    @Volatile
    private var isConnecting = false

    // Audio Engines
    val player = AudioTrackPlayer()
    val captureEngine = AudioCaptureEngine { pcmChunk, isSpeech ->
        handleOutgoingPcm(pcmChunk, isSpeech)
    }

    // State Flows
    private val _connectionState = MutableStateFlow(ConnectionState.CONNECTING)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _liveStats = MutableStateFlow(LiveStats())
    val liveStats: StateFlow<LiveStats> = _liveStats.asStateFlow()

    private val _transcriptFlow = MutableSharedFlow<Pair<String, Boolean>>() // text to isUser
    val transcriptFlow: SharedFlow<Pair<String, Boolean>> = _transcriptFlow.asSharedFlow()

    private val _toolExecutionFlow = MutableSharedFlow<ToolExecutionResult>()
    val toolExecutionFlow: SharedFlow<ToolExecutionResult> = _toolExecutionFlow.asSharedFlow()

    private var currentModel = DEFAULT_MODEL
    private var lastSendTime = 0L
    private var currentApiKey: String = ""

    init {
        tts = TextToSpeech(context.applicationContext, this)
        player.initialize()

        // Listen for network reconnection from foreground service
        SparkOverlayService.onNetworkReconnectedListener = {
            reconnectNow()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isTtsReady = true
        }
    }

    fun setApiKey(key: String) {
        val trimmed = key.trim()
        if (trimmed != currentApiKey) {
            val hadKey = currentApiKey.isNotBlank()
            currentApiKey = trimmed
            if (hadKey && currentApiKey.isNotBlank() && shouldStayConnected) {
                reconnectNow()
            }
        }
    }

    fun setModel(model: String) {
        currentModel = model
        if (shouldStayConnected && currentApiKey.isNotBlank()) {
            reconnectNow()
        }
    }

    /**
     * Starts or forces persistent connection to Gemini Live.
     */
    fun connect(apiKey: String) {
        shouldStayConnected = true
        currentApiKey = apiKey.trim()
        reconnectAttempts = 0
        reconnectJob?.cancel()
        internalConnect()
    }

    /**
     * Immediately attempts connection using current credentials.
     */
    fun reconnectNow() {
        if (currentApiKey.isBlank()) return
        reconnectJob?.cancel()
        reconnectAttempts = 0
        internalConnect()
    }

    private fun internalConnect() {
        if (currentApiKey.isBlank()) {
            _connectionState.value = ConnectionState.CONNECTING
            return
        }

        // Avoid duplicating active connections
        if (_connectionState.value == ConnectionState.STREAMING && webSocket != null) {
            return
        }

        if (isConnecting) {
            return
        }
        isConnecting = true

        val oldSocket = webSocket
        webSocket = null
        try {
            oldSocket?.close(1000, "Reconnecting")
        } catch (e: Exception) {
            try { oldSocket?.cancel() } catch (_: Exception) {}
        }

        _connectionState.value = ConnectionState.CONNECTING

        val urlWithKey = "$LIVE_WS_URL?key=$currentApiKey"
        val request = Request.Builder()
            .url(urlWithKey)
            .addHeader("Sec-WebSocket-Protocol", "bidi")
            .build()

        val newSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnecting = false
                if (this@GeminiLiveService.webSocket !== webSocket) return
                reconnectAttempts = 0
                _connectionState.value = ConnectionState.CONNECTED

                // 500ms delay to ensure handshake completes smoothly and avoid race condition
                scope.launch {
                    delay(500L)
                    if (this@GeminiLiveService.webSocket === webSocket) {
                        sendSetupHandshake(webSocket)
                        captureEngine.start()
                        _connectionState.value = ConnectionState.STREAMING
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (this@GeminiLiveService.webSocket !== webSocket) return
                handleIncomingMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (this@GeminiLiveService.webSocket !== webSocket) return
                player.playChunk(bytes.toByteArray())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnecting = false
                if (this@GeminiLiveService.webSocket !== webSocket) return

                val msg = t.message.orEmpty()
                if (msg.equals("Canceled", ignoreCase = true) || msg.equals("Socket closed", ignoreCase = true)) {
                    return
                }

                val errorBody = try { response?.body?.string() } catch (e: Exception) { null }
                val errorDetail = t.message ?: errorBody ?: t.javaClass.simpleName
                android.util.Log.e("SparkLive", "WebSocket Failure: $errorDetail", t)
                handleConnectionDrop()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (this@GeminiLiveService.webSocket !== webSocket) return
                handleConnectionDrop()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (this@GeminiLiveService.webSocket !== webSocket) return
                handleConnectionDrop()
            }
        })
        webSocket = newSocket
    }

    private fun handleConnectionDrop() {
        isConnecting = false
        if (!shouldStayConnected || currentApiKey.isBlank()) {
            _connectionState.value = ConnectionState.CONNECTING
            return
        }

        // Never transition to Standby. Silently retry with exponential backoff.
        _connectionState.value = ConnectionState.CONNECTING

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            // Exponential backoff: 1s, 2s, 4s, 8s, 16s, up to 30s
            val backoffSec = (1L shl minOf(reconnectAttempts, 5)).coerceAtMost(30L)
            val backoffMs = backoffSec * 1000L
            reconnectAttempts++
            delay(backoffMs)
            if (shouldStayConnected && currentApiKey.isNotBlank()) {
                internalConnect()
            }
        }
    }

    fun disconnect() {
        shouldStayConnected = false
        isConnecting = false
        reconnectJob?.cancel()
        captureEngine.stop()
        player.stopAndFlush()
        try {
            webSocket?.close(1000, "User requested")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        webSocket = null
        _connectionState.value = ConnectionState.CONNECTING
    }

    private fun sendSetupHandshake(ws: WebSocket) {
        try {
            val setupObj = JSONObject()
            val innerSetup = JSONObject()
            innerSetup.put("model", currentModel)

            // Generation config: temperature 0.3, AUDIO response modality
            val genConfig = JSONObject()
            val modalities = JSONArray()
            modalities.put("AUDIO")
            genConfig.put("responseModalities", modalities)
            genConfig.put("response_modalities", modalities)
            genConfig.put("temperature", 0.3)

            val speechConfig = JSONObject()
            val voiceConfig = JSONObject()
            val prebuilt = JSONObject()
            prebuilt.put("voiceName", "Aoede")
            prebuilt.put("voice_name", "Aoede")
            voiceConfig.put("prebuiltVoiceConfig", prebuilt)
            voiceConfig.put("prebuilt_voice_config", prebuilt)
            speechConfig.put("voiceConfig", voiceConfig)
            speechConfig.put("voice_config", voiceConfig)
            genConfig.put("speechConfig", speechConfig)
            genConfig.put("speech_config", speechConfig)

            innerSetup.put("generationConfig", genConfig)
            innerSetup.put("generation_config", genConfig)

            // Audio config: 16kHz LINEAR16 Mono
            val audioConfig = JSONObject()
            audioConfig.put("encoding", "LINEAR16")
            audioConfig.put("sampleRateHertz", 16000)
            audioConfig.put("sample_rate_hertz", 16000)
            audioConfig.put("channels", 1)

            innerSetup.put("inputAudioConfig", audioConfig)
            innerSetup.put("input_audio_config", audioConfig)
            innerSetup.put("outputAudioConfig", audioConfig)
            innerSetup.put("output_audio_config", audioConfig)

            // System Instruction
            val sysInst = JSONObject()
            val parts = JSONArray()
            val partText = JSONObject().put("text", SYSTEM_PROMPT)
            parts.put(partText)
            sysInst.put("parts", parts)
            innerSetup.put("systemInstruction", sysInst)
            innerSetup.put("system_instruction", sysInst)

            // Tools Registry: Real Android native function declarations
            val toolsArray = JSONArray()
            val toolsObj = JSONObject()
            val declarations = toolRegistry.getGeminiToolDeclarationsJson()
            toolsObj.put("functionDeclarations", declarations)
            toolsObj.put("function_declarations", declarations)
            toolsArray.put(toolsObj)
            innerSetup.put("tools", toolsArray)

            setupObj.put("setup", innerSetup)
            ws.send(setupObj.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleOutgoingPcm(chunk: ByteArray, isSpeech: Boolean) {
        if (_connectionState.value != ConnectionState.STREAMING) return

        // Only stream active microphone bytes when VAD speech is detected
        if (isSpeech) {
            try {
                val base64Data = Base64.encodeToString(chunk, Base64.NO_WRAP)
                sendAudioFrame(base64Data, isSilence = false)

                _liveStats.value = _liveStats.value.copy(
                    bytesSent = _liveStats.value.bytesSent + chunk.size
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun sendAudioFrame(base64Data: String, isSilence: Boolean) {
        val ws = webSocket ?: return
        try {
            val root = JSONObject()
            val realtimeInput = JSONObject()
            val mediaChunks = JSONArray()
            val chunkObj = JSONObject()
            chunkObj.put("mimeType", "audio/pcm;rate=16000")
            chunkObj.put("mime_type", "audio/pcm;rate=16000")
            chunkObj.put("data", base64Data)
            mediaChunks.put(chunkObj)

            realtimeInput.put("mediaChunks", mediaChunks)
            realtimeInput.put("media_chunks", mediaChunks)
            root.put("realtimeInput", realtimeInput)
            root.put("realtime_input", realtimeInput)

            if (!isSilence) {
                lastSendTime = System.currentTimeMillis()
            }
            ws.send(root.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val root = JSONObject(text)

            // Latency tracking
            if (lastSendTime > 0) {
                val latency = System.currentTimeMillis() - lastSendTime
                _liveStats.value = _liveStats.value.copy(latencyMs = latency)
            }

            // Check for interruption signal from server
            if (root.has("serverContent")) {
                val serverContent = root.getJSONObject("serverContent")
                if (serverContent.optBoolean("interrupted", false)) {
                    // Flush output instantly on interruption
                    player.stopAndFlush()
                    captureEngine.setMute(false)
                }

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts") ?: JSONArray()

                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)

                        // Handle text transcript
                        if (part.has("text")) {
                            val msg = part.getString("text")
                            scope.launch { _transcriptFlow.emit(Pair(msg, false)) }
                        }

                        // Handle Audio PCM chunk
                        if (part.has("inlineData")) {
                            val inlineData = part.getJSONObject("inlineData")
                            val dataBase64 = inlineData.getString("data")
                            val audioBytes = Base64.decode(dataBase64, Base64.DEFAULT)

                            // Mute mic briefly while audio is actively streaming to prevent echo
                            captureEngine.setMute(true)
                            player.playChunk(audioBytes)

                            _liveStats.value = _liveStats.value.copy(
                                bytesReceived = _liveStats.value.bytesReceived + audioBytes.size,
                                isSpeaking = true
                            )
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    // Turn complete: unmute mic with acoustic decay
                    captureEngine.setMute(false)
                    _liveStats.value = _liveStats.value.copy(isSpeaking = false)
                }
            }

            // Check for tool call
            if (root.has("toolCall")) {
                val toolCall = root.getJSONObject("toolCall")
                val functionCalls = toolCall.optJSONArray("functionCalls") ?: JSONArray()

                for (i in 0 until functionCalls.length()) {
                    val fc = functionCalls.getJSONObject(i)
                    val callId = fc.optString("id", "call_${System.currentTimeMillis()}")
                    val funcName = fc.getString("name")
                    val args = fc.optJSONObject("args") ?: JSONObject()

                    _liveStats.value = _liveStats.value.copy(activeTool = funcName)

                    scope.launch {
                        val executionResult = toolRegistry.executeTool(funcName, args)
                        _toolExecutionFlow.emit(executionResult)
                        sendToolResponse(callId, funcName, executionResult.resultMessage)
                        _liveStats.value = _liveStats.value.copy(activeTool = null)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendToolResponse(callId: String, name: String, resultText: String) {
        val ws = webSocket ?: return
        try {
            val root = JSONObject()
            val toolResponse = JSONObject()
            val fnResponses = JSONArray()
            val fnObj = JSONObject()
            fnObj.put("id", callId)
            fnObj.put("name", name)
            val respObj = JSONObject()
            val outputObj = JSONObject()
            outputObj.put("result", resultText)
            respObj.put("output", outputObj)
            fnObj.put("response", respObj)
            fnResponses.put(fnObj)

            toolResponse.put("functionResponses", fnResponses)
            toolResponse.put("function_responses", fnResponses)
            root.put("toolResponse", toolResponse)
            root.put("tool_response", toolResponse)

            ws.send(root.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Executes a user voice/text command with fallback handling & TTS speech.
     */
    suspend fun executeCommandText(command: String, speakResult: Boolean = true) {
        _transcriptFlow.emit(Pair(command, true))

        // First, check direct regex/wake-word intent matching for instant sub-second local action
        val localToolMatch = resolveLocalIntent(command)

        if (localToolMatch != null) {
            val result = toolRegistry.executeTool(localToolMatch.first, localToolMatch.second)
            _toolExecutionFlow.emit(result)
            val speech = result.resultMessage
            _transcriptFlow.emit(Pair(speech, false))
            if (speakResult) speak(speech)
            return
        }

        // If connected to WebSocket streaming, send as user turn
        val ws = webSocket
        if (ws != null && _connectionState.value == ConnectionState.STREAMING) {
            try {
                val root = JSONObject()
                val clientContent = JSONObject()
                val turns = JSONArray()
                val turn = JSONObject()
                turn.put("role", "user")
                val parts = JSONArray()
                parts.put(JSONObject().put("text", command))
                turn.put("parts", parts)
                turns.put(turn)
                clientContent.put("turns", turns)
                clientContent.put("turnComplete", true)
                root.put("clientContent", clientContent)
                root.put("client_content", clientContent)
                ws.send(root.toString())
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Fallback REST call
        callRestFallback(command, speakResult)
    }

    private suspend fun callRestFallback(command: String, speakResult: Boolean) = kotlinx.coroutines.withContext(Dispatchers.IO) {
        if (currentApiKey.isBlank()) {
            val err = "API Key not configured. Please set your Gemini API Key in Settings or .env."
            _transcriptFlow.emit(Pair(err, false))
            if (speakResult) speak(err)
            return@withContext
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$currentApiKey"

            val bodyJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().put("text", command))
                        }
                        put("parts", parts)
                    }
                    put(contentObj)
                }
                put("contents", contents)

                // Tools
                val tools = JSONArray().apply {
                    val toolObj = JSONObject().apply {
                        put("functionDeclarations", toolRegistry.getGeminiToolDeclarationsJson())
                    }
                    put(toolObj)
                }
                put("tools", tools)

                // System Instruction
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", SYSTEM_PROMPT))
                    })
                })
            }

            val jsonMediaType = "application/json".toMediaType()
            val requestBody = bodyJson.toString().toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseString = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errMsg = "Gemini Error (${response.code}): ${if (responseString.isNotBlank()) responseString else response.message}"
                _transcriptFlow.emit(Pair(errMsg, false))
                return@withContext
            }

            val respObj = JSONObject(responseString)
            val candidates = respObj.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")

            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.has("functionCall")) {
                        val fc = part.getJSONObject("functionCall")
                        val name = fc.getString("name")
                        val args = fc.optJSONObject("args") ?: JSONObject()
                        val result = toolRegistry.executeTool(name, args)
                        _toolExecutionFlow.emit(result)
                        val respText = result.resultMessage
                        _transcriptFlow.emit(Pair(respText, false))
                        if (speakResult) speak(respText)
                    } else if (part.has("text")) {
                        val text = part.getString("text")
                        _transcriptFlow.emit(Pair(text, false))
                        if (speakResult) speak(text)
                    }
                }
            }
        } catch (e: Exception) {
            val errorReason = e.localizedMessage ?: e.message ?: e.cause?.message ?: e.javaClass.simpleName
            val err = "Request error: $errorReason"
            _transcriptFlow.emit(Pair(err, false))
        }
    }

    private fun resolveLocalIntent(input: String): Pair<String, JSONObject>? {
        var lower = input.lowercase().trim()

        // Strip wake words if present ("Hey Spark", "Spark", "OK Spark")
        for (wakeWord in listOf("hey spark", "ok spark", "spark")) {
            if (lower.startsWith(wakeWord)) {
                lower = lower.removePrefix(wakeWord).trim(' ', ',', '!', '.', ':')
            }
        }

        return when {
            lower.contains("flashlight on") || lower.contains("turn on flashlight") || lower.contains("torch on") -> {
                Pair("control_device_hardware", JSONObject().put("action", "flashlight_on"))
            }
            lower.contains("flashlight off") || lower.contains("turn off flashlight") || lower.contains("torch off") -> {
                Pair("control_device_hardware", JSONObject().put("action", "flashlight_off"))
            }
            lower.contains("toggle flashlight") || lower.contains("flashlight") || lower.contains("torch") -> {
                Pair("control_device_hardware", JSONObject().put("action", "toggle_flashlight"))
            }
            lower.contains("wifi") || lower.contains("wi-fi") -> {
                Pair("control_device_hardware", JSONObject().put("action", "wifi_settings"))
            }
            lower.contains("bluetooth") -> {
                Pair("control_device_hardware", JSONObject().put("action", "bluetooth_settings"))
            }
            lower.contains("location settings") || lower.contains("gps") -> {
                Pair("control_device_hardware", JSONObject().put("action", "location_settings"))
            }
            lower.contains("hotspot") -> {
                Pair("control_device_hardware", JSONObject().put("action", "hotspot_settings"))
            }
            lower.contains("pause music") || lower.contains("pause the music") || lower.contains("pause playback") -> {
                Pair("control_media_playback", JSONObject().put("action", "pause"))
            }
            lower.contains("play music") || lower.contains("resume music") || lower.contains("play song") -> {
                Pair("control_media_playback", JSONObject().put("action", "play"))
            }
            lower.contains("next song") || lower.contains("skip song") || lower.contains("next track") -> {
                Pair("control_media_playback", JSONObject().put("action", "next"))
            }
            lower.contains("previous song") || lower.contains("previous track") -> {
                Pair("control_media_playback", JSONObject().put("action", "previous"))
            }
            lower.contains("volume up") || lower.contains("louder") -> {
                Pair("control_media_playback", JSONObject().put("action", "volume_up"))
            }
            lower.contains("volume down") || lower.contains("softer") -> {
                Pair("control_media_playback", JSONObject().put("action", "volume_down"))
            }
            lower.contains("who is calling") || lower.contains("who's calling") -> {
                Pair("control_incoming_call", JSONObject().put("action", "announce"))
            }
            lower.contains("answer the call") || lower.contains("answer call") || lower.contains("pick up") -> {
                Pair("control_incoming_call", JSONObject().put("action", "answer"))
            }
            lower.contains("reject call") || lower.contains("decline call") || lower.contains("hang up") -> {
                Pair("control_incoming_call", JSONObject().put("action", "reject"))
            }
            lower.contains("read my latest whatsapp") || lower.contains("read whatsapp") -> {
                Pair("manage_notification_listener", JSONObject().put("action", "read_latest").put("appName", "whatsapp"))
            }
            lower.contains("read sms") || lower.contains("read messages") || lower.contains("read notifications") -> {
                Pair("manage_notification_listener", JSONObject().put("action", "read_latest").put("appName", "all"))
            }
            lower.contains("turn on auto-reply") || lower.contains("enable auto reply") -> {
                Pair("manage_notification_listener", JSONObject().put("action", "toggle_auto_reply").put("enabled", true))
            }
            lower.contains("turn off auto-reply") || lower.contains("disable auto reply") -> {
                Pair("manage_notification_listener", JSONObject().put("action", "toggle_auto_reply").put("enabled", false))
            }
            lower.startsWith("send whatsapp to ") -> {
                val rest = lower.removePrefix("send whatsapp to ").trim()
                val parts = rest.split(" saying ", limit = 2)
                val contact = parts.getOrNull(0) ?: "Contact"
                val msg = parts.getOrNull(1) ?: "Hello from Spark"
                Pair("send_whatsapp_message", JSONObject().put("contactName", contact).put("message", msg))
            }
            lower.startsWith("send sms to ") || lower.startsWith("text ") -> {
                val rest = if (lower.startsWith("text ")) lower.removePrefix("text ").trim() else lower.removePrefix("send sms to ").trim()
                val parts = rest.split(" saying ", limit = 2)
                val recipient = parts.getOrNull(0) ?: "Recipient"
                val msg = parts.getOrNull(1) ?: "Hello from Spark"
                Pair("send_sms", JSONObject().put("recipient", recipient).put("message", msg))
            }
            lower.startsWith("find ") || lower.startsWith("search contacts ") -> {
                val query = lower.removePrefix("find ").removePrefix("search contacts ").trim()
                Pair("search_contacts", JSONObject().put("query", query))
            }
            lower.startsWith("call ") -> {
                val recipient = lower.removePrefix("call ").trim()
                Pair("make_phone_call", JSONObject().put("recipient", recipient))
            }
            lower.startsWith("open ") -> {
                val app = lower.removePrefix("open ").trim()
                Pair("open_app", JSONObject().put("appName", app))
            }
            lower.startsWith("close ") -> {
                val app = lower.removePrefix("close ").trim()
                Pair("close_app", JSONObject().put("appName", app))
            }
            lower.startsWith("remember ") -> {
                val content = lower.removePrefix("remember ").trim()
                val parts = content.split(" is ", limit = 2)
                val key = parts.getOrNull(0) ?: content
                val value = parts.getOrNull(1) ?: "true"
                Pair("save_core_memory", JSONObject().put("key", key).put("value", value))
            }
            lower.startsWith("what is my ") || lower.startsWith("what's my ") -> {
                val query = lower.removePrefix("what is my ").removePrefix("what's my ").trim()
                Pair("access_core_memory", JSONObject().put("query", query))
            }
            lower.contains("calendar") || lower.contains("schedule today") || lower.contains("what's on my schedule") -> {
                Pair("check_schedule", JSONObject())
            }
            lower.startsWith("search for ") || lower.startsWith("google ") -> {
                val q = lower.removePrefix("search for ").removePrefix("google ").trim()
                Pair("query_web_search", JSONObject().put("query", q))
            }
            else -> null
        }
    }

    fun speak(text: String) {
        if (isTtsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "spark_speech_${System.currentTimeMillis()}")
        }
    }

    fun release() {
        disconnect()
        player.release()
        tts?.stop()
        tts?.shutdown()
    }
}
