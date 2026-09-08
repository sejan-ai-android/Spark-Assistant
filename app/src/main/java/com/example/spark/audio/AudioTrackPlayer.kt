package com.example.spark.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.LinkedBlockingQueue

class AudioTrackPlayer(
    private val sampleRate: Int = 24000
) {
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private var audioTrack: AudioTrack? = null
    private val audioQueue = LinkedBlockingQueue<ByteArray>()
    private var playerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _outputAmplitude = MutableStateFlow(0f)
    val outputAmplitude: StateFlow<Float> = _outputAmplitude.asStateFlow()

    fun initialize() {
        if (audioTrack != null) return

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            startPlaybackLoop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startPlaybackLoop() {
        playerJob?.cancel()
        playerJob = scope.launch {
            while (isActive) {
                try {
                    val chunk = audioQueue.poll(50, java.util.concurrent.TimeUnit.MILLISECONDS)
                    if (chunk != null) {
                        _isPlaying.value = true
                        _outputAmplitude.value = calculateQuickRms(chunk)
                        audioTrack?.write(chunk, 0, chunk.size)
                    } else {
                        if (audioQueue.isEmpty()) {
                            _isPlaying.value = false
                            _outputAmplitude.value = 0f
                        }
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun playChunk(pcmBytes: ByteArray) {
        if (audioTrack == null) initialize()
        audioQueue.offer(pcmBytes)
    }

    /**
     * Immediate interruption handling: flushes the queue and stops output instantly.
     */
    fun stopAndFlush() {
        audioQueue.clear()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _isPlaying.value = false
        _outputAmplitude.value = 0f
    }

    fun release() {
        playerJob?.cancel()
        audioQueue.clear()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioTrack = null
        _isPlaying.value = false
    }

    private fun calculateQuickRms(buffer: ByteArray): Float {
        var sum = 0.0
        var i = 0
        while (i < buffer.size - 1) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val shortSample = sample.toShort()
            sum += shortSample * shortSample
            i += 2
        }
        val count = buffer.size / 2
        if (count == 0) return 0f
        return (kotlin.math.sqrt(sum / count).toFloat() / 8000f).coerceIn(0f, 1f)
    }
}
