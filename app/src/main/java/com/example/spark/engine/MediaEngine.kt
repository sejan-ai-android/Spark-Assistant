package com.example.spark.engine

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent

class MediaEngine(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun controlPlayback(action: String): Result<String> {
        val am = audioManager ?: return Result.failure(IllegalStateException("AudioManager unavailable"))

        return try {
            when (action.lowercase().trim()) {
                "play" -> {
                    dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
                    Result.success("Playback started")
                }
                "pause", "stop" -> {
                    dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
                    Result.success("Playback paused")
                }
                "toggle", "play_pause", "play/pause" -> {
                    dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    Result.success("Toggled media playback")
                }
                "next", "skip", "next_track" -> {
                    dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                    Result.success("Skipped to next track")
                }
                "prev", "previous", "previous_track" -> {
                    dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                    Result.success("Returned to previous track")
                }
                "volume_up", "raise_volume", "louder" -> {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    Result.success("Raised volume")
                }
                "volume_down", "lower_volume", "quieter" -> {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    Result.success("Lowered volume")
                }
                "mute" -> {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                    Result.success("Muted media")
                }
                "unmute" -> {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                    Result.success("Unmuted media")
                }
                else -> {
                    // Fallback toggle
                    dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    Result.success("Triggered media key event: $action")
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val am = audioManager ?: return
        val eventTime = SystemClock.uptimeMillis()

        val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
        val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)

        am.dispatchMediaKeyEvent(downEvent)
        am.dispatchMediaKeyEvent(upEvent)

        // Broadcast fallback for broader player compatibility
        try {
            val mediaIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(Intent.EXTRA_KEY_EVENT, downEvent)
            }
            context.sendOrderedBroadcast(mediaIntent, null)

            val mediaUpIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(Intent.EXTRA_KEY_EVENT, upEvent)
            }
            context.sendOrderedBroadcast(mediaUpIntent, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
