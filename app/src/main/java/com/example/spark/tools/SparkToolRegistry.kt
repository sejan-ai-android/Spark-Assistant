package com.example.spark.tools

import android.content.Context
import com.example.spark.data.SparkRepository
import com.example.spark.engine.AppLauncherEngine
import com.example.spark.engine.CalendarEngine
import com.example.spark.engine.HardwareEngine
import com.example.spark.engine.MediaEngine
import com.example.spark.engine.NotificationStore
import com.example.spark.engine.TelephonyEngine
import com.example.spark.model.ToolDefinition
import com.example.spark.model.ToolExecutionResult
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SparkToolRegistry(
    private val context: Context,
    private val repository: SparkRepository,
    val hardwareEngine: HardwareEngine,
    val mediaEngine: MediaEngine,
    val telephonyEngine: TelephonyEngine,
    val appLauncherEngine: AppLauncherEngine,
    val calendarEngine: CalendarEngine
) {

    val toolDefinitions: List<ToolDefinition> = listOf(
        ToolDefinition(
            name = "control_incoming_call",
            description = "Answer, reject, or announce who is calling on incoming phone calls.",
            parametersJson = """{"type":"OBJECT","properties":{"action":{"type":"STRING","description":"Action: answer, reject, or announce"}},"required":["action"]}""",
            examplePrompt = "\"Who is calling?\" / \"Answer the call.\""
        ),
        ToolDefinition(
            name = "manage_notification_listener",
            description = "Read WhatsApp, Instagram, or SMS notifications, or toggle auto-reply.",
            parametersJson = """{"type":"OBJECT","properties":{"action":{"type":"STRING","description":"read_latest or toggle_auto_reply"},"appName":{"type":"STRING","description":"Filter by app name: whatsapp, instagram, sms, or all"},"enabled":{"type":"BOOLEAN","description":"If toggling auto-reply, true to enable, false to disable"}},"required":["action"]}""",
            examplePrompt = "\"Read my latest WhatsApp messages.\" / \"Turn on auto-reply.\""
        ),
        ToolDefinition(
            name = "control_media_playback",
            description = "Play, pause, skip to next song, previous song, or adjust volume for system media playback.",
            parametersJson = """{"type":"OBJECT","properties":{"action":{"type":"STRING","description":"play, pause, toggle, next, previous, volume_up, volume_down, mute, unmute"}},"required":["action"]}""",
            examplePrompt = "\"Pause the music.\" / \"Skip to next song.\""
        ),
        ToolDefinition(
            name = "send_whatsapp_message",
            description = "Send a WhatsApp message to a specific contact or phone number.",
            parametersJson = """{"type":"OBJECT","properties":{"contactName":{"type":"STRING","description":"Name of contact"},"message":{"type":"STRING","description":"Message content"},"phoneNumber":{"type":"STRING","description":"Optional phone number"}},"required":["contactName","message"]}""",
            examplePrompt = "\"Send WhatsApp to Mom saying I'm late.\""
        ),
        ToolDefinition(
            name = "search_contacts",
            description = "Search stored phone contacts by name.",
            parametersJson = """{"type":"OBJECT","properties":{"query":{"type":"STRING","description":"Name to look for"}},"required":["query"]}""",
            examplePrompt = "\"Find Alex.\""
        ),
        ToolDefinition(
            name = "make_phone_call",
            description = "Initiate a phone call to a contact or direct phone number.",
            parametersJson = """{"type":"OBJECT","properties":{"recipient":{"type":"STRING","description":"Name of contact or phone number to dial"}},"required":["recipient"]}""",
            examplePrompt = "\"Call Rahul.\""
        ),
        ToolDefinition(
            name = "send_sms",
            description = "Send an SMS text message to a contact or phone number.",
            parametersJson = """{"type":"OBJECT","properties":{"recipient":{"type":"STRING","description":"Contact name or phone number"},"message":{"type":"STRING","description":"SMS body text"}},"required":["recipient","message"]}""",
            examplePrompt = "\"Send SMS to Rahul: Arriving in 10 minutes.\""
        ),
        ToolDefinition(
            name = "open_deep_link",
            description = "Open a specific URL or deep link (e.g. YouTube search, web page).",
            parametersJson = """{"type":"OBJECT","properties":{"url":{"type":"STRING","description":"Full web URL or deep-link URI"}},"required":["url"]}""",
            examplePrompt = "\"Play Believer on YouTube.\""
        ),
        ToolDefinition(
            name = "open_app",
            description = "Launch an application installed on device (e.g. Spotify, YouTube, Camera, Maps, Settings).",
            parametersJson = """{"type":"OBJECT","properties":{"appName":{"type":"STRING","description":"Name of application to open"}},"required":["appName"]}""",
            examplePrompt = "\"Open Spotify.\""
        ),
        ToolDefinition(
            name = "close_app",
            description = "Dismiss foreground app and return to home screen or Spark Assist.",
            parametersJson = """{"type":"OBJECT","properties":{"appName":{"type":"STRING","description":"Name of app"}},"required":["appName"]}""",
            examplePrompt = "\"Close app.\""
        ),
        ToolDefinition(
            name = "control_device_hardware",
            description = "Toggle or turn on/off flashlight, or open Wi-Fi, Bluetooth, Location, or Hotspot settings.",
            parametersJson = """{"type":"OBJECT","properties":{"action":{"type":"STRING","description":"toggle_flashlight, flashlight_on, flashlight_off, wifi_settings, bluetooth_settings, location_settings, hotspot_settings"}},"required":["action"]}""",
            examplePrompt = "\"Turn on flashlight.\" / \"Open Wi-Fi settings.\""
        ),
        ToolDefinition(
            name = "check_schedule",
            description = "Check calendar events or agenda for today or specified date.",
            parametersJson = """{"type":"OBJECT","properties":{"query":{"type":"STRING","description":"Optional date or search filter"}},"required":[]}""",
            examplePrompt = "\"What's on my calendar today?\""
        ),
        ToolDefinition(
            name = "schedule_new_event",
            description = "Create a new meeting or calendar event with title, time, and location.",
            parametersJson = """{"type":"OBJECT","properties":{"title":{"type":"STRING","description":"Event title"},"timeDescription":{"type":"STRING","description":"When the event takes place"},"location":{"type":"STRING","description":"Optional location"}},"required":["title","timeDescription"]}""",
            examplePrompt = "\"Schedule a meeting with Team tomorrow at 3 PM.\""
        ),
        ToolDefinition(
            name = "save_core_memory",
            description = "Save a persistent piece of user knowledge or context (e.g., parking spot, preferences, facts).",
            parametersJson = """{"type":"OBJECT","properties":{"key":{"type":"STRING","description":"Topic or subject of memory"},"value":{"type":"STRING","description":"Information to remember"},"category":{"type":"STRING","description":"Optional category (e.g. vehicle, home, work, preference)"}},"required":["key","value"]}""",
            examplePrompt = "\"Remember my parking spot is level 2.\""
        ),
        ToolDefinition(
            name = "access_core_memory",
            description = "Retrieve saved memories and persistent information by key or search query.",
            parametersJson = """{"type":"OBJECT","properties":{"query":{"type":"STRING","description":"Keyword or topic to look up"}},"required":["query"]}""",
            examplePrompt = "\"What is my parking spot?\""
        ),
        ToolDefinition(
            name = "query_web_search",
            description = "Search the web for up-to-date live information or news.",
            parametersJson = """{"type":"OBJECT","properties":{"query":{"type":"STRING","description":"Search query"}},"required":["query"]}""",
            examplePrompt = "\"Search for latest AI news.\""
        )
    )

    fun getGeminiToolDeclarationsJson(): JSONArray {
        val array = JSONArray()
        for (tool in toolDefinitions) {
            val toolObj = JSONObject()
            toolObj.put("name", tool.name)
            toolObj.put("description", tool.description)
            toolObj.put("parameters", JSONObject(tool.parametersJson))
            array.put(toolObj)
        }
        return array
    }

    suspend fun executeTool(name: String, args: JSONObject): ToolExecutionResult {
        val result = try {
            when (name) {
                "control_incoming_call" -> {
                    val action = args.optString("action", "announce")
                    val res = telephonyEngine.controlCall(action)
                    ToolExecutionResult(name, res.isSuccess, res.getOrElse { it.message ?: "Call action failed" })
                }
                "manage_notification_listener" -> {
                    val action = args.optString("action", "read_latest")
                    val appFilter = args.optString("appName", "all")
                    if (action == "toggle_auto_reply") {
                        val enabled = args.optBoolean("enabled", !NotificationStore.isAutoReplyEnabled.value)
                        NotificationStore.setAutoReply(enabled)
                        ToolExecutionResult(name, true, "Auto-reply is now ${if (enabled) "ENABLED" else "DISABLED"}")
                    } else {
                        val notes = NotificationStore.getRecent(appFilter)
                        val text = if (notes.isEmpty()) {
                            "No recent notifications found for '$appFilter'."
                        } else {
                            notes.joinToString("; ") { "[${it.appName}] ${it.title}: ${it.text}" }
                        }
                        ToolExecutionResult(name, true, text)
                    }
                }
                "control_media_playback" -> {
                    val action = args.optString("action", "toggle")
                    val res = mediaEngine.controlPlayback(action)
                    ToolExecutionResult(name, res.isSuccess, res.getOrElse { it.message ?: "Playback command failed" })
                }
                "send_whatsapp_message" -> {
                    val contact = args.optString("contactName", "")
                    val message = args.optString("message", "")
                    val phone = if (args.has("phoneNumber")) args.optString("phoneNumber") else null
                    val res = telephonyEngine.sendWhatsApp(contact, message, phone)
                    ToolExecutionResult(name, res.isSuccess, res.getOrElse { it.message ?: "Failed to send WhatsApp" })
                }
                "search_contacts" -> {
                    val query = args.optString("query", "")
                    val contacts = telephonyEngine.searchContacts(query)
                    val text = if (contacts.isEmpty()) {
                        "No contacts found matching '$query'."
                    } else {
                        contacts.joinToString(", ") { "${it.name} (${it.phoneNumber})" }
                    }
                    ToolExecutionResult(name, true, text)
                }
                "make_phone_call" -> {
                    val recipient = args.optString("recipient", "")
                    val res = telephonyEngine.makeCall(recipient)
                    ToolExecutionResult(name, res.isSuccess, res.getOrElse { it.message ?: "Failed to place call" })
                }
                "send_sms", "send_sms_message" -> {
                    val recipient = args.optString("recipient", args.optString("phoneNumber", ""))
                    val message = args.optString("message", "")
                    val res = telephonyEngine.sendSms(recipient, message)
                    ToolExecutionResult(name, res.isSuccess, res.getOrElse { it.message ?: "Failed to send SMS" })
                }
                "open_deep_link" -> {
                    val url = args.optString("url", "")
                    val res = appLauncherEngine.openDeepLink(url)
                    ToolExecutionResult(name, res.isSuccess, res.getOrElse { it.message ?: "Failed to open link" })
                }
                "open_app" -> {
                    val appName = args.optString("appName", "")
                    val res = appLauncherEngine.openApp(appName)
                    ToolExecutionResult(name, res.isSuccess, res.getOrElse { it.message ?: "Failed to launch app" })
                }
                "close_app" -> {
                    val appName = args.optString("appName", "current app")
                    val res = appLauncherEngine.closeApp(appName)
                    ToolExecutionResult(name, res.isSuccess, res.getOrElse { it.message ?: "Failed to close app" })
                }
                "control_device_hardware" -> {
                    val action = args.optString("action", "toggle_flashlight")
                    when {
                        action.contains("flashlight") || action.contains("torch") -> {
                            val desired = if (action.contains("on")) true else if (action.contains("off")) false else null
                            val res = hardwareEngine.toggleFlashlight(desired)
                            val stateStr = if (res.getOrDefault(false)) "ON" else "OFF"
                            ToolExecutionResult(name, res.isSuccess, "Flashlight turned $stateStr")
                        }
                        else -> {
                            val res = hardwareEngine.openSettings(action)
                            ToolExecutionResult(name, res.isSuccess, res.getOrElse { it.message ?: "Failed to open settings" })
                        }
                    }
                }
                "check_schedule" -> {
                    val query = args.optString("query", "")
                    val events = calendarEngine.checkSchedule(query)
                    val dateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                    val text = events.joinToString("; ") {
                        "${it.title} at ${dateFormat.format(Date(it.startTime))}${if (it.location.isNotBlank()) " (${it.location})" else ""}"
                    }
                    ToolExecutionResult(name, true, text)
                }
                "schedule_new_event" -> {
                    val title = args.optString("title", "New Event")
                    val timeDesc = args.optString("timeDescription", "Soon")
                    val loc = args.optString("location", "")
                    val res = calendarEngine.scheduleNewEvent(title, timeDesc, loc)
                    ToolExecutionResult(name, res.isSuccess, res.getOrElse { it.message ?: "Failed to schedule event" })
                }
                "save_core_memory" -> {
                    val key = args.optString("key", "")
                    val value = args.optString("value", "")
                    val category = args.optString("category", "general")
                    repository.saveMemory(key, value, category)
                    ToolExecutionResult(name, true, "Saved to Core Memory: '$key' = '$value'")
                }
                "access_core_memory" -> {
                    val query = args.optString("query", "")
                    val memories = repository.searchMemories(query)
                    val text = if (memories.isEmpty()) {
                        "No memories found for '$query'."
                    } else {
                        memories.joinToString("; ") { "${it.key}: ${it.value}" }
                    }
                    ToolExecutionResult(name, true, text)
                }
                "query_web_search" -> {
                    val query = args.optString("query", "")
                    appLauncherEngine.openDeepLink("https://www.google.com/search?q=" + java.net.URLEncoder.encode(query, "UTF-8"))
                    ToolExecutionResult(name, true, "Searched Google for: \"$query\"")
                }
                else -> {
                    ToolExecutionResult(name, false, "Unknown tool '$name'")
                }
            }
        } catch (e: Exception) {
            ToolExecutionResult(name, false, "Execution error: ${e.message}")
        }

        // Log to Room database for persistent action history
        try {
            repository.logAction(
                toolName = result.toolName,
                argumentsJson = args.toString(),
                resultText = result.resultMessage,
                isSuccess = result.isSuccess
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return result
    }
}
