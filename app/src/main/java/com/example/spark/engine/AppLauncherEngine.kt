package com.example.spark.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import java.net.URLEncoder

class AppLauncherEngine(private val context: Context) {

    fun openApp(appName: String): Result<String> {
        val cleanName = appName.lowercase().trim()

        return try {
            when {
                cleanName.contains("camera") -> {
                    val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    Result.success("Opened Camera")
                }
                cleanName.contains("settings") -> {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    Result.success("Opened System Settings")
                }
                cleanName.contains("youtube") -> {
                    launchPackageOrUrl("com.google.android.youtube", "https://www.youtube.com", "YouTube")
                }
                cleanName.contains("spotify") -> {
                    launchPackageOrUrl("com.spotify.music", "https://open.spotify.com", "Spotify")
                }
                cleanName.contains("whatsapp") -> {
                    launchPackageOrUrl("com.whatsapp", "https://web.whatsapp.com", "WhatsApp")
                }
                cleanName.contains("maps") || cleanName.contains("navigation") -> {
                    launchPackageOrUrl("com.google.android.apps.maps", "https://maps.google.com", "Google Maps")
                }
                cleanName.contains("instagram") -> {
                    launchPackageOrUrl("com.instagram.android", "https://www.instagram.com", "Instagram")
                }
                cleanName.contains("chrome") || cleanName.contains("browser") -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    Result.success("Opened Web Browser")
                }
                else -> {
                    // Try package manager search
                    val pm = context.packageManager
                    val launchIntent = pm.getLaunchIntentForPackage(cleanName)
                    if (launchIntent != null) {
                        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(launchIntent)
                        Result.success("Opened $cleanName")
                    } else {
                        // Search via web query or play store
                        val searchUri = Uri.parse("https://www.google.com/search?q=" + URLEncoder.encode(appName, "UTF-8"))
                        val searchIntent = Intent(Intent.ACTION_VIEW, searchUri).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(searchIntent)
                        Result.success("Searched web for '$appName'")
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openDeepLink(uriString: String): Result<String> {
        return try {
            val uri = Uri.parse(uriString.trim())
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Result.success("Opened deep link: $uriString")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun closeApp(appName: String): Result<String> {
        // Android does not allow 3rd party apps to kill other apps directly without root/system privileges.
        // The standard action is returning to the home launcher screen.
        return try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
            Result.success("Closed $appName and returned to home screen")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun launchPackageOrUrl(packageName: String, fallbackUrl: String, displayName: String): Result<String> {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
        return if (launchIntent != null) {
            launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(launchIntent)
            Result.success("Opened $displayName")
        } else {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(browserIntent)
            Result.success("Opened $displayName Web")
        }
    }
}
