package com.example.spark.live

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Base64
import com.example.spark.audio.AudioCaptureEngine
import com.example.spark.audio.AudioTrackPlayer
import com.example.spark.model.ConnectionState
import com.example.spark.model.LiveStats
import com.example.spark.model.ToolExecutionResult
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
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
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
        const val DEFAULT_MODEL = "models/gemini-3.5-flash-lite"
        const val SYSTEM_PROMPT = """You are Spark Assistant, a voice-first mobile AI assistant. You execute real actions on the user's Android phone through voice commands. You are fast, zero-friction, and interruption-proof.

Key principles:
- Understand natural language and extract intent immediately
- Use the available tools to perform actions, not just chat
- Keep responses brief and action-oriented
- Announce caller names, read notifications, control media, manage calls, send messages, control hardware, and manage calendar

When a user speaks, determine the appropriate tool and execute it. If the intent is unclear, ask a clarifying question. Always confirm critical actions (like sending messages or making calls) before execution.

Remember: You are a doer, not just a talker. Actions happen live on the device. Your name is Spark."""
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

    // Audio Engines
    val player = AudioTrackPlayer()
    val captureEngine = AudioCaptureEngine { pcmChunk ->
        handleOutgoingPcm(pcmChunk)
    }

    // State Flows
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
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
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isTtsReady = true
        }
    }

    fun setApiKey(key: String) {
        currentApiKey = key.trim()
    }

    fun setModel(model: String) {
        currentModel = model
    }

    fun connect(apiKey: String) {
        if (apiKey.isBlank()) {
            _connectionState.value = ConnectionState.ERROR
            return
        }
        currentApiKey = apiKey.trim()
        disconnect()

        _connectionState.value = ConnectionState.CONNECTING

        val urlWithKey = "$LIVE_WS_URL?key=$currentApiKey"
        val request = Request.Builder().url(urlWithKey).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = ConnectionState.CONNECTED
                sendSetupHandshake(webSocket)
                captureEngine.start()
                _connectionState.value = ConnectionState.STREAMING
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                player.playChunk(bytes.toByteArray())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                t.printStackTrace()
                _connectionState.value = ConnectionState.ERROR
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = ConnectionState.DISCONNECTED
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        })
    }

    fun disconnect() {
        captureEngine.stop()
        player.stopAndFlush()
        try {
            webSocket?.close(1000, "User disconnected")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun sendSetupHandshake(ws: WebSocket) {
        try {
            val setupObj = JSONObject()
            val innerSetup = JSONObject()
            innerSetup.put("model", currentModel)

            // Generation config
            val genConfig = JSONObject()
            val modalities = JSONArray()
            modalities.put("AUDIO")
            modalities.put("TEXT")
            genConfig.put("responseModalities", modalities)
            genConfig.put("temperature", 0.3)

            val speechConfig = JSONObject()
            val voiceConfig = JSONObject()
            val prebuilt = JSONObject()
            prebuilt.put("voiceName", "Aoede")
            voiceConfig.put("prebuiltVoiceConfig", prebuilt)
            speechConfig.put("voiceConfig", voiceConfig)
            genConfig.put("speechConfig", speechConfig)
            innerSetup.put("generationConfig", genConfig)

            // System Instruction
            val sysInst = JSONObject()
            val parts = JSONArray()
            val partText = JSONObject().put("text", SYSTEM_PROMPT)
            parts.put(partText)
            sysInst.put("parts", parts)
            innerSetup.put("systemInstruction", sysInst)

            // Tools Registry
            val toolsArray = JSONArray()
            val toolsObj = JSONObject()
            toolsObj.put("functionDeclarations", toolRegistry.getGeminiToolDeclarationsJson())
            toolsArray.put(toolsObj)
            innerSetup.put("tools", toolsArray)

            setupObj.put("setup", innerSetup)
            ws.send(setupObj.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleOutgoingPcm(chunk: ByteArray) {
        val ws = webSocket ?: return
        if (_connectionState.value != ConnectionState.STREAMING) return

        try {
            val base64Data = Base64.encodeToString(chunk, Base64.NO_WRAP)
            val root = JSONObject()
            val realtimeInput = JSONObject()
            val mediaChunks = JSONArray()
            val chunkObj = JSONObject()
            chunkObj.put("mimeType", "audio/pcm;rate=16000")
            chunkObj.put("data", base64Data)
            mediaChunks.put(chunkObj)
            realtimeInput.put("mediaChunks", mediaChunks)
            root.put("realtimeInput", realtimeInput)

            lastSendTime = System.currentTimeMillis()
            ws.send(root.toString())

            _liveStats.value = _liveStats.value.copy(
                bytesSent = _liveStats.value.bytesSent + chunk.size
            )
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
            root.put("toolResponse", toolResponse)

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

        // First, check direct regex intent matching for instant sub-second local action
        val lower = command.lowercase().trim()
        val localToolMatch = resolveLocalIntent(lower)

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
                ws.send(root.toString())
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Fallback REST call
        callRestFallback(command, speakResult)
    }

    private suspend fun callRestFallback(command: String, speakResult: Boolean) {
        if (currentApiKey.isBlank()) {
            val err = "API Key not configured. Please set your Gemini API Key in Settings or .env."
            _transcriptFlow.emit(Pair(err, false))
            if (speakResult) speak(err)
            return
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
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errMsg = "Gemini Error (${response.code}): $responseString"
                _transcriptFlow.emit(Pair(errMsg, false))
                return
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
            val err = "Request error: ${e.message}"
            _transcriptFlow.emit(Pair(err, false))
        }
    }

    private fun resolveLocalIntent(lower: String): Pair<String, JSONObject>? {
        return when {
            lower.contains("flashlight on") || lower.contains("turn on flashlight") || lower.contains("torch on") -> {
                Pair("control_device_hardware", JSONObject().put("action", "flashlight_on"))
            }
            lower.contains("flashlight off") || lower.contains("turn off flashlight") || lower.contains("torch off") -> {
                Pair("control_device_hardware", JSONObject().put("action", "flashlight_off"))
            }
            lower.contains("toggle flashlight") || lower.contains("flashlight") -> {
                Pair("control_device_hardware", JSONObject().put("action", "toggle_flashlight"))
            }
            lower.contains("wifi") || lower.contains("wi-fi") -> {
                Pair("control_device_hardware", JSONObject().put("action", "wifi_settings"))
            }
            lower.contains("bluetooth") -> {
                Pair("control_device_hardware", JSONObject().put("action", "bluetooth_settings"))
            }
            lower.contains("pause music") || lower.contains("pause the music") || lower.contains("pause playback") -> {
                Pair("control_media_playback", JSONObject().put("action", "pause"))
            }
            lower.contains("play music") || lower.contains("resume music") -> {
                Pair("control_media_playback", JSONObject().put("action", "play"))
            }
            lower.contains("next song") || lower.contains("skip song") -> {
                Pair("control_media_playback", JSONObject().put("action", "next"))
            }
            lower.contains("who is calling") || lower.contains("who's calling") -> {
                Pair("control_incoming_call", JSONObject().put("action", "announce"))
            }
            lower.contains("answer the call") || lower.contains("answer call") -> {
                Pair("control_incoming_call", JSONObject().put("action", "answer"))
            }
            lower.contains("reject call") || lower.contains("decline call") -> {
                Pair("control_incoming_call", JSONObject().put("action", "reject"))
            }
            lower.contains("read my latest whatsapp") || lower.contains("read whatsapp") -> {
                Pair("manage_notification_listener", JSONObject().put("action", "read_latest").put("appName", "whatsapp"))
            }
            lower.contains("turn on auto-reply") || lower.contains("enable auto reply") -> {
                Pair("manage_notification_listener", JSONObject().put("action", "toggle_auto_reply").put("enabled", true))
            }
            lower.startsWith("find ") || lower.startsWith("search contacts ") -> {
                val query = lower.removePrefix("find ").removePrefix("search contacts ").trim()
                Pair("search_contacts", JSONObject().put("query", query))
            }
            lower.startsWith("call ") -> {
                val recipient = lower.removePrefix("call ").trim()
                Pair("make_phone_call", JSONObject().put("recipient", recipient))
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
            lower.contains("calendar") || lower.contains("schedule today") -> {
                Pair("check_schedule", JSONObject())
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
